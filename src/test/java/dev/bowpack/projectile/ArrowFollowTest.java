package dev.bowpack.projectile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ArrowFollowTest {
    @Test
    void turnsAtMostTenDegreesTowardARearTarget() {
        Vec3 forward = new Vec3(0, 0, 1);
        Vec3 backward = new Vec3(0, 0, -1);
        Vec3 next = ArrowFollow.turnToward(forward, backward, ArrowFollow.MAX_TURN_RADIANS);
        assertEquals(1, next.length(), 1.0e-12);
        assertEquals(10, Math.toDegrees(Math.acos(forward.dot(next))), 1.0e-9);
        assertTrue(next.dot(backward) > forward.dot(backward));
    }

    @Test
    void snapsToTargetWhenWithinTurnLimit() {
        Vec3 forward = new Vec3(0, 0, 1);
        Vec3 desired = new Vec3(0.1, 0, 1).normalize();
        assertEquals(desired, ArrowFollow.turnToward(forward, desired, ArrowFollow.MAX_TURN_RADIANS));
    }
}
