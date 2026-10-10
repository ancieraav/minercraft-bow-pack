package dev.bowpack;

public record ShotLevels(int multi, int front, int back, int diagonal, int ricochet,
                         int bouncy, boolean antiFriction, boolean breakthrough,
                         boolean follow, boolean unstopable, int accelerateLevel) {
    public static final ShotLevels NONE = new ShotLevels(0, 0, 0, 0, 0, 0, false, false, false, false, 0);

    public boolean active() {
        return multi > 0 || front > 0 || back > 0 || diagonal > 0 || ricochet > 0
                || bouncy > 0 || antiFriction || breakthrough || follow || unstopable || accelerateLevel > 0;
    }
}
