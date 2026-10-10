package dev.bowpack.gametest;

import dev.bowpack.BowEnchantments;
import dev.bowpack.projectile.ArrowAccess;
import dev.bowpack.projectile.ShotState;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

final class ProjectileFixtures {
    static final String ARENA = "bowpack-gametest:arena";

    private ProjectileFixtures() {}

    static ItemStack maxWeapon(Item item, HolderLookup.Provider registries) {
        ItemStack weapon = new ItemStack(item);
        var enchantments = registries.lookupOrThrow(Registries.ENCHANTMENT);
        for (var key : BowEnchantments.ALL) {
            var enchantment = enchantments.getOrThrow(key);
            weapon.enchant(enchantment, enchantment.value().getMaxLevel());
        }
        return weapon;
    }

    // Center each fixture so projectile collisions stay inside the arena.
    static Vec3 position(GameTestHelper test, Vec3 local) {
        return test.absoluteVec(local.add(28, 0, 28));
    }

    static Arrow arrow(GameTestHelper test, Vec3 position, Vec3 velocity,
                       boolean ricochet, int bounces, boolean antiFriction) {
        return arrow(test, position, velocity, ricochet ? 4 : 0, bounces, antiFriction);
    }

    static Arrow arrow(GameTestHelper test, Vec3 position, Vec3 velocity,
                       int ricochetLevel, int bounces, boolean antiFriction) {
        Vec3 absolute = position(test, position);
        Arrow arrow = new Arrow(test.getLevel(), absolute.x, absolute.y, absolute.z,
                new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
        ((ArrowAccess) arrow).bowpack$setState(new ShotState(
                ricochetLevel, bounces, antiFriction, false, false,
                velocity.length(), UUID.randomUUID(), Optional.empty()));
        arrow.setNoGravity(antiFriction);
        arrow.setDeltaMovement(velocity);
        test.getLevel().addFreshEntity(arrow);
        return arrow;
    }

    static ShotState state(AbstractArrow arrow) {
        ShotState state = ((ArrowAccess) arrow).bowpack$getState();
        if (state == null) throw new IllegalStateException("Managed arrow lost its ShotState");
        return state;
    }

    static Mob cow(GameTestHelper test, Vec3 position) {
        Mob cow = test.spawnWithNoFreeWill(EntityTypes.COW, position.add(28, 0, 28));
        cow.setNoGravity(true);
        return cow;
    }

    static void near(GameTestHelper test, double expected, double actual, String message) {
        test.assertTrue(Math.abs(expected - actual) < 1e-6,
                message + ": expected " + expected + ", got " + actual);
    }
}
