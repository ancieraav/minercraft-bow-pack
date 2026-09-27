package dev.bowpack.gametest;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.bowpack.BowEnchantments;
import dev.bowpack.projectile.ArrowAccess;
import dev.bowpack.projectile.ArrowUnstopable;
import dev.bowpack.projectile.ShotState;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.enchantment.Enchantment;

public class UnstopableGameTests {
    private static final BlockPos FIRST = new BlockPos(32, 4, 32);
    private static final BlockPos SECOND = new BlockPos(34, 4, 32);

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void bowAndCrossbowShotsCarryUnstopable(GameTestHelper test) {
        var enchantment = test.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(BowEnchantments.UNSTOPABLE);
        for (Item item : List.of(Items.BOW, Items.CROSSBOW)) {
            ItemStack weapon = new ItemStack(item);
            weapon.enchant(enchantment, 1);
            Player shooter = WeaponGameTests.shooter(test, weapon);
            if (item instanceof BowItem bow) {
                bow.releaseUsing(weapon, test.getLevel(), shooter, weapon.getUseDuration(shooter) - 20);
            } else {
                WeaponGameTests.fireCrossbow(test, shooter, weapon);
            }
        }
        var arrows = test.getEntities(EntityTypes.ARROW);
        test.assertValueEqual(arrows.size(), 2, "Both weapons fire a real arrow");
        test.assertTrue(arrows.stream().allMatch(arrow -> ProjectileFixtures.state(arrow).unstopable()),
                "Both Bow and Crossbow shots inherit Unstopable");
        test.succeed();
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void bouncyAndUnstopableCannotCombineInEitherOrder(GameTestHelper test)
            throws CommandSyntaxException {
        ServerPlayer player = BookAccessGameTests.player(test);
        player.giveExperienceLevels(100);
        try {
            for (Item item : List.of(Items.BOW, Items.CROSSBOW)) {
                for (ResourceKey<Enchantment> first :
                        List.of(BowEnchantments.BOUNCY_SHOT, BowEnchantments.UNSTOPABLE)) {
                    ResourceKey<Enchantment> second = first == BowEnchantments.BOUNCY_SHOT
                            ? BowEnchantments.UNSTOPABLE : BowEnchantments.BOUNCY_SHOT;
                    ItemStack weapon = BookAccessGameTests.combine(test, player, new ItemStack(item),
                            BookAccessGameTests.giveBook(test, player, first, 1));
                    ItemStack book = BookAccessGameTests.giveBook(test, player, second, 1);
                    AnvilMenu menu = new AnvilMenu(1, player.getInventory());
                    menu.getSlot(AnvilMenu.INPUT_SLOT).set(weapon);
                    menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(book);
                    menu.createResult();
                    test.assertTrue(menu.getSlot(AnvilMenu.RESULT_SLOT).getItem().isEmpty(),
                            "The anvil must reject Bouncy and Unstopable in either order");
                }
            }
            test.succeed();
        } finally {
            player.discard();
        }
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void arrowBreaksOnlyHitBlocksAndKeepsMoving(GameTestHelper test) {
        test.setBlock(FIRST, Blocks.DIRT);
        test.setBlock(SECOND, Blocks.DIRT);
        test.setBlock(32, 4, 33, Blocks.DIRT);
        Mob owner = ProjectileFixtures.cow(test, new Vec3(1, 2, 9));
        Mob nearby = ProjectileFixtures.cow(test, new Vec3(4, 4, 6));
        float health = nearby.getHealth();
        Arrow arrow = arrow(test, owner);

        arrow.tick();
        test.assertTrue(test.getLevel().getBlockState(test.absolutePos(FIRST)).isAir(),
                "The first impacted dirt block is removed");
        test.assertTrue(arrow.isAlive() && arrow.getDeltaMovement().x > 0,
                "The arrow keeps moving forward after a successful break");
        for (int tick = 0; tick < 4
                && !test.getLevel().getBlockState(test.absolutePos(SECOND)).isAir(); tick++) {
            arrow.tick();
            test.assertTrue(arrow.getDeltaMovement().x > 0,
                    "Arrow stopped after collision tick " + tick + "; x="
                            + (arrow.getX() - test.absolutePos(FIRST).getX())
                            + ", owner=" + arrow.getOwner()
                            + ", state=" + ProjectileFixtures.state(arrow));
        }
        test.assertTrue(test.getLevel().getBlockState(test.absolutePos(SECOND)).isAir(),
                "The same arrow can break a later block on another tick; arrow=" + arrow.position()
                        + ", velocity=" + arrow.getDeltaMovement()
                        + ", alive=" + arrow.isAlive());
        test.assertBlockPresent(Blocks.DIRT, 32, 4, 33);
        test.assertValueEqual(nearby.getHealth(), health,
                "Breaking a block does not damage nearby mobs");
        test.assertTrue(test.getEntities(EntityTypes.ITEM).stream()
                        .anyMatch(drop -> drop.getItem().is(Items.DIRT)),
                "A broken dirt block uses ordinary block drops");
        test.succeed();
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void obsidianAndBedrockStopTheArrow(GameTestHelper test) {
        Mob owner = ProjectileFixtures.cow(test, new Vec3(1, 2, 9));
        for (Block resistant : new Block[] {Blocks.OBSIDIAN, Blocks.BEDROCK}) {
            test.setBlock(FIRST, resistant);
            Arrow arrow = arrow(test, owner);
            arrow.tick();
            test.assertBlockPresent(resistant, FIRST);
            test.assertTrue(arrow.getDeltaMovement().lengthSqr() < 1.0e-8,
                    "A resistant block stops Unstopable like an ordinary impact");
            arrow.discard();
        }
        test.succeed();
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void burningArrowBreaksTntWithoutPrimingIt(GameTestHelper test) {
        test.setBlock(FIRST, Blocks.TNT);
        Mob owner = ProjectileFixtures.cow(test, new Vec3(1, 2, 9));
        Arrow arrow = arrow(test, owner);
        arrow.setRemainingFireTicks(100);
        arrow.tick();
        test.assertTrue(test.getLevel().getBlockState(test.absolutePos(FIRST)).isAir(),
                "The TNT block is removed without an explosion");
        test.assertTrue(test.getEntities(EntityTypes.TNT).isEmpty(),
                "Unstopable must not prime TNT even when the arrow burns");
        test.succeed();
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void gameRulesAndReplacementBlocksPreventDestruction(GameTestHelper test) {
        test.setBlock(FIRST, Blocks.DIRT);
        Mob owner = ProjectileFixtures.cow(test, new Vec3(1, 2, 9));
        Arrow arrow = arrow(test, owner);
        BlockPos absolute = test.absolutePos(FIRST);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolute),
                Direction.WEST, absolute, false);
        var rules = test.getLevel().getGameRules();
        boolean projectilesBefore = rules.get(GameRules.PROJECTILES_CAN_BREAK_BLOCKS);
        boolean griefingBefore = rules.get(GameRules.MOB_GRIEFING);
        try {
            rules.set(GameRules.PROJECTILES_CAN_BREAK_BLOCKS, false, test.getLevel().getServer());
            test.assertFalse(ArrowUnstopable.canBreak(arrow, hit),
                    "The projectile block-breaking rule protects the block");
            rules.set(GameRules.PROJECTILES_CAN_BREAK_BLOCKS, true, test.getLevel().getServer());
            rules.set(GameRules.MOB_GRIEFING, false, test.getLevel().getServer());
            test.assertFalse(ArrowUnstopable.canBreak(arrow, hit),
                    "A mob-owned arrow respects mobGriefing");
            rules.set(GameRules.MOB_GRIEFING, true, test.getLevel().getServer());
            test.assertTrue(ArrowUnstopable.canBreak(arrow, hit),
                    "The dirt block is eligible when both rules allow it");
            var original = test.getLevel().getBlockState(absolute);
            test.setBlock(FIRST, Blocks.STONE);
            test.assertFalse(ArrowUnstopable.tryBreak(arrow, hit, original),
                    "A projectile callback cannot make Unstopable break a replacement block");
            test.assertBlockPresent(Blocks.STONE, FIRST);
        } finally {
            rules.set(GameRules.PROJECTILES_CAN_BREAK_BLOCKS,
                    projectilesBefore, test.getLevel().getServer());
            rules.set(GameRules.MOB_GRIEFING, griefingBefore, test.getLevel().getServer());
        }
        test.succeed();
    }

    private static Arrow arrow(GameTestHelper test, Entity owner) {
        Vec3 velocity = new Vec3(2, 0, 0);
        Arrow arrow = ProjectileFixtures.arrow(test, new Vec3(2.5, 4.5, 4.5),
                velocity, false, 0, true);
        arrow.setOwner(owner);
        ((ArrowAccess) arrow).bowpack$setState(new ShotState(0, 0, true,
                false, false, velocity.length(), owner.getUUID(), Optional.empty())
                .withUnstopable(true));
        return arrow;
    }
}
