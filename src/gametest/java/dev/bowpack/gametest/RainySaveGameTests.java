package dev.bowpack.gametest;

import dev.bowpack.projectile.ArrowAccess;
import dev.bowpack.projectile.ArrowLifetime;
import dev.bowpack.projectile.RainyAccess;
import dev.bowpack.projectile.RainyShot;
import dev.bowpack.projectile.ShotState;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

public class RainySaveGameTests {
    @GameTest(structure = ProjectileFixtures.ARENA, maxTicks = 55)
    public void savedHoverKeepsTargetAndOriginalReleaseTime(GameTestHelper test) {
        ServerPlayer shooter = test.makeMockServerPlayerInLevel();
        shooter.setGameMode(GameType.SURVIVAL);
        shooter.setPos(ProjectileFixtures.position(test, new Vec3(4, 3, 4)));
        Mob target = ProjectileFixtures.cow(test, new Vec3(4, 3, 10));
        ItemStack ammo = new ItemStack(Items.ARROW);
        Arrow original = (Arrow) ((ArrowItem) ammo.getItem()).createArrow(test.getLevel(), ammo,
                shooter, new ItemStack(Items.BOW));
        original.setPos(target.getX(), target.getBoundingBox().maxY + 4, target.getZ());
        Vec3 hoverPosition = original.position();
        ShotState state = new ShotState(0, 0, true, false, 0, false,
                RainyShot.FALL_SPEED, shooter.getUUID(), Optional.empty())
                .asRainyProjectile().withDeadline(ArrowLifetime.deadline(test.getLevel()));
        ((ArrowAccess) original).bowpack$setState(state);
        ((RainyAccess) original).bowpack$startRainyHover(target.getUUID(),
                ArrowLifetime.time(test.getLevel()) + RainyShot.HOVER_TICKS);
        test.getLevel().addFreshEntity(original);

        ProblemReporter.Collector problems = new ProblemReporter.Collector();
        var output = TagValueOutput.createWithContext(problems, test.getLevel().registryAccess());
        original.saveWithoutId(output);
        CompoundTag saved = output.buildResult();
        test.assertTrue(problems.isEmpty(), "Rainy hover save errors: " + problems.getReport());
        original.discard();
        Arrow restored = new Arrow(EntityTypes.ARROW, test.getLevel());
        test.runAfterDelay(20, () -> {
            restored.load(TagValueInput.create(problems, test.getLevel().registryAccess(), saved));
            test.assertTrue(problems.isEmpty(), "Rainy hover load errors: " + problems.getReport());
            test.getLevel().addFreshEntity(restored);
            test.assertTrue(((RainyAccess) restored).bowpack$isRainyHovering()
                    && ProjectileFixtures.state(restored).rainyProjectile() && restored.isNoGravity(),
                    "Restored Rainy arrow remains stationary and independently managed");
        });
        test.runAfterDelay(25, () -> target.setPos(target.position().add(2, 0, 0)));
        test.runAfterDelay(30, () -> {
            Vec3 aim = target.getBoundingBox().getCenter().subtract(restored.position());
            test.assertTrue(((RainyAccess) restored).bowpack$isRainyHovering()
                    && Math.abs(restored.getYRot() - Math.toDegrees(Math.atan2(aim.x, aim.z))) < 0.5,
                    "Restored hover keeps tracking its saved target");
        });
        test.runAfterDelay(39, () -> {
            test.assertTrue(((RainyAccess) restored).bowpack$isRainyHovering(),
                    "Reloading cannot release the arrow before its original deadline");
            test.assertValueEqual(restored.position(), hoverPosition, "The arrow stays in place while hovering");
        });
        test.runAfterDelay(42, () -> {
            test.assertFalse(((RainyAccess) restored).bowpack$isRainyHovering(),
                    "The arrow releases at its original deadline after reload");
            test.assertTrue(restored.position().distanceToSqr(hoverPosition) > 1,
                    "The restored arrow launches toward the target");
            test.succeed();
        });
    }
}
