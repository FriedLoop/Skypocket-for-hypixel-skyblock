package dev.skypocket.ui.widget;

import dev.skypocket.ui.Style;
import dev.skypocket.ui.UiPainter;

import java.util.Objects;
import java.util.function.Supplier;

/** A focusable push button with a dynamic label. */
public final class Button extends Control {
    public enum SurfaceMode {
        CARD,
        MENU_ONLY,
        MENU_TOP,
        MENU_MIDDLE,
        MENU_BOTTOM
    }

    private final Supplier<String> label;
    private final Runnable action;
    private final SurfaceMode surfaceMode;

    public Button(String label, Runnable action) {
        this(() -> label, action, Style.builder().width(100f).height(24f).build(),
                WidgetPalette.DARK);
    }

    public Button(String label, Runnable action, Style style, WidgetPalette palette) {
        this(() -> label, action, style, palette);
    }

    public Button(Supplier<String> label, Runnable action, Style style, WidgetPalette palette) {
        this(label, action, style, palette, SurfaceMode.CARD);
    }

    public Button(String label, Runnable action, Style style, WidgetPalette palette,
                  SurfaceMode surfaceMode) {
        this(() -> label, action, style, palette, surfaceMode);
    }

    public Button(Supplier<String> label, Runnable action, Style style, WidgetPalette palette,
                  SurfaceMode surfaceMode) {
        super(style, palette);
        this.label = Objects.requireNonNull(label);
        this.action = Objects.requireNonNull(action);
        this.surfaceMode = Objects.requireNonNull(surfaceMode);
    }

    @Override
    protected void activate() {
        action.run();
    }

    @Override
    public void paint(UiPainter painter) {
        if (surfaceMode == SurfaceMode.CARD) {
            painter.surface(bounds(), palette.radius(), background(), 0);
            paintFocus(painter);
        } else {
            // Menu rows need a hover state; the normal card button deliberately keeps hover flat.
            int fill = hovered && !pressed && !neutralPaint() ? palette.hover() : background();
            if (surfaceMode == SurfaceMode.MENU_ONLY) {
                painter.fillRounded(bounds(), palette.radius(), fill);
            } else if (surfaceMode == SurfaceMode.MENU_TOP) {
                painter.fillRoundedTop(bounds(), palette.radius(), fill);
            } else if (surfaceMode == SurfaceMode.MENU_BOTTOM) {
                painter.fillRoundedBottom(bounds(), palette.radius(), fill);
            } else {
                painter.fill(bounds(), fill);
            }
            paintFocus(painter);
        }
        String text = label.get();
        float x = bounds().x() + (bounds().width() - painter.textWidth(text)) / 2f;
        float y = bounds().y() + (bounds().height() - painter.lineHeight()) / 2f;
        painter.text(text, x, y, palette.text());
    }
}
