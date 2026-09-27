package dev.bowpack;

import dev.bowpack.projectile.RainyShot;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;

public final class BowPack implements ModInitializer {
    public static final String MOD_ID = "bowpack";

    @Override
    public void onInitialize() {
        BowCreative.register();
        RainyShot.register();
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
