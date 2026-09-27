package dev.bowpack.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.bowpack.projectile.ArrowAccess;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
abstract class LivingDamageMixin {
    @WrapOperation(method = "hurtServer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/damagesource/DamageSource;is(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean bowpack$countEachArrow(DamageSource source, TagKey<DamageType> tag,
                                           Operation<Boolean> original) {
        return original.call(source, tag) || (tag == DamageTypeTags.BYPASSES_COOLDOWN
                && source.getDirectEntity() instanceof AbstractArrow arrow
                && ((ArrowAccess) arrow).bowpack$getState() != null);
    }
}
