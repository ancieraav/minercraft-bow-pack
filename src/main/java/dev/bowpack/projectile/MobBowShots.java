package dev.bowpack.projectile;

import dev.bowpack.BowEnchantments;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;

/** Expands the mob's native ballistic shot before its first world insertion. */
public final class MobBowShots {
    private MobBowShots() {}

    public static boolean expand(LivingEntity shooter, Projectile projectile, ServerLevel level,
                                 ItemStack ammo, ItemStack weapon, double x, double y, double z,
                                 float power, float uncertainty, Supplier<AbstractArrow> create) {
        var levels = BowEnchantments.read(weapon, level.registryAccess());
        if (!levels.active() || !(projectile instanceof AbstractArrow arrow)) return false;
        arrow.shoot(x, y, z, power, uncertainty);
        ShotExpansion.spawn(level, shooter, ammo, arrow.getDeltaMovement(), levels, arrow, create);
        return true;
    }
}
