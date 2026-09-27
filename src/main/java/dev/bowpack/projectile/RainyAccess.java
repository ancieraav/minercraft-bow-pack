package dev.bowpack.projectile;

import java.util.UUID;

/** Runtime controls for a Rainy Shot arrow's stationary aiming phase. */
public interface RainyAccess {
    double bowpack$getBaseDamage();

    boolean bowpack$isRainyHovering();

    void bowpack$startRainyHover(UUID target, long releaseAt);
}
