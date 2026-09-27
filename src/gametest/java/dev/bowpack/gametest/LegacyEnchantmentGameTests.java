package dev.bowpack.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

public class LegacyEnchantmentGameTests {
    @GameTest(structure = ProjectileFixtures.ARENA)
    public void oldRainyEnchantmentIsDroppedWithoutLosingBow(GameTestHelper test) {
        var registries = test.getLevel().registryAccess();
        var mending = registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.MENDING);
        ItemStack bow = new ItemStack(Items.BOW);
        bow.enchant(mending, 1);

        ProblemReporter.Collector saveProblems = new ProblemReporter.Collector();
        var output = TagValueOutput.createWithContext(saveProblems, registries);
        output.store("item", ItemStack.CODEC, bow);
        test.assertTrue(saveProblems.isEmpty(), "Native bow save must succeed: " + saveProblems.getReport());
        CompoundTag saved = output.buildResult();
        saved.getCompound("item").orElseThrow()
                .getCompound("components").orElseThrow()
                .getCompound("minecraft:enchantments").orElseThrow()
                .putInt("bowpack:rainy_shot", 3);

        ProblemReporter.Collector loadProblems = new ProblemReporter.Collector();
        var loaded = TagValueInput.create(loadProblems, registries, saved).read("item", ItemStack.CODEC);
        test.assertTrue(loaded.isPresent(), "The old Rainy entry must not discard the bow");
        ItemStack restored = loaded.orElseThrow();
        test.assertTrue(restored.is(Items.BOW), "The item must remain a bow");
        test.assertValueEqual(EnchantmentHelper.getItemEnchantmentLevel(mending, restored), 1,
                "Valid enchantments must survive loading");
        test.assertValueEqual(EnchantmentHelper.getEnchantmentsForCrafting(restored).size(), 1,
                "The removed Rainy enchantment must be dropped");
        test.assertFalse(loadProblems.isEmpty(), "The unknown enchantment must be reported");
        test.succeed();
    }
}
