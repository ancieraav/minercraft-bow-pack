package dev.bowpack.gametest;

import dev.bowpack.projectile.ArrowAccess;
import dev.bowpack.projectile.ShotState;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public class FollowPhysicsGameTests {
    @GameTest(structure = ProjectileFixtures.ARENA)
    public void antiFrictionKeepsSpeedAndHeightInAirAndWater(GameTestHelper test) {
        Arrow air = arrow(test, new Vec3(2.5, 4.5, 2.5), new Vec3(0.4, 0, 0), false);
        double startY = air.getY();
        for (int tick = 0; tick < 4; tick++) air.tick();
        ProjectileFixtures.near(test, startY, air.getY(), "Anti Friction prevents falling in air");
        ProjectileFixtures.near(test, 0.4, air.getDeltaMovement().length(),
                "Anti Friction prevents air drag");

        test.setBlock(29, 4, 29, Blocks.WATER);
        Arrow water = arrow(test, new Vec3(1.5, 4.5, 1.5), new Vec3(0.4, 0, 0), false);
        water.tick();
        ProjectileFixtures.near(test, 0.4, water.getDeltaMovement().length(),
                "Anti Friction prevents water drag");
        air.discard();
        water.discard();
        test.succeed();
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void followTurnsTenDegreesTowardAnEnemyBehindTheArrow(GameTestHelper test) {
        Mob target = ProjectileFixtures.cow(test, new Vec3(10, 4, 20));
        Arrow arrow = arrow(test, new Vec3(20, 4.5, 20), new Vec3(1, 0, 0), true);
        arrow.tick();
        double degrees = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1,
                new Vec3(1, 0, 0).dot(arrow.getDeltaMovement().normalize())))));
        test.assertTrue(degrees > 9.9 && degrees < 10.1,
                "Follow must turn at most ten degrees toward a target behind it: " + degrees);
        target.discard();
        arrow.discard();
        test.succeed();
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void followCanSwitchToANearerTarget(GameTestHelper test) {
        Mob first = ProjectileFixtures.cow(test, new Vec3(17, 4, 20));
        Mob second = ProjectileFixtures.cow(test, new Vec3(26, 4, 20));
        Arrow arrow = arrow(test, new Vec3(20, 4.5, 20), new Vec3(0, 0, 1), true);
        arrow.tick();
        double towardFirst = arrow.getDeltaMovement().x;
        second.setPos(ProjectileFixtures.position(test, new Vec3(22, 4, 22)));
        arrow.tick();
        test.assertTrue(arrow.getDeltaMovement().x > towardFirst,
                "Follow must change direction when a different target becomes nearest");
        first.discard();
        second.discard();
        arrow.discard();
        test.succeed();
    }

    private static Arrow arrow(GameTestHelper test, Vec3 local, Vec3 velocity, boolean follow) {
        Vec3 position = ProjectileFixtures.position(test, local);
        Arrow arrow = new Arrow(test.getLevel(), position.x, position.y, position.z,
                new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
        arrow.setDeltaMovement(velocity);
        arrow.setNoGravity(true);
        ((ArrowAccess) arrow).bowpack$setState(new ShotState(0, 0, true, false, 0, follow,
                velocity.length(), UUID.randomUUID(), Optional.empty()));
        test.getLevel().addFreshEntity(arrow);
        return arrow;
    }
}
