package dev.bowpack;

import java.util.List;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

public final class BowEnchantments {
    public static final ResourceKey<Enchantment> MULTI_SHOT = key("multi_shot");
    public static final ResourceKey<Enchantment> FRONT_SHOT = key("front_shot");
    public static final ResourceKey<Enchantment> BACK_SHOT = key("back_shot");
    public static final ResourceKey<Enchantment> DIAGONAL_SHOT = key("diagonal_shot");
    public static final ResourceKey<Enchantment> RICOCHET_SHOT = key("ricochet_shot");
    public static final ResourceKey<Enchantment> BOUNCY_SHOT = key("bouncy_shot");
    public static final ResourceKey<Enchantment> ANTI_FRICTION_SHOT = key("anti_friction_shot");
    public static final ResourceKey<Enchantment> BREAKTHROUGH_SHOT = key("breakthrough_shot");
    public static final ResourceKey<Enchantment> RAINY_SHOT = key("rainy_shot");
    public static final ResourceKey<Enchantment> FOLLOW_SHOT = key("follow_shot");
    public static final List<ResourceKey<Enchantment>> ALL = List.of(
            MULTI_SHOT, FRONT_SHOT, BACK_SHOT, DIAGONAL_SHOT, RICOCHET_SHOT, BOUNCY_SHOT,
            ANTI_FRICTION_SHOT, BREAKTHROUGH_SHOT, RAINY_SHOT, FOLLOW_SHOT);

    private BowEnchantments() {
    }

    public static ShotLevels read(ItemStack weapon, RegistryAccess registryAccess) {
        if (!weapon.is(Items.BOW) && !weapon.is(Items.CROSSBOW)) {
            return ShotLevels.NONE;
        }
        var enchantments = registryAccess.lookupOrThrow(Registries.ENCHANTMENT);
        return new ShotLevels(
                level(weapon, enchantments, MULTI_SHOT, 4),
                level(weapon, enchantments, FRONT_SHOT, 4),
                level(weapon, enchantments, BACK_SHOT, 5),
                level(weapon, enchantments, DIAGONAL_SHOT, 3),
                level(weapon, enchantments, RICOCHET_SHOT, 4),
                level(weapon, enchantments, BOUNCY_SHOT, 3),
                level(weapon, enchantments, ANTI_FRICTION_SHOT, 1) > 0,
                level(weapon, enchantments, BREAKTHROUGH_SHOT, 1) > 0,
                level(weapon, enchantments, RAINY_SHOT, 3),
                level(weapon, enchantments, FOLLOW_SHOT, 1) > 0);
    }

    private static int level(ItemStack weapon, HolderLookup.RegistryLookup<Enchantment> registry,
                             ResourceKey<Enchantment> key, int maximum) {
        return Math.min(maximum, EnchantmentHelper.getItemEnchantmentLevel(registry.getOrThrow(key), weapon));
    }

    private static ResourceKey<Enchantment> key(String path) {
        return ResourceKey.create(Registries.ENCHANTMENT, BowPack.id(path));
    }
}
