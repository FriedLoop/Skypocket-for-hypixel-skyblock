package dev.skypocket.ui.widget;

import dev.skypocket.ui.Style;
import dev.skypocket.ui.UiPainter;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** A labelled on/off switch. */
public final class Switch extends Control {
    private final String label;
    private final BooleanSupplier value;
    private final Consumer<Boolean> onChange;

    public Switch(String label, BooleanSupplier value, Consumer<Boolean> onChange) {
        this(label, value, onChange, Style.builder().width(160f).height(20f).build(),
                WidgetPalette.DARK);
    }

    public Switch(String label, BooleanSupplier value, Consumer<Boolean> onChange, Style style,
                  WidgetPalette palette) {
        super(style, palette);
        this.label = Objects.requireNonNull(label);
        this.value = Objects.requireNonNull(value);
        this.onChange = Objects.requireNonNull(onChange);
    }

    @Override
    protected void activate() {
        onChange.accept(!value.getAsBoolean());
    }

    @Override
    public void paint(UiPainter painter) {
        painter.fill(bounds(), background());
        paintFocus(painter);
        float trackWidth = 28f;
        float trackX = bounds().right() - trackWidth - 4f;
        float trackY = bounds().y() + (bounds().height() - 12f) / 2f;
        int trackColor = value.getAsBoolean() ? palette.accent() : palette.outline();
        painter.fill(new dev.skypocket.ui.Rect(trackX, trackY, trackWidth, 12f), trackColor);
        float knobX = value.getAsBoolean() ? trackX + trackWidth - 10f : trackX + 2f;
        painter.fill(new dev.skypocket.ui.Rect(knobX, trackY + 2f, 8f, 8f), palette.text());
        painter.text(label, bounds().x() + 6f,
                bounds().y() + (bounds().height() - painter.lineHeight()) / 2f, palette.text());
    }
}
