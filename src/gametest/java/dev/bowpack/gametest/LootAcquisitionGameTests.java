package dev.bowpack.gametest;

import dev.bowpack.BowEnchantments;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.trading.VillagerTrade;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

public class LootAcquisitionGameTests {
    @GameTest(structure = ProjectileFixtures.ARENA)
    public void nativeFishingTreasureGeneratesAllTenBooks(GameTestHelper test) {
        assertLootBooks(test, BuiltInLootTables.FISHING_TREASURE, true);
        test.succeed();
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void nativeStrongholdLibraryGeneratesAllTenBooks(GameTestHelper test) {
        assertLootBooks(test, BuiltInLootTables.STRONGHOLD_LIBRARY, false);
        test.succeed();
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void standardLibrarianDefinitionGeneratesAllTenBooksWithNativePricing(GameTestHelper test)
            throws IOException {
        var access = test.getLevel().registryAccess();
        var key = ResourceKey.create(Registries.VILLAGER_TRADE,
                Identifier.withDefaultNamespace("librarian/1/emerald_and_book_enchanted_book"));
        assertNativeLibrarianRoutes(test, key);
        var trade = access.lookupOrThrow(Registries.VILLAGER_TRADE).getOrThrow(key);
        var profession = access.lookupOrThrow(Registries.VILLAGER_PROFESSION)
                .getOrThrow(VillagerProfession.LIBRARIAN);
        Villager villager = new Villager(EntityTypes.VILLAGER, test.getLevel());
        villager.setVillagerData(villager.getVillagerData().withProfession(profession).withLevel(1));
        villager.setPos(ProjectileFixtures.position(test, new Vec3(4, 3, 4)));
        var params = new LootParams.Builder(test.getLevel())
                .withParameter(LootContextParams.ORIGIN, villager.position())
                .withParameter(LootContextParams.THIS_ENTITY, villager)
                .withParameter(LootContextParams.ADDITIONAL_COST_COMPONENT_ALLOWED, Unit.INSTANCE)
                .create(LootContextParamSets.VILLAGER_TRADE);
        var observed = new HashSet<ResourceKey<Enchantment>>();
        try {
            for (long seed = 1; seed <= 1024; seed++) {
                var context = new LootContext.Builder(params)
                        .withOptionalRandomSource(RandomSource.create(seed)).create(Optional.empty());
                var offer = trade.value().getOffer(context);
                test.assertTrue(offer != null, "The native librarian book offer must be valid");
                test.assertTrue(offer.getResult().is(Items.ENCHANTED_BOOK),
                        "The librarian must sell an enchanted book");
                test.assertTrue(offer.getCostA().is(Items.EMERALD) && offer.getCostB().is(Items.BOOK),
                        "Native librarian offers must require emeralds and a book");
                test.assertTrue(offer.getCostA().getCount() > 0 && offer.getCostA().getCount() <= 64,
                        "Native pricing must produce a valid emerald price");
                AcquisitionFixtures.collect(test, offer.getResult(), observed);
            }
            AcquisitionFixtures.assertObserved(test, observed, BowEnchantments.ALL, "Native librarian");
            test.succeed();
        } finally {
            villager.discard();
        }
    }

    private static void assertNativeLibrarianRoutes(GameTestHelper test, ResourceKey<VillagerTrade> standard)
            throws IOException {
        var tagId = Identifier.withDefaultNamespace("tags/villager_trade/librarian/level_1.json");
        var baseTag = test.getLevel().getServer().getResourceManager().getResourceStack(tagId).stream()
                .filter(resource -> resource.sourcePackId().equals("vanilla")).findFirst();
        test.assertTrue(baseTag.isPresent(), "The original vanilla novice trade tag must be available");
        var json = GsonHelper.parse(baseTag.orElseThrow().readAllAsString());
        test.assertTrue(json.getAsJsonArray("values").asList().stream()
                        .anyMatch(value -> value.getAsString().equals(standard.identifier().toString())),
                "The base vanilla route must include the tested standard librarian definition");

        var access = test.getLevel().registryAccess();
        var profession = access.lookupOrThrow(Registries.VILLAGER_PROFESSION)
                .getOrThrow(VillagerProfession.LIBRARIAN);
        var novice = access.lookupOrThrow(Registries.TRADE_SET)
                .getValueOrThrow(profession.value().getTrades(1));
        var actualBooks = novice.trades().stream().map(holder -> holder.unwrapKey().orElseThrow().identifier())
                .filter(id -> id.getPath().endsWith("_enchanted_book")).collect(Collectors.toSet());
        Set<Identifier> expectedBooks;
        // Native GameTestServer enables Trade Rebalance; keep its experimental route unchanged.
        if (test.getLevel().enabledFeatures().contains(FeatureFlags.TRADE_REBALANCE)) {
            expectedBooks = List.of("desert", "jungle", "plains", "savanna", "snow", "swamp", "taiga")
                    .stream().map(biome -> Identifier.withDefaultNamespace(
                            "librarian/1/emerald_and_book_" + biome + "_enchanted_book"))
                    .collect(Collectors.toSet());
        } else {
            expectedBooks = Set.of(standard.identifier());
        }
        test.assertValueEqual(actualBooks, expectedBooks,
                "The live librarian route must match its active native feature set");
    }

    private static void assertLootBooks(GameTestHelper test, ResourceKey<LootTable> key, boolean fishing) {
        var table = test.getLevel().getServer().reloadableRegistries().getLootTable(key);
        var builder = new LootParams.Builder(test.getLevel())
                .withParameter(LootContextParams.ORIGIN, ProjectileFixtures.position(test, new Vec3(4, 3, 4)));
        if (fishing) builder.withParameter(LootContextParams.TOOL, new ItemStack(Items.FISHING_ROD));
        var params = builder.create(table.getParamSet());
        var observed = new HashSet<ResourceKey<Enchantment>>();
        for (long seed = 1; seed <= 4096 && observed.size() < BowEnchantments.ALL.size(); seed++) {
            for (ItemStack stack : table.getRandomItems(params, seed)) {
                var found = new HashSet<ResourceKey<Enchantment>>();
                AcquisitionFixtures.collect(test, stack, found);
                if (stack.is(Items.ENCHANTED_BOOK)) observed.addAll(found);
            }
        }
        AcquisitionFixtures.assertObserved(test, observed, BowEnchantments.ALL, key.identifier().toString());
    }
}
