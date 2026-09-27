package dev.bowpack.math;

import java.util.ArrayList;
import java.util.List;

/** Builds independent forward, diagonal and backward lanes, then repeats their trailing rows. */
public final class ShotPattern {
    private static final double MULTI_GAP = 0.4;
    private static final double SIDE_GAP = 0.45;
    private static final double[] FRONT_OFFSETS = {0, -0.45, 0.45, -0.9, 0.9};
    private static final double[][] DIAGONAL_ANGLES = {
        {},
        {-45, 45},
        {-90, -45, 45, 90},
        {-90, -60, -30, 30, 60, 90}
    };

    private ShotPattern() {}

    public record Shot(ShotVector offset, ShotVector velocity) {}

    /**
     * Uses Minecraft yaw: zero faces +Z and increasing yaw turns toward -X.
     * The first shot is always the original projectile at a zero offset.
     */
    public static List<Shot> generate(
            ShotVector forward, double speed, float yawDegrees,
            int multi, int front, int diagonal, int back) {
        checkLevel("Multi Shot", multi, 4);
        checkLevel("Front Shot", front, 4);
        checkLevel("Diagonal Shot", diagonal, 3);
        checkLevel("Back Shot", back, 5);
        if (!Double.isFinite(speed) || speed <= 0 || !Float.isFinite(yawDegrees)) {
            throw new IllegalArgumentException("Shot speed must be positive and speed/yaw finite");
        }

        ShotVector direction = forward.normalize();
        ShotVector backward = direction.multiply(-1);
        double yaw = Math.toRadians(yawDegrees);
        ShotVector right = new ShotVector(-Math.cos(yaw), 0, -Math.sin(yaw));
        double[] angles = DIAGONAL_ANGLES[diagonal];
        List<Shot> shots = new ArrayList<>((multi + 1) * (front + 1 + angles.length + back));
        for (int row = 0; row <= multi; row++) {
            for (int column = 0; column <= front; column++) {
                add(shots, direction, right.multiply(FRONT_OFFSETS[column]), speed, row);
            }
            for (double angle : angles) {
                add(shots, rotateYaw(direction, angle).normalize(), new ShotVector(0, 0, 0), speed, row);
            }
            for (int column = 0; column < back; column++) {
                double offset = (column - (back - 1) / 2.0) * SIDE_GAP;
                add(shots, backward, right.multiply(-offset), speed, row);
            }
        }
        return List.copyOf(shots);
    }

    private static void add(List<Shot> shots, ShotVector direction, ShotVector lateral,
                            double speed, int row) {
        shots.add(new Shot(lateral.add(direction.multiply(-row * MULTI_GAP)), direction.multiply(speed)));
    }

    /** Reflects velocity without energy loss; the surface normal need not be normalized. */
    public static ShotVector reflect(ShotVector velocity, ShotVector surfaceNormal) {
        ShotVector normal = surfaceNormal.normalize();
        return velocity.subtract(normal.multiply(2 * velocity.dot(normal)));
    }

    private static ShotVector rotateYaw(ShotVector direction, double degrees) {
        double radians = Math.toRadians(degrees);
        double cosine = Math.cos(radians);
        double sine = Math.sin(radians);
        return new ShotVector(
                direction.x() * cosine - direction.z() * sine,
                direction.y(),
                direction.x() * sine + direction.z() * cosine);
    }

    private static void checkLevel(String name, int level, int maximum) {
        if (level < 0 || level > maximum) {
            throw new IllegalArgumentException(name + " level must be between 0 and " + maximum);
        }
    }
}
