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

    /** Small set: footer settings button and sidebar rows. */
    static final int SMALL_CELL = 128;
    static final int SMALL_COLUMNS = 6;
    static final int SMALL_ROWS = 2;

    /** Large set: module cards. */
    static final int LARGE_CELL = 192;
    static final int LARGE_COLUMNS = 8;
    static final int LARGE_ROWS = 5;

    static int smallWidth() {
        return SMALL_COLUMNS * SMALL_CELL;
    }

    static int smallHeight() {
        return SMALL_ROWS * SMALL_CELL;
    }

    static int largeWidth() {
        return LARGE_COLUMNS * LARGE_CELL;
    }

    static int largeHeight() {
        return LARGE_ROWS * LARGE_CELL;
    }
}
