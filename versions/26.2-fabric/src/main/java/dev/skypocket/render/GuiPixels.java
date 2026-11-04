package dev.skypocket.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;

/**
 * Device-pixel drawing primitives for the Click GUI: snapping, scissor bounds and rounded rects.
 *
 * <p>They live here rather than inside the panel screen because the widgets need them too (see
 * {@code gui/widget/ClickGuiStyle}): one design unit is {@code renderScale * guiScale} device
 * pixels - not one screen pixel - so any pixel-level work has to happen in the device space these
 * helpers push ({@link #pushDeviceSpace}), and the widgets must not have to depend on the screen
 * class to reach them.
 *
 * <p>The rules encoded here (each one cost a debugging session; see the project docs):
 * <ul>
 *   <li>{@link #snapSize}: round both edges of a span, never the offset and the size separately,
 *       or two pieces that share an edge end up one pixel apart.</li>
 *   <li>{@link #scissorRow} / {@link #scissorFloor}: a scissor can only sit on whole gui-scaled
 *       units, because {@code ScreenRectangle.transformAxisAligned} floors the rect's size as well
 *       as its position. Snap every bound, and round the top/left outwards so the clip can never
 *       eat into the content.</li>
 *   <li>{@link #fillRoundedBorderedRect}: build a border ring in device pixels; "outer rect plus a
 *       rect inset by one design unit" leaves the ring uneven per side.</li>
 * </ul>
 */
public final class GuiPixels {

    private GuiPixels() {
    }

    /**
     * Device pixels per design unit: the window's GUI scale on top of the panel scale.
     * One design unit is <em>not</em> one screen pixel (at scale 1.0 and GUI scale 3 a
     * design unit is 3 device pixels), which is what any pixel-level drawing has to
     * respect.
     */
    public static float deviceStep() {
        return Math.max(1.0e-4f, ClickGuiFonts.deviceFactor());
    }

    /** Enters device-pixel space: one unit is exactly one device pixel. */
    public static void pushDeviceSpace(GuiGraphicsExtractor g) {
        float step = deviceStep();
        g.pose().pushMatrix();
        g.pose().scale(1f / step, 1f / step);
    }

    public static void popDeviceSpace(GuiGraphicsExtractor g) {
        g.pose().popMatrix();
    }

    /**
     * A panel-local design coordinate as a device value snapped to the gui-scaled grid, i.e. to a
     * multiple of {@code guiScale} device pixels.
     *
     * <p>Every scissor bound must be snapped this way, because {@code
     * ScreenRectangle.transformAxisAligned} <b>floors the size</b> as well as the position:
     * <pre>
     *   Vector2f a = pose.transformPosition(left(), top());
     *   Vector2f b = pose.transformPosition(right(), bottom());
     *   new ScreenRectangle(floor(a.x), floor(a.y), floor(b.x - a.x), floor(b.y - a.y));
     * </pre>
     * So a rect whose size is not a whole number of gui-scaled units loses up to {@code guiScale - 1}
     * device pixels off its bottom (and right) edge. With guiScale 3 the clip ended 2px above the
     * footer strip - which is exactly the "card mask has a 2px gap above the bottom bar" report.
     * Snapping every bound makes both the position and the size floor exactly.
     */
    public static int scissorRow(int designValue) {
        float step = deviceStep();
        float guiScale = ClickGuiFonts.guiScale();
        return Math.round(Math.round(designValue * step / guiScale) * guiScale);
    }

    /**
     * A scissor bound snapped to the gui-scaled grid, but rounded <b>outwards</b> (floor): used for
     * the top/left/right edges so the clip can never eat into the content.
     *
     * <p>Rounding to the nearest grid row instead moves the top edge *down* half the time, which
     * slices the first pixel rows off whatever sits highest - observed as "the top border of the
     * topmost button is thinner than its other borders" on both settings pages (the first widget
     * is laid out exactly at the content top, so the clip was cutting into it). The bottom edge is
     * different: it is pinned with {@link #scissorRow} to the row the footer strip is drawn on, so
     * both land on exactly the same device row and neither gaps nor overlaps.
     */
    public static int scissorFloor(int designValue) {
        float step = deviceStep();
        float guiScale = ClickGuiFonts.guiScale();
        return Math.round((float) (Math.floor(designValue * step / guiScale) * guiScale));
    }

    /**
     * Device size of a design-space span, rounded from <em>both</em> of its edges.
     *
     * <p>Rounding the offset and the size independently is what leaves two pieces that share
     * an edge one pixel apart: a caller passing a full-width strip gets
     * {@code round(x) + round(w)}, while the piece next to it starts at {@code round(x + w)},
     * and the two differ by one whenever {@code x * step} is fractionary - which is the usual
     * case, since the panel scale is deliberately not an integer. That is exactly how the body
     * gradient ended up 1px short of the tab strips on the right edge. Rounding both edges and
     * deriving the size from the difference makes shared edges land on the same device pixel
     * row/column exactly, so adjacent pieces neither gap nor overlap.
     */
    private static int snapSize(int x, int w, float step) {
        int x0 = Math.round(x * step);
        int x1 = Math.round((x + w) * step);
        return Math.max(1, x1 - x0);
    }

    /**
     * Fills a design-space rectangle whose bottom edge is pinned to an exact device row (the one
     * the content scissor lands on); the top and the horizontal edges snap the usual way. Used by
     * the sidebar pieces, which have to end on the same row as the footer strip.
     */
    public static void fillDeviceToRow(GuiGraphicsExtractor g, int x, int y, int w, int bottomRow, int color) {
        float step = deviceStep();
        int dx = Math.round(x * step);
        int dy = Math.round(y * step);
        int dw = snapSize(x, w, step);
        if (dw <= 0 || bottomRow <= dy) {
            return;
        }
        pushDeviceSpace(g);
        g.fill(dx, dy, dx + dw, bottomRow, color);
        popDeviceSpace(g);
    }

    // The panel body used to be a vertical gradient drawn by a helper here; the gradient was removed
    // (it read as uneven lighting next to the flat cards), so the helper went with it.

    /**
     * Bottom-anchored rounded rect (the footer strip) whose top edge is pinned to an exact device
     * row, so it meets the content scissor exactly.
     */
    public static void fillRoundedBottomToRow(GuiGraphicsExtractor g, int x, int w, int r, int color,
                                              int topRow, int bottomDesign) {
        float step = deviceStep();
        int dx = Math.round(x * step);
        int dw = snapSize(x, w, step);
        int dh = Math.round(bottomDesign * step) - topRow;
        if (dw <= 0 || dh <= 0) {
            return;
        }
        pushDeviceSpace(g);
        fillRoundedDevice(g, dx, topRow, dw, dh, Math.round(r * step), color, false, true);
        popDeviceSpace(g);
    }

    /** Rounded rect (all four corners). */
    public static void fillRoundedRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        fillRoundedBlit(g, x, y, w, h, r, color, true, true);
    }

    /** Rounded top corners with a straight bottom edge (for the main-tab strip). */
    public static void fillRoundedTopRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        fillRoundedBlit(g, x, y, w, h, r, color, true, false);
    }

    /** Straight top edge with rounded bottom corners (for the panel body). */
    public static void fillRoundedBottomRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        fillRoundedBlit(g, x, y, w, h, r, color, false, true);
    }

    /**
     * Rounded rect with a border of <em>uniform</em> width: the ring is laid out in device
     * pixels, so all four sides and the arc get the same thickness.
     *
     * <p>Why not the obvious "outer rounded rect, then the same rect inset by one design unit":
     * one design unit is {@code step} device pixels (≈2-4), and each inner edge is snapped on
     * its own, so per side the ring comes out {@code floor(step)} or {@code ceil(step)} and the
     * four sides need not agree - a button whose right border is visibly thinner than its left,
     * and the same in the other directions. Here the outer rect is snapped once, the inner rect
     * is that same rect inset by a whole number of device pixels, and the radius is reduced by
     * the same amount, so the ring is even by construction (including around the corners).
     */
    public static void fillRoundedBorderedRect(GuiGraphicsExtractor g, int x, int y, int w, int h,
                                               int r, int fill, int border) {
        float step = deviceStep();
        int dx = Math.round(x * step);
        int dy = Math.round(y * step);
        int dw = snapSize(x, w, step);
        int dh = snapSize(y, h, step);
        int rr = Math.max(1, Math.round(r * step));
        // One design unit, rounded to the nearest whole device pixel: a uniform ring can only
        // be an integer number of device pixels, so this is both "1 design unit" and even.
        int inset = Math.max(1, Math.round(step));
        pushDeviceSpace(g);
        fillRoundedDevice(g, dx, dy, dw, dh, rr, border, true, true);
        if (dw > 2 * inset && dh > 2 * inset) {
            fillRoundedDevice(g, dx + inset, dy + inset, dw - 2 * inset, dh - 2 * inset,
                    Math.max(1, rr - inset), fill, true, true);
        }
        popDeviceSpace(g);
    }

    /** Rounded fill using per-row corner rasterization instead of the shared corner mask. */
    public static void fillRoundedRectRows(GuiGraphicsExtractor g, int x, int y, int w, int h,
                                           int r, int color) {
        float step = deviceStep();
        int dx = Math.round(x * step);
        int dy = Math.round(y * step);
        int dw = snapSize(x, w, step);
        int dh = snapSize(y, h, step);
        int rr = Math.min(Math.max(0, Math.round(r * step)), Math.max(0, Math.min(dw, dh) / 2 - 1));
        if (dw <= 0 || dh <= 0) {
            return;
        }
        pushDeviceSpace(g);
        if (rr < 1) {
            g.fill(dx, dy, dx + dw, dy + dh, color);
        } else {
            g.fill(dx + rr, dy, dx + dw - rr, dy + dh, color);
            g.fill(dx, dy + rr, dx + rr, dy + dh - rr, color);
            g.fill(dx + dw - rr, dy + rr, dx + dw, dy + dh - rr, color);
            for (int row = 0; row < rr; row++) {
                int inset = (int) Math.floor(arcInset(rr, row));
                cornerRowLeft(g, dx + inset, dx + rr, dy + row, color);
                cornerRowRight(g, dx + dw - rr, dx + dw - inset, dy + row, color);
                int bottom = dy + dh - row - 1;
                cornerRowLeft(g, dx + inset, dx + rr, bottom, color);
                cornerRowRight(g, dx + dw - rr, dx + dw - inset, bottom, color);
            }
        }
        popDeviceSpace(g);
    }

    /**
     * Rounded fill plus a fixed-width device-space ring. The outer rect is snapped once and the
     * inner fill is derived from that same rect, so all four border sides share one thickness.
     */
    public static void fillRoundedBorderedRectFixed(GuiGraphicsExtractor g, int x, int y, int w,
                                                    int h, int r, int fill, int border,
                                                    int thicknessDevice) {
        float step = deviceStep();
        int dx = Math.round(x * step);
        int dy = Math.round(y * step);
        int dw = snapSize(x, w, step);
        int dh = snapSize(y, h, step);
        int rr = Math.max(1, Math.round(r * step));
        int inset = Math.max(1, thicknessDevice);
        if (dw <= 2 * inset || dh <= 2 * inset) {
            pushDeviceSpace(g);
            fillRoundedDevice(g, dx, dy, dw, dh, rr, border, true, true);
            popDeviceSpace(g);
            return;
        }
        pushDeviceSpace(g);
        fillRoundedDevice(g, dx, dy, dw, dh, rr, border, true, true);
        fillRoundedDevice(g, dx + inset, dy + inset, dw - 2 * inset, dh - 2 * inset,
                Math.max(1, rr - inset), fill, true, true);
        popDeviceSpace(g);
    }

    /** A standalone two-device-pixel rounded outline; it does not cover the interior. */
    public static void strokeRoundedRect(GuiGraphicsExtractor g, int x, int y, int w, int h,
                                         int r, int color) {
        float step = deviceStep();
        int dx = Math.round(x * step);
        int dy = Math.round(y * step);
        int dw = snapSize(x, w, step);
        int dh = snapSize(y, h, step);
        int rr = Math.min(Math.max(0, Math.round(r * step)), Math.max(0, Math.min(dw, dh) / 2 - 2));
        if (dw <= 0 || dh <= 0) {
            return;
        }
        pushDeviceSpace(g);
        int left = dx;
        int top = dy;
        int right = dx + dw;
        int bottom = dy + dh;
        int thickness = 2;
        if (rr < 1) {
            g.fill(left, top, right, top + thickness, color);
            g.fill(left, bottom - thickness, right, bottom, color);
            g.fill(left, top, left + thickness, bottom, color);
            g.fill(right - thickness, top, right, bottom, color);
        } else {
            int topInset = (int) Math.floor(arcInset(rr, 0));
            g.fill(left + topInset, top, right - topInset, top + thickness, color);
            g.fill(left + topInset, bottom - thickness, right - topInset, bottom, color);
            g.fill(left, top + rr, left + thickness, bottom - rr, color);
            g.fill(right - thickness, top + rr, right, bottom - rr, color);
            for (int row = 0; row < rr; row++) {
                int inset = (int) Math.floor(arcInset(rr, row));
                g.fill(left + inset, top + row, left + inset + thickness, top + row + 1,
                        color);
                g.fill(right - inset - thickness, top + row, right - inset, top + row + 1,
                        color);
                g.fill(left + inset, bottom - row - 1, left + inset + thickness, bottom - row,
                        color);
                g.fill(right - inset - thickness, bottom - row - 1, right - inset,
                        bottom - row, color);
            }
        }
        popDeviceSpace(g);
    }

    /** Snaps a design-space rounded rect to the device grid and fills it. */
    private static void fillRoundedBlit(GuiGraphicsExtractor g, int x, int y, int w, int h,
                                        int r, int color, boolean roundTop, boolean roundBottom) {
        float step = deviceStep();
        int dx = Math.round(x * step);
        int dy = Math.round(y * step);
        int dw = snapSize(x, w, step);
        int dh = snapSize(y, h, step);
        int rr = Math.round(r * step);
        pushDeviceSpace(g);
        fillRoundedDevice(g, dx, dy, dw, dh, rr, color, roundTop, roundBottom);
        popDeviceSpace(g);
    }

    /**
     * Device-space rounded rect: all arguments are device pixels and the device space must
     * already be pushed. Uses a 9-slice of the pre-baked anti-aliased corner mask (four 1:1
     * corner blits plus three straight fills) and falls back to the row-by-row version when no
     * mask can be built. The mask path is what the panel uses - the row version costs up to ~200
     * GUI elements per rounded rect, so do not make it the default.
     *
     * <p>Pieces stay disjoint (a translucent color is never blended twice) and the mask's
     * straight edge column/row is what the fills butt against exactly.
     */
    private static void fillRoundedDevice(GuiGraphicsExtractor g, int dx, int dy, int dw, int dh,
                                          int rr, int color, boolean roundTop, boolean roundBottom) {
        // A mask spans radius + 1 px, and the two corners of a rounded side must not overlap:
        // shrink the radius (never the rect) when the shape is too small.
        int maxR = dw / 2 - 1;
        if (roundTop && roundBottom) {
            maxR = Math.min(maxR, dh / 2 - 1);
        } else if (roundTop || roundBottom) {
            maxR = Math.min(maxR, dh - 1);
        }
        rr = Math.min(Math.max(0, rr), maxR);
        if (rr < 1) {
            g.fill(dx, dy, dx + dw, dy + dh, color);
            return;
        }
        GuiCornerTextures.Mask mask = GuiCornerTextures.get(rr);
        if (mask != null) {
            int s = mask.quadrant();
            int texture = mask.textureSize();
            int bandTop = roundTop ? dy + s : dy;
            int bandBottom = roundBottom ? dy + dh - s : dy + dh;
            if (bandBottom > bandTop) {
                g.fill(dx, bandTop, dx + s, bandBottom, color);
                g.fill(dx + dw - s, bandTop, dx + dw, bandBottom, color);
            }
            if (dw - 2 * s > 0) {
                g.fill(dx + s, dy, dx + dw - s, dy + dh, color);
            }
            if (roundTop) {
                blitCorner(g, mask, 0, 0, dx, dy, texture, color);
                blitCorner(g, mask, 1, 0, dx + dw - s, dy, texture, color);
            }
            if (roundBottom) {
                blitCorner(g, mask, 0, 1, dx, dy + dh - s, texture, color);
                blitCorner(g, mask, 1, 1, dx + dw - s, dy + dh - s, texture, color);
            }
            return;
        }
        // Fallback: row-by-row anti-aliasing, kept for when the corner mask cannot be baked.
        // One device pixel per row, and the boundary pixel the arc cuts gets its exact coverage
        // as alpha (arcInset samples the row centre), so the pieces still never overlap.
        g.fill(dx + rr, dy, dx + dw - rr, dy + dh, color);
        int bandTop = roundTop ? dy + rr : dy;
        int bandBottom = roundBottom ? dy + dh - rr : dy + dh;
        if (bandBottom > bandTop) {
            g.fill(dx, bandTop, dx + rr, bandBottom, color);
            g.fill(dx + dw - rr, bandTop, dx + dw, bandBottom, color);
        }
        for (int i = 0; i < rr; i++) {
            double inset = arcInset(rr, i);
            if (roundTop) {
                cornerRowLeft(g, dx + inset, dx + rr, dy + i, color);
                cornerRowRight(g, dx + dw - rr, dx + dw - inset, dy + i, color);
            }
            if (roundBottom) {
                int row = dy + dh - i - 1;
                cornerRowLeft(g, dx + inset, dx + rr, row, color);
                cornerRowRight(g, dx + dw - rr, dx + dw - inset, row, color);
            }
        }
    }

    /** One quadrant of the corner mask, drawn 1:1 (one texel = one device pixel). */
    private static void blitCorner(GuiGraphicsExtractor g, GuiCornerTextures.Mask mask,
                                   int col, int row, int x, int y, int size, int color) {
        int quadrant = mask.quadrant();
        g.blit(RenderPipelines.GUI_TEXTURED, mask.id(), x, y,
                (float) (col * quadrant), (float) (row * quadrant),
                quadrant, quadrant, size, size, color);
    }

    /**
     * Horizontal inset of the corner arc for row {@code i} (counted in device pixels
     * from the rounded edge), sampled at the row's centre so it can be fractional.
     */
    private static double arcInset(int rr, int i) {
        double dy = rr - (i + 0.5);
        if (dy < 0) {
            dy = 0;
        }
        return rr - Math.sqrt(Math.max(0.0, (double) rr * rr - dy * dy));
    }

    /** One corner row from a fractional left edge; only the left edge is softened. */
    private static void cornerRowLeft(GuiGraphicsExtractor g, double x0, int x1, int y, int color) {
        int start = (int) Math.floor(x0);
        double frac = x0 - start;
        if (frac > 0.002) {
            g.fill(start, y, start + 1, y + 1, scaleAlpha(color, (float) (1.0 - frac)));
            start++;
        }
        if (start < x1) {
            g.fill(start, y, x1, y + 1, color);
        }
    }

    /** One corner row up to a fractional right edge; only the right edge is softened. */
    private static void cornerRowRight(GuiGraphicsExtractor g, int x0, double x1, int y, int color) {
        int end = (int) Math.ceil(x1);
        double frac = end - x1;
        if (frac > 0.002) {
            g.fill(end - 1, y, end, y + 1, scaleAlpha(color, (float) (1.0 - frac)));
            end--;
        }
        if (x0 < end) {
            g.fill(x0, y, end, y + 1, color);
        }
    }

    /** The same colour with its alpha scaled, for a partially covered edge pixel. */
    private static int scaleAlpha(int argb, float factor) {
        float f = Math.max(0f, Math.min(1f, factor));
        int a = Math.round(((argb >>> 24) & 0xFF) * f);
        return (a << 24) | (argb & 0x00FFFFFF);
    }
}
