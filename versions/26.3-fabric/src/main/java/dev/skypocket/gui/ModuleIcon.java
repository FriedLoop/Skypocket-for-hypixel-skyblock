package dev.skypocket.gui;

import dev.skypocket.SkyPocketMod;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Resolves a module's icon texture. Icons live under
 * {@code assets/skypocket/textures/gui/modules/{moduleId}.png}; when a texture is
 * present the panel draws it, otherwise it falls back to a generated letter tile.
 */
public final class ModuleIcon {
    private ModuleIcon() {
    }

    public static Identifier texture(String moduleId) {
        return Identifier.fromNamespaceAndPath(SkyPocketMod.MOD_ID, "textures/gui/modules/" + moduleId + ".png");
    }

    /** Whether a custom icon texture has been provided for the module. */
    public static boolean exists(String moduleId) {
        return !Minecraft.getInstance().getResourceManager().getResourceStack(texture(moduleId)).isEmpty();
    }
}
