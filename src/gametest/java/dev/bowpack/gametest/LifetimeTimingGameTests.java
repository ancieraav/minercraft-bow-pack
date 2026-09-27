package dev.bowpack.gametest;

import dev.bowpack.projectile.ArrowLifetime;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.Vec3;

public class LifetimeTimingGameTests {
    @GameTest(structure = ProjectileFixtures.ARENA, maxTicks = 620)
    public void realRicochetExpiresAfterSixHundredWorldTicks(GameTestHelper test) {
        Mob first = ProjectileFixtures.cow(test, new Vec3(2, 2, 4));
        Mob second = ProjectileFixtures.cow(test, new Vec3(6, 2, 4));
        for (Mob target : new Mob[] {first, second}) {
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
            target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
            target.setHealth(1000);
        }
        var arrow = ProjectileFixtures.arrow(test, new Vec3(4, 2.6, 4), new Vec3(-3, 0, 0), true, 4, true);
        arrow.setBaseDamage(0.01);
        long deadline = ProjectileFixtures.state(arrow).expiresAt().orElseThrow();
        test.runAfterDelay(598, () -> {
            test.assertTrue(arrow.isAlive(), "Ricochet must continue until expiry: now="
                    + ArrowLifetime.time(test.getLevel()) + ", deadline=" + deadline
                    + ", position=" + arrow.position() + ", health=" + first.getHealth() + "/" + second.getHealth());
            test.assertTrue(first.getHealth() < 990 && second.getHealth() < 990,
                    "Both mobs must receive many real impacts before expiry");
            test.assertValueEqual(ProjectileFixtures.state(arrow).expiresAt().orElseThrow(), deadline,
                    "Actual Ricochet redirects cannot reset the absolute deadline");
        });
        test.runAfterDelay(601, () -> {
            test.assertTrue(ArrowLifetime.time(test.getLevel()) >= deadline,
                    "The world clock must have reached the original deadline");
            test.assertFalse(arrow.isAlive(), "Ricochet must stop after 600 world ticks");
            test.succeed();
        });
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void gravityAtAnApexDoesNotCountAsAStalledArrow(GameTestHelper test) {
        var arrow = ProjectileFixtures.arrow(test, new Vec3(4, 8, 4),
                new Vec3(0, 0.001, 0), false, 0, false);
        arrow.pickup = AbstractArrow.Pickup.ALLOWED;
        double startY = arrow.getY();
        for (int tick = 0; tick < 45; tick++) arrow.tick();
        test.assertTrue(arrow.isAlive(), "A gravity-driven arrow must survive passing its apex");
        test.assertTrue(arrow.getY() < startY, "The arrow must fall under normal gravity");
        test.succeed();
    }
}
