package dev.skypocket.render;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;
import net.minecraft.client.Minecraft;

/** Shared state for the third-person no-clip camera mixins. */
public final class ThirdPersonCamera {

    private ThirdPersonCamera() {
    }

    public static boolean isActive() {
        ModConfig cfg = ConfigManager.get();
        if (!cfg.thirdPersonBlock.thirdPersonThroughWalls) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        return mc.options != null && !mc.options.getCameraType().isFirstPerson();
    }
}
