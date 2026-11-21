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

    /**
     * Runs {@code body} with the rasterisation scale set to {@code scale}, restoring the previous
     * value afterwards.
     *
     * <p>Glyphs are rasterised at {@code DESIGN_SIZE × renderScale × guiScale} and then placed using
     * the pose they are drawn under. Those two have to agree: rasterising at one scale and drawing at
     * another means the texture is resampled, which shows up as soft text. A host that draws a page
     * under {@code pose.scale(s)} therefore also has to set the raster scale to {@code s}, and it has
     * to put it back afterwards or every later screen inherits it.
     *
     * <p>That pairing is the thing this method exists to keep together. It is easy to write the pose
     * scale and forget the raster scale - the text then looks merely "not quite right", which is much
     * harder to notice than a layout error. Both of the hosts that scale a page by hand (the theme
     * drawer and the HUD settings card) do exactly this, and both had the bug once.
     */
    public static <T> T withScale(float scale, java.util.function.Supplier<T> body) {
        float previous = effectiveScale();
        setEffectiveScale(scale);
        try {
            return body.get();
        } finally {
            setEffectiveScale(previous);
        }
    }

    /** Void form of {@link #withScale(float, java.util.function.Supplier)}. */
    public static void withScale(float scale, Runnable body) {
        withScale(scale, () -> {
            body.run();
            return null;
        });
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
