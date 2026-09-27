package dev.bowpack.math;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ShotPatternTest {
    private static final double EPSILON = 1e-10;
    private static final ShotVector FORWARD = new ShotVector(0, 0, 1);

    @Test
    void everyLevelCombinationCountsIndependentLanesWithOriginalFirst() {
        for (int multi = 0; multi <= 4; multi++) {
            for (int front = 0; front <= 4; front++) {
                for (int diagonal = 0; diagonal <= 3; diagonal++) {
                    for (int back = 0; back <= 5; back++) {
                        var shots = ShotPattern.generate(FORWARD, 3, 0, multi, front, diagonal, back);
                        assertEquals((multi + 1) * (front + 1 + 2 * diagonal + back), shots.size());
                        assertVector(new ShotVector(0, 0, 0), shots.getFirst().offset());
                        assertVector(new ShotVector(0, 0, 3), shots.getFirst().velocity());
                    }
                }
            }
        }
        assertEquals(80, ShotPattern.generate(FORWARD, 3, 0, 4, 4, 3, 5).size());
    }

    @Test
    void frontColumnsAreCloseParallelAndRepeatedBehindEveryMultiRow() {
        var shots = ShotPattern.generate(FORWARD, 2.5, 0, 4, 4, 0, 0);
        double[] expectedX = {0, 0.45, -0.45, 0.9, -0.9};
        for (int row = 0; row < 5; row++) {
            for (int column = 0; column < 5; column++) {
                var shot = shots.get(row * 5 + column);
                assertVector(new ShotVector(expectedX[column], 0, -row * 0.4), shot.offset());
                assertVector(new ShotVector(0, 0, 2.5), shot.velocity());
            }
        }
    }

    @Test
    void diagonalKeepsExactAnglesAndDoesNotReceiveFrontColumns() {
        double[][] angles = {{}, {-45, 45}, {-90, -45, 45, 90}, {-90, -60, -30, 30, 60, 90}};
        for (int diagonal = 0; diagonal <= 3; diagonal++) {
            var shots = ShotPattern.generate(FORWARD, 3.1, 0, 0, 4, diagonal, 0);
            assertEquals(5 + 2 * diagonal, shots.size());
            for (int column = 0; column < 5; column++) {
                assertVector(new ShotVector(0, 0, 3.1), shots.get(column).velocity());
            }
            for (int side = 0; side < angles[diagonal].length; side++) {
                var shot = shots.get(side + 5);
                assertEquals(angles[diagonal][side], yawOf(shot.velocity()), EPSILON);
                assertEquals(3.1, shot.velocity().length(), EPSILON);
                assertVector(new ShotVector(0, 0, 0), shot.offset());
            }
        }
    }

    @Test
    void backHasExactlyItsLevelCenteredAndReversesPitch() {
        for (int back = 1; back <= 5; back++) {
            var shots = ShotPattern.generate(new ShotVector(0, 0.6, 0.8), 3, 0, 0, 0, 0, back);
            assertEquals(1 + back, shots.size());
            double center = 0;
            for (int column = 0; column < back; column++) {
                var shot = shots.get(column + 1);
                double lateral = (column - (back - 1) / 2.0) * 0.45;
                assertVector(new ShotVector(lateral, 0, 0), shot.offset());
                assertVector(new ShotVector(0, -1.8, -2.4), shot.velocity());
                center += shot.offset().x();
            }
            assertEquals(0, center, EPSILON);
        }
    }

    @Test
    void multiRepeatsEveryLaneBehindItsOwnTravelDirection() {
        var base = ShotPattern.generate(FORWARD, 3, 0, 0, 2, 2, 4);
        var repeated = ShotPattern.generate(FORWARD, 3, 0, 2, 2, 2, 4);
        assertEquals(base.size() * 3, repeated.size());
        for (int row = 0; row < 3; row++) {
            for (int lane = 0; lane < base.size(); lane++) {
                var first = base.get(lane);
                var shot = repeated.get(row * base.size() + lane);
                assertVector(first.velocity(), shot.velocity());
                assertVector(first.offset().add(first.velocity().normalize().multiply(-row * 0.4)), shot.offset());
            }
        }
    }

    @Test
    void diagonalRetainsPitchAndFrontIsPerpendicularToTheMainLane() {
        float yaw = 37;
        double pitch = Math.toRadians(-25);
        ShotVector forward = new ShotVector(
                -Math.sin(Math.toRadians(yaw)) * Math.cos(pitch),
                -Math.sin(pitch), Math.cos(Math.toRadians(yaw)) * Math.cos(pitch));
        var shots = ShotPattern.generate(forward, 3, yaw, 0, 1, 1, 0);
        assertEquals(4, shots.size());
        for (var shot : shots) {
            assertEquals(forward.y() * 3, shot.velocity().y(), EPSILON);
            assertEquals(3, shot.velocity().length(), EPSILON);
        }
        assertVector(shots.getFirst().velocity(), shots.get(1).velocity());
        assertEquals(0, shots.get(1).offset().dot(forward), EPSILON);
        assertEquals(yaw - 45, yawOf(shots.get(2).velocity()), EPSILON);
        assertEquals(yaw + 45, yawOf(shots.get(3).velocity()), EPSILON);
    }

    @Test
    void straightUpAndDownRemainFiniteAndKeepHorizontalSpacing() {
        for (double vertical : new double[] {-1, 1}) {
            var shots = ShotPattern.generate(new ShotVector(0, vertical, 0), 3, 90, 4, 4, 3, 5);
            for (int index = 0; index < shots.size(); index++) {
                var shot = shots.get(index);
                assertTrue(Double.isFinite(shot.offset().length()));
                double direction = index % 16 < 11 ? vertical : -vertical;
                assertVector(new ShotVector(0, direction * 3, 0), shot.velocity());
            }
            assertVector(new ShotVector(0, 0, 0.45), shots.get(1).offset());
        }
    }

    @Test
    void reflectionHandlesWallsFloorsCeilingsAndObliqueSurfacesWithoutLosingSpeed() {
        ShotVector incoming = new ShotVector(1, -2, 3);
        assertVector(new ShotVector(-1, -2, 3), ShotPattern.reflect(incoming, new ShotVector(1, 0, 0)));
        assertVector(new ShotVector(1, 2, 3), ShotPattern.reflect(incoming, new ShotVector(0, 1, 0)));
        assertVector(new ShotVector(1, 2, 3), ShotPattern.reflect(incoming, new ShotVector(0, -1, 0)));
        ShotVector normal = new ShotVector(2, 3, -1);
        ShotVector reflected = ShotPattern.reflect(incoming, normal);
        assertEquals(incoming.length(), reflected.length(), EPSILON);
        assertEquals(-incoming.dot(normal), reflected.dot(normal), EPSILON);
        assertVector(incoming, ShotPattern.reflect(reflected, normal));
    }

    @Test
    void rejectsInvalidLevelsAndUndefinedDirections() {
        assertThrows(IllegalArgumentException.class, () -> ShotPattern.generate(FORWARD, 3, 0, 5, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> ShotPattern.generate(FORWARD, 3, 0, 0, -1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> ShotPattern.generate(FORWARD, 3, 0, 0, 5, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> ShotPattern.generate(FORWARD, 3, 0, 0, 0, 4, 0));
        assertThrows(IllegalArgumentException.class, () -> ShotPattern.generate(FORWARD, 3, 0, 0, 0, 0, 6));
        assertThrows(IllegalArgumentException.class, () -> ShotPattern.generate(FORWARD, 3, 0, 0, 0, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> ShotPattern.generate(FORWARD, 0, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> ShotPattern.generate(FORWARD, 3, Float.NaN, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () ->
                ShotPattern.generate(new ShotVector(0, 0, 0), 3, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () ->
                ShotPattern.reflect(FORWARD, new ShotVector(0, 0, 0)));
    }

    private static double yawOf(ShotVector velocity) {
        return Math.toDegrees(Math.atan2(-velocity.x(), velocity.z()));
    }

    private static void assertVector(ShotVector expected, ShotVector actual) {
        assertEquals(expected.x(), actual.x(), EPSILON);
        assertEquals(expected.y(), actual.y(), EPSILON);
        assertEquals(expected.z(), actual.z(), EPSILON);
    }
}
