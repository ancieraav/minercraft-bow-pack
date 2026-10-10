package dev.bowpack.projectile;

import net.minecraft.world.phys.Vec3;

/** Adds constant speed each tick while retaining the direction produced by native physics. */
public final class ArrowAcceleration {
    private ArrowAcceleration() {}

    public static Vec3 accelerate(Vec3 movement, double previousSpeed, int level) {
        if (level < 1 || level > 3) throw new IllegalArgumentException("Unsupported Accelerate level: " + level);
        double speed = movement.length();
        if (previousSpeed < 1.0e-8 || speed < 1.0e-8) return movement;
        double nextSpeed = Math.min(3 + level * 2, previousSpeed + 0.05 * (level + 1));
        return movement.scale(nextSpeed / speed);
    }
}
