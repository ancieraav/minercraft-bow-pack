package dev.bowpack.projectile;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.BlockHitResult;

/** Breaks only the struck block when a point-blank TNT blast could break it. */
public final class ArrowUnstopable {
    private static final float TNT_POWER = 4.0F;
    private static final ThreadLocal<BreakEffects> QUIET_BREAK = new ThreadLocal<>();

    private ArrowUnstopable() {}

    public static boolean canBreak(AbstractArrow arrow, BlockHitResult hit) {
        if (!(arrow.level() instanceof ServerLevel level)) return false;
        BlockPos pos = hit.getBlockPos();
        if (!level.isInWorldBounds(pos) || !level.hasChunkAt(pos)
                || !level.getWorldBorder().isWithinBounds(pos)
                || !level.getGameRules().get(GameRules.PROJECTILES_CAN_BREAK_BLOCKS)) return false;

        Entity owner = arrow.getOwner();
        if (owner == null) return false;
        if (owner instanceof Player && !level.mayInteract(owner, pos)) return false;
        if (owner instanceof Mob && !level.getGameRules().get(GameRules.MOB_GRIEFING)) return false;

        BlockState block = level.getBlockState(pos);
        if (block.isAir() || block.getDestroySpeed(level, pos) < 0) return false;
        float resistance = Math.max(block.getBlock().getExplosionResistance(),
                level.getFluidState(pos).getExplosionResistance());
        return TNT_POWER - 0.3F * (resistance + 0.3F) > 0.0F;
    }

    public static boolean tryBreak(AbstractArrow arrow, BlockHitResult hit, BlockState expectedState) {
        if (!(arrow.level() instanceof ServerLevel level)
                || level.getBlockState(hit.getBlockPos()) != expectedState
                || !canBreak(arrow, hit)) return false;
        BreakEffects previous = QUIET_BREAK.get();
        QUIET_BREAK.set(new BreakEffects(level, hit.getBlockPos().immutable(), expectedState));
        try {
            return level.destroyBlock(hit.getBlockPos(),
                    level.getGameRules().get(GameRules.BLOCK_DROPS), arrow.getOwner());
        } finally {
            if (previous == null) QUIET_BREAK.remove();
            else QUIET_BREAK.set(previous);
        }
    }

    public static boolean consumeBreakEffects(Level level, BlockPos position, BlockState state) {
        BreakEffects effects = QUIET_BREAK.get();
        if (effects == null || effects.level() != level || effects.state() != state
                || !effects.position().equals(position)) return false;
        // Consume before native drop and block callbacks can destroy another block.
        QUIET_BREAK.remove();
        return true;
    }

    private record BreakEffects(Level level, BlockPos position, BlockState state) {}
}
