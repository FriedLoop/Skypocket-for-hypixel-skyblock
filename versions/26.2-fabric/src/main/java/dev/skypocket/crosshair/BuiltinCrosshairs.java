package dev.skypocket.crosshair;

import dev.skypocket.config.ModConfig;

import java.util.List;

/**
 * The crosshairs that ship with the mod.
 *
 * <p>Built-in presets are code, not files, for the same reason {@code ThemeStore}'s Dark and Light
 * are: they cannot go missing from the config directory, and deleting them is meaningless, so
 * {@code CrosshairStore.delete} refuses their names instead of letting a stray click remove them.
 *
 * <p>All of them use white, no stroke, a 1-unit cell and scale 1, so switching to a built-in gives a
 * predictable starting point rather than inheriting whatever the last preset used.
 */
public final class BuiltinCrosshairs {

    /** The classic plus: six cells long and six tall, with two-cell-thick arms. */
    public static final String CROSS = "经典十字";
    /** A hollow square four cells on a side. */
    public static final String SQUARE = "空心方块";

    private BuiltinCrosshairs() {
    }

    /** Names of every built-in, in the order the picker should show them. */
    public static List<String> names() {
        return List.of(CROSS, SQUARE);
    }

    /** Whether {@code name} is a built-in (case-insensitive, like the theme store). */
    public static boolean isBuiltin(String name) {
        if (name == null) {
            return false;
        }
        for (String builtin : names()) {
            if (builtin.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The preset with this name, built on demand, or {@code null} when it is not a built-in.
     *
     * <p>Built lazily rather than held in a static field: the grid depends on {@code ModConfig.GRID},
     * and a static array would be shared with every caller and could be mutated through the record's
     * accessor.
     */
    public static CrosshairPreset byName(String name) {
        if (name == null) {
            return null;
        }
        if (name.equalsIgnoreCase(CROSS)) {
            return new CrosshairPreset(CROSS, plus(2), 1f, 1f, false, 1f, WHITE);
        }
        if (name.equalsIgnoreCase(SQUARE)) {
            return new CrosshairPreset(SQUARE, hollowSquare(4), 1f, 1f, false, 1f, WHITE);
        }
        return null;
    }

    /** Opaque white, the colour every built-in starts from. */
    private static final int WHITE = 0xFFFFFFFF;

    /**
     * A plus with two-cell-thick arms reaching {@code armCells} cells from the middle.
     *
     * <p>{@code armCells = 2} produces the built-in preset: arms spanning
     * {@code [n/2 - 2, n/2 + 1]}, which is six cells - two either side of the two-cell centre band.
     * The total is even on purpose: an odd span cannot be centred on an even grid, and that half-cell
     * bias is exactly what moving the grid to 16 cells was meant to remove.
     */
    static boolean[] plus(int armCells) {
        int n = ModConfig.GRID;
        boolean[] grid = new boolean[n * n];
        int lo = n / 2 - 1;
        int hi = n / 2;
        int from = Math.max(0, lo - armCells);
        int to = Math.min(n - 1, hi + armCells);
        for (int i = from; i <= to; i++) {
            grid[lo * n + i] = true;
            grid[hi * n + i] = true;
            grid[i * n + lo] = true;
            grid[i * n + hi] = true;
        }
        return grid;
    }

    /**
     * A hollow square {@code side} cells on a side, centred on the grid.
     *
     * <p>Only the outline is set, so the inside stays empty - that is what makes it read as a frame
     * rather than a block.
     */
    static boolean[] hollowSquare(int side) {
        int n = ModConfig.GRID;
        boolean[] grid = new boolean[n * n];
        // Centre the square; an odd side cannot be centred exactly on an even grid, so the extra cell
        // goes to the right/bottom (matching the half-cell bias the even grid itself avoids).
        int left = n / 2 - side / 2;
        int top = n / 2 - side / 2;
        for (int i = 0; i < side; i++) {
            int x0 = left + i;
            int y0 = top + i;
            if (x0 < 0 || x0 >= n || y0 < 0 || y0 >= n) {
                continue;
            }
            grid[top * n + x0] = true;
            grid[(top + side - 1) * n + x0] = true;
            grid[y0 * n + left] = true;
            grid[y0 * n + (left + side - 1)] = true;
        }
        return grid;
    }
}
