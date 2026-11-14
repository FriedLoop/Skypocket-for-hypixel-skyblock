package dev.skypocket.crosshair;

import dev.skypocket.config.ModConfig;

/**
 * One saved crosshair: the drawn grid plus every parameter that shapes it.
 *
 * <p>A value object, deliberately separate from {@link ModConfig}: the config holds the crosshair
 * currently in use, while a preset is a snapshot that can be applied to it later. Keeping them apart
 * means an edit to the live crosshair never silently changes a saved preset, which is the behaviour
 * a "load preset" button has to be able to offer.
 *
 * <p>The grid is stored as {@code boolean[]} of {@code GRID * GRID} entries, row-major, matching how
 * {@code ModConfig.crosshairGrid} already works so applying a preset is a plain copy.
 *
 * @param name        file name without extension; also what the picker shows
 * @param grid        row-major cells, {@code GRID * GRID} entries
 * @param pixelSize   cell edge in design units
 * @param scale       overall scale multiplier
 * @param stroke      whether cells are outlined
 * @param strokeWidth outline thickness in design units
 * @param color       packed ARGB tint
 */
public record CrosshairPreset(String name, boolean[] grid, float pixelSize, float scale,
                              boolean stroke, float strokeWidth, int color) {

    /**
     * Copies the live crosshair settings into a preset.
     *
     * <p>The grid is cloned: the config array is mutated in place by the editor, so keeping a
     * reference would make every saved preset follow later edits.
     */
    public static CrosshairPreset fromConfig(String name, ModConfig cfg) {
        return new CrosshairPreset(name, cfg.crosshairGrid.clone(), cfg.crosshairPixelSize,
                cfg.crosshairScale, cfg.crosshairStroke, cfg.crosshairStrokeWidth,
                cfg.getCrosshairColorARGB());
    }

    /**
     * Writes this preset into the live config.
     *
     * <p>Grid length is checked before copying: a preset file written by a different grid size (or a
     * hand-edited one) must not leave the config with an array the renderer cannot index.
     *
     * @return whether the grid was compatible and got applied
     */
    /**
     * Writes this preset into the live config.
     *
     * <p>The grid length is checked <b>before</b> anything is written: a preset file written for a
     * different grid size (or hand-edited) must not leave the config half-updated - the shape would
     * be the old one while the colour and scale silently came from the rejected preset.
     *
     * @return {@code false} when the grid does not match {@code ModConfig.GRID}; nothing was applied
     */
    public boolean applyTo(ModConfig cfg) {
        if (grid == null || grid.length != ModConfig.GRID * ModConfig.GRID) {
            return false;
        }
        cfg.crosshairPixelSize = pixelSize;
        cfg.crosshairScale = scale;
        cfg.crosshairStroke = stroke;
        cfg.crosshairStrokeWidth = strokeWidth;
        cfg.crosshairRed = (color >> 16) & 0xFF;
        cfg.crosshairGreen = (color >> 8) & 0xFF;
        cfg.crosshairBlue = color & 0xFF;
        cfg.crosshairAlpha = (color >>> 24) & 0xFF;
        System.arraycopy(grid, 0, cfg.crosshairGrid, 0, grid.length);
        return true;
    }

    /** True when the name would be rejected as a file name or collides with a built-in. */
    public boolean hasUsableName() {
        return name != null && !name.isBlank() && !name.contains("\\") && !name.contains("/");
    }
}
