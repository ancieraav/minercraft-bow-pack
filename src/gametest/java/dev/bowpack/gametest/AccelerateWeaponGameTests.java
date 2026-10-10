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
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.ChargedProjectiles;

public class AccelerateWeaponGameTests {
    @GameTest(structure = ProjectileFixtures.ARENA)
    public void accelerateAloneActivatesBothWeaponsAndCapsOverleveledBooks(GameTestHelper test) {
        var enchantment = test.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(BowEnchantments.ACCELERATE);
        for (Item item : List.of(Items.BOW, Items.CROSSBOW)) {
            for (int level : new int[] {1, 2, 3, 5}) {
                ItemStack weapon = new ItemStack(item);
                weapon.enchant(enchantment, level);
                Player shooter = WeaponGameTests.shooter(test, weapon);
                fire(test, shooter, weapon);
                var arrows = test.getEntities(EntityTypes.ARROW);
                test.assertValueEqual(arrows.size(), 1, "Accelerate alone fires exactly one managed arrow");
                var arrow = arrows.getFirst();
                int expected = Math.min(3, level);
                test.assertValueEqual(ProjectileFixtures.state(arrow).accelerateLevel(), expected,
                        "Both weapon paths carry Accelerate and enforce its maximum effect level");
                test.assertValueEqual(((ArrowAccess) arrow).bowpack$accelerateLevel(), expected,
                        "Accelerate is available to client prediction through synchronized metadata");
                double launch = arrow.getDeltaMovement().length();
                arrow.tick();
                ProjectileFixtures.near(test, launch + 0.05 * (expected + 1),
                        arrow.getDeltaMovement().length(), "Both weapons accelerate after native flight physics");
                arrow.discard();
            }
        }
        test.succeed();
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void everyNormalTippedAndSpectralCopyInheritsAccelerate(GameTestHelper test) {
        var registry = test.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        PotionContents poison = new PotionContents(Potions.POISON);
        for (Item item : List.of(Items.BOW, Items.CROSSBOW)) {
            for (Item ammunition : List.of(Items.ARROW, Items.TIPPED_ARROW, Items.SPECTRAL_ARROW)) {
                ItemStack weapon = new ItemStack(item);
                for (var key : List.of(BowEnchantments.MULTI_SHOT, BowEnchantments.FRONT_SHOT,
                        BowEnchantments.BACK_SHOT, BowEnchantments.DIAGONAL_SHOT, BowEnchantments.ACCELERATE)) {
                    var enchantment = registry.getOrThrow(key);
                    weapon.enchant(enchantment, enchantment.value().getMaxLevel());
                }
                ItemStack ammo = new ItemStack(ammunition, 32);
                if (ammunition == Items.TIPPED_ARROW) ammo.set(DataComponents.POTION_CONTENTS, poison);
                Player shooter = WeaponGameTests.shooter(test, weapon);
                shooter.setItemInHand(InteractionHand.OFF_HAND, ammo);
                fire(test, shooter, weapon);
                List<? extends AbstractArrow> arrows = ammunition == Items.SPECTRAL_ARROW
                        ? test.getEntities(EntityTypes.SPECTRAL_ARROW) : test.getEntities(EntityTypes.ARROW);
                test.assertValueEqual(arrows.size(), 80, "Accelerate preserves the complete 80-arrow formation");
                for (AbstractArrow arrow : arrows) {
                    test.assertValueEqual(ProjectileFixtures.state(arrow).accelerateLevel(), 3,
                            "Every direction and ammunition copy inherits Accelerate III");
                    test.assertTrue(arrow.getPickupItemStackOrigin().is(ammunition),
                            "Acceleration preserves the native ammunition type");
                    if (ammunition == Items.TIPPED_ARROW) test.assertValueEqual(
                            arrow.getPickupItemStackOrigin().get(DataComponents.POTION_CONTENTS), poison,
                            "Accelerate preserves the potion payload");
                    arrow.discard();
                }
                test.assertValueEqual(ammo.getCount(), 31, "An accelerated volley still consumes one arrow");
                test.assertValueEqual(weapon.getDamageValue(), 1, "An accelerated volley costs one durability");
            }
        }
        test.succeed();
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void accelerateLeavesCrossbowFireworksUnchanged(GameTestHelper test) {
        ItemStack weapon = new ItemStack(Items.CROSSBOW);
        weapon.enchant(test.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(BowEnchantments.ACCELERATE), 3);
        weapon.set(DataComponents.CHARGED_PROJECTILES,
                ChargedProjectiles.ofNonEmpty(List.of(new ItemStack(Items.FIREWORK_ROCKET))));
        Player shooter = WeaponGameTests.shooter(test, weapon);
        ((CrossbowItem) Items.CROSSBOW).performShooting(test.getLevel(), shooter,
                InteractionHand.MAIN_HAND, weapon, 1.6F, 0, null);
        test.assertValueEqual(test.getEntities(EntityTypes.FIREWORK_ROCKET).size(), 1,
                "An Accelerate crossbow still fires one native firework");
        test.assertTrue(test.getEntities(EntityTypes.ARROW).isEmpty(), "Fireworks cannot become managed arrows");
        test.succeed();
    }

    private static void fire(GameTestHelper test, Player shooter, ItemStack weapon) {
        if (weapon.getItem() instanceof BowItem bow) {
            bow.releaseUsing(weapon, test.getLevel(), shooter, weapon.getUseDuration(shooter) - 20);
        } else {
            WeaponGameTests.fireCrossbow(test, shooter, weapon);
        }
    }
}
