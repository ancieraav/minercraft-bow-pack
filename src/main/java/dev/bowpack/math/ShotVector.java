package dev.bowpack.math;

/** An immutable vector shared by projectile calculations. */
public record ShotVector(double x, double y, double z) {
    public ShotVector {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("Vector coordinates must be finite");
        }
    }

    public ShotVector add(ShotVector other) {
        return new ShotVector(x + other.x, y + other.y, z + other.z);
    }

    public ShotVector subtract(ShotVector other) {
        return new ShotVector(x - other.x, y - other.y, z - other.z);
    }

    public ShotVector multiply(double scalar) {
        return new ShotVector(x * scalar, y * scalar, z * scalar);
    }

    public double dot(ShotVector other) {
        return x * other.x + y * other.y + z * other.z;
    }

    public double lengthSquared() {
        return dot(this);
    }

    public double length() {
        return Math.hypot(Math.hypot(x, y), z);
    }

    public ShotVector normalize() {
        double magnitude = length();
        if (magnitude == 0 || !Double.isFinite(magnitude)) {
            throw new IllegalArgumentException("A direction must have a finite, nonzero length");
        }
        return new ShotVector(x / magnitude, y / magnitude, z / magnitude);
    }
}
