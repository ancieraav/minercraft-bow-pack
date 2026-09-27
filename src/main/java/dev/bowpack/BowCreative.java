package dev.bowpack;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;

public final class BowCreative {
    private BowCreative() {
    }

    public static void register() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, BowPack.id("bow_pack"),
                FabricCreativeModeTab.builder()
                        .title(Component.translatable("itemGroup.bowpack"))
                        .icon(() -> new ItemStack(Items.ENCHANTED_BOOK))
                        .displayItems(BowCreative::populate)
                        .build());
        CreativeModeTabEvents.MODIFY_OUTPUT_ALL.register((tab, output) -> {
            output.getDisplayStacks().removeIf(BowCreative::isVanillaMultishotBook);
            output.getSearchTabStacks().removeIf(BowCreative::isVanillaMultishotBook);
        });
    }

    private static void populate(CreativeModeTab.ItemDisplayParameters parameters, CreativeModeTab.Output output) {
        var enchantments = parameters.holders().lookupOrThrow(Registries.ENCHANTMENT);
        for (var key : BowEnchantments.ALL) {
            var enchantment = enchantments.getOrThrow(key);
            for (int level = 1; level <= enchantment.value().getMaxLevel(); level++) {
                output.accept(EnchantmentHelper.createBook(new EnchantmentInstance(enchantment, level)));
            }
        }
    }

    private static boolean isVanillaMultishotBook(ItemStack stack) {
        return stack.is(Items.ENCHANTED_BOOK)
                && EnchantmentHelper.getEnchantmentsForCrafting(stack).keySet().stream()
                        .anyMatch(enchantment -> enchantment.is(Enchantments.MULTISHOT));
    }
}
