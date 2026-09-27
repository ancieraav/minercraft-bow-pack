package dev.bowpack.gametest;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.bowpack.BowEnchantments;
import dev.bowpack.BowPack;
import java.util.Collection;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

public class BookAccessGameTests {
    private static final List<BookSpec> BOOKS = List.of(
            new BookSpec(BowEnchantments.MULTI_SHOT, 4),
            new BookSpec(BowEnchantments.FRONT_SHOT, 4),
            new BookSpec(BowEnchantments.BACK_SHOT, 5),
            new BookSpec(BowEnchantments.DIAGONAL_SHOT, 3),
            new BookSpec(BowEnchantments.RICOCHET_SHOT, 4),
            new BookSpec(BowEnchantments.BOUNCY_SHOT, 3),
            new BookSpec(BowEnchantments.ANTI_FRICTION_SHOT, 1),
            new BookSpec(BowEnchantments.BREAKTHROUGH_SHOT, 1),
            new BookSpec(BowEnchantments.RAINY_SHOT, 3),
            new BookSpec(BowEnchantments.FOLLOW_SHOT, 1));

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void nativeGiveProvidesEveryDocumentedBookWithoutCustomCommands(GameTestHelper test)
            throws CommandSyntaxException {
        ServerPlayer player = player(test);
        try {
            var source = player.createCommandSourceStack();
            test.assertTrue(source.dispatcher().getRoot().getChild("bowpack") == null,
                    "Bow Pack must not register a custom command root");
            for (BookSpec spec : BOOKS) {
                ItemStack book = giveBook(test, player, spec.key(), spec.maximum());
                var enchantment = test.getLevel().registryAccess()
                        .lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(spec.key());
                test.assertTrue(isBook(book, enchantment, spec.maximum()),
                        "Native /give must create the documented book: " + spec.key().identifier());
            }
            test.succeed();
        } finally {
            player.discard();
        }
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void creativeCatalogContainsOnlyTwentyNineSearchableBooks(GameTestHelper test) {
        CreativeModeTabs.tryRebuildTabContents(test.getLevel().enabledFeatures(), true,
                test.getLevel().registryAccess());
        var tab = BuiltInRegistries.CREATIVE_MODE_TAB.getOptional(BowPack.id("bow_pack")).orElseThrow();
        assertCatalog(test, tab.getDisplayItems());
        assertCatalog(test, tab.getSearchTabDisplayItems());
        for (ItemStack book : tab.getDisplayItems()) {
            test.assertTrue(CreativeModeTabs.searchTab().getDisplayItems().stream()
                            .anyMatch(stack -> ItemStack.isSameItemSameComponents(stack, book)),
                    "Every Bow Pack book level must appear in Creative search");
        }
        test.succeed();
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void nativeAnvilCombinesRicochetAndNewBooksOnBothWeapons(GameTestHelper test)
            throws CommandSyntaxException {
        ServerPlayer player = player(test);
        player.giveExperienceLevels(100);
        try {
            for (Item item : List.of(Items.BOW, Items.CROSSBOW)) {
                for (var pair : List.of(
                        List.of(BowEnchantments.RICOCHET_SHOT, BowEnchantments.ANTI_FRICTION_SHOT),
                        List.of(BowEnchantments.BREAKTHROUGH_SHOT, BowEnchantments.FOLLOW_SHOT))) {
                    ItemStack weapon = new ItemStack(item);
                    var enchantments = test.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
                    for (var key : pair) {
                        int maximum = enchantments.getOrThrow(key).value().getMaxLevel();
                        weapon = combine(test, player, weapon, giveBook(test, player, key, maximum));
                    }
                    test.assertTrue(weapon.is(item), "The anvil must preserve the weapon type");
                    for (var key : pair) {
                        var enchantment = enchantments.getOrThrow(key);
                        test.assertValueEqual(EnchantmentHelper.getItemEnchantmentLevel(enchantment, weapon),
                                enchantment.value().getMaxLevel(),
                                "Both book enchantments must remain after sequential anvil operations");
                    }
                }
            }
            test.succeed();
        } finally {
            player.discard();
        }
    }

    static ServerPlayer player(GameTestHelper test) {
        ServerPlayer player = test.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(ProjectileFixtures.position(test, new Vec3(4, 3, 4)));
        return player;
    }

    static ItemStack giveBook(GameTestHelper test, ServerPlayer player,
                                     ResourceKey<Enchantment> key, int level) throws CommandSyntaxException {
        player.getInventory().clearContent();
        var source = player.createCommandSourceStack()
                .withPermission(LevelBasedPermissionSet.OWNER).withSuppressedOutput();
        String command = "give @s minecraft:enchanted_book[minecraft:stored_enchantments={\""
                + key.identifier() + "\":" + level + "}] 1";
        test.assertValueEqual(source.dispatcher().execute(command, source), 1,
                "The native give command must succeed for one player");
        ItemStack book = player.getInventory().getItem(0).copy();
        test.assertValueEqual(book.getCount(), 1, "The native command must give exactly one book");
        player.getInventory().clearContent();
        return book;
    }

    static ItemStack combine(GameTestHelper test, ServerPlayer player,
                                    ItemStack weapon, ItemStack book) {
        AnvilMenu menu = new AnvilMenu(1, player.getInventory());
        menu.getSlot(AnvilMenu.INPUT_SLOT).set(weapon);
        menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(book);
        menu.createResult();
        int cost = menu.getCost();
        int previousLevel = player.experienceLevel;
        test.assertTrue(cost > 0 && menu.getSlot(AnvilMenu.RESULT_SLOT).mayPickup(player),
                "The native survival anvil must allow the book combination");
        ItemStack result = menu.getSlot(AnvilMenu.RESULT_SLOT).safeTake(1, 1, player);
        test.assertFalse(result.isEmpty(), "Taking the anvil output must return the enchanted weapon");
        test.assertValueEqual(player.experienceLevel, previousLevel - cost,
                "Taking the anvil output must consume the native experience cost");
        test.assertTrue(menu.getSlot(AnvilMenu.INPUT_SLOT).getItem().isEmpty()
                        && menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).getItem().isEmpty(),
                "Taking the anvil output must consume the input weapon and book");
        return result;
    }

    private static void assertCatalog(GameTestHelper test, Collection<ItemStack> books) {
        test.assertValueEqual(books.size(), 29, "The Bow Pack catalog must contain exactly 29 books");
        test.assertTrue(books.stream().allMatch(stack -> stack.is(Items.ENCHANTED_BOOK)),
                "The Bow Pack catalog must not contain demo weapons or other items");
        var enchantments = test.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        for (BookSpec spec : BOOKS) {
            var enchantment = enchantments.getOrThrow(spec.key());
            for (int level = 1; level <= spec.maximum(); level++) {
                int expectedLevel = level;
                long matches = books.stream().filter(stack -> isBook(stack, enchantment, expectedLevel)).count();
                test.assertValueEqual(matches, 1L, "Every enchantment level must appear exactly once");
            }
        }
    }

    private static boolean isBook(ItemStack stack, Holder<Enchantment> enchantment, int level) {
        var stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
        return stack.is(Items.ENCHANTED_BOOK) && stored != null
                && stored.size() == 1 && stored.getLevel(enchantment) == level;
    }

    private record BookSpec(ResourceKey<Enchantment> key, int maximum) {}
}
