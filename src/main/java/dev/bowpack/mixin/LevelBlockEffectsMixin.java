package dev.bowpack.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.bowpack.projectile.ArrowUnstopable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Level.class)
abstract class LevelBlockEffectsMixin {
    @WrapOperation(method = "destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;spawnDestroyParticles(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V"))
    private void bowpack$quietUnstopableBreak(Block block, Level level, BlockPos position,
                                             BlockState state, Operation<Void> original) {
        if (!ArrowUnstopable.consumeBreakEffects(level, position, state)) {
            original.call(block, level, position, state);
        }
    }
}
