package dev.skypocket.ui.widget;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;
import dev.skypocket.ui.Rect;
import dev.skypocket.ui.Style;
import dev.skypocket.ui.UiPainter;
import dev.skypocket.ui.input.PointerEvent;

import java.util.function.IntSupplier;

/**
 * Click-to-paint 15x15 crosshair grid for the v2 settings page.
 *
 * <p>Cell edits write into the config array and then call {@link ConfigManager#touch()}, which is
 * what makes the cached panel re-bake. The widget deliberately does <b>not</b> override
 * {@code dynamicOverlay()}: doing that bypasses the panel cache entirely and repaints all 225 cells
 * every frame, which is what made this page the most expensive one in the panel.
 */
public final class CrosshairGrid extends Control {
    private static final int GAP = 1;
    /**
     * Cell edge in design units.
     *
     * <p>Sized so the grid is readable at the row height the settings page now gives it: 15 cells of
     * 18 plus 14 gaps of 1 is 284, and {@link #preferredSize()} adds the 1-unit border on both sides
     * for 286. It is not a round 300 because a cell must stay an integer for the 1:1 pixel grid to
     * line up; the row centres the widget vertically instead of stretching it.
     */
    private static final int CELL = 18;

    private final boolean[] grid;
    private final IntSupplier color;
    private boolean paintValue = true;

    public CrosshairGrid(boolean[] grid, IntSupplier color, Style style) {
        this(grid, color, style, WidgetPalette.DARK);
    }

    public CrosshairGrid(boolean[] grid, IntSupplier color, Style style, WidgetPalette palette) {
        super(style, palette);
        this.grid = grid;
        this.color = color;
    }

    public static int preferredSize() {
        return ModConfig.GRID * (CELL + GAP) + 1;
    }

    @Override
    protected void activate() {
        // Cells are toggled by pointer events.
    }

    @Override
    public boolean focusable() {
        return false;
    }

    @Override
    public boolean onPointerDown(PointerEvent event) {
        pressed = true;
        int index = cellAt(event.x() - ancestorOffsetX(), event.y() - ancestorOffsetY());
        if (index >= 0) {
            paintValue = !grid[index];
            grid[index] = paintValue;
            // The panel is cached, so a cell edit only becomes visible if the cache key changes.
            // This used to be handled by overriding dynamicOverlay() and repainting the whole widget
            // every frame, which cost roughly 450 draws per frame for the 15x15 grid alone and took
            // the settings page from triple-digit to double-digit FPS. Telling the cache instead
            // costs one re-bake per edit.
            ConfigManager.touch();
        }
        return true;
    }

    @Override
    public boolean onPointerMove(PointerEvent event) {
        if (!pressed) {
            return false;
        }
        int index = cellAt(event.x() - ancestorOffsetX(), event.y() - ancestorOffsetY());
        if (index >= 0) {
            grid[index] = paintValue;
            ConfigManager.touch();
        }
        return true;
    }

    @Override
    public boolean onPointerUp(PointerEvent event) {
        pressed = false;
        return true;
    }

    @Override
    public void paint(UiPainter painter) {
        painter.fill(bounds(), palette.background());
        int tint = color.getAsInt();
        int originX = Math.round(bounds().x()) + 1;
        int originY = Math.round(bounds().y()) + 1;
        for (int cy = 0; cy < ModConfig.GRID; cy++) {
            for (int cx = 0; cx < ModConfig.GRID; cx++) {
                int px = originX + cx * (CELL + GAP);
                int py = originY + cy * (CELL + GAP);
                painter.fill(new Rect(px, py, CELL, CELL),
                        grid[cy * ModConfig.GRID + cx] ? tint : palette.outline());
            }
        }
    }

    private int cellAt(float mouseX, float mouseY) {
        int originX = Math.round(bounds().x()) + 1;
        int originY = Math.round(bounds().y()) + 1;
        int relX = (int) Math.floor(mouseX - originX);
        int relY = (int) Math.floor(mouseY - originY);
        int cx = relX / (CELL + GAP);
        int cy = relY / (CELL + GAP);
        if (cx < 0 || cx >= ModConfig.GRID || cy < 0 || cy >= ModConfig.GRID) {
            return -1;
        }
        if (relX - cx * (CELL + GAP) >= CELL || relY - cy * (CELL + GAP) >= CELL) {
            return -1;
        }
        return cy * ModConfig.GRID + cx;
    }
}
