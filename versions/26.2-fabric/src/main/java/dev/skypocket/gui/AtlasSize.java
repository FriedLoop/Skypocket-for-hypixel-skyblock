package dev.skypocket.gui;

/**
 * Dimensions of the icon atlases, in one place.
 *
 * <p>Every one of these has a counterpart in {@code tools/render-icons.mjs}, which asserts against
 * them and refuses to render when they disagree. That check is the point of this class: the numbers
 * cannot be derived from the enums, because the atlas height depends on how many rows the renderer
 * chose to fill, and a mismatch does not fail loudly - it samples the wrong part of the texture and
 * every icon comes out squashed or shifted. Two earlier attempts on this feature shipped exactly that
 * bug, once for each direction of the mismatch.
 *
 * <p>So the rule is: change a number here, change the matching one in the renderer, re-run it. The
 * script will tell you if you forgot.
 */
final class AtlasSize {
    private AtlasSize() {
    }

    /**
     * Transparent margin around each cell's icon, in pixels.
     *
     * <p>The icon is drawn at CELL and centred inside a (CELL + 2*PADDING) tile. Without the margin
     * the icon touches the tile edge, and any sub-pixel error in the sampled u/v samples a blank
     * pixel from the neighbour or from the tile's own edge - which showed up as transparent pinholes
     * at fixed positions inside the glyphs.
     */
    static final int PADDING = 4;

    /**
     * Small set: footer settings button and sidebar rows.
     *
     * <p>72 = 24 x 3. The icon is drawn at 24, and the cell is an integer multiple of that on
     * purpose. The game samples with nearest-neighbour and the panel scales, so a cell only slightly
     * larger than the target (74 for 62, ratio 1.19) makes some screen pixels skip a source pixel and
     * others take one twice - single-pixel pinholes and bumps at fixed positions on thin strokes. A
     * large integer ratio averages that away; the antialiasing it costs is a separate problem.
     */
    static final int SMALL_ICON = 72;
    static final int SMALL_COLUMNS = 6;
    static final int SMALL_ROWS = 2;

    /** Large set: module cards. 186 = 62 x 3, for the reason above. */
    static final int LARGE_ICON = 186;
    static final int LARGE_COLUMNS = 8;
    static final int LARGE_ROWS = 5;

    /** Tile size in the small atlas: the icon plus its margin. */
    static int smallCell() {
        return SMALL_ICON + PADDING * 2;
    }

    /** Tile size in the large atlas: the icon plus its margin. */
    static int largeCell() {
        return LARGE_ICON + PADDING * 2;
    }

    static int smallWidth() {
        return SMALL_COLUMNS * smallCell();
    }

    static int smallHeight() {
        return SMALL_ROWS * smallCell();
    }

    static int largeWidth() {
        return LARGE_COLUMNS * largeCell();
    }

    static int largeHeight() {
        return LARGE_ROWS * largeCell();
    }
}
