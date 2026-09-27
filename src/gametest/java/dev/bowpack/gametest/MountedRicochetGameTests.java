package dev.bowpack.gametest;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.phys.Vec3;

public class MountedRicochetGameTests {
    @GameTest(structure = ProjectileFixtures.ARENA)
    public void hittingZombieRidingHorseReachesSeparateMob(GameTestHelper test) {
        assertRedirect(test, EntityTypes.HORSE, true);
        test.succeed();
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void hittingZombieRidingZombieHorseReachesSeparateMob(GameTestHelper test) {
        assertRedirect(test, EntityTypes.ZOMBIE_HORSE, true);
        test.succeed();
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void hittingHorseWithZombieRiderReachesSeparateMob(GameTestHelper test) {
        assertRedirect(test, EntityTypes.HORSE, false);
        test.succeed();
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void hittingZombieHorseWithZombieRiderReachesSeparateMob(GameTestHelper test) {
        assertRedirect(test, EntityTypes.ZOMBIE_HORSE, false);
        test.succeed();
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void lowerRiderImpactInsideMountStillReachesSeparateMob(GameTestHelper test) {
        assertRedirect(test, EntityTypes.ZOMBIE_HORSE, true, true, false);
        test.succeed();
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void lethalRiderImpactStillReachesSeparateMob(GameTestHelper test) {
        assertRedirect(test, EntityTypes.HORSE, true, false, true);
        test.succeed();
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void lethalMountImpactStillReachesSeparateMob(GameTestHelper test) {
        assertRedirect(test, EntityTypes.ZOMBIE_HORSE, false, false, true);
        test.succeed();
    }

    @GameTest(structure = ProjectileFixtures.ARENA)
    public void mountedGroupAloneCannotKeepRicochetingIntoItself(GameTestHelper test) {
        for (var mountType : List.of(EntityTypes.HORSE, EntityTypes.ZOMBIE_HORSE)) {
            for (boolean hitRider : new boolean[] {true, false}) {
                MountedPair pair = mount(test, mountType);
                Mob hit = hitRider ? pair.rider() : pair.mount();
                Mob companion = hitRider ? pair.mount() : pair.rider();
                float initialHealth = hit.getHealth();
                float companionHealth = companion.getHealth();
                Arrow arrow = shootAt(test, initialAim(test, pair, hitRider));
                arrow.tick();
                test.assertTrue(hit.getHealth() < initialHealth, "The chosen mounted entity must be hit");
                test.assertFalse(arrow.isAlive(),
                        "Ricochet must stop when only members of the hit riding group remain");
                test.assertValueEqual(companion.getHealth(), companionHealth,
                        "An overlapping rider or mount is not a new Ricochet target");
                pair.rider().discard();
                pair.mount().discard();
            }
        }
        test.succeed();
    }

    private static void assertRedirect(GameTestHelper test, EntityType<? extends Mob> mountType,
                                       boolean hitRider) {
        assertRedirect(test, mountType, hitRider, false, false);
    }

    private static void assertRedirect(GameTestHelper test, EntityType<? extends Mob> mountType,
                                       boolean hitRider, boolean overlappingImpact, boolean lethal) {
        MountedPair pair = mount(test, mountType);
        Mob hit = hitRider ? pair.rider() : pair.mount();
        Mob companion = hitRider ? pair.mount() : pair.rider();
        Mob separate = ProjectileFixtures.cow(test, new Vec3(10, 2, 4));
        if (lethal) hit.setHealth(1);
        float hitHealth = hit.getHealth();
        float companionHealth = companion.getHealth();
        float separateHealth = separate.getHealth();
        Vec3 aim = initialAim(test, pair, hitRider);
        if (overlappingImpact) {
            double bottom = Math.max(pair.rider().getBoundingBox().minY, pair.mount().getBoundingBox().minY);
            double top = Math.min(pair.rider().getBoundingBox().maxY, pair.mount().getBoundingBox().maxY);
            test.assertTrue(top > bottom, "Native rider and mount must have overlapping bounding boxes");
            aim = new Vec3(aim.x, (bottom + top) / 2, aim.z);
        }
        Arrow arrow = shootAt(test, aim);
        if (lethal) arrow.setBaseDamage(3);
        arrow.tick();
        test.assertTrue(hit.getHealth() < hitHealth, "The initial arrow must hit the chosen rider or mount");
        if (lethal) test.assertFalse(hit.isAlive(), "The initial impact must kill the chosen rider or mount");
        if (overlappingImpact) {
            test.assertTrue(pair.mount().getBoundingBox().contains(arrow.position()),
                    "The real lower-rider collision must leave the arrow inside the mount bounding box");
        }
        test.assertTrue(arrow.isAlive(), "A separate nearby target should keep Ricochet active");
        test.assertTrue(arrow.isNoGravity(), "Ricochet must retain Anti Friction after a mounted impact");
        for (int tick = 0; tick < 12 && separate.getHealth() == separateHealth && arrow.isAlive(); tick++) {
            arrow.tick();
        }
        test.assertTrue(separate.getHealth() < separateHealth,
                "The same arrow must leave the riding group and actually damage the separate nearby mob");
        test.assertValueEqual(companion.getHealth(), companionHealth,
                "Ricochet cannot become trapped hitting the overlapping rider or mount");
    }

    private static MountedPair mount(GameTestHelper test, EntityType<? extends Mob> mountType) {
        Mob horse = test.spawnWithNoFreeWill(mountType, new Vec3(32, 2, 32));
        var zombie = test.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new Vec3(32, 2, 32));
        horse.setNoGravity(true);
        zombie.setNoGravity(true);
        zombie.setBaby(false);
        test.assertTrue(zombie.startRiding(horse, true, false), "Zombie must mount the native horse entity");
        horse.positionRider(zombie);
        test.assertTrue(zombie.getVehicle() == horse, "Fixture must retain its real passenger relationship");
        return new MountedPair(horse, zombie);
    }

    private static Vec3 initialAim(GameTestHelper test, MountedPair pair, boolean hitRider) {
        if (hitRider) return pair.rider().getBoundingBox().getCenter();
        var horseBounds = pair.mount().getBoundingBox();
        double bottom = horseBounds.minY + 0.1;
        // Leave clearance below the rider's expanded projectile collision box.
        double top = Math.min(horseBounds.maxY - 0.1, pair.rider().getBoundingBox().minY - 0.4);
        test.assertTrue(top > bottom, "The native mount must have an exposed lower body below its rider");
        Vec3 center = horseBounds.getCenter();
        return new Vec3(center.x, (bottom + top) / 2, center.z);
    }

    private static Arrow shootAt(GameTestHelper test, Vec3 aim) {
        Vec3 start = aim.add(-2, 0, 0);
        Vec3 local = start.subtract(ProjectileFixtures.position(test, Vec3.ZERO));
        Arrow arrow = ProjectileFixtures.arrow(test, local, new Vec3(3, 0, 0), true, 0, true);
        arrow.setBaseDamage(0.25);
        return arrow;
    }

    private record MountedPair(Mob mount, Mob rider) {}
}
