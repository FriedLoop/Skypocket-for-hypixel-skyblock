package dev.skypocket.ui.widget;

import dev.skypocket.ui.Rect;
import dev.skypocket.ui.Style;
import dev.skypocket.ui.UiPainter;
import dev.skypocket.ui.input.KeyEvent;
import dev.skypocket.ui.input.Keys;
import dev.skypocket.ui.input.PointerEvent;

import java.util.Objects;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/** A horizontal value slider with drag capture. */
public final class Slider extends Control {
    private final String label;
    private final double min;
    private final double max;
    private final DoubleSupplier value;
    private final DoubleConsumer onChange;
    private final String format;

    public Slider(String label, double min, double max, DoubleSupplier value,
                  DoubleConsumer onChange, String format) {
        this(label, min, max, value, onChange, format,
                Style.builder().width(200f).height(20f).build(), WidgetPalette.DARK);
    }

    public Slider(String label, double min, double max, DoubleSupplier value,
                  DoubleConsumer onChange, String format, Style style, WidgetPalette palette) {
        super(style, palette);
        this.label = Objects.requireNonNull(label);
        this.min = min;
        this.max = Math.max(min + 1.0e-6, max);
        this.value = Objects.requireNonNull(value);
        this.onChange = Objects.requireNonNull(onChange);
        this.format = format;
    }

    @Override
    protected void activate() {
        // Keyboard activation moves the value by one step; left/right is handled in onKeyDown.
    }

    @Override
    public boolean onPointerDown(PointerEvent event) {
        pressed = true;
        updateFromPointer(event.x());
        return true;
    }

    @Override
    public boolean onPointerMove(PointerEvent event) {
        if (pressed) {
            updateFromPointer(event.x());
            return true;
        }
        return false;
    }

    @Override
    public boolean onPointerUp(PointerEvent event) {
        pressed = false;
        return true;
    }

    @Override
    public boolean onKeyDown(KeyEvent event) {
        double step = Math.max((max - min) / 100.0, 0.01);
        if (event.key() == Keys.LEFT) {
            flashActivation();
            onChange.accept(clamp(value.getAsDouble() - step));
            return true;
        }
        if (event.key() == Keys.RIGHT) {
            flashActivation();
            onChange.accept(clamp(value.getAsDouble() + step));
            return true;
        }
        return super.onKeyDown(event);
    }

    private void updateFromPointer(float pointerX) {
        // Pointer events are in root coordinates; bounds may live inside a scrolled card or popup.
        float localX = pointerX - ancestorOffsetX();
        float trackX = bounds().x() + 8f;
        float trackWidth = Math.max(1f, bounds().width() - 16f);
        double t = Math.max(0.0, Math.min(1.0, (localX - trackX) / trackWidth));
        onChange.accept(clamp(min + (max - min) * t));
    }

    private double clamp(double value) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public void paint(UiPainter painter) {
        paintRowBackground(painter);
        paintFocus(painter);
        float trackX = bounds().x() + 8f;
        float trackY = bounds().y() + bounds().height() - 6f;
        float trackWidth = Math.max(1f, bounds().width() - 16f);
        painter.pill(new Rect(trackX, trackY, trackWidth, 3f), palette.outline(), 0);
        double t = (value.getAsDouble() - min) / (max - min);
        float knobX = trackX + (float) t * trackWidth;
        float accentWidth = Math.max(0f, knobX - 2f - trackX);
        if (accentWidth > 0.5f) {
            painter.pill(new Rect(trackX, trackY, accentWidth, 3f), palette.accent(), 0);
        }
        // The white knob has rounded corners; mask the track beneath it so its corner pixels do not
        // show through as a few blue dots at integer knob positions.
        painter.fill(new Rect(knobX - 3f, trackY - 3f, 6f, 9f), background());
        painter.fillRounded(new Rect(knobX - 2f, trackY - 3f, 4f, 9f), 2f, palette.text());
        painter.text(label, bounds().x() + 6f, bounds().y() + 2f, palette.text());
        String shown = String.format(format, value.getAsDouble());
        painter.text(shown, bounds().right() - painter.textWidth(shown) - 6f,
                bounds().y() + 2f, palette.text());
    }
}
