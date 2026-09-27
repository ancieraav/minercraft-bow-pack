package dev.bowpack.gametest;

import dev.bowpack.projectile.ArrowRicochet;
import dev.bowpack.projectile.ArrowAccess;
import dev.bowpack.projectile.ShotState;
import java.util.Optional;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

public class RicochetGameTests {
    @GameTest(structure = ProjectileFixtures.ARENA)
    public void ricochetCanRevisitEarlierMobsForManyRealImpacts(GameTestHelper test) {
        Mob first = ProjectileFixtures.cow(test, new Vec3(2, 2, 4));
        Mob second = ProjectileFixtures.cow(test, new Vec3(6, 2, 4));
        for (Mob target : new Mob[] {first, second}) {
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
            target.setHealth(1000);
        }
        Arrow arrow = ProjectileFixtures.arrow(test, new Vec3(4, 2.6, 4),
                new Vec3(-3, 0, 0), true, 4, true);
        arrow.setBaseDamage(0.01);
        for (int tick = 0; tick < 100; tick++) arrow.tick();
        test.assertTrue(arrow.isAlive(), "Ricochet must survive more than six impacts");
        test.assertTrue(first.getHealth() <= 990 && second.getHealth() <= 990,
                "Both mobs must be damaged at least ten times by actual projectile collisions");
        test.assertValueEqual(ProjectileFixtures.state(arrow).bounces(), 4,
                "Mob impacts cannot consume or reset the block bounce budget");
        test.succeed();
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void eachRadiusUsesTheLatestImpactAndExcludesTheJustHitMob(GameTestHelper test) {
        int[] radii = {10, 20, 30, 35};
        for (int level = 1; level <= radii.length; level++) {
            double radius = radii[level - 1];
            Vec3 firstImpact = ProjectileFixtures.position(test, new Vec3(1, 4, 4));
            Mob hit = ProjectileFixtures.cow(test, new Vec3(1, 4, 4));
            Mob within = ProjectileFixtures.cow(test, new Vec3(1 + radius - 0.1, 4, 4));
            Mob beyond = ProjectileFixtures.cow(test, new Vec3(1 + radius + 0.1, 4, 4));
            Arrow arrow = ProjectileFixtures.arrow(test, new Vec3(0, 4.5, 4),
                    new Vec3(3, 0, 0), level, 0, false);
            test.assertTrue(ArrowRicochet.findTarget(arrow, List.of(hit.getUUID()), firstImpact) == within,
                    "A target just inside the level " + level + " radius should be selected");
            within.discard();
            test.assertTrue(ArrowRicochet.findTarget(arrow, List.of(hit.getUUID()), firstImpact) == null,
                    "A target just outside the level " + level + " radius must be excluded");
            Vec3 latestImpact = ProjectileFixtures.position(test, new Vec3(2, 4, 4));
            hit.setPos(latestImpact);
            test.assertTrue(ArrowRicochet.findTarget(arrow, List.of(hit.getUUID()), latestImpact) == beyond,
                    "Moving the impact must move the level " + level + " search radius");
            arrow.discard();
            hit.discard();
            beyond.discard();
        }
        test.succeed();
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void lowerRicochetLevelsStopAfterTheirExactRedirectBudgets(GameTestHelper test) {
        int[] budgets = {3, 6, 10};
        for (int level = 1; level <= budgets.length; level++) {
            Mob first = ProjectileFixtures.cow(test, new Vec3(2, 2, 4));
            Mob second = ProjectileFixtures.cow(test, new Vec3(6, 2, 4));
            for (Mob target : new Mob[] {first, second}) {
                target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
                target.setHealth(1000);
            }
            Arrow arrow = ProjectileFixtures.arrow(test, new Vec3(4, 2.6, 4),
                    new Vec3(-3, 0, 0), level, 0, true);
            arrow.setBaseDamage(0.01);
            for (int tick = 0; tick < 80 && arrow.isAlive(); tick++) arrow.tick();
            test.assertFalse(arrow.isAlive(), "Ricochet level " + level + " must stop");
            test.assertValueEqual(ProjectileFixtures.state(arrow).ricochetsRemaining(), 0,
                    "Ricochet level " + level + " must spend its exact budget");
            test.assertValueEqual(2000 - first.getHealth() - second.getHealth(),
                    (float) budgets[level - 1] + 1, "One initial hit plus the allowed redirects deal damage");
            first.discard();
            second.discard();
        }
        test.succeed();
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void redirectAimsOnceAndGravityRemainsActive(GameTestHelper test) {
        Mob target = ProjectileFixtures.cow(test, new Vec3(6, 3, 4));
        Arrow arrow = ProjectileFixtures.arrow(test, new Vec3(2, 3.5, 4),
                new Vec3(0.5, 0, 0), true, 2, false);
        Vec3 impact = arrow.position();
        ArrowRicochet.redirect(arrow, target, impact);
        double aimY = arrow.getDeltaMovement().y;
        test.assertFalse(arrow.isNoGravity(), "Ricochet alone retains gravity");
        target.setPos(target.position().add(0, 2, 0));
        arrow.tick();
        test.assertTrue(arrow.getDeltaMovement().y < aimY,
                "Arrow falls under gravity instead of continually homing toward a moving target");
        test.assertValueEqual(ProjectileFixtures.state(arrow).bounces(), 2,
                "Redirect leaves the remaining block bounce budget intact");
        test.succeed();
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void ricochetStopsWhenOnlyTheJustHitMobRemains(GameTestHelper test) {
        Mob target = ProjectileFixtures.cow(test, new Vec3(4, 2, 5));
        Arrow arrow = ProjectileFixtures.arrow(test, new Vec3(4, 2.6, 3),
                new Vec3(0, 0, 3), true, 6, false);
        arrow.tick();
        test.assertTrue(target.getHealth() < target.getMaxHealth(), "The lone mob takes damage");
        test.assertFalse(arrow.isAlive(), "Ricochet stops instead of immediately hitting the same mob again");
        test.succeed();
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void ricochetSelectsSurvivalPlayersButExcludesOwnerAndCreativePlayers(GameTestHelper test) {
        Mob hit = ProjectileFixtures.cow(test, new Vec3(4, 3, 4));
        ServerPlayer owner = test.makeMockServerPlayerInLevel();
        ServerPlayer target = test.makeMockServerPlayerInLevel();
        owner.setGameMode(GameType.SURVIVAL);
        target.setGameMode(GameType.SURVIVAL);
        owner.setPos(ProjectileFixtures.position(test, new Vec3(3, 3, 4)));
        target.setPos(ProjectileFixtures.position(test, new Vec3(6, 3, 4)));
        Arrow arrow = ProjectileFixtures.arrow(test, new Vec3(4, 3.5, 3),
                new Vec3(0, 0, 3), true, 0, false);
        arrow.setOwner(owner);
        ((ArrowAccess) arrow).bowpack$setState(new ShotState(4, 0, false, false, 0, false,
                3, owner.getUUID(), Optional.empty()));
        test.assertTrue(ArrowRicochet.findTarget(arrow, List.of(hit.getUUID()), hit.position()) == target,
                "The nearby owner is excluded while a survival player is a valid next target");
        target.setGameMode(GameType.CREATIVE);
        test.assertTrue(ArrowRicochet.findTarget(arrow, List.of(hit.getUUID()), hit.position()) == null,
                "An invulnerable creative player cannot be selected");
        owner.discard();
        target.discard();
        test.succeed();
    }
}
