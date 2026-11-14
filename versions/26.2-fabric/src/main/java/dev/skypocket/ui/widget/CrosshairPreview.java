package dev.skypocket.ui.widget;

import dev.skypocket.config.ModConfig;
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
            // Draw at the real size first, then shrink only if it would not fit.
            //
            // The previous version clamped pixel to 1..3 and scale to 0.25..2, which were not the
            // configurable ranges (1..3 and 0.25..4): a scaled-up crosshair was silently shown
            // smaller than it really is. Real proportions matter more here than filling the box, so
            // the clamp is gone and an oversized crosshair is scaled down to fit instead - that keeps
            // the shape comparable across presets while never drawing outside the frame.
            int pixel = Math.max(1, Math.round(cfg.crosshairPixelSize));
            float scale = Math.max(0.25f, cfg.crosshairScale);

            // Leave a 2-unit inset so the outer ring of cells is not flush against the border.
            float box = Math.max(1f, Math.min(w, h) - 4f);
            int naturalSize = CrosshairRenderer.sizeFor(pixel, scale);
            float fit = naturalSize <= box ? 1f : box / naturalSize;

            CrosshairRenderer.render(mc.graphics(), x + w / 2, y + h / 2, pixel, scale * fit,
                    cfg.crosshairGrid, cfg.getCrosshairColorARGB(), cfg.crosshairStroke,
                    cfg.crosshairStrokeWidth);
        }
    }
}
