package dev.skypocket.gui.widget;

import dev.skypocket.config.ModConfig;
import dev.skypocket.render.CrosshairRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Live preview of the custom crosshair as it appears in-game, centered in the
 * widget. Pixel size and scale are clamped so the preview always fits.
 */
public class CrosshairPreviewWidget extends BaseWidget {
    private final ModConfig cfg;

    public CrosshairPreviewWidget(int x, int y, int width, int height, ModConfig cfg) {
        super(x, y, width, height);
        this.cfg = cfg;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // Checkerboard background (the "transparent" convention) so the crosshair
        // stays visible against any color, including black and white.
        int cell = 8;
        int xEnd = getX() + width;
        int yEnd = getY() + height;
        for (int cy = 0; getY() + cy * cell < yEnd; cy++) {
            for (int cx = 0; getX() + cx * cell < xEnd; cx++) {
                int bg = ((cx + cy) & 1) == 0 ? 0xFF333333 : 0xFF4C4C4C;
                graphics.fill(getX() + cx * cell, getY() + cy * cell,
                        Math.min(getX() + (cx + 1) * cell, xEnd),
                        Math.min(getY() + (cy + 1) * cell, yEnd), bg);
            }
        }
        graphics.outline(getX(), getY(), width, height, 0xFF333333);

        int pixel = Math.max(1, Math.min(3, Math.round(cfg.crosshairPixelSize)));
        float scale = Math.max(0.25f, Math.min(cfg.crosshairScale, 2.0f));
        CrosshairRenderer.render(graphics, getX() + width / 2, getY() + height / 2, pixel, scale,
                cfg.crosshairGrid, cfg.getCrosshairColorARGB(), cfg.crosshairStroke, cfg.crosshairStrokeWidth);
    }
}
