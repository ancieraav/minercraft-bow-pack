package dev.bowpack.projectile;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.Vec3;

public final class ArrowFollow {
    public static final double RANGE = 64;
    public static final double MAX_TURN_RADIANS = Math.toRadians(10);

    private ArrowFollow() {}

    public static void steer(AbstractArrow arrow) {
        ShotState state = ((ArrowAccess) arrow).bowpack$getState();
        if (!(arrow.level() instanceof ServerLevel) || state == null || !state.follow()) return;
        Vec3 movement = arrow.getDeltaMovement();
        double speedSquared = movement.lengthSqr();
        if (speedSquared < 1.0e-8) return;
        LivingEntity target = ArrowRicochet.findFollowTarget(arrow, RANGE);
        if (target == null) return;
        Vec3 aim = target.getBoundingBox().getCenter().subtract(arrow.position());
        if (aim.lengthSqr() < 1.0e-8) return;
        double speed = Math.sqrt(speedSquared);
        Vec3 direction = turnToward(movement.scale(1 / speed), aim.normalize(), MAX_TURN_RADIANS);
        Vec3 nextMovement = direction.scale(speed);
        if (nextMovement.distanceToSqr(movement) <= speedSquared * 1.0e-24) return;
        arrow.setDeltaMovement(nextMovement);
        arrow.needsSync = true;
    }

    public static Vec3 turnToward(Vec3 current, Vec3 desired, double maxRadians) {
        double cosine = Math.max(-1, Math.min(1, current.dot(desired)));
        double turnCosine = Math.cos(maxRadians);
        if (maxRadians >= Math.PI || maxRadians >= 0 && cosine >= turnCosine) return desired;
        Vec3 axis = current.cross(desired);
        if (axis.lengthSqr() < 1.0e-12) {
            // A target exactly behind needs a stable turning plane. Turn horizontally first.
            axis = new Vec3(0, 1, 0).subtract(current.scale(current.y));
            if (axis.lengthSqr() < 1.0e-12) {
                axis = new Vec3(1, 0, 0).subtract(current.scale(current.x));
            }
        }
        axis = axis.normalize();
        return current.scale(turnCosine)
                .add(axis.cross(current).scale(Math.sin(maxRadians))).normalize();
    }
}
