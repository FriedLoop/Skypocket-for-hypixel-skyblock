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

    /** The SkyBlock-only badge shown on card titles. */
    public static Identifier marker() {
        return Identifier.fromNamespaceAndPath(SkyPocketMod.MOD_ID,
                "textures/gui/sprites/icons/skyblockmarker_40.png");
    }

    /** The ClickGUI title-bar logo. A text-free variant wins when supplied. */
    public static Identifier logo() {
        return logo(48);
    }

    /** Pick a prefiltered logo matching the display's device resolution. */
    public static Identifier logo(int sourceSize) {
        int size = switch (sourceSize) {
            case 24, 48, 72 -> sourceSize;
            default -> 96;
        };
        return Identifier.fromNamespaceAndPath(SkyPocketMod.MOD_ID,
                "textures/gui/sprites/logos/skypocketiconforclickgui_rounded_" + size + ".png");
    }

    /** Whether a custom icon texture has been provided for the module. */
    public static boolean exists(String moduleId) {
        return !Minecraft.getInstance().getResourceManager().getResourceStack(texture(moduleId)).isEmpty();
    }
}
