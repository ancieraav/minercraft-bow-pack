package dev.bowpack.mixin;

import dev.bowpack.projectile.ArrowFollow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractArrow.class)
abstract class ArrowFollowMixin {
    @Shadow protected abstract boolean isInGround();

    @Inject(method = "tick", at = @At("HEAD"))
    private void bowpack$steerBeforeFlight(CallbackInfo callback) {
        AbstractArrow arrow = (AbstractArrow) (Object) this;
        if (arrow.level() instanceof ServerLevel && arrow.isAlive() && !isInGround()) {
            ArrowFollow.steer(arrow);
        }
    }
}
