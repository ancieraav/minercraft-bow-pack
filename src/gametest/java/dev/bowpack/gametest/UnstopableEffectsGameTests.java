package dev.bowpack.gametest;

import dev.bowpack.projectile.ArrowUnstopable;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class UnstopableEffectsGameTests {
    private static final BlockPos TARGET = new BlockPos(32, 4, 32);

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void onlyUnstopableBreaksAreQuietAndNativeNeighborUpdatesRemain(GameTestHelper test) {
        Mob owner = ProjectileFixtures.cow(test, new Vec3(1, 2, 9));
        BlockPos position = test.absolutePos(TARGET);
        try (BlockEffectRecorder effects = new BlockEffectRecorder(test)) {
            test.setBlock(TARGET, Blocks.STONE);
            test.assertTrue(test.getLevel().destroyBlock(position, false, owner),
                    "An ordinary block break succeeds");
            test.assertValueEqual(effects.breaksAt(position), 1L,
                    "Ordinary breaks emit the native sound and particle event");
            effects.clear();

            test.setBlock(TARGET, Blocks.STONE);
            test.setBlock(TARGET.above(), Blocks.TORCH);
            Arrow arrow = UnstopableGameTests.arrow(test, owner);
            arrow.tick();
            test.assertTrue(test.getLevel().getBlockState(position).isAir(),
                    "Unstopable still destroys its struck block");
            test.assertTrue(test.getLevel().getBlockState(position.above()).isAir(),
                    "Native neighbor updates remove the unsupported torch");
            test.assertValueEqual(effects.breaksAt(position), 0L,
                    "Unstopable does not emit the native sound and particle event");
            test.assertValueEqual(effects.breaksAt(position.above()), 1L,
                    "A nested neighbor break retains its own normal effects");
            arrow.discard();

            test.setBlock(TARGET, Blocks.DIRT);
            test.getLevel().destroyBlock(position, false, owner);
            test.assertValueEqual(effects.breaksAt(position), 1L,
                    "The quiet scope cannot leak into a later ordinary break");
        }
        test.succeed();
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void unstopableHonorsDropChangesWithoutChangingDestruction(GameTestHelper test) {
        Mob owner = ProjectileFixtures.cow(test, new Vec3(1, 2, 9));
        var rules = test.getLevel().getGameRules();
        boolean dropsBefore = rules.get(GameRules.BLOCK_DROPS);
        try {
            rules.set(GameRules.BLOCK_DROPS, false, test.getLevel().getServer());
            test.setBlock(TARGET, Blocks.DIRT);
            Arrow noDrops = UnstopableGameTests.arrow(test, owner);
            noDrops.tick();
            noDrops.discard();
            test.assertTrue(test.getLevel().getBlockState(test.absolutePos(TARGET)).isAir(),
                    "Turning drops off does not limit block destruction");
            test.assertTrue(test.getEntities(EntityTypes.ITEM).isEmpty(),
                    "No item entity is created when block drops are disabled");

            rules.set(GameRules.BLOCK_DROPS, true, test.getLevel().getServer());
            test.setBlock(TARGET, Blocks.DIRT);
            Arrow withDrops = UnstopableGameTests.arrow(test, owner);
            withDrops.tick();
            withDrops.discard();
            test.assertTrue(test.getLevel().getBlockState(test.absolutePos(TARGET)).isAir(),
                    "Turning drops on preserves the same destruction");
            test.assertTrue(test.getEntities(EntityTypes.ITEM).stream()
                            .anyMatch(entity -> entity.getItem().is(Items.DIRT)),
                    "Ordinary block drops remain available when enabled");
        } finally {
            rules.set(GameRules.BLOCK_DROPS, dropsBefore, test.getLevel().getServer());
        }
        test.succeed();
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void rejectedReplacementDoesNotMuteTheFollowingNativeBreak(GameTestHelper test) {
        Mob owner = ProjectileFixtures.cow(test, new Vec3(1, 2, 9));
        Arrow arrow = UnstopableGameTests.arrow(test, owner);
        BlockPos position = test.absolutePos(TARGET);
        test.setBlock(TARGET, Blocks.DIRT);
        var original = test.getLevel().getBlockState(position);
        test.setBlock(TARGET, Blocks.STONE);
        try (BlockEffectRecorder effects = new BlockEffectRecorder(test)) {
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(position), Direction.WEST, position, false);
            test.assertFalse(ArrowUnstopable.tryBreak(arrow, hit, original),
                    "A replacement block rejects the stale Unstopable request");
            test.getLevel().destroyBlock(position, false, owner);
            test.assertValueEqual(effects.breaksAt(position), 1L,
                    "A rejected request cannot silence unrelated effects");
        }
        test.succeed();
    }
}
