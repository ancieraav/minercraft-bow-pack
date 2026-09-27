package dev.bowpack.projectile;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;

/** Projectile-owned budgets and hit history survive chunk save/load. */
public record ShotState(int ricochetLevel, int ricochetsRemaining, int bounces,
                        boolean antiFriction, boolean breakthrough, boolean follow,
                        int rainyLevel, boolean rainyProjectile, double launchSpeed,
                        UUID shooter, Optional<UUID> ignoredEntity, List<UUID> ignoredCompanions,
                        List<UUID> piercedTargets, Optional<UUID> apparentAttacker,
                        Optional<Long> expiresAt) {
    public static final Codec<ShotState> CODEC = RecordCodecBuilder.<ShotState>create(instance -> instance.group(
            Codec.intRange(0, 4).fieldOf("ricochet_level").forGetter(ShotState::ricochetLevel),
            Codec.intRange(-1, 10).fieldOf("ricochets_remaining").forGetter(ShotState::ricochetsRemaining),
            Codec.intRange(0, 30).fieldOf("bounces").forGetter(ShotState::bounces),
            Codec.BOOL.fieldOf("anti_friction").forGetter(ShotState::antiFriction),
            Codec.BOOL.fieldOf("breakthrough").forGetter(ShotState::breakthrough),
            Codec.BOOL.fieldOf("follow").forGetter(ShotState::follow),
            Codec.intRange(0, 3).fieldOf("rainy_level").forGetter(ShotState::rainyLevel),
            Codec.BOOL.fieldOf("rainy_projectile").forGetter(ShotState::rainyProjectile),
            Codec.doubleRange(0.00001, 1000).fieldOf("launch_speed").forGetter(ShotState::launchSpeed),
            UUIDUtil.CODEC.fieldOf("shooter").forGetter(ShotState::shooter),
            UUIDUtil.CODEC.optionalFieldOf("ignored_entity").forGetter(ShotState::ignoredEntity),
            UUIDUtil.CODEC.listOf().optionalFieldOf("ignored_companions", List.of())
                    .forGetter(ShotState::ignoredCompanions),
            UUIDUtil.CODEC.listOf().optionalFieldOf("pierced_targets", List.of())
                    .forGetter(ShotState::piercedTargets),
            UUIDUtil.CODEC.optionalFieldOf("apparent_attacker").forGetter(ShotState::apparentAttacker),
            Codec.LONG.optionalFieldOf("expires_at").forGetter(ShotState::expiresAt)
    ).apply(instance, ShotState::new)).validate(ShotState::validate);

    public ShotState {
        ignoredCompanions = List.copyOf(ignoredCompanions);
        piercedTargets = List.copyOf(piercedTargets);
    }

    public ShotState(int ricochetLevel, int bounces, boolean antiFriction,
                     boolean breakthrough, int rainyLevel, boolean follow,
                     double launchSpeed, UUID shooter, Optional<UUID> ignoredEntity) {
        this(ricochetLevel, ricochetBudget(ricochetLevel), bounces, antiFriction,
                breakthrough, follow, rainyLevel, false, launchSpeed, shooter,
                ignoredEntity, List.of(), List.of(), Optional.empty(), Optional.empty());
    }

    private static int ricochetBudget(int level) {
        return switch (level) {
            case 0 -> 0;
            case 1 -> 3;
            case 2 -> 6;
            case 3 -> 10;
            case 4 -> -1;
            default -> throw new IllegalArgumentException("Unsupported Ricochet level: " + level);
        };
    }

    private static DataResult<ShotState> validate(ShotState state) {
        int maximum = ricochetBudget(state.ricochetLevel());
        if (state.ricochetLevel() == 4 ? state.ricochetsRemaining() != -1
                : state.ricochetsRemaining() < 0 || state.ricochetsRemaining() > maximum) {
            return DataResult.error(() -> "Ricochet redirect budget is inconsistent with its level");
        }
        if (state.ignoredEntity().isEmpty() && !state.ignoredCompanions().isEmpty()) {
            return DataResult.error(() -> "Ignored riding companions have no primary entity");
        }
        if (!state.breakthrough() && !state.piercedTargets().isEmpty()) {
            return DataResult.error(() -> "A non-penetrating arrow has pierced target history");
        }
        if (state.ricochetLevel() != 4 && state.apparentAttacker().isPresent()) {
            return DataResult.error(() -> "Only Ricochet IV can impersonate a previous mob");
        }
        if (state.rainyProjectile() && (state.rainyLevel() != 0 || state.ricochetLevel() != 0
                || state.bounces() != 0 || !state.antiFriction() || state.breakthrough()
                || state.follow() || state.apparentAttacker().isPresent())) {
            return DataResult.error(() -> "Rainy projectile carries another enchantment effect");
        }
        return DataResult.success(state);
    }

    public boolean canRicochet() {
        return !rainyProjectile && ricochetLevel > 0 && ricochetsRemaining != 0;
    }

    public boolean redirectsOnHit() {
        return canRicochet() && !breakthrough;
    }

    public boolean penetrates() {
        return breakthrough && !rainyProjectile;
    }

    public double ricochetRadius() {
        return switch (ricochetLevel) {
            case 1 -> 10;
            case 2 -> 20;
            case 3 -> 30;
            case 4 -> 35;
            default -> 0;
        };
    }

    public ShotState withDeadline(long deadline) {
        return new ShotState(ricochetLevel, ricochetsRemaining, bounces, antiFriction,
                breakthrough, follow, rainyLevel, rainyProjectile, launchSpeed, shooter,
                ignoredEntity, ignoredCompanions, piercedTargets, apparentAttacker, Optional.of(deadline));
    }

    public ShotState bounce() {
        if (bounces == 0) throw new IllegalStateException("Arrow has no block bounces remaining");
        return new ShotState(ricochetLevel, ricochetsRemaining, bounces - 1, antiFriction,
                breakthrough, follow, rainyLevel, rainyProjectile, launchSpeed, shooter,
                ignoredEntity, ignoredCompanions, piercedTargets, apparentAttacker, expiresAt);
    }

    public ShotState spendRicochet(Optional<UUID> hitMob) {
        if (!canRicochet()) throw new IllegalStateException("Arrow has no Ricochet redirects remaining");
        int remaining = ricochetLevel == 4 ? -1 : ricochetsRemaining - 1;
        return new ShotState(ricochetLevel, remaining, bounces, antiFriction,
                breakthrough, follow, rainyLevel, rainyProjectile, launchSpeed, shooter,
                ignoredEntity, ignoredCompanions, piercedTargets,
                ricochetLevel == 4 ? hitMob : Optional.empty(), expiresAt);
    }

    public ShotState withoutBreakthrough() {
        return new ShotState(ricochetLevel, ricochetsRemaining, bounces, antiFriction,
                false, follow, rainyLevel, rainyProjectile, launchSpeed, shooter,
                ignoredEntity, ignoredCompanions, List.of(), apparentAttacker, expiresAt);
    }

    public ShotState pierce(UUID target) {
        if (piercedTargets.contains(target)) return this;
        var targets = new ArrayList<>(piercedTargets);
        targets.add(target);
        return new ShotState(ricochetLevel, ricochetsRemaining, bounces, antiFriction,
                breakthrough, follow, rainyLevel, rainyProjectile, launchSpeed, shooter,
                ignoredEntity, ignoredCompanions, targets, apparentAttacker, expiresAt);
    }

    public ShotState ignore(Optional<UUID> entity) {
        return new ShotState(ricochetLevel, ricochetsRemaining, bounces, antiFriction,
                breakthrough, follow, rainyLevel, rainyProjectile, launchSpeed, shooter,
                entity, List.of(), piercedTargets, apparentAttacker, expiresAt);
    }

    public ShotState ignoreGroup(UUID hit, List<UUID> group) {
        return new ShotState(ricochetLevel, ricochetsRemaining, bounces, antiFriction,
                breakthrough, follow, rainyLevel, rainyProjectile, launchSpeed, shooter,
                Optional.of(hit), group.stream().filter(id -> !id.equals(hit)).toList(),
                piercedTargets, apparentAttacker, expiresAt);
    }

    public ShotState asRainyProjectile() {
        return new ShotState(0, 0, 0, true, false, false, 0, true,
                launchSpeed, shooter, Optional.empty(), List.of(), List.of(),
                Optional.empty(), expiresAt);
    }

    public boolean ignores(UUID entity) {
        return ignoresRecent(entity) || piercedTargets.contains(entity);
    }

    public boolean ignoresRecent(UUID entity) {
        return ignoredEntity.filter(entity::equals).isPresent()
                || ignoredCompanions.contains(entity);
    }
}
