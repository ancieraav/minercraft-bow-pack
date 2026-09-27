package dev.bowpack.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.bowpack.math.ShotPattern;
import dev.bowpack.math.ShotVector;
import dev.bowpack.projectile.ArrowAccess;
import dev.bowpack.projectile.ArrowBreakthrough;
import dev.bowpack.projectile.ArrowRicochet;
import dev.bowpack.projectile.RainyShot;
import dev.bowpack.projectile.ShotState;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractArrow.class)
abstract class ArrowImpactMixin {
    @Shadow protected abstract void setInGround(boolean value);
    @Shadow protected abstract void hitBlockEnchantmentEffects(ServerLevel level, BlockHitResult hit, ItemStack weapon);
    @Shadow private @Nullable IntOpenHashSet piercingIgnoreEntityIds;
    @Unique private @Nullable LivingEntity bowpack$nextTarget;
    @Unique private @Nullable LivingEntity bowpack$splitTarget;
    @Unique private List<UUID> bowpack$splitHitGroup = List.of();

    @Inject(method = "onHitEntity", at = @At("HEAD"), cancellable = true)
    private void bowpack$startImpact(EntityHitResult hit, CallbackInfo callback) {
        bowpack$nextTarget = null;
        bowpack$splitTarget = null;
        bowpack$splitHitGroup = List.of();
        AbstractArrow arrow = (AbstractArrow) (Object) this;
        if (arrow.level().isClientSide() && ((ArrowAccess) this).bowpack$isManaged()) {
            // RemotePlayer predicts successful damage. Only the server may remove our arrows.
            callback.cancel();
        }
    }

    @WrapOperation(method = "onHitEntity", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;hurtOrSimulate(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean bowpack$damageAndFindTarget(Entity entity, DamageSource source, float damage,
                                               Operation<Boolean> original) {
        AbstractArrow arrow = (AbstractArrow) (Object) this;
        ArrowAccess access = (ArrowAccess) this;
        ShotState state = access.bowpack$getState();
        List<UUID> hitGroup = state != null && state.canRicochet()
                ? ArrowRicochet.collisionGroup(entity) : List.of();
        boolean damaged = original.call(entity, source, damage);
        if (state != null && state.penetrates() && !arrow.level().isClientSide()) {
            // Native Piercing records even a deflected hit. Keep that protection when its ID set is cleared.
            access.bowpack$setState(state.pierce(entity.getUUID()));
        }
        if (!damaged || !(entity instanceof LivingEntity victim) || state == null
                || !(arrow.level() instanceof ServerLevel level)) return damaged;
        if (!state.rainyProjectile()) RainyShot.onHit(arrow, victim);
        if (state.ricochetLevel() == 4 && victim instanceof Mob mob
                && state.apparentAttacker().isPresent()
                && level.getEntity(state.apparentAttacker().get()) instanceof Mob apparent) {
            mob.setLastHurtByMob(apparent);
        }
        if (state.penetrates()) {
            if (state.canRicochet()) {
                bowpack$splitTarget = ArrowRicochet.findTarget(arrow, hitGroup, arrow.position());
                if (bowpack$splitTarget != null) bowpack$splitHitGroup = hitGroup;
            }
        } else if (state.redirectsOnHit()) {
            bowpack$nextTarget = ArrowRicochet.findTarget(arrow, hitGroup, arrow.position());
            if (bowpack$nextTarget != null) {
                Optional<UUID> apparent = entity instanceof Mob
                        ? Optional.of(entity.getUUID()) : Optional.empty();
                access.bowpack$setState(state.spendRicochet(apparent)
                        .ignoreGroup(entity.getUUID(), hitGroup));
            }
        }
        return damaged;
    }

    @WrapOperation(method = "onHitEntity", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/projectile/arrow/AbstractArrow;discard()V"))
    private void bowpack$keepRicochetingArrow(AbstractArrow arrow, Operation<Void> original) {
        if (bowpack$nextTarget == null) original.call(arrow);
    }

    @Inject(method = "onHitEntity", at = @At("RETURN"))
    private void bowpack$finishImpact(EntityHitResult hit, CallbackInfo callback) {
        AbstractArrow arrow = (AbstractArrow) (Object) this;
        if (bowpack$nextTarget != null && arrow.isAlive()) {
            ArrowRicochet.redirect(arrow, bowpack$nextTarget, hit.getLocation());
        }
        if (bowpack$splitTarget != null && arrow.isAlive()
                && hit.getEntity() instanceof LivingEntity victim) {
            ArrowBreakthrough.spawnRicochetBranch(
                    arrow, victim, bowpack$splitHitGroup, bowpack$splitTarget, hit.getLocation());
        }
        ShotState state = ((ArrowAccess) this).bowpack$getState();
        if (state != null && state.penetrates() && piercingIgnoreEntityIds != null) {
            // Native Piercing stops at level + 1 hits; UUID history enforces once per target.
            piercingIgnoreEntityIds.clear();
        }
        bowpack$nextTarget = null;
        bowpack$splitTarget = null;
        bowpack$splitHitGroup = List.of();
    }

    @Inject(method = "onHitBlock", at = @At("HEAD"), cancellable = true)
    private void bowpack$reflect(BlockHitResult hit, CallbackInfo callback) {
        AbstractArrow arrow = (AbstractArrow) (Object) this;
        ArrowAccess access = (ArrowAccess) this;
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
