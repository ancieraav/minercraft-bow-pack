package dev.bowpack.gametest;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

public class AmmunitionGameTests {
    @GameTest(structure = ProjectileFixtures.ARENA)
    public void tippedArrowCopiesRetainTheirPotionForBothWeapons(GameTestHelper test) {
        PotionContents poison = new PotionContents(Potions.POISON);
        for (Item item : List.of(Items.BOW, Items.CROSSBOW)) {
            ItemStack ammo = new ItemStack(Items.TIPPED_ARROW, 32);
            ammo.set(DataComponents.POTION_CONTENTS, poison);
            fire(test, item, ammo);
            var arrows = test.getEntities(EntityTypes.ARROW);
            test.assertValueEqual(arrows.size(), 80, "Every tipped projectile is a real Arrow");
            for (var arrow : arrows) {
                test.assertValueEqual(arrow.getPickupItemStackOrigin().get(DataComponents.POTION_CONTENTS),
                        poison, "Every additional projectile retains its potion payload");
                test.assertValueEqual(ProjectileFixtures.state(arrow).ricochetLevel(), 4,
                        "Every copy retains Ricochet IV");
                arrow.discard();
            }
        }
        test.succeed();
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void spectralArrowCopiesRetainTheirNativeTypeForBothWeapons(GameTestHelper test) {
        for (Item item : List.of(Items.BOW, Items.CROSSBOW)) {
            fire(test, item, new ItemStack(Items.SPECTRAL_ARROW, 32));
            var arrows = test.getEntities(EntityTypes.SPECTRAL_ARROW);
            test.assertValueEqual(arrows.size(), 80, "Every spectral copy remains a SpectralArrow");
            test.assertTrue(test.getEntities(EntityTypes.ARROW).isEmpty(),
                    "Spectral ammunition cannot silently turn into ordinary arrows");
            for (var arrow : arrows) {
                test.assertTrue(ProjectileFixtures.state(arrow).antiFriction(),
                        "Every copy inherits Anti Friction");
                test.assertValueEqual(ProjectileFixtures.state(arrow).bounces(), 30,
                        "Every copy inherits Bouncy III");
                arrow.discard();
            }
        }
        test.succeed();
    }

    private static void fire(GameTestHelper test, Item item, ItemStack ammo) {
        ItemStack weapon = ProjectileFixtures.maxWeapon(item, test.getLevel().registryAccess());
        Player shooter = WeaponGameTests.shooter(test, weapon);
        shooter.setItemInHand(InteractionHand.OFF_HAND, ammo);
        if (item instanceof BowItem bow) {
            bow.releaseUsing(weapon, test.getLevel(), shooter, weapon.getUseDuration(shooter) - 20);
        } else {
            WeaponGameTests.fireCrossbow(test, shooter, weapon);
        }
        test.assertValueEqual(ammo.getCount(), 31, "The entire volley consumes one special arrow");
        test.assertValueEqual(weapon.getDamageValue(), 1, "The entire volley costs one durability");
    }
}
