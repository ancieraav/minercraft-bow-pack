package dev.bowpack.projectile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A penetrating arrow creates one non-penetrating Ricochet branch per eligible hit. */
public final class ArrowBreakthrough {
    private static final Logger LOGGER = LoggerFactory.getLogger("BowPack");

    private ArrowBreakthrough() {}

    public static void spawnRicochetBranch(AbstractArrow source, LivingEntity hit,
                                           List<UUID> hitGroup, LivingEntity target, Vec3 impact) {
        if (!(source.level() instanceof ServerLevel level)) return;
        ShotState state = ((ArrowAccess) source).bowpack$getState();
        if (state == null || !state.penetrates() || !state.canRicochet()) {
            throw new IllegalStateException("Only a penetrating Ricochet arrow can create a branch");
        }
        if (!(level.getEntity(state.shooter()) instanceof LivingEntity shooter)) {
            LOGGER.warn("Cannot create Ricochet branch for arrow {}: shooter {} is unavailable",
                    source.getUUID(), state.shooter());
            return;
        }
        ItemStack ammo = source.getPickupItemStackOrigin();
        ItemStack weapon = source.getWeaponItem();
        if (!(ammo.getItem() instanceof ArrowItem arrowItem) || weapon == null) {
            LOGGER.warn("Cannot create Ricochet branch for arrow {}: arrow ammunition or weapon is unavailable",
                    source.getUUID());
            return;
        }
        AbstractArrow branch = arrowItem.createArrow(level, ammo.copyWithCount(1), shooter, weapon.copy());
        branch.setBaseDamage(((RainyAccess) source).bowpack$getBaseDamage());
        branch.setCritArrow(source.isCritArrow());
        branch.setRemainingFireTicks(source.getRemainingFireTicks());
        branch.pickup = AbstractArrow.Pickup.DISALLOWED;
        Optional<UUID> apparent = hit instanceof net.minecraft.world.entity.Mob
                ? Optional.of(hit.getUUID()) : Optional.empty();
        ShotState branchState = state.withoutBreakthrough().spendRicochet(apparent)
                .ignoreGroup(hit.getUUID(), hitGroup);
        ((ArrowAccess) branch).bowpack$setState(branchState);
        ArrowRicochet.redirect(branch, target, impact);
        Projectile.spawnProjectile(branch, level, ammo.copyWithCount(1));
    }
}
