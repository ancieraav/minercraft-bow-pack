package dev.bowpack.mixin;

import com.mojang.serialization.Codec;
import dev.bowpack.projectile.ArrowAccess;
import dev.bowpack.projectile.ArrowLifetime;
import dev.bowpack.projectile.RainyAccess;
import dev.bowpack.projectile.RainyShot;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractArrow.class)
abstract class ArrowRainyMixin implements RainyAccess {
    @Unique private static final String RAIN_TARGET = "bowpack:rain_target";
    @Unique private static final String RAIN_RELEASE = "bowpack:rain_release";
    @Unique private static final EntityDataAccessor<Boolean> BOWPACK_RAIN_HOVER =
            SynchedEntityData.defineId(AbstractArrow.class, EntityDataSerializers.BOOLEAN);
    @Shadow private double baseDamage;
    @Unique private @Nullable UUID bowpack$rainTarget;
    @Unique private long bowpack$rainReleaseAt;

    @Override
    public double bowpack$getBaseDamage() {
        return baseDamage;
    }

    @Override
    public boolean bowpack$isRainyHovering() {
        return ((AbstractArrow) (Object) this).getEntityData().get(BOWPACK_RAIN_HOVER);
    }

    @Override
    public void bowpack$startRainyHover(UUID target, long releaseAt) {
        if (releaseAt < 0) throw new IllegalArgumentException("Rainy Shot release tick cannot be negative");
        AbstractArrow arrow = (AbstractArrow) (Object) this;
        bowpack$rainTarget = target;
        bowpack$rainReleaseAt = releaseAt;
        arrow.getEntityData().set(BOWPACK_RAIN_HOVER, true);
        arrow.setDeltaMovement(Vec3.ZERO);
        arrow.setNoGravity(true);
        if (arrow.level() instanceof ServerLevel level
                && level.getEntity(target) instanceof LivingEntity living) {
            bowpack$face(arrow, living);
        }
        arrow.needsSync = true;
    }

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void bowpack$defineRainHover(SynchedEntityData.Builder builder, CallbackInfo callback) {
        builder.define(BOWPACK_RAIN_HOVER, false);
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void bowpack$saveRainHover(ValueOutput output, CallbackInfo callback) {
        if (bowpack$rainTarget == null || !bowpack$isRainyHovering()) return;
        output.store(RAIN_TARGET, UUIDUtil.CODEC, bowpack$rainTarget);
        output.putLong(RAIN_RELEASE, bowpack$rainReleaseAt);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void bowpack$loadRainHover(ValueInput input, CallbackInfo callback) {
        Optional<UUID> target = input.read(RAIN_TARGET, UUIDUtil.CODEC);
        Optional<Long> release = input.read(RAIN_RELEASE, Codec.LONG);
        boolean hasTarget = input.read(RAIN_TARGET, Codec.PASSTHROUGH).isPresent();
        boolean hasRelease = input.read(RAIN_RELEASE, Codec.PASSTHROUGH).isPresent();
        if (!hasTarget && !hasRelease) return;
        if (target.isEmpty() || release.isEmpty() || release.get() < 0) {
            ArrowLifetime.discardInvalid((AbstractArrow) (Object) this,
                    "Rainy Shot hover target or release tick cannot be decoded");
            return;
        }
        bowpack$startRainyHover(target.get(), release.get());
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void bowpack$hoverBeforeFlight(CallbackInfo callback) {
        if (!bowpack$isRainyHovering()) return;
        AbstractArrow arrow = (AbstractArrow) (Object) this;
        if (!(arrow.level() instanceof ServerLevel level)) {
            // The server sends every aiming rotation while clients hold the arrow still.
            callback.cancel();
            return;
        }
        if (!ArrowLifetime.canTick(arrow)) {
            callback.cancel();
            return;
        }
        var state = ((ArrowAccess) arrow).bowpack$getState();
        if (state == null || !state.rainyProjectile() || bowpack$rainTarget == null) {
            ArrowLifetime.discardInvalid(arrow, "Rainy Shot hover state is incomplete");
            callback.cancel();
            return;
        }
        if (!(arrow.getOwner() instanceof LivingEntity shooter)
                || !(level.getEntity(bowpack$rainTarget) instanceof LivingEntity target)
                || !RainyShot.validTarget(shooter, target)) {
            arrow.discard();
            callback.cancel();
            return;
        }
        Vec3 aim = target.getBoundingBox().getCenter().subtract(arrow.position());
        if (aim.lengthSqr() < 1.0e-8) {
            arrow.discard();
            callback.cancel();
            return;
        }
        bowpack$face(arrow, target);
        arrow.needsSync = true;
        if (ArrowLifetime.time(level) < bowpack$rainReleaseAt) {
            arrow.setDeltaMovement(Vec3.ZERO);
            callback.cancel();
            return;
        }
        bowpack$rainTarget = null;
        bowpack$rainReleaseAt = 0;
        arrow.getEntityData().set(BOWPACK_RAIN_HOVER, false);
        arrow.shoot(aim.x, aim.y, aim.z, RainyShot.FALL_SPEED, 0);
        arrow.setNoGravity(true);
    }

    @Unique
    private static void bowpack$face(AbstractArrow arrow, LivingEntity target) {
        Vec3 aim = target.getBoundingBox().getCenter().subtract(arrow.position());
        arrow.yRotO = arrow.getYRot();
        arrow.xRotO = arrow.getXRot();
        arrow.setYRot((float) Math.toDegrees(Math.atan2(aim.x, aim.z)));
        arrow.setXRot((float) Math.toDegrees(Math.atan2(aim.y, aim.horizontalDistance())));
    }
}
