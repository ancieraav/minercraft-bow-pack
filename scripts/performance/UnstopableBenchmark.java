package dev.bowpack.gametest;

import com.sun.management.ThreadMXBean;
import dev.bowpack.projectile.ArrowAccess;
import dev.bowpack.projectile.ShotState;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;

/** Opt-in workload copied into isolated baseline/release builds by profile.ps1. */
public class UnstopableBenchmark {
    private static final int WARMUP_ROUNDS = 50;
    private static final int MEASURED_ROUNDS = 30;
    private static final int FLIGHT_TICKS = 20;

    @GameTest(structure = ProjectileFixtures.ARENA, maxTicks = 100)
    public void compareEightyArrowBlockDestruction(GameTestHelper test) throws Exception {
        Path output = Path.of("bowpack-benchmark").toAbsolutePath();
        Files.createDirectories(output);
        var rules = test.getLevel().getGameRules();
        boolean drops = rules.get(GameRules.BLOCK_DROPS);
        boolean griefing = rules.get(GameRules.MOB_GRIEFING);
        boolean projectiles = rules.get(GameRules.PROJECTILES_CAN_BREAK_BLOCKS);
        List<String> rows = new ArrayList<>();
        rows.add("follow,round,tick,nanoseconds,allocated_bytes");
        List<String> totals = new ArrayList<>();
        totals.add("follow,round,broken_blocks");
        try (Recording recording = new Recording(Configuration.getConfiguration("profile"))) {
            rules.set(GameRules.BLOCK_DROPS, false, test.getLevel().getServer());
            rules.set(GameRules.MOB_GRIEFING, true, test.getLevel().getServer());
            rules.set(GameRules.PROJECTILES_CAN_BREAK_BLOCKS, true, test.getLevel().getServer());
            recording.setDestination(output.resolve("unstopable.jfr"));
            recording.start();
            for (boolean follow : new boolean[] {false, true}) {
                for (int round = -WARMUP_ROUNDS; round < MEASURED_ROUNDS; round++) {
                    runRound(test, follow, round, rows, totals);
                }
            }
            recording.stop();
            Files.write(output.resolve("ticks.csv"), rows);
            Files.write(output.resolve("blocks.csv"), totals);
            System.out.println("BOWPACK_BENCHMARK_OUTPUT=" + output);
        } finally {
            rules.set(GameRules.BLOCK_DROPS, drops, test.getLevel().getServer());
            rules.set(GameRules.MOB_GRIEFING, griefing, test.getLevel().getServer());
            rules.set(GameRules.PROJECTILES_CAN_BREAK_BLOCKS, projectiles, test.getLevel().getServer());
        }
        test.succeed();
    }

    private static void runRound(GameTestHelper test, boolean follow, int round,
                                 List<String> rows, List<String> totals) {
        for (int x = 18; x < 34; x++) {
            for (int y = 3; y < 8; y++) {
                for (int z = 16; z < 32; z++) test.setBlock(x, y, z, Blocks.STONE);
            }
        }
        Mob owner = test.spawnWithNoFreeWill(EntityTypes.COW, new Vec3(10, 5, 24));
        Mob target = test.spawnWithNoFreeWill(EntityTypes.COW, new Vec3(50, 5, 24));
        owner.setNoGravity(true);
        target.setNoGravity(true);
        List<Arrow> arrows = new ArrayList<>(80);
        ShotState state = new ShotState(0, 0, true, false, follow, 2,
                owner.getUUID(), Optional.empty()).withUnstopable(true);
        for (int index = 0; index < 80; index++) {
            Vec3 position = test.absoluteVec(new Vec3(17.5, 3.5 + index / 16, 16.5 + index % 16));
            Arrow arrow = new Arrow(test.getLevel(), position.x, position.y, position.z,
                    new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
            arrow.setOwner(owner);
            arrow.setNoGravity(true);
            arrow.setDeltaMovement(new Vec3(2, 0, 0));
            ((ArrowAccess) arrow).bowpack$setState(state);
            test.getLevel().addFreshEntity(arrow);
            arrows.add(arrow);
        }
        ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        long thread = Thread.currentThread().threadId();
        try {
            for (int tick = 0; tick < FLIGHT_TICKS; tick++) {
                long bytes = bean.getThreadAllocatedBytes(thread);
                long start = System.nanoTime();
                for (Arrow arrow : arrows) if (arrow.isAlive()) arrow.tick();
                long elapsed = System.nanoTime() - start;
                long allocated = bean.getThreadAllocatedBytes(thread) - bytes;
                if (round >= 0) rows.add(follow + "," + round + "," + tick + "," + elapsed + "," + allocated);
            }
            int broken = 0;
            for (int x = 18; x < 34; x++) {
                for (int y = 3; y < 8; y++) {
                    for (int z = 16; z < 32; z++) {
                        if (test.getLevel().getBlockState(test.absolutePos(new BlockPos(x, y, z))).isAir()) broken++;
                    }
                }
            }
            test.assertTrue(broken > 0, "The benchmark must actually destroy wall blocks");
            if (round >= 0) totals.add(follow + "," + round + "," + broken);
        } finally {
            arrows.forEach(Arrow::discard);
            owner.discard();
            target.discard();
        }
    }
}
