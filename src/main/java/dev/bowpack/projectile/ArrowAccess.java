package dev.bowpack.projectile;

import org.jspecify.annotations.Nullable;

public interface ArrowAccess {
    @Nullable ShotState bowpack$getState();
    void bowpack$setState(@Nullable ShotState state);
    boolean bowpack$isManaged();
    boolean bowpack$ricochetEnabled();
    int bowpack$remainingBounces();
    void bowpack$spendBounce();
}
