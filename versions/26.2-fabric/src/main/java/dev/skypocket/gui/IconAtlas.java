package dev.skypocket.gui;

import dev.skypocket.ui.Rect;
import dev.skypocket.ui.UiPainter;
import dev.skypocket.ui.mc.McUiPainter;
import net.minecraft.resources.Identifier;

/**
 * The UI's icon set, drawn from one texture atlas.
 *
 * <p>Why an atlas rather than shapes: the painter offers rounded rectangles and straight lines
 * only. It has no polygon, no stroke path, and {@code GuiPixels} pushes a device space of its own,
 * so a pose rotation does not reach it. A gear built from eight axis-aligned rectangles was the
 * previous attempt and read as a smudge at 22px; the icons in the sidebar have to be more detailed
 * than that (a radar, a wireframe cube, crossed blades), which is not reachable with what the
 * painter has.
 *
 * <p>The sources are Lucide icons (ISC licence - see {@code tools/ICON-LICENSES.md}), rasterised by
 * {@code tools/render-icons.mjs} at 128px per cell and drawn here at 24px. The reduction from 128 is
 * what produces the antialiasing; drawing them at 24 and scaling up would be blurry.
 *
 * <p>Icons are white with alpha; the colour comes from the tint applied at the call site, so a theme
 * change needs no re-render.
 */
final class IconAtlas {
    /** Icons in atlas order. Adding one means adding it here and in render-icons.mjs. */
    enum Icon {
        GEAR,
        PICKAXE,
        SWORDS,
        FISH,
        RADAR,
        WIRE_CUBE,
        BELL,
        ARROWS,
        MONITOR,
        LAYERS,
    }

    private static final int CELL = AtlasSize.smallCell();
    private static final int PAD = AtlasSize.PADDING;
    private static final int COLUMNS = AtlasSize.SMALL_COLUMNS;
    private static final int ATLAS_W = AtlasSize.smallWidth();
    private static final int ATLAS_H = AtlasSize.smallHeight();

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
            dev.skypocket.SkyPocketMod.MOD_ID, "textures/gui/sprites/ui/icons.png");

    private IconAtlas() {
    }

    /**
     * Draws {@code icon} centred in {@code box} at the box's size.
     *
     * @param box   the square the icon fills; a non-square box uses the smaller side
     * @param color ARGB tint; white ({@code 0xFFFFFFFF}) leaves the icon as drawn
     */
    static void paint(UiPainter painter, Icon icon, Rect box, int color) {
        if (!(painter instanceof McUiPainter mc)) {
            return;
        }
        float side = Math.min(box.width(), box.height());
        if (side <= 0f) {
            return;
        }
        float x = box.x() + (box.width() - side) / 2f;
        float y = box.y() + (box.height() - side) / 2f;

        int col = icon.ordinal() % COLUMNS;
        int row = icon.ordinal() / COLUMNS;
        int u = col * CELL + PAD;
        int v = row * CELL + PAD;
        int iconSize = CELL - PAD * 2;

        // blit takes destination size in whole pixels; round outward so a fractional box still
        // covers the space it was given rather than leaving a hairline gap.
        int w = Math.max(1, Math.round(side));
        int h = Math.max(1, Math.round(side));
        // The last two size arguments are the atlas dimensions, not the cell's. Passing CELL for both
        // normalises the u/v against a 128x128 texture instead of the real 768x256 one, which samples
        // the whole atlas into the destination: the icon came out as scrambled pixels.
        // Drawn with the atlas already carrying its antialiasing - see render-icons.mjs. The
        // linear-sampler overload exists but does not take effect here, so the edges are softened
        // in the texture itself rather than by the sampler.
        mc.graphics().blit(
                net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, TEXTURE,
                Math.round(x), Math.round(y), u, v, w, h, iconSize, iconSize, ATLAS_W, ATLAS_H,
                color);
    }
}
