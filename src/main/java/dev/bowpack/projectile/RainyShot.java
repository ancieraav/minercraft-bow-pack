package dev.bowpack.projectile;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** One fixed 10-second Rainy Shot schedule per shooter, aimed at their latest victim. */
public final class RainyShot {
    public static final int PERIOD_TICKS = 200;
    public static final int INACTIVITY_TICKS = 420;
    public static final int HOVER_TICKS = 40;
    public static final float FALL_SPEED = 6;
    private static final Logger LOGGER = LoggerFactory.getLogger("BowPack");
    private static final Map<MinecraftServer, Map<UUID, Session>> SESSIONS = new IdentityHashMap<>();

    private RainyShot() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(RainyShot::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(SESSIONS::remove);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            Map<UUID, Session> sessions = SESSIONS.get(server);
            if (sessions == null) return;
            sessions.remove(handler.getPlayer().getUUID());
            if (sessions.isEmpty()) SESSIONS.remove(server);
        });
    }

    public static void onHit(AbstractArrow arrow, LivingEntity victim) {
        ShotState state = ((ArrowAccess) arrow).bowpack$getState();
        if (state == null || state.rainyLevel() == 0 || state.rainyProjectile()
                || !(arrow.level() instanceof ServerLevel level)
                || !(arrow.getOwner() instanceof LivingEntity shooter)
                || !shooter.getUUID().equals(state.shooter())) return;

        RainyPayload payload = RainyPayload.capture(arrow);
        if (payload == null) {
            LOGGER.warn("Skipping Rainy Shot for arrow {}: its ammunition is not an ArrowItem", arrow.getUUID());
            return;
        }

        long now = ArrowLifetime.time(level);
        Map<UUID, Session> sessions = SESSIONS.computeIfAbsent(level.getServer(), ignored -> new HashMap<>());
        Session session = sessions.get(state.shooter());
        if (session == null || now - session.lastHitAt >= INACTIVITY_TICKS) {
            session = new Session(now, now + PERIOD_TICKS);
            sessions.put(state.shooter(), session);
        }
        session.lastHitAt = now;
        session.target = victim.getUUID();
        session.dimension = level.dimension();
        session.level = state.rainyLevel();
        session.payload = payload;
    }

    public static boolean validTarget(LivingEntity shooter, LivingEntity target) {
        if (shooter == target || !shooter.isAlive() || !target.isAlive() || target.isSpectator()
                || target.isInvulnerable() || !target.canBeHitByProjectile()) return false;
        if (target instanceof Player player) {
            if (player.getAbilities().invulnerable) return false;
            if (shooter instanceof Player owner && !owner.canHarmPlayer(player)) return false;
        }
        return true;
    }

    private static void tick(MinecraftServer server) {
        Map<UUID, Session> sessions = SESSIONS.get(server);
        if (sessions == null) return;
        long now = ArrowLifetime.time(server.overworld());
        Iterator<Map.Entry<UUID, Session>> entries = sessions.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<UUID, Session> entry = entries.next();
            Session session = entry.getValue();
            if (now - session.lastHitAt >= INACTIVITY_TICKS) {
                entries.remove();
                continue;
            }
            if (now < session.nextVolleyAt) continue;
            // A delayed server tick must never produce a backlog of volleys.
            do {
                session.nextVolleyAt += PERIOD_TICKS;
            } while (session.nextVolleyAt <= now);
            ServerLevel level = server.getLevel(session.dimension);
            if (level == null) continue;
            Entity shooterEntity = level.getEntity(entry.getKey());
            Entity targetEntity = level.getEntity(session.target);
            if (shooterEntity instanceof LivingEntity shooter
                    && targetEntity instanceof LivingEntity target && validTarget(shooter, target)) {
                RainyVolley.spawn(level, shooter, target, session.level, session.payload, now);
            }
        }
        if (sessions.isEmpty()) SESSIONS.remove(server);
    }

    private static final class Session {
        private long lastHitAt;
        private long nextVolleyAt;
        private UUID target;
        private ResourceKey<Level> dimension;
        private int level;
        private RainyPayload payload;

        private Session(long lastHitAt, long nextVolleyAt) {
            this.lastHitAt = lastHitAt;
            this.nextVolleyAt = nextVolleyAt;
        }
    }
}
