package dev.bowpack.projectile;

import dev.bowpack.ShotLevels;
import dev.bowpack.math.ShotPattern;
import dev.bowpack.math.ShotVector;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class ShotExpansion {
    private ShotExpansion() {}

    public static void spawn(ServerLevel level, LivingEntity shooter, ItemStack ammo, Vec3 launchVelocity,
                             ShotLevels levels, AbstractArrow original, Supplier<AbstractArrow> create) {
        double speed = launchVelocity.length();
        float yaw = launchVelocity.horizontalDistanceSqr() < 1e-12 ? shooter.getYRot()
                : (float) Math.toDegrees(Math.atan2(-launchVelocity.x, launchVelocity.z));
        Vec3 origin = original.position();
        var pattern = ShotPattern.generate(new ShotVector(launchVelocity.x, launchVelocity.y, launchVelocity.z),
                speed, yaw, levels.multi(), levels.front(), levels.diagonal(), levels.back());
        ShotState state = new ShotState(levels.ricochet(), levels.bouncy() * 10,
                levels.antiFriction(), levels.breakthrough(), levels.follow(),
                speed, shooter.getUUID(), Optional.empty())
                .withUnstopable(levels.unstopable())
                .withDeadline(ArrowLifetime.deadline(level));
        for (int index = 0; index < pattern.size(); index++) {
            var shot = pattern.get(index);
            AbstractArrow arrow = index == 0 ? original : create.get();
            ((ArrowAccess) arrow).bowpack$setState(state);
            Vec3 position = origin.add(shot.offset().x(), shot.offset().y(), shot.offset().z());
            var obstruction = level.clip(new ClipContext(origin, position,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, arrow));
            if (obstruction.getType() != HitResult.Type.MISS) {
                // Never create an offset projectile through a wall or inside a solid block.
                position = obstruction.getLocation().add(Vec3.atLowerCornerOf(obstruction.getDirection().getUnitVec3i()).scale(0.01));
            }
            arrow.setPos(position);
            arrow.shoot(shot.velocity().x(), shot.velocity().y(), shot.velocity().z(), (float) speed, 0);
            arrow.setDeltaMovement(new Vec3(shot.velocity().x(), shot.velocity().y(), shot.velocity().z()));
            arrow.setNoGravity(levels.antiFriction());
            if (index != 0) arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
            Projectile.spawnProjectile(arrow, level, ammo.copy());
        }
    }
}
