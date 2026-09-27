package dev.bowpack.mixin;

import com.mojang.serialization.Codec;
import dev.bowpack.projectile.ArrowAccess;
import dev.bowpack.projectile.ArrowLifetime;
import dev.bowpack.projectile.ShotState;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractArrow.class)
abstract class ArrowStateMixin implements ArrowAccess {
    @Shadow private double baseDamage;
    @Unique private static final EntityDataAccessor<Integer> BOWPACK_SHOT_FLAGS =
            SynchedEntityData.defineId(AbstractArrow.class, EntityDataSerializers.INT);
    @Unique private static final int BOWPACK_MANAGED = 1;
    @Unique private static final int BOWPACK_RICOCHET = 2;
    @Unique private static final int BOWPACK_BOUNCE_SHIFT = 2;
    @Unique private @Nullable ShotState bowpack$state;

    public @Nullable ShotState bowpack$getState() { return bowpack$state; }

    public double bowpack$getBaseDamage() { return baseDamage; }

    public void bowpack$setState(@Nullable ShotState state) {
        AbstractArrow arrow = (AbstractArrow) (Object) this;
        bowpack$state = state == null ? null : ArrowLifetime.initialize(arrow, state);
        int flags = state == null ? 0 : BOWPACK_MANAGED
                | (state.ricochetLevel() > 0 && !state.penetrates() ? BOWPACK_RICOCHET : 0)
                | (state.bounces() << BOWPACK_BOUNCE_SHIFT);
        arrow.getEntityData().set(BOWPACK_SHOT_FLAGS, flags);
    }

    public boolean bowpack$isManaged() {
        return (bowpack$flags() & BOWPACK_MANAGED) != 0;
    }

    public boolean bowpack$ricochetEnabled() {
        return (bowpack$flags() & BOWPACK_RICOCHET) != 0;
    }

    public int bowpack$remainingBounces() {
        return bowpack$flags() >>> BOWPACK_BOUNCE_SHIFT;
    }

    public void bowpack$spendBounce() {
        if (bowpack$remainingBounces() == 0) {
            throw new IllegalStateException("Arrow has no block bounces remaining");
        }
        AbstractArrow arrow = (AbstractArrow) (Object) this;
        if (arrow.level().isClientSide()) {
            // Predict only the public counter; the next server metadata update corrects it.
            arrow.getEntityData().set(BOWPACK_SHOT_FLAGS, bowpack$flags() - (1 << BOWPACK_BOUNCE_SHIFT));
        } else {
            if (bowpack$state == null) {
                throw new IllegalStateException("A managed server arrow is missing its shot state");
            }
            bowpack$setState(bowpack$state.bounce());
        }
    }

    @Unique
    private int bowpack$flags() {
        return ((AbstractArrow) (Object) this).getEntityData().get(BOWPACK_SHOT_FLAGS);
    }

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void bowpack$defineFlags(SynchedEntityData.Builder builder, CallbackInfo callback) {
        builder.define(BOWPACK_SHOT_FLAGS, 0);
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void bowpack$save(ValueOutput output, CallbackInfo callback) {
        output.storeNullable("bowpack:shot", ShotState.CODEC, bowpack$state);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void bowpack$load(ValueInput input, CallbackInfo callback) {
        ValueInput shot = input.childOrEmpty("bowpack:shot");
        var legacyGenerated = shot.read("rainy_projectile", Codec.BOOL);
        if (legacyGenerated.orElse(false)
                || shot.read("rainy_projectile", Codec.PASSTHROUGH).isPresent() && legacyGenerated.isEmpty()
                || input.read("bowpack:rain_target", Codec.PASSTHROUGH).isPresent()
                || input.read("bowpack:rain_release", Codec.PASSTHROUGH).isPresent()) {
            ArrowLifetime.discardInvalid((AbstractArrow) (Object) this, "legacy Rainy projectile");
            return;
        }
        var saved = input.read("bowpack:shot", ShotState.CODEC);
        if (saved.isEmpty() && input.read("bowpack:shot", Codec.PASSTHROUGH).isPresent()) {
            ArrowLifetime.discardInvalid((AbstractArrow) (Object) this, "saved shot state cannot be decoded");
            return;
        }
        if (saved.isPresent() && saved.get().expiresAt().isEmpty()
                && shot.read("expires_at", Codec.PASSTHROUGH).isPresent()) {
            ArrowLifetime.discardInvalid((AbstractArrow) (Object) this, "saved deadline cannot be decoded");
            return;
        }
        bowpack$setState(saved.orElse(null));
        if (bowpack$state != null) {
            ((AbstractArrow) (Object) this).setNoGravity(bowpack$state.antiFriction());
        }
    }
}
