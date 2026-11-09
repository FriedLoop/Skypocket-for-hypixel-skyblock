package dev.skypocket.render;

import dev.skypocket.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The one place a HUD element asks for text.
 *
 * <p>The SkyPocket font path deliberately uses the atlas renderer's measurement and drawing in the
 * same GUI units. Measuring with the vanilla font while drawing with a device-pixel atlas was what
 * made the old HUD font switch drift; both halves now come from {@link ClickGuiFonts}. With the
 * option off, this still forwards to vanilla so the two paths can be compared live.
 */
public final class HudText {

    private HudText() {
    }

    /** Width of {@code text} in the HUD element's local GUI units, before its pose scale. */
    public static int width(ModConfig cfg, String text, float scale) {
        if (cfg.hudSkyPocketFont) {
            return ClickGuiFonts.hudWidth(text, scale);
        }
        return Minecraft.getInstance().font.width(text);
    }

    /** Fixed-scale text (debug readouts drawn at 1:1). */
    public static int width(ModConfig cfg, String text) {
        return width(cfg, text, 1.0f);
    }

    /** Line height in local GUI units; the element's pose applies the scale. */
    public static int lineHeight(ModConfig cfg) {
        if (cfg.hudSkyPocketFont) {
            return ClickGuiFonts.hudLineHeight();
        }
        return Minecraft.getInstance().font.lineHeight;
    }

    /**
     * Draws text at {@code (x, y)} in the current pose's units. {@code scale} must be the same
     * scale already applied to the pose, so the atlas rasterizes at the final device size.
     */
    public static void draw(GuiGraphicsExtractor graphics, ModConfig cfg, String text, int x, int y,
                            int color, boolean shadow, float scale) {
        if (cfg.hudSkyPocketFont) {
            ClickGuiFonts.hudDraw(graphics, text, x, y, color, shadow, scale);
            return;
        }
        graphics.text(Minecraft.getInstance().font, text, x, y, color, shadow);
    }

    /** Fixed-scale text (debug readouts drawn at 1:1). */
    public static void draw(GuiGraphicsExtractor graphics, ModConfig cfg, String text, int x, int y,
                            int color, boolean shadow) {
        draw(graphics, cfg, text, x, y, color, shadow, 1.0f);
    }
}
