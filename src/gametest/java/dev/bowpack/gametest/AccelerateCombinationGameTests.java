package dev.bowpack.gametest;

import dev.bowpack.projectile.ArrowAccess;
import dev.bowpack.projectile.ShotState;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public class AccelerateCombinationGameTests {
    @GameTest(structure = ProjectileFixtures.ARENA)
    public void breakingThenBouncingPreservesTheSpeedRampAndBounceBudget(GameTestHelper test) {
        BlockPos dirt = new BlockPos(32, 4, 32);
        BlockPos resistant = new BlockPos(36, 4, 32);
        test.setBlock(dirt, Blocks.DIRT);
        test.setBlock(resistant, Blocks.OBSIDIAN);
        var owner = ProjectileFixtures.cow(test, new Vec3(1, 2, 12));
        Arrow arrow = UnstopableGameTests.arrow(test, owner, 3);
        ((ArrowAccess) arrow).bowpack$setState(ProjectileFixtures.state(arrow).withAccelerate(2));
        long deadline = ProjectileFixtures.state(arrow).expiresAt().orElseThrow();
        arrow.tick();
        test.assertTrue(test.getLevel().getBlockState(test.absolutePos(dirt)).isAir(),
                "The first collision actually destroys the dirt block");
        test.assertTrue(arrow.getDeltaMovement().x > 0, "Breaking a block keeps the arrow flying forward");
        test.assertValueEqual(ProjectileFixtures.state(arrow).bounces(), 3,
                "Unstopable does not spend Bouncy charges on a successful break");
        ProjectileFixtures.near(test, 2.15, arrow.getDeltaMovement().length(),
                "A destructive impact still receives one acceleration step");
        int ticks = 1;
        while (arrow.getDeltaMovement().x > 0 && ticks < 6) {
            arrow.tick();
            ticks++;
            ProjectileFixtures.near(test, 2 + 0.15 * ticks, arrow.getDeltaMovement().length(),
                    "Forward flight and the later reflection preserve continuous acceleration");
        }
        test.assertBlockPresent(Blocks.OBSIDIAN, resistant);
        test.assertTrue(arrow.getDeltaMovement().x < 0, "The resistant block causes a real Bouncy reflection");
        test.assertValueEqual(ProjectileFixtures.state(arrow).bounces(), 2,
                "Only the resistant impact spends one Bouncy charge");
        test.assertValueEqual(ProjectileFixtures.state(arrow).expiresAt().orElseThrow(), deadline,
                "Neither kind of block impact resets the lifetime");
        test.succeed();
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void followSteersAtItsTurnLimitWhileAccelerateIncreasesSpeed(GameTestHelper test) {
        var target = ProjectileFixtures.cow(test, new Vec3(10, 4, 20));
        Vec3 start = new Vec3(20, 4.5, 20);
        Arrow arrow = ProjectileFixtures.arrow(test, start, new Vec3(1, 0, 0), false, 0, true);
        ((ArrowAccess) arrow).bowpack$setState(new ShotState(0, 0, true,
                false, true, 1, UUID.randomUUID(), Optional.empty()).withAccelerate(3));
        for (int tick = 1; tick <= 4; tick++) {
            arrow.setPos(ProjectileFixtures.position(test, start));
            Vec3 previousDirection = arrow.getDeltaMovement().normalize();
            Vec3 desiredDirection = target.getBoundingBox().getCenter().subtract(arrow.position()).normalize();
            arrow.tick();
            Vec3 direction = arrow.getDeltaMovement().normalize();
            double degrees = Math.toDegrees(Math.acos(Math.clamp(previousDirection.dot(direction), -1, 1)));
            test.assertTrue(degrees > 9.99 && degrees < 10.01,
                    "Acceleration preserves Follow's ten-degree turn limit: " + degrees);
            test.assertTrue(direction.dot(desiredDirection) > previousDirection.dot(desiredDirection),
                    "Each real native tick steers closer to the target direction");
            ProjectileFixtures.near(test, 1 + tick * 0.2, arrow.getDeltaMovement().length(),
                    "Follow's direction change does not interrupt the acceleration ramp");
        }
        test.succeed();
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void everyLevelAcceleratesDuringActualUnderwaterTicks(GameTestHelper test) {
        for (int x = 29; x <= 40; x++) {
            for (int y = 3; y <= 6; y++) {
                for (int z = 29; z <= 31; z++) test.setBlock(x, y, z, Blocks.WATER);
            }
        }
        Vec3 start = new Vec3(1.5, 4.5, 2.5);
        Arrow control = ProjectileFixtures.arrow(test, start, new Vec3(1, 0, 0), false, 0, false);
        control.tick();
        test.assertTrue(control.isInWater() && control.getDeltaMovement().length() < 1,
                "The fixture exercises native underwater drag without Anti Friction");
        control.discard();
        double[] increments = {0.10, 0.15, 0.20};
        for (int level = 1; level <= 3; level++) {
            Arrow arrow = ProjectileFixtures.arrow(test, start, new Vec3(1, 0, 0), false, 0, false);
            ((ArrowAccess) arrow).bowpack$setState(ProjectileFixtures.state(arrow).withAccelerate(level));
            for (int tick = 1; tick <= 4; tick++) {
                arrow.setPos(ProjectileFixtures.position(test, start));
                arrow.tick();
                test.assertTrue(arrow.isInWater(), "Each accelerated flight tick takes place underwater");
                ProjectileFixtures.near(test, 1 + tick * increments[level - 1], arrow.getDeltaMovement().length(),
                        "The level's acceleration offsets water drag while increasing speed");
                test.assertTrue(arrow.getDeltaMovement().y < 0, "Underwater acceleration retains gravity's direction change");
            }
            arrow.discard();
        }
        test.succeed();
    }
}
