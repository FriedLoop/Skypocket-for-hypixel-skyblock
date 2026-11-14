package dev.skypocket.ui.widget;

import dev.skypocket.config.ModConfig;
import dev.skypocket.render.ClickGuiMetrics;
import dev.skypocket.render.CrosshairRenderer;
import dev.skypocket.ui.Rect;
import dev.skypocket.ui.Style;
import dev.skypocket.ui.UiPainter;
import dev.skypocket.ui.mc.McUiPainter;

/**
 * Live crosshair preview for the v2 settings page.
 *
 * <p>Every input it reads is a panel-cache input: colours and size go through {@code Setting}
 * setters (which call {@code ConfigManager.touch()}), and cell edits call it directly from
 * {@code CrosshairGrid}. It therefore does not need to bypass the cache, and overriding
 * {@code dynamicOverlay()} here was what repainted the checkerboard and the crosshair every frame.
 */
public final class CrosshairPreview extends Control {
    private final ModConfig cfg;

    public CrosshairPreview(ModConfig cfg, Style style) {
        this(cfg, style, WidgetPalette.DARK);
    }

    public CrosshairPreview(ModConfig cfg, Style style, WidgetPalette palette) {
        super(style, palette);
        this.cfg = cfg;
    }

    @Override
    public boolean focusable() {
        return false;
    }

    @Override
    protected void activate() {
        // Preview only.
    }

    @Override
    public void paint(UiPainter painter) {
        int x = Math.round(bounds().x());
        int y = Math.round(bounds().y());
        int w = Math.round(bounds().width());
        int h = Math.round(bounds().height());
        // Checkerboard cell. It used to be 8, which was fine in the old 136-tall letterbox but
        // produces a 36x36 mosaic in the square preview; 16 keeps the same visual density (about
        // 18 cells across) and quarters the number of fills.
        int cell = 16;
        for (int cy = 0; y + cy * cell < y + h; cy++) {
            for (int cx = 0; x + cx * cell < x + w; cx++) {
                int bg = ((cx + cy) & 1) == 0 ? palette.background() : palette.hover();
                painter.fill(new Rect(x + cx * cell, y + cy * cell,
                                Math.min(cell, x + w - (x + cx * cell)),
                                Math.min(cell, y + h - (y + cy * cell))), bg);
            }
        }
        int border = palette.outline();
        painter.fill(new Rect(x, y, w, 1), border);
        painter.fill(new Rect(x, y + h - 1, w, 1), border);
        painter.fill(new Rect(x, y, 1, h), border);
        painter.fill(new Rect(x + w - 1, y, 1, h), border);
        if (painter instanceof McUiPainter mc) {
            // Draw at the size the crosshair actually occupies on screen, then shrink only if it
            // would not fit.
            //
            // Two conversions are involved and they pull in opposite directions:
            //  - the game draws the real crosshair in GUI units, at a size of GRID * pixel * scale;
            //  - this preview draws in panel design units, and the whole panel is then scaled by
            //    ClickGuiMetrics.effectiveScale() when it is blitted to the screen.
            // So a crosshair drawn here at GRID * pixel * scale design units reaches the screen at
            // effectiveScale times that - which is why the preview looked smaller than the real
            // thing by roughly the panel scale (about 0.5 to 0.7 depending on resolution). Dividing
            // by the panel scale makes the previewed crosshair cover the same fraction of the screen
            // as the real one.
            int pixel = Math.max(1, Math.round(cfg.crosshairPixelSize));
            float scale = Math.max(0.25f, cfg.crosshairScale);
            float panelScale = Math.max(0.05f, ClickGuiMetrics.effectiveScale());

            // Leave a 2-unit inset so the outer ring of cells is not flush against the border.
            float box = Math.max(1f, Math.min(w, h) - 4f);
            int naturalSize = CrosshairRenderer.sizeFor(pixel, scale / panelScale);
            float fit = naturalSize <= box ? 1f : box / naturalSize;

            CrosshairRenderer.render(mc.graphics(), x + w / 2, y + h / 2, pixel,
                    (scale / panelScale) * fit,
                    cfg.crosshairGrid, cfg.getCrosshairColorARGB(), cfg.crosshairStroke,
                    cfg.crosshairStrokeWidth);
        }
    }
}
