package dev.bowpack.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

public class LegacyShotStateGameTests {
    @GameTest(structure = ProjectileFixtures.ARENA)
    public void ordinaryOldArrowKeepsItsStateWithoutRainyEffect(GameTestHelper test) {
        Arrow arrow = ProjectileFixtures.arrow(test, new Vec3(2, 4, 2),
                new Vec3(0.2, 0, 0), 2, 4, true);
        var expected = ProjectileFixtures.state(arrow);
        CompoundTag oldData = save(test, arrow);
        var shot = oldData.getCompound("bowpack:shot").orElseThrow();
        shot.remove("accelerate_level");
        shot.putInt("rainy_level", 3);
        shot.putBoolean("rainy_projectile", false);
        Arrow restored = load(test, oldData);
        test.assertTrue(restored.isAlive(), "An ordinary saved arrow must still load");
        test.assertValueEqual(ProjectileFixtures.state(restored).accelerateLevel(), 0,
                "Saved arrows predating Accelerate must retain ordinary flight");
        test.assertValueEqual(ProjectileFixtures.state(restored), expected,
                "Old Rainy level must have no effect on a regular arrow");
        CompoundTag migrated = save(test, restored).getCompound("bowpack:shot").orElseThrow();
        test.assertFalse(migrated.contains("rainy_level") || migrated.contains("rainy_projectile"),
                "The next save must remove obsolete Rainy fields");
        test.succeed();
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void obsoleteRainyProjectilesAreDiscardedOnLoad(GameTestHelper test) {
        Arrow arrow = ProjectileFixtures.arrow(test, new Vec3(2, 4, 2),
                new Vec3(0.2, 0, 0), false, 0, true);
        CompoundTag generated = save(test, arrow);
        generated.getCompound("bowpack:shot").orElseThrow().putBoolean("rainy_projectile", true);
        test.assertFalse(load(test, generated).isAlive(), "A generated Rainy arrow cannot resume flying");
        CompoundTag hovering = save(test, arrow);
        hovering.putString("bowpack:rain_target", "obsolete-target");
        test.assertFalse(load(test, hovering).isAlive(), "A hovering Rainy arrow cannot resume flying");
        test.succeed();
    }

    private static CompoundTag save(GameTestHelper test, Arrow arrow) {
        ProblemReporter.Collector problems = new ProblemReporter.Collector();
        var output = TagValueOutput.createWithContext(problems, test.getLevel().registryAccess());
        arrow.saveWithoutId(output);
        test.assertTrue(problems.isEmpty(), "Native serialization errors: " + problems.getReport());
        return output.buildResult();
    }

    private static Arrow load(GameTestHelper test, CompoundTag data) {
        ProblemReporter.Collector problems = new ProblemReporter.Collector();
        Arrow arrow = new Arrow(EntityTypes.ARROW, test.getLevel());
        arrow.load(TagValueInput.create(problems, test.getLevel().registryAccess(), data));
        test.assertTrue(problems.isEmpty(), "Native loading errors: " + problems.getReport());
        return arrow;
    }
}
