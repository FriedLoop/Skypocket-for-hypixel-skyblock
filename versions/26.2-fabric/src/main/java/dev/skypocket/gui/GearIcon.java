package dev.skypocket.gui;

import dev.skypocket.render.GuiPixels;
import dev.skypocket.ui.Rect;
import dev.skypocket.ui.UiPainter;
import dev.skypocket.ui.mc.McUiPainter;

/**
 * Paints the settings glyph in the footer button: an open ring with eight evenly spaced teeth.
 *
 * <p>Drawn rather than set in text. The UI's font chain is Inter for Latin and Noto Sans SC for CJK,
 * and neither carries U+2699, so a gear character would render as a blank box. A raster asset was the
 * other option, but the button must stay crisp at every panel scale, and a 36px PNG is resampled at
 * all of them.
 *
 * <p>The painter has no arc or polygon primitive, so the ring is two fills: an outer disc, then an
 * inner disc in the button's own background. Both go through {@code GuiPixels.fillRoundedRect} with
 * radius = half the side, which is what makes them circles rather than rounded squares, and which
 * carries the same antialiasing as every other rounded surface in the UI.
 *
 * <p>Teeth are placed on the eight 45-degree steps and drawn <em>before</em> the disc so their inner
 * ends are covered; the silhouette then reads as one shape. Each tooth is a small rounded rect
 * rotated to face outward, approximated by offsetting it along its own axis.
 */
final class GearIcon {
    /** Disc diameter as a fraction of the button side. */
    private static final float SIZE_RATIO = 0.62f;
    /** Ring thickness as a fraction of the disc diameter. */
    private static final float RING_RATIO = 0.26f;
    /** Tooth length as a fraction of the disc diameter; it starts at the ring's outer edge. */
    private static final float TOOTH_LENGTH_RATIO = 0.22f;
    /** Tooth width as a fraction of the disc diameter. */
    private static final float TOOTH_WIDTH_RATIO = 0.20f;
    private static final int TEETH = 8;

    private GearIcon() {
    }

    /**
     * Paints a gear centred in {@code box}.
     *
     * @param box       the button's bounds; the gear is centred in it
     * @param color     ARGB of the gear itself
     * @param holeColor fill for the centre hole - the button's current background, because the
     *                  painter has no erase operation and the backdrop changes with hover
     */
    static void paint(UiPainter painter, Rect box, int color, int holeColor) {
        if (!(painter instanceof McUiPainter mc)) {
            return;
        }
        float cx = box.x() + box.width() / 2f;
        float cy = box.y() + box.height() / 2f;
        float diameter = Math.min(box.width(), box.height()) * SIZE_RATIO;
        float outerR = diameter / 2f;
        float innerR = outerR * (1f - RING_RATIO);
        float toothLen = diameter * TOOTH_LENGTH_RATIO;
        float toothW = diameter * TOOTH_WIDTH_RATIO;

        // Teeth first: the disc covers their inner ends, so only the protruding part shows.
        for (int i = 0; i < TEETH; i++) {
            double angle = Math.PI * 2 * i / TEETH;
            float dirX = (float) Math.cos(angle);
            float dirY = (float) Math.sin(angle);
            // Centre each tooth halfway along its protrusion, measured from the outer edge.
            float midR = outerR + toothLen / 2f;
            float tx = cx + dirX * midR;
            float ty = cy + dirY * midR;
            // Teeth are axis-aligned rounded rects; at this size the misalignment for the diagonal
            // ones reads as a bevel rather than as a misplaced tooth.
            GuiPixels.fillRoundedRect(mc.graphics(),
                    Math.round(tx - toothW / 2f), Math.round(ty - toothLen / 2f),
                    Math.max(1, Math.round(toothW)), Math.max(1, Math.round(toothLen)),
                    Math.max(1, Math.round(toothW * 0.35f)), color);
        }

        // Disc, then the hole: two circles, the second in the button's background.
        int discX = Math.round(cx - outerR);
        int discY = Math.round(cy - outerR);
        int discSize = Math.max(1, Math.round(diameter));
        GuiPixels.fillRoundedRect(mc.graphics(), discX, discY, discSize, discSize,
                Math.round(outerR), color);

        int holeSize = Math.max(1, Math.round(innerR * 2f));
        GuiPixels.fillRoundedRect(mc.graphics(), Math.round(cx - innerR), Math.round(cy - innerR),
                holeSize, holeSize, Math.round(innerR), holeColor);
    }
}
