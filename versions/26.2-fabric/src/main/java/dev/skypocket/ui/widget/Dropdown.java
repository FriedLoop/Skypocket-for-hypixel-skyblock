package dev.skypocket.ui.widget;

import dev.skypocket.ui.Box;
import dev.skypocket.ui.Dimension;
import dev.skypocket.ui.LayoutEngine;
import dev.skypocket.ui.Style;
import dev.skypocket.ui.TextMeasurer;
import dev.skypocket.ui.UiPainter;
import dev.skypocket.ui.input.KeyEvent;
import dev.skypocket.ui.input.Keys;
import dev.skypocket.ui.input.PointerEvent;

import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * A compact dropdown. The option list is a separate popup layer so it can escape the parent's
 * clipping and scroll viewport; the host must add {@link #popup()} to its InputRouter while open.
 */
public final class Dropdown extends Control {
    private final String label;
    private final List<String> options;
    private final IntSupplier selected;
    private final IntConsumer onChange;
    private final Box popup;
    private boolean open;

    public Dropdown(String label, List<String> options, IntSupplier selected, IntConsumer onChange) {
        this(label, options, selected, onChange,
                Style.builder().width(180f).height(20f).build(), WidgetPalette.DARK);
    }

    public Dropdown(String label, List<String> options, IntSupplier selected, IntConsumer onChange,
                    Style style, WidgetPalette palette) {
        super(style, palette);
        this.label = Objects.requireNonNull(label);
        this.options = List.copyOf(options);
        this.selected = Objects.requireNonNull(selected);
        this.onChange = Objects.requireNonNull(onChange);
        WidgetPalette popupPalette = new WidgetPalette(
                palette.field(), palette.hover(), palette.pressed(), palette.outline(),
                palette.focus(), palette.text(), palette.muted(), palette.accent());
        popup = new Card(Style.builder()
                .direction(Style.Direction.COLUMN)
                .width(style.width())
                .height(this.options.size() * 20f + 4f)
                .padding(2f)
                .build(), popupPalette);
        popup.visible(false);
        for (int i = 0; i < this.options.size(); i++) {
            int index = i;
            Button.SurfaceMode mode = this.options.size() == 1 ? Button.SurfaceMode.MENU_ONLY
                    : i == 0 ? Button.SurfaceMode.MENU_TOP
                    : i == this.options.size() - 1 ? Button.SurfaceMode.MENU_BOTTOM
                    : Button.SurfaceMode.MENU_MIDDLE;
            popup.add(new Button(this.options.get(i), () -> select(index),
                    Style.builder().width(Dimension.percent(100f)).height(20f).build(),
                    popupPalette, mode));
        }
    }

    public Box popup() {
        return popup;
    }

    public boolean isOpen() {
        return open;
    }

    public void open() {
        open = true;
        popup.visible(true);
    }

    public void close() {
        open = false;
        popup.visible(false);
    }

    /** Closes only when focus left both the field and its option popup. */
    public void closeIfFocusOutside(dev.skypocket.ui.UiElement focused) {
        if (!open) {
            return;
        }
        if (focused == null
                || (!focused.isDescendantOf(this) && !focused.isDescendantOf(popup))) {
            close();
        }
    }

    private void select(int index) {
        onChange.accept(index);
        close();
    }

    /** Positions and lays out the popup under the current field. */
    public void layoutPopup(TextMeasurer measurer) {
        layoutPopup(measurer, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY);
    }

    /** Positions the popup in the page viewport, flipping above when there is no room below. */
    public void layoutPopup(TextMeasurer measurer, float viewWidth, float viewHeight) {
        if (!open) {
            return;
        }
        float width = bounds().width();
        float height = options.size() * 20f + 4f;
        popup.style(Style.builder().from(popup.style()).width(width).height(height).build());
        LayoutEngine.layout(popup, width, height, measurer);
        float x = visualX();
        if (Float.isFinite(viewWidth)) {
            x = Math.max(0f, Math.min(x, Math.max(0f, viewWidth - width)));
        }
        float below = visualY() + bounds().height() + 2f;
        float y = Float.isFinite(viewHeight) && below + height > viewHeight
                ? visualY() - height - 2f
                : below;
        if (Float.isFinite(viewHeight)) {
            y = Math.max(0f, Math.min(y, Math.max(0f, viewHeight - height)));
        }
        popup.offset(x, y);
    }

    @Override
    protected void activate() {
        if (open) {
            close();
        } else {
            open();
        }
    }

    @Override
    public boolean onKeyDown(KeyEvent event) {
        if (event.key() == Keys.ENTER || event.key() == 32) {
            activate();
            return true;
        }
        if (event.key() == Keys.ESCAPE && open) {
            close();
            return true;
        }
        return false;
    }

    @Override
    public void paint(UiPainter painter) {
        int index = selected.getAsInt();
        String value = index >= 0 && index < options.size() ? options.get(index) : label;
        String caption = label == null ? "" : label;
        String arrow = open ? "▲" : "▼";

        float labelW = painter.textWidth(caption);
        float arrowW = painter.textWidth(arrow);
        float maxValueW = 0f;
        for (String option : options) {
            maxValueW = Math.max(maxValueW, painter.textWidth(option));
        }
        float fieldH = Math.min(16f, Math.max(10f, bounds().height() - 4f));
        float fieldW = Math.min(Math.max(36f, bounds().width() - labelW - 18f),
                maxValueW + arrowW + 14f);
        float fieldX = bounds().right() - 6f - fieldW;
        float fieldY = bounds().y() + (bounds().height() - fieldH) / 2f;
        float centreY = bounds().y() + (bounds().height() - painter.lineHeight()) / 2f;

        painter.text(caption, bounds().x() + 6f, centreY, palette.text());
        int border = focused && !neutralPaint() ? palette.focus() : 0;
        painter.surface(new dev.skypocket.ui.Rect(fieldX, fieldY, fieldW, fieldH), 3f,
                palette.field(), border);
        painter.text(value, fieldX + 4f,
                fieldY + (fieldH - painter.lineHeight()) / 2f, palette.text());
        painter.text(arrow, fieldX + fieldW - 4f - arrowW,
                fieldY + (fieldH - painter.lineHeight()) / 2f, palette.muted());
    }
}
