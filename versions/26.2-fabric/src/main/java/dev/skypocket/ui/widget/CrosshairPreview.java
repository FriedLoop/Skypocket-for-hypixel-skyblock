package dev.skypocket.ui.widget;

import dev.skypocket.config.ModConfig;
import dev.skypocket.render.CrosshairRenderer;
import dev.skypocket.ui.Rect;
import dev.skypocket.ui.Style;
import dev.skypocket.ui.UiPainter;
import dev.skypocket.ui.mc.McUiPainter;

/** Live crosshair preview for the v2 settings page. */
public final class CrosshairPreview extends Control {
    private final ModConfig cfg;

    public CrosshairPreview(ModConfig cfg, Style style) {
        super(style, WidgetPalette.DARK);
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
    public boolean dynamicOverlay() {
        // The preview follows colours/size even when no control is focused.
        return true;
    }

    @Override
    public void paint(UiPainter painter) {
        int x = Math.round(bounds().x());
        int y = Math.round(bounds().y());
        int w = Math.round(bounds().width());
        int h = Math.round(bounds().height());
        int cell = 8;
        for (int cy = 0; y + cy * cell < y + h; cy++) {
            for (int cx = 0; x + cx * cell < x + w; cx++) {
                int bg = ((cx + cy) & 1) == 0 ? 0xFF333333 : 0xFF4C4C4C;
                painter.fill(new Rect(x + cx * cell, y + cy * cell,
                                Math.min(cell, x + w - (x + cx * cell)),
                                Math.min(cell, y + h - (y + cy * cell))), bg);
            }
        }
        painter.fill(new Rect(x, y, w, 1), 0xFF333333);
        painter.fill(new Rect(x, y + h - 1, w, 1), 0xFF333333);
        painter.fill(new Rect(x, y, 1, h), 0xFF333333);
        painter.fill(new Rect(x + w - 1, y, 1, h), 0xFF333333);
        if (painter instanceof McUiPainter mc) {
            int pixel = Math.max(1, Math.min(3, Math.round(cfg.crosshairPixelSize)));
            float scale = Math.max(0.25f, Math.min(cfg.crosshairScale, 2.0f));
            CrosshairRenderer.render(mc.graphics(), x + w / 2, y + h / 2, pixel, scale,
                    cfg.crosshairGrid, cfg.getCrosshairColorARGB(), cfg.crosshairStroke,
                    cfg.crosshairStrokeWidth);
        }
    }
}
