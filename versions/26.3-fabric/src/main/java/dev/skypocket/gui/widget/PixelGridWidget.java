package dev.skypocket.gui.widget;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.function.IntSupplier;

/**
 * Click-to-paint pixel grid (15x15) used as the crosshair editor.
 *
 * <p>Cells are drawn with a fixed 1px gap so every pixel stays crisply aligned
 * (no overlapping grid lines). Click toggles a cell; dragging continues painting
 * with the value of the first clicked cell.
 */
public class PixelGridWidget extends BaseWidget {
    private static final int GAP = 1;

    private final int cells;
    private final int cellSize;
    private final boolean[] grid;
    private final IntSupplier colorSupplier;
    private boolean paintValue = true;

    public PixelGridWidget(int x, int y, int cells, int cellSize, boolean[] grid, IntSupplier colorSupplier) {
        super(x, y, cells * (cellSize + GAP) + 1, cells * (cellSize + GAP) + 1);
        this.cells = cells;
        this.cellSize = cellSize;
        this.grid = grid;
        this.colorSupplier = colorSupplier;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int color = colorSupplier.getAsInt();

        graphics.fill(getX(), getY(), getX() + width, getY() + height, 0xFF000000);

        for (int cy = 0; cy < cells; cy++) {
            for (int cx = 0; cx < cells; cx++) {
                int px = getX() + 1 + cx * (cellSize + GAP);
                int py = getY() + 1 + cy * (cellSize + GAP);
                int cellColor = grid[cy * cells + cx] ? color : 0xFF3A3A3A;
                graphics.fill(px, py, px + cellSize, py + cellSize, cellColor);
            }
        }
    }

    private int cellAt(double mouseX, double mouseY) {
        int relX = (int) Math.floor(mouseX - getX() - 1);
        int relY = (int) Math.floor(mouseY - getY() - 1);
        int cx = relX / (cellSize + GAP);
        int cy = relY / (cellSize + GAP);
        if (cx < 0 || cx >= cells || cy < 0 || cy >= cells) {
            return -1;
        }
        // ignore clicks that land exactly in the 1px gap
        if (relX - cx * (cellSize + GAP) >= cellSize || relY - cy * (cellSize + GAP) >= cellSize) {
            return -1;
        }
        return cy * cells + cx;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubled) {
        if (event.button() != 0) {
            return;
        }
        int index = cellAt(event.x(), event.y());
        if (index >= 0) {
            paintValue = !grid[index];
            grid[index] = paintValue;
        }
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double deltaX, double deltaY) {
        if (event.button() != 0) {
            return;
        }
        int index = cellAt(event.x(), event.y());
        if (index >= 0) {
            grid[index] = paintValue;
        }
    }
}
