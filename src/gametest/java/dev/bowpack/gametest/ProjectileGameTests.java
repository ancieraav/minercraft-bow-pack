package dev.bowpack.gametest;

import dev.bowpack.projectile.ArrowAccess;
import dev.bowpack.projectile.ShotState;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

public class ProjectileGameTests {
    @GameTest(structure = ProjectileFixtures.ARENA)
    public void successiveManagedArrowsBothDamageTheSameMob(GameTestHelper test) {
        Mob target = ProjectileFixtures.cow(test, new Vec3(4, 2, 5));
        float start = target.getHealth();
        Arrow first = ProjectileFixtures.arrow(test, new Vec3(4, 2.6, 3),
                new Vec3(0, 0, 3), false, 0, false);
        first.tick();
        float afterFirst = target.getHealth();
        test.assertTrue(afterFirst < start, "The first real collision must deal damage");
        Arrow second = ProjectileFixtures.arrow(test, new Vec3(4, 2.6, 3),
                new Vec3(0, 0, 3), false, 0, false);
        second.tick();
        test.assertTrue(target.getHealth() < afterFirst,
                "The next managed arrow must also damage the same mob immediately");
        test.succeed();
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void eachBouncyLevelStopsAfterItsExactBudget(GameTestHelper test) {
        test.setBlock(32, 4, 32, Blocks.STONE);
        for (int budget : new int[] {10, 20, 30}) {
            Arrow arrow = ProjectileFixtures.arrow(test, new Vec3(3.5, 4.5, 4.5),
                    new Vec3(1, 0, 0), false, budget, true);
            for (int hit = 0; hit < budget; hit++) {
                arrow.setPos(ProjectileFixtures.position(test, new Vec3(3.5, 4.5, 4.5)));
                arrow.setDeltaMovement(new Vec3(1, 0, 0));
                arrow.tick();
                test.assertTrue(arrow.getDeltaMovement().x < 0, "Wall collision should reflect X");
                test.assertValueEqual(ProjectileFixtures.state(arrow).bounces(), budget - hit - 1,
                        "Every actual collision consumes one bounce");
            }
            arrow.setPos(ProjectileFixtures.position(test, new Vec3(3.5, 4.5, 4.5)));
            arrow.setDeltaMovement(new Vec3(1, 0, 0));
            arrow.tick();
            ProjectileFixtures.near(test, 0, arrow.getDeltaMovement().length(),
                    "An exhausted arrow embeds in the block");
            arrow.discard();
        }
        test.succeed();
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void floorAndCeilingReflectWhileGravityStillActs(GameTestHelper test) {
        test.setBlock(30, 1, 30, Blocks.STONE);
        test.setBlock(33, 5, 33, Blocks.STONE);
        Arrow floor = ProjectileFixtures.arrow(test, new Vec3(2.5, 2.5, 2.5),
                new Vec3(0, -1, 0), false, 2, false);
        Arrow ceiling = ProjectileFixtures.arrow(test, new Vec3(5.5, 4.5, 5.5),
                new Vec3(0, 1, 0), false, 2, false);
        floor.tick();
        ceiling.tick();
        test.assertTrue(floor.getDeltaMovement().y > 0, "Floor impact reflects upward");
        test.assertTrue(ceiling.getDeltaMovement().y < 0, "Ceiling impact reflects downward");
        ProjectileFixtures.near(test, 0.94, floor.getDeltaMovement().y, "Gravity acts after floor bounce");
        ProjectileFixtures.near(test, -1.04, ceiling.getDeltaMovement().y, "Gravity acts after ceiling bounce");
        test.succeed();
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void antiFrictionPreservesHorizontalAndUpwardDirections(GameTestHelper test) {
        Arrow horizontal = ProjectileFixtures.arrow(test, new Vec3(1, 4, 1),
                new Vec3(0.4, 0, 0), false, 0, true);
        Arrow upward = ProjectileFixtures.arrow(test, new Vec3(5, 2, 5),
                new Vec3(0, 0.4, 0), false, 0, true);
        double initialY = horizontal.getY();
        for (int tick = 0; tick < 4; tick++) {
            horizontal.tick();
            upward.tick();
        }
        ProjectileFixtures.near(test, initialY, horizontal.getY(), "Horizontal arrow cannot fall");
        ProjectileFixtures.near(test, 0, horizontal.getDeltaMovement().y, "No vertical acceleration");
        test.assertTrue(upward.getDeltaMovement().y > 0, "Upward arrow continues upward");
        ProjectileFixtures.near(test, 0, upward.getDeltaMovement().horizontalDistance(), "No sideways drift");
        test.succeed();
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void nativeSaveLoadPreservesProjectileEffectsAndRemainingBudget(GameTestHelper test) {
        Arrow original = ProjectileFixtures.arrow(test, new Vec3(3, 4, 3),
                new Vec3(1, 0, 0), true, 3, true);
        UUID hit = UUID.randomUUID();
        UUID companion = UUID.randomUUID();
        ShotState expected = ProjectileFixtures.state(original).ignoreGroup(hit, List.of(hit, companion));
        ((ArrowAccess) original).bowpack$setState(expected);
        ProblemReporter.Collector problems = new ProblemReporter.Collector();
        var output = TagValueOutput.createWithContext(problems, test.getLevel().registryAccess());
        original.saveWithoutId(output);
        Arrow restored = new Arrow(EntityTypes.ARROW, test.getLevel());
        restored.load(TagValueInput.create(problems, test.getLevel().registryAccess(), output.buildResult()));
        test.assertTrue(problems.isEmpty(), "Native serialization errors: " + problems.getReport());
        test.assertValueEqual(ProjectileFixtures.state(restored), expected, "All effect state survives reload");
        test.assertTrue(restored.isNoGravity(), "Reloaded arrow retains Anti Friction");
        var legacyTag = output.buildResult();
        legacyTag.getCompound("bowpack:shot").orElseThrow().remove("ignored_companions");
        Arrow legacy = new Arrow(EntityTypes.ARROW, test.getLevel());
        legacy.load(TagValueInput.create(problems, test.getLevel().registryAccess(), legacyTag));
        test.assertTrue(problems.isEmpty(), "Old saved arrows must remain readable: " + problems.getReport());
        test.assertValueEqual(ProjectileFixtures.state(legacy), expected.ignore(Optional.of(hit)),
                "Version 1.0.0 arrow data preserves its original hit immunity without companions");
        test.succeed();
    }
}
