package dev.bowpack.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.bowpack.projectile.MobBowShots;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractSkeleton.class)
abstract class SkeletonBowMixin {
    @Shadow protected abstract AbstractArrow getArrow(ItemStack ammo, float power, ItemStack weapon);

    @WrapOperation(method = "performRangedAttack", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/projectile/Projectile;spawnProjectileUsingShoot(Lnet/minecraft/world/entity/projectile/Projectile;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;DDDFF)Lnet/minecraft/world/entity/projectile/Projectile;"))
    private Projectile bowpack$expandBow(Projectile arrow, ServerLevel level, ItemStack ammo,
            double x, double y, double z, float power, float uncertainty, Operation<Projectile> original,
            @Local(argsOnly = true) float attackPower) {
        AbstractSkeleton shooter = (AbstractSkeleton) (Object) this;
        ItemStack weapon = shooter.getItemInHand(ProjectileUtil.getWeaponHoldingHand(shooter, Items.BOW));
        if (MobBowShots.expand(shooter, arrow, level, ammo, weapon, x, y, z, power, uncertainty,
                () -> getArrow(ammo.copy(), attackPower, weapon))) return arrow;
        return original.call(arrow, level, ammo, x, y, z, power, uncertainty);
    }
}
