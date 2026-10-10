package dev.bowpack.gametest;

import dev.bowpack.projectile.ArrowAccess;
import dev.bowpack.projectile.ShotState;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public class AccelerateImpactGameTests {
    @GameTest(structure = ProjectileFixtures.ARENA)
    public void accelerationKeepsDamageFixedAndPowerStillApplies(GameTestHelper test) {
        float normal = damage(test, 3, 0, 0);
        for (int level = 1; level <= 3; level++) {
            test.assertValueEqual(damage(test, 3 + level * 2, level, 0), normal,
                    "Accelerated speed cannot increase damage at any level");
        }
        float power = damage(test, 3, 0, 3);
        test.assertValueEqual(damage(test, 9, 3, 3), power,
                "Accelerate preserves the original Power-enchanted damage");
        test.assertTrue(power > normal, "The damage-speed hook must not suppress Power");
        test.assertTrue(damage(test, 9, 0, 0) > normal,
                "An arrow without Accelerate retains native speed-based damage");
        test.succeed();
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void ricochetRetainsAccumulatedSpeedAndOriginalDamage(GameTestHelper test) {
        Mob first = ProjectileFixtures.cow(test, new Vec3(4, 2, 4));
        Mob next = ProjectileFixtures.cow(test, new Vec3(4, 2, 9));
        Arrow arrow = ProjectileFixtures.arrow(test, new Vec3(2, 2.6, 4),
                new Vec3(2, 0, 0), 2, 5, true);
        ((ArrowAccess) arrow).bowpack$setState(ProjectileFixtures.state(arrow).withAccelerate(3));
        arrow.setDeltaMovement(new Vec3(6, 0, 0));
        arrow.setBaseDamage(2);
        arrow.tick();
        ProjectileFixtures.near(test, 6.2, arrow.getDeltaMovement().length(),
                "Ricochet continues from impact speed plus the next acceleration step");
        test.assertValueEqual(first.getMaxHealth() - first.getHealth(), 4.0F,
                "The first hit uses launch speed two rather than current speed six");
        test.assertValueEqual(ProjectileFixtures.state(arrow).accelerateLevel(), 3,
                "Ricochet state copies retain Accelerate");
        arrow.tick();
        test.assertValueEqual(next.getMaxHealth() - next.getHealth(), 4.0F,
                "A redirected accelerated hit retains original launch damage");
        test.succeed();
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void breakthroughBranchRetainsSpeedDamageAndDeadline(GameTestHelper test) {
        var shooter = BookAccessGameTests.player(test);
        try {
            shooter.setPos(ProjectileFixtures.position(test, new Vec3(1, 3, 12)));
            Mob first = ProjectileFixtures.cow(test, new Vec3(4, 2, 4));
            Mob next = ProjectileFixtures.cow(test, new Vec3(4, 2, 9));
            Arrow source = ProjectileFixtures.arrow(test, new Vec3(2, 2.6, 4),
                    new Vec3(2, 0, 0), 2, 5, true);
            source.setOwner(shooter);
            ((ArrowAccess) source).bowpack$setState(new ShotState(2, 5, true,
                    true, false, 2, shooter.getUUID(), Optional.empty()).withAccelerate(3));
            source.setDeltaMovement(new Vec3(6, 0, 0));
            source.setBaseDamage(2);
            source.tick();
            var arrows = test.getEntities(EntityTypes.ARROW);
            test.assertValueEqual(arrows.size(), 2, "One penetrating hit creates one Ricochet branch");
            Arrow branch = arrows.stream().filter(arrow -> arrow != source).findFirst().orElseThrow();
            ProjectileFixtures.near(test, 6, branch.getDeltaMovement().length(),
                    "A new branch inherits the source's accumulated impact speed");
            var state = ProjectileFixtures.state(branch);
            test.assertValueEqual(state.accelerateLevel(), 3, "The branch retains Accelerate III");
            test.assertValueEqual(state.expiresAt(), ProjectileFixtures.state(source).expiresAt(),
                    "Acceleration and splitting cannot restart the projectile lifetime");
            ProjectileFixtures.near(test, 2, state.launchSpeed(), "The branch retains its original damage speed");
            branch.tick();
            test.assertValueEqual(next.getMaxHealth() - next.getHealth(), 4.0F,
                    "A fast branch deals original launch damage to its target");
            test.assertValueEqual(first.getMaxHealth() - first.getHealth(), 4.0F,
                    "The branch cannot immediately damage the source target again");
            test.succeed();
        } finally {
            shooter.discard();
        }
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void bouncyRetainsAccumulatedSpeedAndSpendsOneBounce(GameTestHelper test) {
        test.setBlock(32, 4, 32, Blocks.STONE);
        Arrow arrow = ProjectileFixtures.arrow(test, new Vec3(3.5, 4.5, 4.5),
                new Vec3(2, 0, 0), false, 3, true);
        ((ArrowAccess) arrow).bowpack$setState(ProjectileFixtures.state(arrow).withAccelerate(3));
        arrow.setDeltaMovement(new Vec3(6, 0, 0));
        arrow.tick();
        test.assertTrue(arrow.getDeltaMovement().x < 0, "A fast arrow reflects away from the wall");
        ProjectileFixtures.near(test, 6.2, arrow.getDeltaMovement().length(),
                "Bouncy does not reset accumulated speed");
        test.assertValueEqual(ProjectileFixtures.state(arrow).bounces(), 2, "Only one bounce is consumed");
        test.succeed();
    }

    private static float damage(GameTestHelper test, double speed, int accelerate, int power) {
        Mob target = ProjectileFixtures.cow(test, new Vec3(4, 2, 4));
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
        target.setHealth(100);
        Arrow arrow = ProjectileFixtures.arrow(test, new Vec3(2, 2.6, 4),
                new Vec3(3, 0, 0), false, 0, true);
        ((ArrowAccess) arrow).bowpack$setState(ProjectileFixtures.state(arrow).withAccelerate(accelerate));
        arrow.setDeltaMovement(new Vec3(speed, 0, 0));
        arrow.setBaseDamage(2);
        arrow.setCritArrow(false);
        if (power > 0) arrow.getWeaponItem().enchant(test.getLevel().registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.POWER), power);
        arrow.tick();
        float damage = target.getMaxHealth() - target.getHealth();
        test.assertTrue(damage > 0, "The projectile must land a real damage event");
        arrow.discard();
        target.discard();
        return damage;
    }
}
