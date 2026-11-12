package dev.skypocket.render;

import dev.skypocket.config.ConfigManager;
import net.minecraft.client.Minecraft;

/**
 * The single conversion path for ClickGUI geometry.
 *
 * <p>There are three distinct values and they must not be mixed:
 * <ul>
 *   <li>{@link #configuredScale()} - the value stored in the config/slider;</li>
 *   <li>{@link #effectiveScale()} - design units to GUI units after the panel has
 *       been fitted and snapped to device pixels;</li>
 *   <li>{@link #deviceScale()} - design units to real screen pixels.</li>
 * </ul>
 *
 * <p>The design canvas is already calibrated in current design units; there is no
 * legacy multiplier in the runtime path.
 */
public final class ClickGuiMetrics {
    private static volatile float effectiveScale = 1.0f;

    private ClickGuiMetrics() {
    }

    /** The user-facing scale from the config slider. */
    public static float configuredScale() {
        return ConfigManager.get().clickGuiScale;
    }

    /** Un-snapped design -> GUI scale for a given window GUI scale. */
    public static float projectedScale(float guiScale) {
        float scale = Math.max(1.0e-4f, guiScale);
        return configuredScale() / scale;
    }

    /** Actual design -> GUI scale after fit and device-pixel snapping. */
    public static float effectiveScale() {
        return effectiveScale;
    }

    /** Design -> device pixels. This is the only value asset/raster choices should use. */
    public static float deviceScale() {
        return effectiveScale * ClickGuiFonts.guiScale();
    }

    /** Called by the screen after its layout has fitted and snapped the panel. */
    public static void setEffectiveScale(float scale) {
        effectiveScale = Math.max(1.0e-4f, scale);
        ClickGuiFonts.setRenderScale(effectiveScale);
    }

    /** Window GUI scale, exposed here so widgets do not each query the window. */
    public static float guiScale() {
        Minecraft mc = Minecraft.getInstance();
        return mc.getWindow() == null ? 1.0f : Math.max(1.0e-4f, mc.getWindow().getGuiScale());
    }

    /** The nearest shipped logo raster for a design-unit footprint. */
    public static int nearestLogoSample(int designSize) {
        int target = Math.max(1, Math.round(designSize * deviceScale()));
        if (target <= 24) {
            return 24;
        }
        if (target <= 48) {
            return 48;
        }
        if (target <= 72) {
            return 72;
        }
        return 96;
    }
}
