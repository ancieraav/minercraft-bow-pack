package dev.bowpack.gametest;

import dev.bowpack.BowEnchantments;
import java.util.HashSet;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

public class AcquisitionGameTests {
    @GameTest(structure = ProjectileFixtures.ARENA)
    public void nativeTableEligibilityHonorsLevelThresholdsOnBooksAndBothWeapons(GameTestHelper test) {
        var registry = test.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var table = registry.getOrThrow(EnchantmentTags.IN_ENCHANTING_TABLE);
        var thresholds = List.of(
                new Threshold(BowEnchantments.MULTI_SHOT, List.of(5, 15, 25, 35)),
                new Threshold(BowEnchantments.FRONT_SHOT, List.of(5, 15, 25, 35)),
                new Threshold(BowEnchantments.BACK_SHOT, List.of(1, 9, 17, 25, 33)),
                new Threshold(BowEnchantments.DIAGONAL_SHOT, List.of(10, 20, 30)),
                new Threshold(BowEnchantments.BOUNCY_SHOT, List.of(5, 15, 25)),
                new Threshold(BowEnchantments.BREAKTHROUGH_SHOT, List.of(10)),
                new Threshold(BowEnchantments.FOLLOW_SHOT, List.of(10)));
        for (var item : List.of(Items.BOOK, Items.BOW, Items.CROSSBOW)) {
            ItemStack stack = new ItemStack(item);
            for (var spec : thresholds) {
                for (int index = 0; index < spec.costs().size(); index++) {
                    int threshold = spec.costs().get(index);
                    var at = EnchantmentHelper.getAvailableEnchantmentResults(threshold, stack, table.stream());
                    int selected = at.stream().filter(entry -> entry.enchantment().is(spec.key()))
                            .mapToInt(entry -> entry.level()).findFirst().orElse(0);
                    test.assertValueEqual(selected, index + 1,
                            "The native table must unlock each level at its planned cost");
                    var before = EnchantmentHelper.getAvailableEnchantmentResults(
                            threshold - 1, stack, table.stream());
                    test.assertTrue(before.stream().filter(entry -> entry.enchantment().is(spec.key()))
                                    .allMatch(entry -> entry.level() < selected),
                            "The native table must not unlock a level below its planned cost");
                }
            }
            for (int cost = 1; cost <= 60; cost++) {
                var candidates = EnchantmentHelper.getAvailableEnchantmentResults(cost, stack, table.stream());
                test.assertTrue(candidates.stream().noneMatch(entry ->
                                AcquisitionFixtures.TREASURE.stream().anyMatch(entry.enchantment()::is)),
                    "Treasure enchantments must never become table candidates");
            }
        }
        test.succeed();
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void nativeTableRollsGenerateEveryNormalEnchantment(GameTestHelper test) {
        var table = test.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(EnchantmentTags.IN_ENCHANTING_TABLE);
        for (var item : List.of(Items.BOOK, Items.BOW, Items.CROSSBOW)) {
            var observed = new HashSet<ResourceKey<Enchantment>>();
            for (long seed = 1; seed <= 1024; seed++) {
                ItemStack output = EnchantmentHelper.enchantItem(
                        RandomSource.create(seed), new ItemStack(item), 30, table.stream());
                AcquisitionFixtures.collect(test, output, observed);
            }
            AcquisitionFixtures.assertObserved(test, observed, AcquisitionFixtures.NORMAL, "Native table");
            test.assertTrue(AcquisitionFixtures.TREASURE.stream().noneMatch(observed::contains),
                    "Native table rolls must exclude treasure enchantments");
        }
        test.succeed();
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void treasureBooksUseNativeAcquisitionAndPriceTags(GameTestHelper test) {
        var registry = test.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        for (var key : BowEnchantments.ALL) {
            var holder = registry.getOrThrow(key);
            boolean treasure = AcquisitionFixtures.TREASURE.contains(key);
            test.assertTrue(holder.is(EnchantmentTags.TRADEABLE) && holder.is(EnchantmentTags.ON_RANDOM_LOOT),
                    "All Bow Pack enchantments must join native book trades and loot");
            test.assertValueEqual(holder.is(EnchantmentTags.TREASURE), treasure, "Treasure category");
            test.assertValueEqual(holder.is(EnchantmentTags.DOUBLE_TRADE_PRICE), treasure,
                    "Only treasure books must receive native doubled trade pricing");
            test.assertValueEqual(holder.is(EnchantmentTags.IN_ENCHANTING_TABLE), !treasure, "Table category");
        }
        test.succeed();
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void nativeGrindstoneRemovesAllTenEnchantments(GameTestHelper test) {
        var player = BookAccessGameTests.player(test);
        try {
            for (var item : List.of(Items.ENCHANTED_BOOK, Items.BOW, Items.CROSSBOW)) {
                ItemStack enchanted = ProjectileFixtures.maxWeapon(item, test.getLevel().registryAccess());
                test.assertValueEqual(EnchantmentHelper.getEnchantmentsForCrafting(enchanted).size(), 10,
                        "The grindstone input must contain all ten enchantments");
                GrindstoneMenu menu = new GrindstoneMenu(1, player.getInventory());
                menu.getSlot(GrindstoneMenu.INPUT_SLOT).set(enchanted);
                ItemStack result = menu.getSlot(GrindstoneMenu.RESULT_SLOT).safeTake(1, 1, player);
                test.assertFalse(result.isEmpty(), "The native grindstone must produce a usable output");
                test.assertTrue(result.is(item == Items.ENCHANTED_BOOK ? Items.BOOK : item),
                        "The grindstone must preserve weapons and return an ordinary book");
                test.assertTrue(EnchantmentHelper.getEnchantmentsForCrafting(result).isEmpty(),
                        "Every Bow Pack enchantment must be removable by the grindstone");
                test.assertTrue(menu.getSlot(GrindstoneMenu.INPUT_SLOT).getItem().isEmpty(),
                        "Taking grindstone output must consume the enchanted input");
            }
            test.succeed();
        } finally {
            player.discard();
        }
    }

    private record Threshold(ResourceKey<Enchantment> key, List<Integer> costs) {}
}
