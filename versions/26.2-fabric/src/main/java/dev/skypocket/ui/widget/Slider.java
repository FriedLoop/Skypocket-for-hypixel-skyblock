package dev.skypocket.ui.widget;

import dev.skypocket.ui.Rect;
import dev.skypocket.ui.Style;
import dev.skypocket.ui.UiPainter;
import dev.skypocket.ui.input.KeyEvent;
import dev.skypocket.ui.input.Keys;
import dev.skypocket.ui.input.PointerEvent;
import dev.skypocket.ui.input.TextInputEvent;

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
    /** Fixed even value field; wide enough for 200%, -1.000 and four-digit values. */
    private static final float VALUE_BOX_W = 64f;
    private static final float VALUE_BOX_H = 24f;

    private final String format;
    private final double step;
    private boolean editing;
    private String editText = "";

    public Slider(String label, double min, double max, DoubleSupplier value,
                  DoubleConsumer onChange, String format) {
        this(label, min, max, value, onChange, format,
                Style.builder().width(200f).height(20f).build(), WidgetPalette.DARK);
    }

    public Slider(String label, double min, double max, DoubleSupplier value,
                  DoubleConsumer onChange, String format, Style style, WidgetPalette palette) {
        this(label, min, max, value, onChange, format, Double.NaN, style, palette);
    }

    public Slider(String label, double min, double max, DoubleSupplier value,
                  DoubleConsumer onChange, String format, double step, Style style,
                  WidgetPalette palette) {
        super(style, palette);
        this.label = Objects.requireNonNull(label);
        this.min = min;
        this.max = Math.max(min + 1.0e-6, max);
        this.value = Objects.requireNonNull(value);
        this.onChange = Objects.requireNonNull(onChange);
        this.format = format;
        this.step = step;
    }

    @Override
    protected void activate() {
        // Keyboard activation moves the value by one step; left/right is handled in onKeyDown.
    }

    @Override
    public boolean onPointerDown(PointerEvent event) {
        float localX = event.x() - ancestorOffsetX();
        if (insideValue(localX, event.y() - ancestorOffsetY())) {
            editing = true;
            editText = String.format(format, value.getAsDouble());
            return true;
        }
        editing = false;
        float tx = trackX();
        float tw = trackWidth();
        if (localX >= tx && localX <= tx + tw) {
            pressed = true;
            updateFromPointer(event.x());
        }
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
        if (editing) {
            if (event.key() == Keys.ENTER || event.key() == 257) {
                commitEdit();
            } else if (event.key() == Keys.ESCAPE) {
                editing = false;
            } else if (event.key() == Keys.BACKSPACE) {
                if (!editText.isEmpty()) {
                    editText = editText.substring(0, editText.length() - 1);
                }
            }
            return true;
        }
        if (event.key() != Keys.LEFT && event.key() != Keys.RIGHT) {
            return super.onKeyDown(event);
        }
        double increment = step > 0 ? step : Math.max((max - min) / 100.0, 1.0e-4);
        double current = value.getAsDouble();
        // Move by one snapped index rather than arithmetically adding a float step; otherwise a
        // configured coarse step (e.g. 100 ms) can round back to the current value and need a
        // second key press before the displayed number changes.
        long index = Math.round((current - min) / increment);
        index += event.key() == Keys.LEFT ? -1 : 1;
        double next = min + index * increment;
        flashActivation();
        onChange.accept(clamp(next));
        return true;
    }

    @Override
    public boolean onTextInput(TextInputEvent event) {
        if (!editing || event.text().isEmpty()) {
            return false;
        }
        for (int i = 0; i < event.text().length(); i++) {
            char c = event.text().charAt(i);
            if ((c >= '0' && c <= '9') || c == '.' || c == ',' || c == '-') {
                editText += c;
            }
        }
        return true;
    }

    @Override
    public void onFocusChanged(boolean focused) {
        super.onFocusChanged(focused);
        if (!focused && editing) {
            commitEdit();
        }
    }

    @Override
    public boolean dynamicOverlay() {
        return editing;
    }

    private void commitEdit() {
        if (!editing) {
            return;
        }
        editing = false;
        String raw = editText.trim().replace(',', '.');
        if (raw.isEmpty() || raw.equals("-") || raw.equals(".")) {
            return;
        }
        try {
            onChange.accept(clamp(Double.parseDouble(raw)));
        } catch (NumberFormatException ignored) {
            // Invalid input is discarded; the previous value remains.
        }
    }

    private void updateFromPointer(float pointerX) {
        // Pointer events are in root coordinates; bounds may live inside a scrolled card or popup.
        float localX = pointerX - ancestorOffsetX();
        double t = Math.max(0.0, Math.min(1.0, (localX - trackX()) / trackWidth()));
        onChange.accept(clamp(min + (max - min) * t));
    }

    private double clamp(double value) {
        return Math.max(min, Math.min(max, value));
    }

    /** Even length; two fifths of the row. */
    private float trackWidth() {
        int width = Math.round(bounds().width() * 0.4f);
        if ((width & 1) != 0) {
            width++;
        }
        return Math.max(2f, width);
    }

    /** Value box is right-aligned with the switch track's right edge. */
    private float valueRight() {
        return bounds().right() - 8f;
    }

    private float valueX() {
        return valueRight() - VALUE_BOX_W;
    }

    private float valueY() {
        return bounds().y() + (bounds().height() - VALUE_BOX_H) / 2f;
    }

    private float trackX() {
        return valueX() - 12f - trackWidth();
    }

    private float textY(UiPainter painter) {
        return bounds().y() + (bounds().height() - painter.lineHeight()) / 2f;
    }

    private boolean insideValue(float localX, float localY) {
        return localY >= valueY() && localY < valueY() + VALUE_BOX_H
                && localX >= valueX() && localX < valueX() + VALUE_BOX_W;
    }

    @Override
    public void paint(UiPainter painter) {
        paintRowBackground(painter);
        paintFocus(painter);
        float trackX = trackX();
        float trackWidth = trackWidth();
        float trackY = Math.round(bounds().y() + (bounds().height() - 6f) / 2f);
        float textY = textY(painter);
        painter.pill(new Rect(trackX, trackY, trackWidth, 6f), palette.outline(), 0);
        double t = (value.getAsDouble() - min) / (max - min);
        float knobX = trackX + (float) t * trackWidth;
        float accentWidth = Math.max(0f, knobX - 4f - trackX);
        if (accentWidth > 0.5f) {
            painter.pill(new Rect(trackX, trackY, accentWidth, 6f), palette.accent(), 0);
        }
        // The white knob has rounded corners; mask the track beneath it so its corner pixels do not
        // show through as a few blue dots at integer knob positions.
        painter.fill(new Rect(knobX - 6f, trackY - 6f, 12f, 18f), background());
        painter.fillRounded(new Rect(knobX - 4f, trackY - 6f, 8f, 18f),
                Math.max(1f, Math.min(palette.radius(), 4f)), palette.text());
        painter.text(label, bounds().x() + 11f, textY, palette.text());

        int border = editing && !Control.neutralPaint() ? palette.focus() : 0;
        painter.surface(new Rect(valueX(), valueY(), VALUE_BOX_W, VALUE_BOX_H),
                palette.radius(), palette.field(), border);
        String shown = editing ? editText : String.format(format, value.getAsDouble());
        float shownW = painter.textWidth(shown);
        float shownX = valueX() + (VALUE_BOX_W - shownW) / 2f;
        painter.text(shown, shownX, textY, palette.text());
        if (editing) {
            float caretX = shownX + shownW + 1f;
            painter.fill(new Rect(caretX, textY + 2f, 1f, painter.lineHeight() - 4f),
                    palette.text());
        }
    }
}
