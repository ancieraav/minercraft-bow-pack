package dev.bowpack.mixin;

import dev.bowpack.math.ShotPattern;
import dev.bowpack.math.ShotVector;
import dev.bowpack.projectile.ArrowAccess;
import dev.bowpack.projectile.ArrowUnstopable;
import dev.bowpack.projectile.ShotState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractArrow.class)
abstract class ArrowBlockImpactMixin {
    @Shadow protected abstract void setInGround(boolean value);
    @Shadow protected abstract void hitBlockEnchantmentEffects(ServerLevel level, BlockHitResult hit, ItemStack weapon);

    @Inject(method = "onHitBlock", at = @At("HEAD"), cancellable = true)
    private void bowpack$blockImpact(BlockHitResult hit, CallbackInfo callback) {
        AbstractArrow arrow = (AbstractArrow) (Object) this;
        ArrowAccess access = (ArrowAccess) this;
        if (arrow.level().isClientSide() && access.bowpack$unstopableEnabled()) {
            // Only the server knows whether protection and game rules allow the break.
            callback.cancel();
            return;
        }
        ShotState state = access.bowpack$getState();
        if (state != null && state.unstopable() && arrow.level() instanceof ServerLevel level
                && ArrowUnstopable.tryBreak(arrow, hit, level.getBlockState(hit.getBlockPos()))) {
            setInGround(false);
            arrow.shakeTime = 0;
            arrow.setPos(hit.getLocation().add(arrow.getDeltaMovement().normalize().scale(0.01)));
            arrow.needsSync = true;
            callback.cancel();
            return;
        }
        if (access.bowpack$remainingBounces() == 0) return;
        Vec3 velocity = arrow.getDeltaMovement();
        var normal = hit.getDirection().getUnitVec3i();
        ShotVector reflection = ShotPattern.reflect(new ShotVector(velocity.x, velocity.y, velocity.z),
                new ShotVector(normal.getX(), normal.getY(), normal.getZ()));
        if (arrow.level() instanceof ServerLevel level) {
            var blockState = level.getBlockState(hit.getBlockPos());
            blockState.onProjectileHit(level, blockState, hit, arrow);
            ItemStack weapon = arrow.getWeaponItem();
            if (weapon != null) hitBlockEnchantmentEffects(level, hit, weapon);
        }
        access.bowpack$spendBounce();
        setInGround(false);
        arrow.shakeTime = 0;
        arrow.setPos(hit.getLocation().add(normal.getX() * 0.01, normal.getY() * 0.01, normal.getZ() * 0.01));
        arrow.setDeltaMovement(new Vec3(reflection.x(), reflection.y(), reflection.z()));
        arrow.needsSync = true;
        arrow.playSound(SoundEvents.ARROW_HIT, 1, 1);
        callback.cancel();
    }
}
