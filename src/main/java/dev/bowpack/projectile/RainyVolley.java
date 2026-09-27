package dev.bowpack.projectile;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Prepares a full row before inserting any arrow, so obstruction skips the whole volley. */
final class RainyVolley {
    private static final double HEIGHT = 4;
    private static final double SPACING = 0.75;

    private RainyVolley() {}

    static void spawn(ServerLevel level, LivingEntity shooter, LivingEntity target, int rainyLevel,
                      RainyPayload payload, long now) {
        int count = switch (rainyLevel) {
            case 1 -> 6;
            case 2 -> 8;
            case 3 -> 10;
            default -> throw new IllegalArgumentException("Unsupported Rainy Shot level: " + rainyLevel);
        };
        Vec3 horizontal = target.position().subtract(shooter.position()).multiply(1, 0, 1);
        if (horizontal.lengthSqr() < 1.0e-8) {
            horizontal = target.getLookAngle().multiply(1, 0, 1);
        }
        Vec3 lateral = horizontal.lengthSqr() < 1.0e-8 ? new Vec3(1, 0, 0)
                : new Vec3(-horizontal.z, 0, horizontal.x).normalize();
        Vec3 targetCenter = target.getBoundingBox().getCenter();
        Vec3 rowCenter = new Vec3(targetCenter.x, target.getBoundingBox().maxY + HEIGHT, targetCenter.z);
        ShotState state = new ShotState(0, 0, true, false, 0, false,
                RainyShot.FALL_SPEED, shooter.getUUID(), Optional.empty())
                .asRainyProjectile().withDeadline(ArrowLifetime.deadline(level));
        List<AbstractArrow> prepared = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            AbstractArrow arrow = payload.create(level, shooter);
            Vec3 position = rowCenter.add(lateral.scale((index - (count - 1) / 2.0) * SPACING));
            arrow.setPos(position);
            if (!level.noCollision(arrow, arrow.getBoundingBox())
                    || level.clip(new ClipContext(position, targetCenter,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, arrow)).getType()
                    != HitResult.Type.MISS) return;
            ((ArrowAccess) arrow).bowpack$setState(state);
            ((RainyAccess) arrow).bowpack$startRainyHover(target.getUUID(), now + RainyShot.HOVER_TICKS);
            prepared.add(arrow);
        }
        for (AbstractArrow arrow : prepared) {
            Projectile.spawnProjectile(arrow, level, payload.ammo().copy());
        }
    }
}
