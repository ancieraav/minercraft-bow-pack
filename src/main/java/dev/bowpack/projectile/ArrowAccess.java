package dev.bowpack.projectile;

import org.jspecify.annotations.Nullable;

public interface ArrowAccess {
    @Nullable ShotState bowpack$getState();
    void bowpack$setState(@Nullable ShotState state);
    double bowpack$getBaseDamage();
    boolean bowpack$isManaged();
    boolean bowpack$ricochetEnabled();
    boolean bowpack$unstopableEnabled();
    boolean bowpack$antiFrictionEnabled();
    int bowpack$accelerateLevel();
    int bowpack$remainingBounces();
    void bowpack$spendBounce();
}
