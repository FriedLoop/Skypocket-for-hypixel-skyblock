package dev.skypocket.gui.widget;

import dev.skypocket.render.ClickGuiFonts;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.function.DoubleConsumer;

/**
 * Generic slider over a [min, max] range. Value updates on click and drag;
 * drag continues even when the cursor leaves the widget bounds.
 */
public class SliderWidget extends BaseWidget {
    private final String label;
    private final double min;
    private final double max;
    private final String format;
    private final DoubleConsumer onChange;
    private final double step;

    private double value;

    public SliderWidget(int x, int y, int width, int height, String label,
                        double min, double max, double value, String format,
                        DoubleConsumer onChange) {
        this(x, y, width, height, label, min, max, value, format, onChange, 0.0);
    }

    public SliderWidget(int x, int y, int width, int height, String label,
                        double min, double max, double value, String format,
                        DoubleConsumer onChange, double step) {
        super(x, y, width, height);
        this.label = label;
        this.min = min;
        this.max = max;
        this.format = format;
        this.onChange = onChange;
        this.step = step;
        this.value = snap(value);
    }

    /** Updates the displayed value without firing onChange (for external sync, e.g. scroll-to-zoom). */
    public void setValue(double v) {
        this.value = snap(v);
    }

    /** Clamps to [min, max] and, when a step is set, snaps to its multiples. */
    private double snap(double v) {
        v = Math.max(min, Math.min(max, v));
        if (step > 0) {
            v = Math.round(v / step) * step;
            v = Math.max(min, Math.min(max, v));
        }
        return v;
    }

    private void updateFromMouse(double mouseX) {
        double t = (mouseX - getX()) / Math.max(1.0, width);
        t = Math.max(0.0, Math.min(1.0, t));
        this.value = snap(min + t * (max - min));
        if (onChange != null) {
            onChange.accept(this.value);
        }
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubled) {
        updateFromMouse(event.x());
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double deltaX, double deltaY) {
        updateFromMouse(event.x());
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        double t = (value - min) / Math.max(1e-6, max - min);

        // Label inside the widget bounds, directly above the track, so it never
        // invades the space of neighbouring widgets.
        String text = label + ": " + String.format(format, value);
        ClickGuiFonts.drawText(graphics, text, getX(), getY(), ClickGuiStyle.textDim(), true);

        // Rounded track (a pill) + rounded knob, so a slider reads as the same control
        // family as the buttons.
        int trackY = getY() + height - 7;
        ClickGuiStyle.pill(graphics, getX(), trackY, width, 4, 0xFF202020);
        int fillEnd = getX() + (int) Math.round(width * t);
        if (fillEnd > getX()) {
            ClickGuiStyle.pill(graphics, getX(), trackY, fillEnd - getX(), 4, ClickGuiStyle.accent());
        }
        int knobX = getX() + (int) Math.round((width - 6) * t);
        ClickGuiStyle.surface(graphics, knobX, getY() + height - 9, 6, 8, 0xFFDDDDDD, 0);
    }
}
