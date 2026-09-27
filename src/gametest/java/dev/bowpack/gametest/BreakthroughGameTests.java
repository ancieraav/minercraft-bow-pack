package dev.bowpack.gametest;

import dev.bowpack.projectile.ArrowAccess;
import dev.bowpack.projectile.ShotState;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

public class BreakthroughGameTests {
    @GameTest(structure = ProjectileFixtures.ARENA)
    public void breakthroughDamagesMoreThanOneHundredTwentyEightDistinctTargets(GameTestHelper test) {
        ServerPlayer shooter = shooter(test);
        List<Mob> targets = new ArrayList<>();
        try {
            for (int index = 0; index < 130; index++) {
                targets.add(ProjectileFixtures.cow(test, new Vec3(5, 2, 4)));
            }
            Arrow arrow = arrow(test, shooter, new Vec3(2, 2.6, 4),
                    new Vec3(5, 0, 0), 0, true);
            arrow.tick();
            test.assertTrue(targets.stream().allMatch(target -> target.getHealth() < target.getMaxHealth()),
                    "One Breakthrough arrow must actually damage all 130 overlapping targets");
            test.assertValueEqual(ProjectileFixtures.state(arrow).piercedTargets().size(), 130,
                    "The hit history must not stop at the native byte-sized Piercing limit");
            test.assertTrue(arrow.isAlive(), "Breakthrough must keep flying after the 130th target");
            arrow.discard();
            test.succeed();
        } finally {
            targets.forEach(Mob::discard);
            shooter.discard();
        }
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void eachBreakthroughHitCreatesOneRicochetBranchWithoutDoubleHit(GameTestHelper test) {
        ServerPlayer shooter = shooter(test);
        Mob first = ProjectileFixtures.cow(test, new Vec3(4, 2, 4));
        Mob second = ProjectileFixtures.cow(test, new Vec3(10, 2, 4));
        Mob firstBranchTarget = ProjectileFixtures.cow(test, new Vec3(4, 2, 7));
        Mob secondBranchTarget = ProjectileFixtures.cow(test, new Vec3(10, 2, 7));
        Arrow original = arrow(test, shooter, new Vec3(2, 2.6, 4),
                new Vec3(3, 0, 0), 1, true);
        original.tick();
        float firstAfterHit = first.getHealth();
        test.assertTrue(firstAfterHit < first.getMaxHealth(), "The penetrating arrow must damage the first mob");
        List<Arrow> afterFirst = test.getEntities(EntityTypes.ARROW);
        test.assertValueEqual(afterFirst.size(), 2, "The first impact must create exactly one branch");
        Arrow firstBranch = afterFirst.stream().filter(arrow -> arrow != original).findFirst().orElseThrow();
        assertBranch(test, firstBranch, shooter, first);

        for (int tick = 0; tick < 5 && second.getHealth() == second.getMaxHealth(); tick++) original.tick();
        test.assertTrue(second.getHealth() < second.getMaxHealth(),
                "The original Breakthrough arrow must continue straight into the second mob");
        test.assertValueEqual(ProjectileFixtures.state(original).piercedTargets().size(), 2,
                "The original records both distinct hits");
        List<Arrow> afterSecond = test.getEntities(EntityTypes.ARROW);
        test.assertValueEqual(afterSecond.size(), 3, "The second impact must create one more branch");
        Arrow secondBranch = afterSecond.stream()
                .filter(arrow -> arrow != original && arrow != firstBranch).findFirst().orElseThrow();
        assertBranch(test, secondBranch, shooter, second);
        test.assertValueEqual(first.getHealth(), firstAfterHit,
                "Creating two branches must not damage the first mob again");

        for (int tick = 0; tick < 5 && firstBranchTarget.getHealth() == firstBranchTarget.getMaxHealth(); tick++) {
            firstBranch.tick();
        }
        for (int tick = 0; tick < 5 && secondBranchTarget.getHealth() == secondBranchTarget.getMaxHealth(); tick++) {
            secondBranch.tick();
        }
        test.assertTrue(firstBranchTarget.getHealth() < firstBranchTarget.getMaxHealth(),
                "The first branch must damage its sideways target");
        test.assertTrue(secondBranchTarget.getHealth() < secondBranchTarget.getMaxHealth(),
                "The second branch must damage its sideways target");
        test.assertValueEqual(first.getHealth(), firstAfterHit,
                "Neither branch may immediately hit the mob that created it");
        test.succeed();
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void breakthroughDoesNotSplitWhenNoRicochetTargetExists(GameTestHelper test) {
        ServerPlayer shooter = shooter(test);
        Mob target = ProjectileFixtures.cow(test, new Vec3(4, 2, 4));
        Arrow original = arrow(test, shooter, new Vec3(2, 2.6, 4),
                new Vec3(3, 0, 0), 1, true);
        original.tick();
        test.assertTrue(target.getHealth() < target.getMaxHealth(),
                "Breakthrough still damages the only available target");
        test.assertTrue(original.isAlive(), "The penetrating arrow continues after the hit");
        test.assertValueEqual(test.getEntities(EntityTypes.ARROW).size(), 1,
                "No separate Ricochet target means no extra arrow");
        test.assertValueEqual(ProjectileFixtures.state(original).ricochetsRemaining(), 3,
                "Skipping a branch must not spend the Ricochet budget");
        test.succeed();
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void ricochetFourShowsMobAggroButCreditsThePlayerForTheKill(GameTestHelper test) {
        ServerPlayer shooter = shooter(test);
        Mob apparent = skeleton(test, new Vec3(4, 2, 4));
        Mob victim = skeleton(test, new Vec3(8, 2, 4));
        Arrow arrow = arrow(test, shooter, new Vec3(2, 2.6, 4),
                new Vec3(3, 0, 0), 4, false);
        arrow.tick();
        test.assertTrue(apparent.getHealth() < apparent.getMaxHealth(),
                "The first skeleton must be hit before Ricochet");
        test.assertValueEqual(ProjectileFixtures.state(arrow).apparentAttacker(),
                Optional.of(apparent.getUUID()), "The redirected arrow appears to come from that skeleton");
        test.assertTrue(arrow.getOwner() == shooter, "The real arrow owner must remain the player");
        for (int tick = 0; tick < 5 && victim.getHealth() == victim.getMaxHealth(); tick++) arrow.tick();
        test.assertTrue(victim.getHealth() < victim.getMaxHealth(),
                "The redirected arrow must damage the second skeleton");
        test.assertTrue(victim.getLastHurtByMob() == apparent,
                "The second skeleton must treat the first as its attacker");
        test.assertTrue(victim.getKillCredit() == shooter,
                "Apparent mob ownership must not steal the player's kill credit");

        arrow.discard();
        apparent.discard();
        victim.discard();
        Mob lethalApparent = skeleton(test, new Vec3(4, 2, 4));
        Mob lethalVictim = skeleton(test, new Vec3(8, 2, 4));
        lethalVictim.setHealth(1);
        Arrow lethalArrow = arrow(test, shooter, new Vec3(2, 2.6, 4),
                new Vec3(3, 0, 0), 4, false);
        lethalArrow.tick();
        for (int tick = 0; tick < 5 && lethalVictim.isAlive(); tick++) lethalArrow.tick();
        test.assertFalse(lethalVictim.isAlive(), "The Ricochet arrow must land a real killing blow");
        test.assertTrue(lethalVictim.getLastDamageSource() != null
                        && lethalVictim.getLastDamageSource().getEntity() == shooter,
                "The lethal damage source must credit the player");
        test.assertTrue(lethalVictim.getKillCredit() == shooter,
                "The killed skeleton must credit the player rather than the apparent mob");
        test.succeed();
    }

    private static void assertBranch(GameTestHelper test, Arrow branch, ServerPlayer shooter, Mob sourceMob) {
        ShotState state = ProjectileFixtures.state(branch);
        test.assertFalse(state.breakthrough(), "A Ricochet branch cannot spawn another branch");
        test.assertTrue(((ArrowAccess) branch).bowpack$ricochetEnabled(),
                "A split branch redirects itself instead of continuing the piercing sweep");
        test.assertValueEqual(state.ricochetsRemaining(), 2,
                "A level-one branch spends one redirect when created");
        test.assertValueEqual(state.ignoredEntity(), Optional.of(sourceMob.getUUID()),
                "The branch must not hit the mob it just left");
        test.assertTrue(branch.getOwner() == shooter, "The player remains the branch owner");
        test.assertValueEqual(branch.pickup, AbstractArrow.Pickup.DISALLOWED,
                "Split branches cannot duplicate ammunition pickups");
    }

    private static Arrow arrow(GameTestHelper test, ServerPlayer shooter, Vec3 start,
                               Vec3 velocity, int ricochetLevel, boolean breakthrough) {
        Arrow arrow = ProjectileFixtures.arrow(test, start, velocity, ricochetLevel, 0, true);
        arrow.setOwner(shooter);
        ((ArrowAccess) arrow).bowpack$setState(new ShotState(ricochetLevel, 0, true,
                breakthrough, false, velocity.length(), shooter.getUUID(), Optional.empty()));
        arrow.setBaseDamage(0.01);
        return arrow;
    }

    private static ServerPlayer shooter(GameTestHelper test) {
        ServerPlayer player = test.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(ProjectileFixtures.position(test, new Vec3(1, 3, 9)));
        return player;
    }

    private static Mob skeleton(GameTestHelper test, Vec3 local) {
        Mob skeleton = test.spawnWithNoFreeWill(EntityTypes.SKELETON, local.add(28, 0, 28));
        skeleton.setNoGravity(true);
        return skeleton;
    }
}
