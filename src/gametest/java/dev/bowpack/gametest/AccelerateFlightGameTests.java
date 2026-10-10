package dev.bowpack.gametest;

import dev.bowpack.projectile.ArrowAccess;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

public class AccelerateFlightGameTests {
    @GameTest(structure = ProjectileFixtures.ARENA)
    public void allLevelsGrowLinearlyWithAndWithoutAntiFriction(GameTestHelper test) {
        for (boolean antiFriction : List.of(false, true)) {
            for (int level = 1; level <= 3; level++) {
                Arrow arrow = ProjectileFixtures.arrow(test, new Vec3(3, 20, 3),
                        new Vec3(0, 0, 3), false, 0, antiFriction);
                ((ArrowAccess) arrow).bowpack$setState(ProjectileFixtures.state(arrow).withAccelerate(level));
                for (int tick = 1; tick <= 45; tick++) {
                    arrow.setPos(ProjectileFixtures.position(test, new Vec3(3, 20, 3)));
                    arrow.tick();
                    ProjectileFixtures.near(test, Math.min(3 + level * 2, 3 + tick * 0.05 * (level + 1)),
                            arrow.getDeltaMovement().length(), "Every native tick adds speed up to the level cap");
                    if (antiFriction) ProjectileFixtures.near(test, 0, arrow.getDeltaMovement().y,
                            "Anti Friction keeps Accelerate level flight horizontal");
                    else test.assertTrue(arrow.getDeltaMovement().y < 0,
                            "Accelerate alone retains the direction change from gravity");
                }
                arrow.discard();
            }
        }
        test.succeed();
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void embeddedAndExpiredArrowsCannotResumeAcceleration(GameTestHelper test) {
        test.setBlock(32, 4, 32, Blocks.STONE);
        Arrow arrow = ProjectileFixtures.arrow(test, new Vec3(3.5, 4.5, 4.5),
                new Vec3(2, 0, 0), false, 0, true);
        ((ArrowAccess) arrow).bowpack$setState(ProjectileFixtures.state(arrow).withAccelerate(3));
        arrow.tick();
        Vec3 position = arrow.position();
        for (int tick = 0; tick < 3; tick++) arrow.tick();
        test.assertValueEqual(arrow.position(), position, "An embedded accelerated arrow stays embedded");
        ProjectileFixtures.near(test, 0, arrow.getDeltaMovement().length(), "Acceleration cannot restart a stopped arrow");
        var expired = ProjectileFixtures.state(arrow).withDeadline(0);
        ((ArrowAccess) arrow).bowpack$setState(expired);
        arrow.tick();
        test.assertFalse(arrow.isAlive(), "Accelerate does not override the existing lifetime deadline");
        test.succeed();
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void nativeSaveLoadPreservesSpeedAndOldDataDefaultsToNoAccelerate(GameTestHelper test) {
        Arrow original = ProjectileFixtures.arrow(test, new Vec3(3, 8, 3),
                new Vec3(0, 0, 3), false, 30, true);
        ((ArrowAccess) original).bowpack$setState(ProjectileFixtures.state(original)
                .withAccelerate(2).withUnstopable(true));
        original.tick();
        original.tick();
        ProjectileFixtures.near(test, 3.3, original.getDeltaMovement().length(),
                "The live arrow reaches its accumulated speed before saving");
        var expected = ProjectileFixtures.state(original);
        CompoundTag data = save(test, original);
        Arrow restored = load(test, data);
        test.assertValueEqual(ProjectileFixtures.state(restored), expected, "Accelerate state and deadline survive reload");
        ProjectileFixtures.near(test, 3.3, restored.getDeltaMovement().length(), "Native motion keeps accumulated speed");
        ArrowAccess flags = (ArrowAccess) restored;
        test.assertValueEqual(flags.bowpack$accelerateLevel(), 2, "Restored acceleration is synchronized");
        test.assertValueEqual(flags.bowpack$remainingBounces(), 30, "Other flag bits cannot inflate the bounce budget");
        test.assertTrue(flags.bowpack$antiFrictionEnabled() && flags.bowpack$unstopableEnabled(),
                "Both public effect flags survive restoration");
        flags.bowpack$spendBounce();
        test.assertValueEqual(flags.bowpack$remainingBounces(), 29, "Spending a bounce only changes its budget");
        test.assertValueEqual(flags.bowpack$accelerateLevel(), 2, "Spending a bounce preserves Accelerate");
        restored.tick();
        ProjectileFixtures.near(test, 3.45, restored.getDeltaMovement().length(), "Reload resumes from the saved speed");
        data.getCompound("bowpack:shot").orElseThrow().remove("accelerate_level");
        Arrow legacy = load(test, data);
        test.assertValueEqual(ProjectileFixtures.state(legacy), expected.withAccelerate(0),
                "Old arrows keep all effects without acquiring Accelerate");
        test.succeed();
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void maximumSpeedSurvivesNativeMotionSerialization(GameTestHelper test) {
        for (Vec3 direction : List.of(new Vec3(1, 0, 0), new Vec3(0, 0, 1), new Vec3(1, 0, 1).normalize())) {
            Arrow original = ProjectileFixtures.arrow(test, new Vec3(3, 4, 3),
                    direction.scale(3), false, 0, true);
            ((ArrowAccess) original).bowpack$setState(ProjectileFixtures.state(original).withAccelerate(3));
            for (int tick = 0; tick < 35; tick++) {
                original.setPos(ProjectileFixtures.position(test, new Vec3(3, 4, 3)));
                original.tick();
            }
            ProjectileFixtures.near(test, 9, original.getDeltaMovement().length(),
                    "The live arrow reaches the level-three speed cap before saving");
            CompoundTag data = save(test, original);
            var problems = new ProblemReporter.Collector();
            Vec3 savedMovement = TagValueInput.create(problems, test.getLevel().registryAccess(), data)
                    .read("Motion", Vec3.CODEC).orElseThrow();
            test.assertTrue(problems.isEmpty(), "Reading saved native motion: " + problems.getReport());
            ProjectileFixtures.near(test, 9, savedMovement.length(), "Native Motion stores the complete capped speed");
            Arrow restored = load(test, data);
            test.assertValueEqual(restored.getDeltaMovement(), original.getDeltaMovement(),
                    "Native loading preserves both axial and diagonal capped velocity exactly");
            test.assertValueEqual(ProjectileFixtures.state(restored), ProjectileFixtures.state(original),
                    "Loading at the speed cap preserves original damage speed and lifetime");
            restored.tick();
            ProjectileFixtures.near(test, 9, restored.getDeltaMovement().length(), "A loaded arrow remains at its cap");
            original.discard();
            restored.discard();
        }
        test.succeed();
    }

    private static CompoundTag save(GameTestHelper test, Arrow arrow) {
        var problems = new ProblemReporter.Collector();
        var output = TagValueOutput.createWithContext(problems, test.getLevel().registryAccess());
        arrow.saveWithoutId(output);
        test.assertTrue(problems.isEmpty(), "Saving accelerated arrow: " + problems.getReport());
        return output.buildResult();
    }

    private static Arrow load(GameTestHelper test, CompoundTag data) {
        var problems = new ProblemReporter.Collector();
        Arrow arrow = new Arrow(EntityTypes.ARROW, test.getLevel());
        arrow.load(TagValueInput.create(problems, test.getLevel().registryAccess(), data));
        test.assertTrue(problems.isEmpty(), "Loading accelerated arrow: " + problems.getReport());
        return arrow;
    }
}
