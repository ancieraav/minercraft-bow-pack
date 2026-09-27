package dev.bowpack.projectile;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** The arrow and weapon effects from the latest successful bow hit. */
record RainyPayload(ItemStack ammo, @Nullable ItemStack weapon, double baseDamage,
                    boolean critical, int fireTicks) {
    static @Nullable RainyPayload capture(AbstractArrow source) {
        ItemStack ammo = source.getPickupItemStackOrigin();
        if (!(ammo.getItem() instanceof ArrowItem)) return null;
        ItemStack weapon = source.getWeaponItem();
        return new RainyPayload(ammo.copyWithCount(1), weapon == null ? null : weapon.copy(),
                ((RainyAccess) source).bowpack$getBaseDamage(), source.isCritArrow(),
                source.getRemainingFireTicks());
    }

    AbstractArrow create(ServerLevel level, LivingEntity shooter) {
        ArrowItem item = (ArrowItem) ammo.getItem();
        AbstractArrow arrow = item.createArrow(level, ammo.copy(), shooter,
                weapon == null ? null : weapon.copy());
        arrow.setBaseDamage(baseDamage);
        arrow.setCritArrow(critical);
        arrow.setRemainingFireTicks(fireTicks);
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        return arrow;
    }
}
