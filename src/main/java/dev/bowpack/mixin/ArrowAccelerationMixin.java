package dev.bowpack.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.bowpack.projectile.ArrowAcceleration;
import dev.bowpack.projectile.ArrowAccess;
import dev.bowpack.projectile.ShotState;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractArrow.class)
abstract class ArrowAccelerationMixin {
    @Shadow protected abstract boolean isInGround();
    @Unique private double bowpack$previousSpeed;

    @Inject(method = "tick", at = @At("HEAD"))
    private void bowpack$rememberSpeed(CallbackInfo callback) {
        AbstractArrow arrow = (AbstractArrow) (Object) this;
        bowpack$previousSpeed = ((ArrowAccess) this).bowpack$accelerateLevel() > 0
                && arrow.isAlive() && !isInGround() ? arrow.getDeltaMovement().length() : 0;
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void bowpack$accelerateAfterPhysics(CallbackInfo callback) {
        AbstractArrow arrow = (AbstractArrow) (Object) this;
        int level = ((ArrowAccess) this).bowpack$accelerateLevel();
        if (level == 0 || bowpack$previousSpeed == 0 || !arrow.isAlive() || isInGround()) return;
        // Both sides predict this deterministic change; normal tracking handles corrections.
        arrow.setDeltaMovement(ArrowAcceleration.accelerate(arrow.getDeltaMovement(), bowpack$previousSpeed, level));
    }

    @ModifyExpressionValue(method = "onHitEntity", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;length()D", ordinal = 0))
    private double bowpack$keepOriginalDamageSpeed(double speed) {
        ShotState state = ((ArrowAccess) this).bowpack$getState();
        return state != null && state.accelerateLevel() > 0 ? state.launchSpeed() : speed;
    }
}
