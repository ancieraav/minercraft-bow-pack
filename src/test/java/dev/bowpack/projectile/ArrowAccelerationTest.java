package dev.bowpack.projectile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ArrowAccelerationTest {
    @Test
    void everyLevelGrowsLinearlyUntilItsExactCap() {
        for (int level = 1; level <= 3; level++) {
            Vec3 movement = new Vec3(0, 0, 3);
            for (int tick = 1; tick <= 60; tick++) {
                double before = movement.length();
                movement = ArrowAcceleration.accelerate(movement.scale(0.99), before, level);
                assertEquals(Math.min(3 + level * 2, 3 + tick * 0.05 * (level + 1)),
                        movement.length(), 1.0e-9);
            }
        }
    }

    @Test
    void physicsDirectionSurvivesGravityWaterAndReflections() {
        for (Vec3 movement : new Vec3[] {new Vec3(1, -0.05, 0), new Vec3(0, 0, 0.6), new Vec3(-2, 0, 0)}) {
            Vec3 accelerated = ArrowAcceleration.accelerate(movement, 3, 2);
            assertEquals(3.15, accelerated.length(), 1.0e-12);
            assertEquals(1, accelerated.normalize().dot(movement.normalize()), 1.0e-12);
        }
    }

    @Test
    void stoppedMovementIsNotRevivedAndInvalidLevelsAreRejected() {
        assertEquals(Vec3.ZERO, ArrowAcceleration.accelerate(Vec3.ZERO, 3, 1));
        Vec3 gravity = new Vec3(0, -0.05, 0);
        assertEquals(gravity, ArrowAcceleration.accelerate(gravity, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> ArrowAcceleration.accelerate(gravity, 3, 4));
    }
}
