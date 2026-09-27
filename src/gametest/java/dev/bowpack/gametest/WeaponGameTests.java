package dev.bowpack.gametest;

import dev.bowpack.BowEnchantments;
import dev.bowpack.projectile.ArrowAccess;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

public class WeaponGameTests {
    @GameTest(structure = ProjectileFixtures.ARENA)
    public void bowCreates80RealProjectilesWithAllEffects(GameTestHelper test) {
        ItemStack weapon = ProjectileFixtures.maxWeapon(Items.BOW, test.getLevel().registryAccess());
        Player player = shooter(test, weapon);
        ((BowItem) Items.BOW).releaseUsing(weapon, test.getLevel(), player,
                weapon.getUseDuration(player) - 20);
        assertMaximumFormation(test, player, 3);
        test.assertValueEqual(player.getOffhandItem().getCount(), 31, "Only one arrow is consumed");
        test.assertValueEqual(weapon.getDamageValue(), 1, "The entire volley costs one durability");
        test.succeed();
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void crossbowCreates80RealProjectilesAfterNativeLoading(GameTestHelper test) {
        ItemStack weapon = ProjectileFixtures.maxWeapon(Items.CROSSBOW, test.getLevel().registryAccess());
        Player player = shooter(test, weapon);
        fireCrossbow(test, player, weapon);
        assertMaximumFormation(test, player, 3.15);
        test.assertValueEqual(player.getOffhandItem().getCount(), 31, "Only one arrow is loaded");
        test.assertFalse(CrossbowItem.isCharged(weapon), "The crossbow should be discharged");
        test.assertValueEqual(weapon.getDamageValue(), 1, "The entire volley costs one durability");
        test.succeed();
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void legacyMultishotCannotCreateAnExtraVolley(GameTestHelper test) {
        ItemStack weapon = new ItemStack(Items.CROSSBOW);
        var vanillaMultishot = test.getLevel().registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.MULTISHOT);
        weapon.enchant(vanillaMultishot, 1);
        Player player = shooter(test, weapon);
        fireCrossbow(test, player, weapon);
        test.assertValueEqual(test.getEntities(EntityTypes.ARROW).size(), 1,
                "Existing vanilla Multishot must no longer multiply arrows");
        test.succeed();
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void alreadyLoadedLegacyTripleCrossbowFiresOnlyOnce(GameTestHelper test) {
        ItemStack weapon = new ItemStack(Items.CROSSBOW);
        weapon.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.ofNonEmpty(List.of(
                new ItemStack(Items.ARROW), new ItemStack(Items.ARROW), new ItemStack(Items.ARROW))));
        Player player = shooter(test, weapon);
        ((CrossbowItem) Items.CROSSBOW).performShooting(test.getLevel(), player,
                InteractionHand.MAIN_HAND, weapon, 3.15F, 0, null);
        test.assertValueEqual(test.getEntities(EntityTypes.ARROW).size(), 1,
                "A crossbow loaded before removal cannot retain its old triple volley");
        test.assertValueEqual(weapon.getDamageValue(), 1, "Only one projectile is discharged");
        test.succeed();
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void unenchantedWeaponsRetainOneVanillaArrow(GameTestHelper test) {
        for (Item item : List.of(Items.BOW, Items.CROSSBOW)) {
            ItemStack weapon = new ItemStack(item);
            Player player = shooter(test, weapon);
            if (item instanceof BowItem bow) {
                bow.releaseUsing(weapon, test.getLevel(), player, weapon.getUseDuration(player) - 20);
            } else {
                fireCrossbow(test, player, weapon);
            }
        }
        List<Arrow> arrows = test.getEntities(EntityTypes.ARROW);
        test.assertValueEqual(arrows.size(), 2, "Unenchanted weapons each fire one arrow");
        for (Arrow arrow : arrows) {
            test.assertTrue(((ArrowAccess) arrow).bowpack$getState() == null,
                    "Unenchanted projectiles must retain vanilla behavior");
            test.assertFalse(arrow.isNoGravity(), "Vanilla arrows retain gravity");
        }
        test.succeed();
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void backArrowDamagesTheMobBehindWithoutHittingItsShooter(GameTestHelper test) {
        var player = BookAccessGameTests.player(test);
        try {
            var target = ProjectileFixtures.cow(test, new Vec3(4, 3.3, -1));
            float health = player.getHealth();
            ItemStack bow = new ItemStack(Items.BOW);
            bow.enchant(test.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                    .getOrThrow(BowEnchantments.BACK_SHOT), 1);
            player.setYRot(0);
            player.setXRot(0);
            player.setItemInHand(InteractionHand.MAIN_HAND, bow);
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.ARROW, 2));
            player.startUsingItem(InteractionHand.MAIN_HAND);
            ((BowItem) Items.BOW).releaseUsing(bow, test.getLevel(), player, bow.getUseDuration(player) - 20);
            List<Arrow> arrows = test.getEntities(EntityTypes.ARROW);
            test.assertValueEqual(arrows.size(), 2, "Back I adds exactly one backward projectile");
            for (int tick = 0; tick < 4; tick++) {
                for (Arrow arrow : arrows) if (arrow.isAlive()) arrow.tick();
            }
            test.assertTrue(target.getHealth() < target.getMaxHealth(),
                    "The real backward projectile must damage the mob behind the shooter");
            test.assertValueEqual(player.getHealth(), health, "The backward arrow cannot hit its owner");
            test.succeed();
        } finally {
            player.discard();
        }
    }

    static Player shooter(GameTestHelper test, ItemStack weapon) {
        Player player = test.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(ProjectileFixtures.position(test, new Vec3(4, 3, 4)));
        player.setYRot(0);
        player.setXRot(0);
        player.setItemInHand(InteractionHand.MAIN_HAND, weapon);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.ARROW, 32));
        player.startUsingItem(InteractionHand.MAIN_HAND);
        return player;
    }

    static void fireCrossbow(GameTestHelper test, Player player, ItemStack weapon) {
        CrossbowItem crossbow = (CrossbowItem) Items.CROSSBOW;
        crossbow.onUseTick(test.getLevel(), player, weapon, weapon.getUseDuration(player) - 30);
        test.assertTrue(CrossbowItem.isCharged(weapon), "Native charging should load an arrow");
        crossbow.performShooting(test.getLevel(), player, InteractionHand.MAIN_HAND, weapon,
                3.15F, 0, null);
    }

    private static void assertMaximumFormation(GameTestHelper test, Player player, double speed) {
        List<Arrow> arrows = test.getEntities(EntityTypes.ARROW);
        test.assertValueEqual(arrows.size(), 80, "All 80 arrows must exist in the server world");
        test.assertValueEqual(arrows.stream().filter(arrow -> arrow.pickup == AbstractArrow.Pickup.ALLOWED).count(),
                1L, "Only the original survival arrow can be picked up");
        test.assertValueEqual(arrows.stream().filter(arrow -> arrow.pickup == AbstractArrow.Pickup.DISALLOWED).count(),
                79L, "Additional arrows cannot duplicate ammunition through pickup");
        for (Arrow arrow : arrows) {
            var state = ProjectileFixtures.state(arrow);
            ArrowAccess synced = (ArrowAccess) arrow;
            test.assertTrue(synced.bowpack$isManaged() && !synced.bowpack$ricochetEnabled(),
                    "A Breakthrough arrow is managed but continues straight instead of redirecting itself");
            test.assertValueEqual(synced.bowpack$remainingBounces(), 30,
                    "Synced entity state carries every arrow's bounce budget");
            test.assertTrue(state.ricochetLevel() == 4 && state.antiFriction() && state.breakthrough(),
                    "Every arrow inherits Ricochet, Anti Friction, and Breakthrough");
            test.assertValueEqual(state.bounces(), 30, "Every arrow has thirty block bounces");
            test.assertValueEqual(state.shooter(), player.getUUID(), "Every arrow retains its shooter");
            test.assertTrue(arrow.isNoGravity(), "Anti Friction applies to every arrow");
            ProjectileFixtures.near(test, speed, arrow.getDeltaMovement().length(), "Equal launch speed");
        }
    }
}
