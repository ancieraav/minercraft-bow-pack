package dev.bowpack.gametest;

import dev.bowpack.BowEnchantments;
import dev.bowpack.projectile.ArrowAccess;
import dev.bowpack.projectile.RainyAccess;
import dev.bowpack.projectile.RainyShot;
import dev.bowpack.projectile.ShotState;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.arrow.SpectralArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

public class RainyShotGameTests {
    @GameTest(structure = ProjectileFixtures.ARENA, maxTicks = 260)
    public void latestTargetKeepsFixedCooldownAndHoverTracksMovement(GameTestHelper test) {
        ServerPlayer shooter = shooter(test, new Vec3(4, 3, 4));
        Mob first = healthyCow(test, new Vec3(4, 3, 10));
        Mob latest = healthyCow(test, new Vec3(12, 3, 10));
        AbstractArrow firstSource = source(test, shooter, enchanted(test, Items.BOW, 2),
                new ItemStack(Items.ARROW), 2, false);
        ItemStack weapon = enchanted(test, Items.CROSSBOW, 2);
        weapon.enchant(test.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.PIERCING), 4);
        ItemStack ammo = new ItemStack(Items.TIPPED_ARROW);
        PotionContents poison = new PotionContents(Potions.POISON);
        ammo.set(DataComponents.POTION_CONTENTS, poison);
        AbstractArrow latestSource = source(test, shooter, weapon, ammo, 2, true);
        latestSource.setBaseDamage(3.5);
        latestSource.setCritArrow(true);
        latestSource.setRemainingFireTicks(100);
        RainyShot.onHit(firstSource, first);
        test.runAfterDelay(100, () -> RainyShot.onHit(latestSource, latest));
        test.runAfterDelay(198, () -> test.assertTrue(hovering(test).isEmpty(),
                "Switching targets must not cause an early Rainy volley"));
        test.runAfterDelay(202, () -> {
            List<Arrow> arrows = hovering(test);
            test.assertValueEqual(arrows.size(), 8, "Rainy II creates exactly eight arrows at the original cooldown");
            double centerX = arrows.stream().mapToDouble(Arrow::getX).average().orElseThrow();
            ProjectileFixtures.near(test, latest.getX(), centerX, "The row follows the latest target");
            for (Arrow arrow : arrows) {
                ShotState state = ProjectileFixtures.state(arrow);
                test.assertTrue(state.rainyProjectile() && state.antiFriction()
                        && !state.canRicochet() && !state.penetrates() && !state.follow()
                        && state.bounces() == 0, "Rainy arrows exclude all other projectile enchantments");
                test.assertValueEqual(arrow.getPierceLevel(), (byte) 0,
                        "Rainy arrows cannot inherit the crossbow's Piercing");
                test.assertTrue(arrow.getWeaponItem() != null && arrow.getWeaponItem().is(Items.CROSSBOW),
                        "Rainy arrows use the latest hit's weapon snapshot");
                test.assertValueEqual(arrow.getPickupItemStackOrigin().get(DataComponents.POTION_CONTENTS),
                        poison, "Rainy arrows keep the tipped ammunition effect");
                ProjectileFixtures.near(test, 3.5, ((RainyAccess) arrow).bowpack$getBaseDamage(),
                        "Rainy arrows keep the triggering arrow's base damage");
                test.assertTrue(arrow.isCritArrow() && arrow.isOnFire(),
                        "Rainy arrows keep critical and fire effects");
                ProjectileFixtures.near(test, 0, arrow.getDeltaMovement().length(),
                        "Rainy arrows remain stationary during the hover");
            }
        });
        test.runAfterDelay(220, () -> latest.setPos(latest.position().add(2, 0, 0)));
        test.runAfterDelay(225, () -> {
            List<Arrow> arrows = hovering(test);
            test.assertValueEqual(arrows.size(), 8, "Rainy arrows remain visible while aiming");
            for (Arrow arrow : arrows) {
                Vec3 toTarget = latest.getBoundingBox().getCenter().subtract(arrow.position()).normalize();
                double yaw = Math.toRadians(arrow.getYRot());
                double pitch = Math.toRadians(arrow.getXRot());
                Vec3 facing = new Vec3(Math.sin(yaw) * Math.cos(pitch), Math.sin(pitch),
                        Math.cos(yaw) * Math.cos(pitch));
                test.assertTrue(facing.dot(toTarget) > 0.98,
                        "A stationary Rainy arrow must point toward its moving target");
            }
        });
        test.runAfterDelay(245, () -> {
            test.assertTrue(hovering(test).isEmpty(), "Rainy arrows leave hover after 40 ticks");
            test.assertTrue(latest.getHealth() < latest.getMaxHealth(),
                    "The fast Rainy volley must attack after hovering");
            test.succeed();
        });
    }

    @GameTest(structure = ProjectileFixtures.ARENA, maxTicks = 640)
    public void volleyRepeatsAtTwentySecondsThenInactivityRestartsCooldown(GameTestHelper test) {
        ServerPlayer shooter = shooter(test, new Vec3(4, 3, 4));
        Mob target = healthyCow(test, new Vec3(4, 3, 10));
        AbstractArrow source = source(test, shooter, enchanted(test, Items.BOW, 1),
                new ItemStack(Items.ARROW), 1, false);
        RainyShot.onHit(source, target);
        test.runAfterDelay(202, () -> test.assertValueEqual(hovering(test).size(), 6,
                "The first Rainy I volley starts ten seconds after a hit"));
        test.runAfterDelay(402, () -> test.assertValueEqual(hovering(test).size(), 6,
                "The second volley starts twenty seconds after the same hit"));
        test.runAfterDelay(421, () -> RainyShot.onHit(source, target));
        test.runAfterDelay(602, () -> test.assertTrue(hovering(test).isEmpty(),
                "After 21 idle seconds, a new hit must start a fresh ten-second cooldown"));
        test.runAfterDelay(623, () -> {
            test.assertValueEqual(hovering(test).size(), 6,
                    "A fresh session starts its first volley ten seconds after the new hit");
            test.succeed();
        });
    }

    @GameTest(structure = ProjectileFixtures.ARENA, maxTicks = 220)
    public void realSpectralHitStartsIndependentRainyThreeVolley(GameTestHelper test) {
        ServerPlayer shooter = shooter(test, new Vec3(4, 3, 4));
        Mob target = healthyCow(test, new Vec3(4, 3, 10));
        AbstractArrow source = source(test, shooter, enchanted(test, Items.BOW, 3),
                new ItemStack(Items.SPECTRAL_ARROW), 3, false);
        source.setPos(ProjectileFixtures.position(test, new Vec3(4, 3.7, 8)));
        source.setDeltaMovement(new Vec3(0, 0, 3));
        test.getLevel().addFreshEntity(source);
        float health = target.getHealth();
        source.tick();
        test.assertTrue(target.getHealth() < health, "The triggering spectral arrow must really hit");
        test.runAfterDelay(202, () -> {
            List<SpectralArrow> arrows = test.getEntities(EntityTypes.SPECTRAL_ARROW).stream()
                    .filter(arrow -> ((RainyAccess) arrow).bowpack$isRainyHovering()).toList();
            test.assertValueEqual(arrows.size(), 10, "A real Rainy III hit creates ten spectral arrows");
            test.assertTrue(arrows.stream().allMatch(arrow -> ProjectileFixtures.state(arrow).rainyProjectile()),
                    "Rainy generated arrows retain their independent identity");
            test.succeed();
        });
    }

    @GameTest(structure = ProjectileFixtures.ARENA, maxTicks = 220)
    public void logoutEndsRainySession(GameTestHelper test) {
        ServerPlayer shooter = shooter(test, new Vec3(4, 3, 4));
        Mob target = healthyCow(test, new Vec3(4, 3, 10));
        AbstractArrow source = source(test, shooter, enchanted(test, Items.BOW, 1),
                new ItemStack(Items.ARROW), 1, false);
        RainyShot.onHit(source, target);
        test.runAfterDelay(20, () -> ServerPlayConnectionEvents.DISCONNECT.invoker()
                .onPlayDisconnect(shooter.connection, test.getLevel().getServer()));
        test.runAfterDelay(202, () -> {
            test.assertTrue(hovering(test).isEmpty(), "Disconnecting ends the Rainy volley schedule");
            test.succeed();
        });
    }

    private static List<Arrow> hovering(GameTestHelper test) {
        return test.getEntities(EntityTypes.ARROW).stream()
                .filter(arrow -> ((RainyAccess) arrow).bowpack$isRainyHovering()).toList();
    }

    private static ServerPlayer shooter(GameTestHelper test, Vec3 local) {
        ServerPlayer player = test.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(ProjectileFixtures.position(test, local));
        return player;
    }

    private static Mob healthyCow(GameTestHelper test, Vec3 local) {
        Mob cow = ProjectileFixtures.cow(test, local);
        cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10000);
        cow.setHealth(10000);
        return cow;
    }

    private static ItemStack enchanted(GameTestHelper test, net.minecraft.world.item.Item item, int level) {
        ItemStack weapon = new ItemStack(item);
        weapon.enchant(test.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(BowEnchantments.RAINY_SHOT), level);
        return weapon;
    }

    private static AbstractArrow source(GameTestHelper test, ServerPlayer shooter, ItemStack weapon,
                                        ItemStack ammo, int rainyLevel, boolean otherEnchantments) {
        AbstractArrow arrow = ((ArrowItem) ammo.getItem()).createArrow(test.getLevel(), ammo, shooter, weapon);
        ((ArrowAccess) arrow).bowpack$setState(new ShotState(otherEnchantments ? 4 : 0,
                otherEnchantments ? 30 : 0, false, otherEnchantments, rainyLevel,
                otherEnchantments, 3, shooter.getUUID(), Optional.empty()));
        return arrow;
    }
}
