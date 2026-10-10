package dev.bowpack.gametest;

import dev.bowpack.projectile.ArrowAccess;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;

public class UnstopableImpactGameTests {
    private static final BlockPos TARGET = new BlockPos(32, 4, 32);

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void combinedShotBreaksFirstAndBouncesOnlyWhenTheBlockSurvives(GameTestHelper test) {
        Mob owner = ProjectileFixtures.cow(test, new Vec3(1, 2, 9));
        for (Block block : new Block[] {Blocks.DIRT, Blocks.STONE, Blocks.OBSIDIAN, Blocks.BEDROCK}) {
            test.setBlock(TARGET, block);
            Arrow arrow = UnstopableGameTests.arrow(test, owner, 10);
            test.assertTrue(((ArrowAccess) arrow).bowpack$unstopableEnabled(),
                    "Unstopable is present in the synchronized projectile flags");
            arrow.tick();
            boolean breakable = block == Blocks.DIRT || block == Blocks.STONE;
            test.assertValueEqual(test.getLevel().getBlockState(test.absolutePos(TARGET)).isAir(),
                    breakable, "Unstopable must take priority on a breakable block");
            test.assertTrue(breakable ? arrow.getDeltaMovement().x > 0 : arrow.getDeltaMovement().x < 0,
                    "Only a surviving block reflects the arrow");
            test.assertValueEqual(((ArrowAccess) arrow).bowpack$remainingBounces(), breakable ? 10 : 9,
                    "Destroying a block does not consume the bounce budget");
            test.assertTrue(((ArrowAccess) arrow).bowpack$unstopableEnabled(),
                    "Spending a bounce preserves the synchronized Unstopable flag");
            arrow.discard();
        }
        test.succeed();
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void deniedDestructionUsesBouncyAndPreservesWaterloggedBlocks(GameTestHelper test) {
        Mob owner = ProjectileFixtures.cow(test, new Vec3(1, 2, 9));
        var rules = test.getLevel().getGameRules();
        boolean projectilesBefore = rules.get(GameRules.PROJECTILES_CAN_BREAK_BLOCKS);
        boolean griefingBefore = rules.get(GameRules.MOB_GRIEFING);
        try {
            rules.set(GameRules.PROJECTILES_CAN_BREAK_BLOCKS, false, test.getLevel().getServer());
            rules.set(GameRules.MOB_GRIEFING, true, test.getLevel().getServer());
            assertProtectedBounce(test, owner, Blocks.DIRT);
            rules.set(GameRules.PROJECTILES_CAN_BREAK_BLOCKS, true, test.getLevel().getServer());
            rules.set(GameRules.MOB_GRIEFING, false, test.getLevel().getServer());
            assertProtectedBounce(test, owner, Blocks.DIRT);
            rules.set(GameRules.MOB_GRIEFING, true, test.getLevel().getServer());
            test.setBlock(TARGET, Blocks.OAK_SLAB.defaultBlockState()
                    .setValue(BlockStateProperties.WATERLOGGED, true));
            Arrow arrow = UnstopableGameTests.arrow(test, owner, 10);
            arrow.setPos(arrow.getX(), test.absolutePos(TARGET).getY() + 0.25, arrow.getZ());
            arrow.tick();
            test.assertBlockPresent(Blocks.OAK_SLAB, TARGET);
            test.assertTrue(test.getLevel().getBlockState(test.absolutePos(TARGET))
                    .getValue(BlockStateProperties.WATERLOGGED), "The protected fluid remains intact");
            test.assertTrue(arrow.getDeltaMovement().x < 0, "A waterlogged block uses Bouncy");
            arrow.discard();
        } finally {
            rules.set(GameRules.PROJECTILES_CAN_BREAK_BLOCKS, projectilesBefore, test.getLevel().getServer());
            rules.set(GameRules.MOB_GRIEFING, griefingBefore, test.getLevel().getServer());
        }
        test.succeed();
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void exhaustedBouncesStillBreakSoftBlocksButStopAtResistantBlocks(GameTestHelper test) {
        Mob owner = ProjectileFixtures.cow(test, new Vec3(1, 2, 9));
        test.setBlock(TARGET, Blocks.OBSIDIAN);
        Arrow arrow = UnstopableGameTests.arrow(test, owner, 1);
        arrow.tick();
        test.assertValueEqual(((ArrowAccess) arrow).bowpack$remainingBounces(), 0,
                "The final bounce is consumed once");
        test.setBlock(TARGET, Blocks.DIRT);
        resetFlight(test, arrow);
        arrow.tick();
        test.assertTrue(test.getLevel().getBlockState(test.absolutePos(TARGET)).isAir(),
                "Unstopable works after all Bouncy charges are spent");
        test.setBlock(TARGET, Blocks.BEDROCK);
        resetFlight(test, arrow);
        arrow.tick();
        test.assertBlockPresent(Blocks.BEDROCK, TARGET);
        test.assertTrue(arrow.getDeltaMovement().lengthSqr() < 1.0e-8,
                "A resistant block stops an arrow with no bounce budget");
        test.succeed();
    }

    private static void assertProtectedBounce(GameTestHelper test, Mob owner, Block block) {
        test.setBlock(TARGET, block);
        Arrow arrow = UnstopableGameTests.arrow(test, owner, 10);
        arrow.tick();
        test.assertBlockPresent(block, TARGET);
        test.assertTrue(arrow.getDeltaMovement().x < 0, "Protection denial uses Bouncy");
        test.assertValueEqual(((ArrowAccess) arrow).bowpack$remainingBounces(), 9,
                "A protected impact consumes one bounce");
        arrow.discard();
    }

    private static void resetFlight(GameTestHelper test, Arrow arrow) {
        arrow.setPos(ProjectileFixtures.position(test, new Vec3(2.5, 4.5, 4.5)));
        arrow.setDeltaMovement(new Vec3(2, 0, 0));
    }
}
