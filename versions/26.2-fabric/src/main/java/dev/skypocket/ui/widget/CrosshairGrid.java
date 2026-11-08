package dev.skypocket.ui.widget;

import dev.skypocket.config.ModConfig;
import dev.skypocket.ui.Rect;
import dev.skypocket.ui.Style;
import dev.skypocket.ui.UiPainter;
import dev.skypocket.ui.input.PointerEvent;

import java.util.function.IntSupplier;

/** Click-to-paint 15x15 crosshair grid for the v2 settings page. */
public final class CrosshairGrid extends Control {
    private static final int GAP = 1;
    private static final int CELL = 8;

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
    public boolean dynamicOverlay() {
        // Cell edits are written directly into the config array; the neutral page base is stale.
        return true;
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
