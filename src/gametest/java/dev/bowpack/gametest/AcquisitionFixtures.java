package dev.bowpack.gametest;

import dev.bowpack.BowEnchantments;
import java.util.List;
import java.util.Set;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

final class AcquisitionFixtures {
    static final List<ResourceKey<Enchantment>> NORMAL = List.of(
            BowEnchantments.MULTI_SHOT, BowEnchantments.FRONT_SHOT, BowEnchantments.BACK_SHOT,
            BowEnchantments.DIAGONAL_SHOT, BowEnchantments.BOUNCY_SHOT,
            BowEnchantments.BREAKTHROUGH_SHOT, BowEnchantments.FOLLOW_SHOT);
    static final List<ResourceKey<Enchantment>> TREASURE = List.of(
            BowEnchantments.RICOCHET_SHOT, BowEnchantments.ANTI_FRICTION_SHOT,
            BowEnchantments.UNSTOPABLE);

    private AcquisitionFixtures() {}

    static void collect(GameTestHelper test, ItemStack stack, Set<ResourceKey<Enchantment>> observed) {
        for (var entry : EnchantmentHelper.getEnchantmentsForCrafting(stack).entrySet()) {
            var holder = entry.getKey();
            test.assertFalse(holder.is(Enchantments.MULTISHOT),
                    "Native acquisition must never generate disabled vanilla Multishot");
            int level = entry.getIntValue();
            test.assertTrue(level >= 1 && level <= holder.value().getMaxLevel(),
                    "Generated enchantments must respect their registered level range");
            var key = holder.unwrapKey().orElseThrow();
            if (BowEnchantments.ALL.contains(key)) observed.add(key);
        }
    }

    static void assertObserved(GameTestHelper test, Set<ResourceKey<Enchantment>> observed,
                               List<ResourceKey<Enchantment>> expected, String source) {
        for (var key : expected) {
            test.assertTrue(observed.contains(key), source + " must generate " + key.identifier());
        }
    }
}
