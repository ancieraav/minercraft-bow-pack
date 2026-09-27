package dev.bowpack.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.bowpack.projectile.ArrowAccess;
import dev.bowpack.projectile.ArrowLifetime;
import dev.bowpack.projectile.ArrowRicochet;
import dev.bowpack.projectile.RainyAccess;
import dev.bowpack.projectile.ShotState;
import java.util.Collection;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractArrow.class)
abstract class ArrowFlightMixin {
    @Shadow protected abstract boolean isInGround();
    @Unique private int bowpack$slowTicks;
    @Unique private int bowpack$embeddedTicks;
    @Unique private double bowpack$startX;
    @Unique private double bowpack$startY;
    @Unique private double bowpack$startZ;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void bowpack$beginTick(CallbackInfo callback) {
        AbstractArrow arrow = (AbstractArrow) (Object) this;
        if (!ArrowLifetime.canTick(arrow)) {
            callback.cancel();
            return;
        }
        bowpack$startX = arrow.getX();
        bowpack$startY = arrow.getY();
        bowpack$startZ = arrow.getZ();
        ArrowRicochet.releaseHitImmunity(arrow);
    }

    // Grounded arrows return before the final airborne return in native tick().
    @Inject(method = "tick", at = @At("RETURN"))
    private void bowpack$removeInactiveArrows(CallbackInfo callback) {
        AbstractArrow arrow = (AbstractArrow) (Object) this;
        if (!(arrow.level() instanceof ServerLevel) || !arrow.isAlive()
                || !((ArrowAccess) this).bowpack$isManaged()) return;
        ShotState state = ((ArrowAccess) this).bowpack$getState();
        if (state != null && state.rainyProjectile()
                && ((RainyAccess) this).bowpack$isRainyHovering()) {
            bowpack$slowTicks = 0;
            return;
        }
        if (isInGround()) {
            bowpack$slowTicks = 0;
            bowpack$embeddedTicks = arrow.pickup == AbstractArrow.Pickup.DISALLOWED
                    ? bowpack$embeddedTicks + 1 : 0;
            if (bowpack$embeddedTicks >= ArrowLifetime.EMBEDDED_TICKS) arrow.discard();
            return;
        }
        bowpack$embeddedTicks = 0;
        double x = arrow.getX() - bowpack$startX;
        double y = arrow.getY() - bowpack$startY;
        double z = arrow.getZ() - bowpack$startZ;
        boolean slow = arrow.getDeltaMovement().lengthSqr() < ArrowLifetime.MIN_MOVEMENT_SQUARED
                && x * x + y * y + z * z < ArrowLifetime.MIN_MOVEMENT_SQUARED;
        bowpack$slowTicks = slow ? bowpack$slowTicks + 1 : 0;
        if (bowpack$slowTicks >= ArrowLifetime.SLOW_TICKS) arrow.discard();
    }

    @Inject(method = "findHitEntities", at = @At("HEAD"), cancellable = true)
    private void bowpack$serverOwnsEntityCollisions(Vec3 from, Vec3 to,
            CallbackInfoReturnable<Collection<EntityHitResult>> callback) {
        if (((AbstractArrow) (Object) this).level().isClientSide()
                && ((ArrowAccess) this).bowpack$isManaged()) {
            callback.setReturnValue(List.of());
        }
    }

    @Inject(method = "canHitEntity", at = @At("HEAD"), cancellable = true)
    private void bowpack$excludeOwnerAndRecentHit(Entity entity, CallbackInfoReturnable<Boolean> callback) {
        ArrowAccess access = (ArrowAccess) this;
        if (access.bowpack$isManaged() && entity == ((AbstractArrow) (Object) this).getOwner()) {
            callback.setReturnValue(false);
            return;
        }
        ShotState state = access.bowpack$getState();
        if (state != null && (entity.getUUID().equals(state.shooter())
                || state.ignores(entity.getUUID()))) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "getPierceLevel", at = @At("HEAD"), cancellable = true)
    private void bowpack$specialPiercing(CallbackInfoReturnable<Byte> callback) {
        ShotState state = ((ArrowAccess) this).bowpack$getState();
        if (state != null) {
            if (state.penetrates()) callback.setReturnValue((byte) 1);
            else if (state.ricochetLevel() > 0 || state.rainyProjectile()) {
                // Redirects and Rainy shots must not continue a stale piercing sweep.
                callback.setReturnValue((byte) 0);
            }
        } else if (((ArrowAccess) this).bowpack$ricochetEnabled()) {
            callback.setReturnValue((byte) 0);
        }
    }

    @WrapOperation(method = "stepMoveAndHit", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/projectile/arrow/AbstractArrow;hitTargetsOrDeflectSelf(Ljava/util/Collection;)Lnet/minecraft/world/entity/projectile/ProjectileDeflection;"))
    private ProjectileDeflection bowpack$oneRicochetHit(AbstractArrow arrow,
            Collection<EntityHitResult> hits, Operation<ProjectileDeflection> original) {
        ShotState state = ((ArrowAccess) arrow).bowpack$getState();
        if (state != null && state.redirectsOnHit() && !hits.isEmpty()) {
            return original.call(arrow, List.of(hits.iterator().next()));
        }
        return original.call(arrow, hits);
    }
}
