package dev.bowpack.gametest;

import dev.bowpack.BowEnchantments;
import dev.bowpack.projectile.ArrowAccess;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.phys.Vec3;

public class MobWeaponGameTests {
    @GameTest(structure = ProjectileFixtures.ARENA)
    public void skeletonExpandsItsNativeTargetedBowShot(GameTestHelper test) {
        verifyVolley(test, test.spawnWithNoFreeWill(EntityTypes.SKELETON, new Vec3(30, 3, 30)), Items.BOW);
        test.succeed();
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void strayCopiesRetainNativeSlowness(GameTestHelper test) {
        verifyVolley(test, test.spawnWithNoFreeWill(EntityTypes.STRAY, new Vec3(30, 3, 30)), Items.BOW);
        assertPayload(test, MobEffects.SLOWNESS);
        test.succeed();
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void boggedCopiesRetainNativePoison(GameTestHelper test) {
        verifyVolley(test, test.spawnWithNoFreeWill(EntityTypes.BOGGED, new Vec3(30, 3, 30)), Items.BOW);
        assertPayload(test, MobEffects.POISON);
        test.succeed();
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void pillagerUsesItsCrossbowTargetInsteadOfItsLookDirection(GameTestHelper test) {
        verifyVolley(test, test.spawnWithNoFreeWill(EntityTypes.PILLAGER, new Vec3(30, 3, 30)), Items.CROSSBOW);
        test.succeed();
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void illusionerExpandsItsNativeBowShotOnce(GameTestHelper test) {
        verifyVolley(test, test.spawnWithNoFreeWill(EntityTypes.ILLUSIONER, new Vec3(30, 3, 30)), Items.BOW);
        test.succeed();
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void unenchantedMobWeaponsKeepTheirSingleUnmanagedNativeShot(GameTestHelper test) {
        var shooters = List.of(
                test.spawnWithNoFreeWill(EntityTypes.SKELETON, new Vec3(30, 3, 30)),
                test.spawnWithNoFreeWill(EntityTypes.STRAY, new Vec3(30, 3, 30)),
                test.spawnWithNoFreeWill(EntityTypes.BOGGED, new Vec3(30, 3, 30)),
                test.spawnWithNoFreeWill(EntityTypes.PILLAGER, new Vec3(30, 3, 30)),
                test.spawnWithNoFreeWill(EntityTypes.ILLUSIONER, new Vec3(30, 3, 30)));
        var target = test.spawnWithNoFreeWill(EntityTypes.COW, new Vec3(46, 6, 30));
        for (Mob shooter : shooters) {
            Item item = shooter.getType() == EntityTypes.PILLAGER ? Items.CROSSBOW : Items.BOW;
            equip(shooter, new ItemStack(item));
            shooter.setTarget(target);
            ((RangedAttackMob) shooter).performRangedAttack(target, 1);
        }
        var arrows = test.getEntities(EntityTypes.ARROW);
        test.assertValueEqual(arrows.size(), shooters.size(), "Each unenchanted mob fires exactly one arrow");
        for (Arrow arrow : arrows) {
            test.assertTrue(((ArrowAccess) arrow).bowpack$getState() == null,
                    "Unenchanted native mob arrows remain unmanaged");
            test.assertFalse(arrow.isNoGravity(), "Unenchanted arrows retain gravity");
        }
        test.succeed();
    }

    private static void verifyVolley(GameTestHelper test, Mob shooter, Item item) {
        ItemStack weapon = new ItemStack(item);
        var enchantments = test.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        for (var key : List.of(BowEnchantments.MULTI_SHOT, BowEnchantments.FRONT_SHOT,
                BowEnchantments.BACK_SHOT, BowEnchantments.DIAGONAL_SHOT, BowEnchantments.BOUNCY_SHOT,
                BowEnchantments.ACCELERATE)) {
            var enchantment = enchantments.getOrThrow(key);
            weapon.enchant(enchantment, enchantment.value().getMaxLevel());
        }
        equip(shooter, weapon);
        var target = test.spawnWithNoFreeWill(EntityTypes.COW, new Vec3(46, 6, 30));
        shooter.setTarget(target);
        ((RangedAttackMob) shooter).performRangedAttack(target, 1);
        var arrows = test.getEntities(EntityTypes.ARROW);
        test.assertValueEqual(arrows.size(), 80, "The native attack expands to 80 arrows with no duplicate original");
        Arrow original = arrows.stream().min(Comparator.comparingInt(Arrow::getId)).orElseThrow();
        Vec3 launch = original.getDeltaMovement();
        double dx = target.getX() - original.getX();
        double dz = target.getZ() - original.getZ();
        Vec3 nativeAim = new Vec3(dx, target.getY(1.0 / 3) - original.getY()
                + Math.hypot(dx, dz) * 0.2, dz).normalize();
        test.assertTrue(launch.normalize().dot(nativeAim) > 0.85,
                "The original follows native target aim, including ballistic elevation and inaccuracy");
        test.assertTrue(launch.x > 0.8 && Math.abs(launch.z) < 0.5,
                "The mob shoots toward its target on +X while its look direction is +Z");
        for (Arrow arrow : arrows) {
            var state = ProjectileFixtures.state(arrow);
            test.assertValueEqual(state.shooter(), shooter.getUUID(), "Every copy retains its actual mob owner");
            test.assertValueEqual(state.bounces(), 30, "Every copy inherits the native weapon's Bouncy level");
            test.assertValueEqual(state.accelerateLevel(), 3, "Every mob arrow inherits Accelerate III");
            ProjectileFixtures.near(test, launch.length(), arrow.getDeltaMovement().length(),
                    "All directions preserve the native shot's launch speed");
            ProjectileFixtures.near(test, launch.length(), state.launchSpeed(), "Ricochet state preserves native speed");
            test.assertFalse(arrow.isNoGravity(), "Normal enchantments do not remove gravity");
            arrow.tick();
            ProjectileFixtures.near(test, launch.length() + 0.2, arrow.getDeltaMovement().length(),
                    "Every mob arrow accelerates from its native launch speed after one tick");
        }
    }

    private static void equip(Mob shooter, ItemStack weapon) {
        shooter.setYRot(0);
        shooter.setXRot(0);
        shooter.setItemInHand(InteractionHand.MAIN_HAND, weapon);
        shooter.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.ARROW));
        if (weapon.is(Items.CROSSBOW)) {
            weapon.set(DataComponents.CHARGED_PROJECTILES,
                    ChargedProjectiles.ofNonEmpty(List.of(new ItemStack(Items.ARROW))));
        }
    }

    private static void assertPayload(GameTestHelper test, Holder<MobEffect> expected) {
        for (Arrow arrow : test.getEntities(EntityTypes.ARROW)) {
            var potion = arrow.getPickupItemStackOrigin().getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
            test.assertTrue(potion.customEffects().stream().anyMatch(effect -> effect.getEffect().equals(expected)),
                    "Every copy preserves the skeleton variant's native potion effect");
        }
    }
}
