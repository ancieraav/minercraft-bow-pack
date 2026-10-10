package dev.bowpack.projectile;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class ArrowRicochet {
    private ArrowRicochet() {}

    public static List<UUID> collisionGroup(Entity hit) {
        // Snapshot before damage: a lethal hit can dismount passengers immediately.
        return hit.getRootVehicle().getSelfAndPassengers().map(Entity::getUUID).toList();
    }

    public static @Nullable LivingEntity findTarget(AbstractArrow arrow, List<UUID> hitGroup, Vec3 impact) {
        ShotState state = ((ArrowAccess) arrow).bowpack$getState();
        if (state == null || !state.canRicochet() || !(arrow.level() instanceof ServerLevel level)) return null;
        double radius = state.ricochetRadius();
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(impact, impact).inflate(radius),
                        target -> validTarget(arrow, target, hitGroup, state, impact, radius, false))
                .stream().min(Comparator.comparingDouble((LivingEntity target) -> target.distanceToSqr(impact))
                        .thenComparing(Entity::getUUID)).orElse(null);
    }

    public static @Nullable LivingEntity findFollowTarget(AbstractArrow arrow, double radius) {
        ShotState state = ((ArrowAccess) arrow).bowpack$getState();
        if (state == null || !state.follow()
                || !(arrow.level() instanceof ServerLevel level)) return null;
        Vec3 position = arrow.position();
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(position, position).inflate(radius),
                        target -> validTarget(arrow, target, List.of(), state, position, radius, true))
                .stream().min(Comparator.comparingDouble((LivingEntity target) -> target.distanceToSqr(position))
                        .thenComparing(Entity::getUUID)).orElse(null);
    }

    private static boolean validTarget(AbstractArrow arrow, LivingEntity target, List<UUID> hitGroup,
                                       ShotState state, Vec3 impact, double radius, boolean follow) {
        // A rider and its mount share the impact area; redirect out of that whole group.
        if (!(target instanceof Mob || target instanceof Player) || hitGroup.contains(target.getUUID())
                || follow && state.ignores(target.getUUID())
                || target.getUUID().equals(state.shooter()) || !target.isAlive() || target.isSpectator()
                || !target.canBeHitByProjectile() || target.distanceToSqr(impact) > radius * radius
                || target.isInvulnerable()) return false;
        if (target instanceof Player player) {
            if (player.getAbilities().invulnerable) return false;
            if (arrow.getOwner() instanceof Player owner && !owner.canHarmPlayer(player)) return false;
        }
        return target.getBoundingBox().getCenter().distanceToSqr(impact) > 1.0e-8;
    }

    public static void redirect(AbstractArrow arrow, LivingEntity target, Vec3 impact) {
        ShotState state = ((ArrowAccess) arrow).bowpack$getState();
        if (state == null) throw new IllegalStateException("Cannot redirect an unmanaged arrow");
        Vec3 direction = target.getBoundingBox().getCenter().subtract(impact).normalize();
        double speed = state.accelerateLevel() > 0 ? arrow.getDeltaMovement().length() : state.launchSpeed();
        arrow.setPos(impact);
        // Aim once. Normal gravity/drag can make this shot fall short of the target.
        arrow.shoot(direction.x, direction.y, direction.z, (float) speed, 0);
        if (state.accelerateLevel() > 0) arrow.setDeltaMovement(direction.scale(speed));
        arrow.setNoGravity(state.antiFriction());
        arrow.needsSync = true;
    }

    public static void releaseHitImmunity(AbstractArrow arrow) {
        ArrowAccess access = (ArrowAccess) arrow;
        ShotState state = access.bowpack$getState();
        if (state == null || state.ignoredEntity().isEmpty()
                || !(arrow.level() instanceof ServerLevel level)) return;
        boolean insideGroup = Stream.concat(state.ignoredEntity().stream(), state.ignoredCompanions().stream())
                .map(level::getEntity)
                .anyMatch(ignored -> ignored != null
                        && ignored.getBoundingBox().inflate(0.3).intersects(arrow.getBoundingBox()));
        if (!insideGroup) {
            access.bowpack$setState(state.ignore(Optional.empty()));
        }
    }
}
