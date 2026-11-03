package dev.skypocket.ui.widget;

import dev.skypocket.ui.Style;
import dev.skypocket.ui.UiPainter;

import java.util.Objects;
import java.util.function.Supplier;

/** A focusable push button with a dynamic label. */
public final class Button extends Control {
    private final Supplier<String> label;
    private final Runnable action;

    public Button(String label, Runnable action) {
        this(() -> label, action, Style.builder().width(100f).height(24f).build(),
                WidgetPalette.DARK);
    }

    public Button(String label, Runnable action, Style style, WidgetPalette palette) {
        this(() -> label, action, style, palette);
    }

    public Button(Supplier<String> label, Runnable action, Style style, WidgetPalette palette) {
        super(style, palette);
        this.label = Objects.requireNonNull(label);
        this.action = Objects.requireNonNull(action);
    }

    @Override
    protected void activate() {
        action.run();
    }

    @Override
    public void paint(UiPainter painter) {
        painter.fill(bounds(), background());
        paintFocus(painter);
        String text = label.get();
        float x = bounds().x() + (bounds().width() - painter.textWidth(text)) / 2f;
        float y = bounds().y() + (bounds().height() - painter.lineHeight()) / 2f;
        painter.text(text, x, y, palette.text());
    }
}
