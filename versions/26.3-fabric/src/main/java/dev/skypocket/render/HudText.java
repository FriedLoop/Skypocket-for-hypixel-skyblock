package dev.skypocket.render;

import dev.skypocket.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The one place a HUD element asks for text.
 *
 * <p>It exists to keep measurement and drawing pointing at the same font. That mattered when the HUD
 * could draw in the Click GUI font - a font switch that was removed, because one {@code Font} has one
 * atlas, so a substitute that is crisp is also one whose metrics are baked for the device grid, and
 * vanilla callers read metrics as GUI units. What is left is the rule it enforced, which is worth
 * keeping: never measure with one font and draw with another, or everything that centres or stacks
 * text (the stats rows, the commission list, the position editor's frames) drifts.
 *
 * <p>So this forwards to the vanilla font, and the HUD elements call it rather than reaching for
 * {@code Minecraft.font} themselves.
 */
public final class HudText {

    private HudText() {
    }

    /** Width of {@code text} in GUI units. */
    public static int width(ModConfig cfg, String text) {
        return Minecraft.getInstance().font.width(text);
    }

    /** Line height in GUI units. */
    public static int lineHeight(ModConfig cfg) {
        return Minecraft.getInstance().font.lineHeight;
    }

    /**
     * Draws text at {@code (x, y)} in the current pose's units - the same call shape as
     * {@code GuiGraphicsExtractor.text}, so an element's existing pose (its position and its scale)
     * keeps working unchanged.
     */
    public static void draw(GuiGraphicsExtractor graphics, ModConfig cfg, String text, int x, int y,
                            int color, boolean shadow) {
        graphics.text(Minecraft.getInstance().font, text, x, y, color, shadow);
    }
}
