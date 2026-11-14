package dev.skypocket.hud;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;

/**
 * The vanilla held item tooltip - the item name that fades in above the hotbar after the selected slot
 * changes - with a configurable offset and size.
 *
 * <p>26.2 registers it as {@code VanillaHudElements.HELD_ITEM_TOOLTIP}, so it can be swapped the same
 * way the crosshair is, with no mixin. The replacement does not reimplement the tooltip at all: it
 * calls the original element inside a pose transform. Everything vanilla does therefore stays exactly
 * as it was - the fade driven by {@code Hud.toolHighlightTimer} and {@code Hud.lastToolHighlight}, the
 * rarity colour, the custom-name italics, the text backdrop, and the conditions under which the
 * tooltip is drawn at all - and the only difference is where and how large it lands.
 *
 * <p><b>The pivot is the screen's horizontal centre, which happens to be the text's own centre.</b>
 * Vanilla draws the name at {@code ((guiWidth - textWidth) / 2, guiHeight - 59)}, so
 * {@code (guiWidth - w)/2 + w/2 == guiWidth/2}: the two candidate pivots are the same point. Using the
 * screen centre means the transform never has to measure the text, which matters because the tooltip
 * carries styling (rarity, italics) that would be fiddly to reproduce exactly.
 *
 * <p><b>The config is an offset, not an absolute position</b>, because this element belongs to the
 * hotbar: it has to keep following the hotbar across window and GUI-scale changes, and relative offsets
 * do that for free. (The absolutely-positioned HUD elements get the equivalent from
 * {@code HudPositionSync} - so this one must <em>not</em> be listed there, or its offsets would get
 * multiplied by the GUI-scale ratio on every resize.)
 */
public final class HeldItemNameHudElement {
    /** Vanilla's baseline: the name's top edge sits this far above the bottom of the screen. */
    public static final int VANILLA_BOTTOM_OFFSET = 59;

    private HeldItemNameHudElement() {
    }

    public static void register() {
        HudElementRegistry.replaceElement(VanillaHudElements.HELD_ITEM_TOOLTIP,
                vanilla -> (graphics, delta) -> render(graphics, delta, vanilla));
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta, HudElement vanilla) {
        ModConfig cfg = ConfigManager.get();
        float scale = scale(cfg);
        if (!cfg.heldItemNameBlock.heldItemNameEnabled
                || (scale == 1f && cfg.heldItemNameBlock.heldItemNameOffsetX == 0 && cfg.heldItemNameBlock.heldItemNameOffsetY == 0)) {
            // Enabled but untouched is exactly the vanilla look, so hand it straight back: taking the
            // element over must not change anything until a value is actually moved.
            vanilla.extractRenderState(graphics, delta);
            return;
        }
        float pivotX = pivotX(graphics.guiWidth());
        float pivotY = pivotY(graphics.guiHeight());
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        // Point p lands on pivot + offset + scale * (p - pivot): the scale keeps the name centred on
        // the screen, and the offset then moves it without the two interfering.
        pose.translate(pivotX + cfg.heldItemNameBlock.heldItemNameOffsetX, pivotY + cfg.heldItemNameBlock.heldItemNameOffsetY);
        pose.scale(scale, scale);
        pose.translate(-pivotX, -pivotY);
        vanilla.extractRenderState(graphics, delta);
        pose.popMatrix();
    }

    /** The horizontal pivot: the screen centre, i.e. the centre of the drawn name (see the class doc). */
    public static int pivotX(int guiWidth) {
        return guiWidth / 2;
    }

    /** The vertical pivot: vanilla's baseline, the top edge of the name. */
    public static int pivotY(int guiHeight) {
        return guiHeight - VANILLA_BOTTOM_OFFSET;
    }

    public static float scale(ModConfig cfg) {
        return Math.max(0.25f, Math.min(6f, cfg.heldItemNameBlock.heldItemNameScale));
    }

    /**
     * The name's rectangle after the transform, as {@code {x, y, width, height}} - what the position
     * editor frames and hit-tests. {@code textWidth} and {@code lineHeight} are the unscaled text
     * metrics, because the pivot-based scale never needs to know them for the transform itself.
     */
    public static void box(ModConfig cfg, int guiWidth, int guiHeight, int textWidth, int lineHeight,
                           int[] out) {
        float scale = scale(cfg);
        out[0] = Math.round(pivotX(guiWidth) - textWidth * scale / 2f) + cfg.heldItemNameBlock.heldItemNameOffsetX;
        out[1] = pivotY(guiHeight) + cfg.heldItemNameBlock.heldItemNameOffsetY;
        out[2] = Math.round(textWidth * scale);
        out[3] = Math.round(lineHeight * scale);
    }

    /**
     * The name the tooltip would show, or the fallback when there is nothing in hand. The editor needs
     * something to draw and measure, and a zero-width box cannot be grabbed.
     */
    public static String previewName(Minecraft mc, String fallback) {
        if (mc.player == null) {
            return fallback;
        }
        ItemStack stack = mc.player.getMainHandItem();
        return stack.isEmpty() ? fallback : stack.getHoverName().getString();
    }
}
