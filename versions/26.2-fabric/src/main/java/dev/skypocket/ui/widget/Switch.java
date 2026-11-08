package dev.skypocket.ui.widget;

import dev.skypocket.ui.Style;
import dev.skypocket.ui.UiPainter;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** A labelled on/off switch. */
public final class Switch extends Control {
    private static final long ANIM_NANOS = 140_000_000L;

    private final String label;
    private final BooleanSupplier value;
    private final Consumer<Boolean> onChange;
    private float progress = Float.NaN;
    private float animFrom;
    private float animTo;
    private long animStart;

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
        paintRowBackground(painter);
        paintFocus(painter);
        boolean on = value.getAsBoolean();
        float p = animate(on);
        float trackWidth = 28f;
        float trackX = bounds().right() - trackWidth - 4f;
        // Round once and derive both the track and knob from it; rounding them separately is what
        // made some knobs sit one pixel high and others one pixel low.
        float trackY = Math.round(bounds().y() + (bounds().height() - 12f) / 2f);
        painter.pill(new dev.skypocket.ui.Rect(trackX, trackY, trackWidth, 12f),
                blend(palette.switchOff(), palette.switchOn(), p), palette.switchOff());
        float knobX = trackX + 2f + p * (trackWidth - 4f - 8f);
        painter.fillRounded(new dev.skypocket.ui.Rect(knobX, trackY + 2f, 8f, 8f), 4f,
                palette.onAccent());
        painter.text(label, bounds().x() + 6f,
                bounds().y() + (bounds().height() - painter.lineHeight()) / 2f,
                on ? palette.text() : palette.muted());
    }

    private float animate(boolean on) {
        float target = on ? 1f : 0f;
        if (Float.isNaN(progress)) {
            progress = target;
            animTo = target;
            return progress;
        }
        if (target != animTo) {
            animFrom = progress;
            animTo = target;
            animStart = System.nanoTime();
        }
        if (progress == animTo) {
            return progress;
        }
        float t = (System.nanoTime() - animStart) / (float) ANIM_NANOS;
        if (t >= 1f) {
            progress = animTo;
            return progress;
        }
        float eased = 1f - (1f - t) * (1f - t);
        progress = animFrom + (animTo - animFrom) * eased;
        return progress;
    }

    private static int blend(int from, int to, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int a = (int) (((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * t);
        int r = (int) (((from >>> 16) & 0xFF) + (((to >>> 16) & 0xFF) - ((from >>> 16) & 0xFF)) * t);
        int g = (int) (((from >>> 8) & 0xFF) + (((to >>> 8) & 0xFF) - ((from >>> 8) & 0xFF)) * t);
        int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
