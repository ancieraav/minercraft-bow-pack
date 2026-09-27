package dev.bowpack.mixin;

import dev.bowpack.BowEnchantments;
import dev.bowpack.projectile.ShotExpansion;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ProjectileWeaponItem.class)
abstract class ProjectileWeaponMixin {
    @Shadow protected abstract Projectile createProjectile(Level level, LivingEntity shooter,
            ItemStack weapon, ItemStack ammo, boolean critical);
    @Shadow protected abstract void shootProjectile(LivingEntity shooter, Projectile arrow, int index,
            float power, float uncertainty, float angle, LivingEntity target);
    @Shadow protected abstract int getDurabilityUse(ItemStack ammo);

    @ModifyVariable(method = "shoot", at = @At("HEAD"), argsOnly = true)
    private List<ItemStack> bowpack$removeLegacyVolley(List<ItemStack> projectiles) {
        if (((Object) this instanceof BowItem || (Object) this instanceof CrossbowItem)
                && projectiles.size() > 1) return List.of(projectiles.getFirst());
        return projectiles;
    }

    @Inject(method = "shoot", at = @At("HEAD"), cancellable = true)
    private void bowpack$shoot(ServerLevel level, LivingEntity shooter, InteractionHand hand,
            ItemStack weapon, List<ItemStack> projectiles, float power, float uncertainty,
            boolean critical, LivingEntity target, CallbackInfo callback) {
        if (!((Object) this instanceof BowItem || (Object) this instanceof CrossbowItem)
                || projectiles.isEmpty() || !(projectiles.getFirst().getItem() instanceof ArrowItem)) return;
        var levels = BowEnchantments.read(weapon, level.registryAccess());
        if (!levels.active()) return;
        ItemStack ammo = projectiles.getFirst();
        AbstractArrow original = (AbstractArrow) createProjectile(level, shooter, weapon, ammo.copy(), critical);
        boolean player = shooter instanceof Player;
        shootProjectile(shooter, original, 0, power, player ? 0 : uncertainty, 0, target);
        var launchVelocity = player ? shooter.getLookAngle().scale(power) : original.getDeltaMovement();
        ShotExpansion.spawn(level, shooter, ammo, launchVelocity, levels, original,
                () -> (AbstractArrow) createProjectile(level, shooter, weapon, ammo.copy(), critical));
        weapon.hurtAndBreak(getDurabilityUse(ammo), shooter, hand.asEquipmentSlot());
        callback.cancel();
    }
}
