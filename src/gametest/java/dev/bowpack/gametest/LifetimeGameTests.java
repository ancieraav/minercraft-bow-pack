package dev.bowpack.gametest;

import dev.bowpack.projectile.ArrowAccess;
import dev.bowpack.projectile.ArrowLifetime;
import dev.bowpack.projectile.ShotState;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

public class LifetimeGameTests {
    @GameTest(structure = ProjectileFixtures.ARENA)
    public void ricochetBudgetAndPiercedTargetsSurviveNativeSaveLoad(GameTestHelper test) {
        ServerPlayer shooter = BookAccessGameTests.player(test);
        try {
            Mob first = ProjectileFixtures.cow(test, new Vec3(4, 2, 5));
            Mob second = ProjectileFixtures.cow(test, new Vec3(4, 2, 6));
            Mob fresh = ProjectileFixtures.cow(test, new Vec3(4, 2, 7));
            Arrow arrow = ProjectileFixtures.arrow(test, new Vec3(4, 2.6, 3),
                    new Vec3(0, 0, 5), 2, 20, true);
            arrow.setOwner(shooter);
            ShotState expected = new ShotState(2, 20, true, true, false,
                    5, shooter.getUUID(), Optional.empty())
                    .withDeadline(ProjectileFixtures.state(arrow).expiresAt().orElseThrow())
                    .spendRicochet(Optional.empty())
                    .pierce(first.getUUID()).pierce(second.getUUID());
            ((ArrowAccess) arrow).bowpack$setState(expected);
            CompoundTag saved = save(test, arrow);
            arrow.discard();

            Arrow restored = load(test, saved, false);
            ShotState loaded = ProjectileFixtures.state(restored);
            test.assertValueEqual(loaded, expected, "Native save/load keeps Ricochet and Breakthrough state");
            test.assertValueEqual(loaded.ricochetsRemaining(), 5,
                    "One spent Ricochet redirect cannot reset on reload");
            test.assertTrue(loaded.ignores(first.getUUID()) && loaded.ignores(second.getUUID()),
                    "Both pierced targets remain immune after reload");
            test.assertTrue(test.getLevel().addFreshEntity(restored), "The restored arrow must enter the world");
            float firstHealth = first.getHealth();
            float secondHealth = second.getHealth();
            float freshHealth = fresh.getHealth();
            restored.tick();
            test.assertValueEqual(first.getHealth(), firstHealth, "The first pierced mob stays immune");
            test.assertValueEqual(second.getHealth(), secondHealth, "The second pierced mob stays immune");
            test.assertTrue(fresh.getHealth() < freshHealth, "The restored arrow still hits a new mob");
            test.succeed();
        } finally {
            shooter.discard();
        }
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void deadlineUsesOverworldTimeAndSurvivesAllStateChanges(GameTestHelper test) {
        Arrow arrow = moving(test);
        var initial = ProjectileFixtures.state(arrow);
        long expected = test.getLevel().getServer().overworld().getLevelData().getGameTime() + 600;
        test.assertValueEqual(initial.expiresAt().orElseThrow(), expected, "Initial lifetime is 600 canonical ticks");
        UUID hit = UUID.randomUUID();
        var changed = initial.bounce().ignoreGroup(hit, List.of(hit, UUID.randomUUID())).ignore(Optional.empty());
        ((ArrowAccess) arrow).bowpack$setState(changed);
        Arrow restored = load(test, save(test, arrow), false);
        test.assertValueEqual(ProjectileFixtures.state(restored), changed,
                "Bounce, immunity changes, and native save/load must retain the absolute deadline");
        for (int tick = 0; tick < 5; tick++) arrow.tick();
        test.assertTrue(ProjectileFixtures.state(arrow) == changed,
                "Ordinary flight must not allocate a new shot state every tick");
        test.succeed();
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void expiredSavedArrowIsRemovedBeforeItsNextMovement(GameTestHelper test) {
        Arrow arrow = moving(test);
        var state = ProjectileFixtures.state(arrow).withDeadline(ArrowLifetime.time(test.getLevel()) + 3);
        ((ArrowAccess) arrow).bowpack$setState(state);
        CompoundTag saved = save(test, arrow);
        Vec3 position = arrow.position();
        arrow.discard();
        test.runAfterDelay(4, () -> {
            Arrow restored = load(test, saved, false);
            test.assertFalse(restored.isAlive(), "Time outside the loaded world cannot renew an expired arrow");
            restored.tick();
            test.assertValueEqual(restored.position(), position, "Expired arrows cannot run projectile physics");
            test.assertValueEqual(ProjectileFixtures.state(restored).expiresAt(), state.expiresAt(),
                    "Reload retains the original absolute deadline");
            test.succeed();
        });
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void legacyMissingDeadlineReceivesOnlyOneTransitionLifetime(GameTestHelper test) {
        Arrow original = moving(test);
        CompoundTag oldData = save(test, original);
        oldData.getCompound("bowpack:shot").orElseThrow().remove("expires_at");
        Arrow legacy = load(test, oldData, false);
        long deadline = ProjectileFixtures.state(legacy).expiresAt().orElseThrow();
        test.assertValueEqual(deadline, ArrowLifetime.deadline(test.getLevel()),
                "A legacy arrow receives one 600-tick transition lifetime");
        CompoundTag migrated = save(test, legacy);
        test.runAfterDelay(3, () -> {
            Arrow restored = load(test, migrated, false);
            test.assertValueEqual(ProjectileFixtures.state(restored).expiresAt().orElseThrow(), deadline,
                    "Subsequent reloads cannot grant another transition lifetime");
            test.succeed();
        });
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void invalidSavedDeadlinesAreDiscardedInsteadOfRenewed(GameTestHelper test) {
        CompoundTag template = save(test, moving(test));
        for (long invalid : new long[] {-1, ArrowLifetime.deadline(test.getLevel()) + 1}) {
            CompoundTag data = template.copy();
            data.getCompound("bowpack:shot").orElseThrow().putLong("expires_at", invalid);
            test.assertFalse(load(test, data, false).isAlive(), "An invalid numerical deadline must discard the arrow");
        }
        CompoundTag malformed = template.copy();
        malformed.getCompound("bowpack:shot").orElseThrow().putString("expires_at", "invalid");
        test.assertFalse(load(test, malformed, true).isAlive(), "Malformed deadline data must never renew an arrow");
        test.succeed();
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void slowAirborneArrowsRequireFortyConsecutiveInactiveTicks(GameTestHelper test) {
        Arrow arrow = slow(test, new Vec3(2, 4, 2));
        for (int tick = 0; tick < 39; tick++) arrow.tick();
        test.assertTrue(arrow.isAlive(), "A slow arrow remains until the 40-tick threshold");
        arrow.setDeltaMovement(new Vec3(0.2, 0, 0));
        arrow.tick();
        arrow.setDeltaMovement(new Vec3(0.001, 0, 0));
        for (int tick = 0; tick < 39; tick++) arrow.tick();
        test.assertTrue(arrow.isAlive(), "One moving tick resets the consecutive slow counter");
        arrow.tick();
        test.assertFalse(arrow.isAlive(), "The 40th consecutive inactive tick removes the arrow");
        test.succeed();
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void lowEndingSpeedDoesNotOverrideActualDisplacement(GameTestHelper test) {
        Arrow arrow = slow(test, new Vec3(2, 4, 2));
        for (int tick = 0; tick < 50; tick++) {
            // Air drag lowers ending speed below 0.02, while actual movement remains above it.
            arrow.setDeltaMovement(new Vec3(0.0201, 0, 0));
            arrow.tick();
        }
        test.assertTrue(arrow.isAlive(), "Cleanup requires both speed and displacement below 0.02");
        test.succeed();
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void onlyUncollectibleManagedArrowsUseTheHundredTickEmbeddedCleanup(GameTestHelper test) {
        test.setBlock(32, 4, 32, Blocks.STONE);
        Arrow additional = embedded(test, true);
        Arrow original = embedded(test, false);
        Vec3 position = ProjectileFixtures.position(test, new Vec3(3.5, 4.5, 4.5));
        Arrow vanilla = new Arrow(test.getLevel(), position.x, position.y, position.z,
                new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
        vanilla.setDeltaMovement(new Vec3(1, 0, 0));
        test.getLevel().addFreshEntity(vanilla);
        for (int tick = 0; tick < 99; tick++) {
            additional.tick();
            original.tick();
            vanilla.tick();
        }
        test.assertTrue(additional.isAlive(), "The additional arrow remains through embedded tick 99");
        additional.tick();
        original.tick();
        vanilla.tick();
        test.assertFalse(additional.isAlive(), "An uncollectible embedded arrow expires at tick 100");
        test.assertTrue(original.isAlive(), "A collectible primary arrow retains its 600-tick deadline");
        test.assertTrue(vanilla.isAlive(), "Vanilla arrows keep vanilla despawn behavior");
        test.succeed();
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void twentyMaximumVolleysDoNotLeaveInactiveEntities(GameTestHelper test) {
        ItemStack weapon = ProjectileFixtures.maxWeapon(Items.BOW, test.getLevel().registryAccess());
        var player = WeaponGameTests.shooter(test, weapon);
        for (int shot = 0; shot < 20; shot++) {
            ((BowItem) Items.BOW).releaseUsing(weapon, test.getLevel(), player,
                    weapon.getUseDuration(player) - 20);
        }
        List<Arrow> arrows = test.getEntities(EntityTypes.ARROW);
        test.assertValueEqual(arrows.size(), 1600, "Twenty native maximum volleys create 1600 real projectiles");
        test.assertValueEqual(player.getOffhandItem().getCount(), 12, "Twenty volleys use only twenty ammo");
        arrows.forEach(arrow -> arrow.setDeltaMovement(new Vec3(0.001, 0, 0)));
        for (int tick = 0; tick < 39; tick++) arrows.forEach(Arrow::tick);
        test.assertValueEqual(arrows.stream().filter(Arrow::isAlive).count(), 1600L,
                "Stress entities must remain real projectiles until the cleanup threshold");
        arrows.forEach(Arrow::tick);
        test.assertTrue(arrows.stream().noneMatch(Arrow::isAlive), "All 1600 inactive projectiles must be removed");
        test.succeed();
    }

    private static Arrow moving(GameTestHelper test) {
        return ProjectileFixtures.arrow(test, new Vec3(2, 4, 2), new Vec3(0.2, 0, 0), true, 6, true);
    }

    private static Arrow slow(GameTestHelper test, Vec3 position) {
        return ProjectileFixtures.arrow(test, position, new Vec3(0.001, 0, 0), false, 0, true);
    }

    private static Arrow embedded(GameTestHelper test, boolean additional) {
        Arrow arrow = ProjectileFixtures.arrow(test, new Vec3(3.5, 4.5, 4.5),
                new Vec3(1, 0, 0), false, 0, true);
        arrow.pickup = additional ? AbstractArrow.Pickup.DISALLOWED : AbstractArrow.Pickup.ALLOWED;
        return arrow;
    }

    private static CompoundTag save(GameTestHelper test, Arrow arrow) {
        ProblemReporter.Collector problems = new ProblemReporter.Collector();
        var output = TagValueOutput.createWithContext(problems, test.getLevel().registryAccess());
        arrow.saveWithoutId(output);
        test.assertTrue(problems.isEmpty(), "Native serialization errors: " + problems.getReport());
        return output.buildResult();
    }

    private static Arrow load(GameTestHelper test, CompoundTag data, boolean malformed) {
        ProblemReporter.Collector problems = new ProblemReporter.Collector();
        Arrow arrow = new Arrow(EntityTypes.ARROW, test.getLevel());
        arrow.load(TagValueInput.create(problems, test.getLevel().registryAccess(), data));
        test.assertValueEqual(problems.isEmpty(), !malformed, "Saved-data diagnostics must match the supplied data");
        return arrow;
    }
}
