package dev.bowpack.mixin;

import dev.bowpack.projectile.ArrowAccess;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractArrow.class)
abstract class ArrowPhysicsMixin {
    @Inject(method = "getAirDrag", at = @At("HEAD"), cancellable = true)
    private void bowpack$removeAirDrag(CallbackInfoReturnable<Float> callback) {
        if (bowpack$antiFriction()) callback.setReturnValue(1.0F);
    }

    @Inject(method = "getWaterInertia", at = @At("HEAD"), cancellable = true)
    private void bowpack$removeWaterDrag(CallbackInfoReturnable<Float> callback) {
        if (bowpack$antiFriction()) callback.setReturnValue(1.0F);
    }

    private boolean bowpack$antiFriction() {
        return ((ArrowAccess) this).bowpack$antiFrictionEnabled();
    }
}
