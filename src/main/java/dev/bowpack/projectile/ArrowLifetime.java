package dev.bowpack.projectile;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ArrowLifetime {
    public static final int LIFETIME_TICKS = 600;
    public static final int SLOW_TICKS = 40;
    public static final int EMBEDDED_TICKS = 100;
    public static final double MIN_MOVEMENT_SQUARED = 0.02 * 0.02;
    private static final Logger LOGGER = LoggerFactory.getLogger("BowPack");

    private ArrowLifetime() {}

    public static long time(ServerLevel level) {
        return level.getServer().overworld().getLevelData().getGameTime();
    }

    public static long deadline(ServerLevel level) {
        return Math.addExact(time(level), LIFETIME_TICKS);
    }

    public static ShotState initialize(AbstractArrow arrow, ShotState state) {
        if (!(arrow.level() instanceof ServerLevel level)) return state;
        if (state.expiresAt().isEmpty()) {
            // Old saved projectiles had no creation time: grant one transition lifetime.
            state = state.withDeadline(deadline(level));
        }
        check(arrow, state, time(level));
        return state;
    }

    public static boolean canTick(AbstractArrow arrow) {
        ShotState state = ((ArrowAccess) arrow).bowpack$getState();
        if (state == null || !(arrow.level() instanceof ServerLevel level)) return true;
        return check(arrow, state, time(level));
    }

    private static boolean check(AbstractArrow arrow, ShotState state, long now) {
        if (state.expiresAt().isEmpty()) {
            discardInvalid(arrow, "managed state is missing its initialized deadline");
            return false;
        }
        long deadline = state.expiresAt().get();
        if (deadline < 0 || deadline > Math.addExact(now, LIFETIME_TICKS)) {
            discardInvalid(arrow, "deadline " + deadline + " is outside the valid range at " + now);
            return false;
        }
        if (deadline <= now) {
            arrow.discard();
            return false;
        }
        return true;
    }

    public static void discardInvalid(AbstractArrow arrow, String reason) {
        LOGGER.warn("Discarding invalid Bow Pack arrow {} in {}: {}", arrow.getUUID(),
                arrow.level().dimension().identifier(), reason);
        arrow.discard();
    }
}
