package dev.bowpack.gametest;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

public class MendingInfinityGameTests {
    @GameTest(structure = ProjectileFixtures.ARENA)
    public void survivalAnvilCombinesMendingAndInfinityInBothOrders(GameTestHelper test)
            throws CommandSyntaxException {
        ServerPlayer player = BookAccessGameTests.player(test);
        player.giveExperienceLevels(100);
        try {
            var enchantments = test.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            for (ResourceKey<Enchantment> first : List.of(Enchantments.MENDING, Enchantments.INFINITY)) {
                ResourceKey<Enchantment> second = first == Enchantments.MENDING
                        ? Enchantments.INFINITY : Enchantments.MENDING;
                ItemStack bow = BookAccessGameTests.combine(test, player, new ItemStack(Items.BOW),
                        BookAccessGameTests.giveBook(test, player, first, 1));
                bow = BookAccessGameTests.combine(test, player, bow,
                        BookAccessGameTests.giveBook(test, player, second, 1));
                test.assertValueEqual(EnchantmentHelper.getItemEnchantmentLevel(
                        enchantments.getOrThrow(Enchantments.MENDING), bow), 1,
                        "The bow keeps Mending after both anvil operations");
                test.assertValueEqual(EnchantmentHelper.getItemEnchantmentLevel(
                        enchantments.getOrThrow(Enchantments.INFINITY), bow), 1,
                        "The bow keeps Infinity after both anvil operations");
            }
            test.succeed();
        } finally {
            player.discard();
        }
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void infinityBookStillCannotEnchantCrossbow(GameTestHelper test)
            throws CommandSyntaxException {
        ServerPlayer player = BookAccessGameTests.player(test);
        player.giveExperienceLevels(100);
        try {
            ItemStack crossbow = BookAccessGameTests.combine(test, player,
                    new ItemStack(Items.CROSSBOW),
                    BookAccessGameTests.giveBook(test, player, Enchantments.MENDING, 1));
            ItemStack infinityBook = BookAccessGameTests.giveBook(test, player, Enchantments.INFINITY, 1);
            AnvilMenu menu = new AnvilMenu(1, player.getInventory());
            menu.getSlot(AnvilMenu.INPUT_SLOT).set(crossbow);
            menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(infinityBook);
            menu.createResult();
            test.assertTrue(menu.getSlot(AnvilMenu.RESULT_SLOT).getItem().isEmpty(),
                    "Infinity remains unavailable on crossbows");
            test.succeed();
        } finally {
            player.discard();
        }
    }
}
