package dev.bowpack.gametest;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.bowpack.BowEnchantments;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

public class AnvilProgressionGameTests {
    @GameTest(structure = ProjectileFixtures.ARENA)
    public void balancedBooksReachAllTenMaximumLevelsInSurvival(GameTestHelper test)
            throws CommandSyntaxException {
        ServerPlayer player = BookAccessGameTests.player(test);
        player.giveExperienceLevels(1000);
        try {
            for (Item item : List.of(Items.BOW, Items.CROSSBOW)) {
                ItemStack weapon = BookAccessGameTests.combine(test, player, new ItemStack(item),
                        book(test, player, BowEnchantments.MULTI_SHOT));
                ItemStack frontBack = BookAccessGameTests.combine(test, player,
                        book(test, player, BowEnchantments.FRONT_SHOT),
                        book(test, player, BowEnchantments.BACK_SHOT));
                ItemStack diagonalRicochet = BookAccessGameTests.combine(test, player,
                        book(test, player, BowEnchantments.DIAGONAL_SHOT),
                        book(test, player, BowEnchantments.RICOCHET_SHOT));
                ItemStack directions = BookAccessGameTests.combine(test, player, frontBack, diagonalRicochet);
                weapon = BookAccessGameTests.combine(test, player, weapon, directions);

                ItemStack antiBreakthrough = BookAccessGameTests.combine(test, player,
                        book(test, player, BowEnchantments.ANTI_FRICTION_SHOT),
                        book(test, player, BowEnchantments.BREAKTHROUGH_SHOT));
                ItemStack rainyFollow = BookAccessGameTests.combine(test, player,
                        book(test, player, BowEnchantments.RAINY_SHOT),
                        book(test, player, BowEnchantments.FOLLOW_SHOT));
                ItemStack special = BookAccessGameTests.combine(test, player, antiBreakthrough, rainyFollow);
                ItemStack bouncySpecial = BookAccessGameTests.combine(test, player,
                        book(test, player, BowEnchantments.BOUNCY_SHOT), special);
                weapon = BookAccessGameTests.combine(test, player, weapon, bouncySpecial);
                var registry = test.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
                for (var key : BowEnchantments.ALL) {
                    var enchantment = registry.getOrThrow(key);
                    test.assertValueEqual(EnchantmentHelper.getItemEnchantmentLevel(enchantment, weapon),
                            enchantment.value().getMaxLevel(), "Survival anvil reaches the maximum: " + key);
                }
            }
            test.succeed();
        } finally {
            player.discard();
        }
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void frontBooksUpgradeToFourAndLegacyFiveCannotExceedIt(GameTestHelper test)
            throws CommandSyntaxException {
        ServerPlayer player = BookAccessGameTests.player(test);
        player.giveExperienceLevels(1000);
        var front = test.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(BowEnchantments.FRONT_SHOT);
        try {
            ItemStack upgraded = BookAccessGameTests.combine(test, player,
                    BookAccessGameTests.giveBook(test, player, BowEnchantments.FRONT_SHOT, 3),
                    BookAccessGameTests.giveBook(test, player, BowEnchantments.FRONT_SHOT, 3));
            test.assertValueEqual(EnchantmentHelper.getEnchantmentsForCrafting(upgraded).getLevel(front),
                    4, "Two Front III books produce Front IV");
            upgraded = BookAccessGameTests.combine(test, player, upgraded,
                    BookAccessGameTests.giveBook(test, player, BowEnchantments.FRONT_SHOT, 4));
            test.assertValueEqual(EnchantmentHelper.getEnchantmentsForCrafting(upgraded).getLevel(front),
                    4, "Two Front IV books cannot produce Front V");
            for (Item item : List.of(Items.BOW, Items.CROSSBOW)) {
                ItemStack result = BookAccessGameTests.combine(test, player, new ItemStack(item),
                        BookAccessGameTests.giveBook(test, player, BowEnchantments.FRONT_SHOT, 5));
                test.assertValueEqual(EnchantmentHelper.getItemEnchantmentLevel(front, result), 4,
                        "Legacy Front V books on the right are capped by the native anvil");
            }
            test.succeed();
        } finally {
            player.discard();
        }
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void legacyFrontFiveWeaponsFireOnlyFiveArrows(GameTestHelper test) {
        var front = test.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(BowEnchantments.FRONT_SHOT);
        for (Item item : List.of(Items.BOW, Items.CROSSBOW)) {
            ItemStack weapon = new ItemStack(item);
            weapon.enchant(front, 5);
            var player = WeaponGameTests.shooter(test, weapon);
            if (item instanceof BowItem bow) {
                bow.releaseUsing(weapon, test.getLevel(), player, weapon.getUseDuration(player) - 20);
            } else {
                WeaponGameTests.fireCrossbow(test, player, weapon);
            }
            var arrows = test.getEntities(EntityTypes.ARROW);
            test.assertValueEqual(arrows.size(), 5, "Legacy V fires one main plus four Front arrows");
            test.assertValueEqual(EnchantmentHelper.getItemEnchantmentLevel(front, weapon), 5,
                    "Existing inventory data is preserved instead of rewritten");
            arrows.forEach(arrow -> arrow.discard());
        }
        test.succeed();
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void nativeTooExpensiveLimitStillBlocksOverworkedWeapons(GameTestHelper test)
            throws CommandSyntaxException {
        ServerPlayer player = BookAccessGameTests.player(test);
        player.giveExperienceLevels(1000);
        try {
            AnvilMenu menu = new AnvilMenu(1, player.getInventory());
            ItemStack weapon = new ItemStack(Items.BOW);
            weapon.set(DataComponents.REPAIR_COST, 63);
            menu.getSlot(AnvilMenu.INPUT_SLOT).set(weapon);
            menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(book(test, player, BowEnchantments.BACK_SHOT));
            menu.createResult();
            test.assertTrue(menu.getCost() >= 40 && menu.getSlot(AnvilMenu.RESULT_SLOT).getItem().isEmpty(),
                    "Survival still enforces Too Expensive even when the player has enough XP");
            test.succeed();
        } finally {
            player.discard();
        }
    }

    private static ItemStack book(GameTestHelper test, ServerPlayer player,
            ResourceKey<Enchantment> key)
            throws CommandSyntaxException {
        int maximum = test.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(key).value().getMaxLevel();
        return BookAccessGameTests.giveBook(test, player, key, maximum);
    }
}
