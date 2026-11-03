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
        popup = new Box(Style.builder()
                .direction(Style.Direction.COLUMN)
                .width(style.width())
                .height(this.options.size() * 20f)
                .build());
        popup.visible(false);
        for (int i = 0; i < this.options.size(); i++) {
            int index = i;
            Button.SurfaceMode mode = this.options.size() == 1 ? Button.SurfaceMode.MENU_ONLY
                    : i == 0 ? Button.SurfaceMode.MENU_TOP
                    : i == this.options.size() - 1 ? Button.SurfaceMode.MENU_BOTTOM
                    : Button.SurfaceMode.MENU_MIDDLE;
            popup.add(new Button(this.options.get(i), () -> select(index),
                    Style.builder().width(Dimension.percent(100f)).height(20f).build(), palette,
                    mode));
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

    private void select(int index) {
        onChange.accept(index);
        close();
    }

    /** Positions and lays out the popup under the current field. */
    public void layoutPopup(TextMeasurer measurer) {
        if (!open) {
            return;
        }
        float width = bounds().width();
        float height = options.size() * 20f;
        popup.style(Style.builder().from(popup.style()).width(width).height(height).build());
        LayoutEngine.layout(popup, width, height, measurer);
        popup.offset(bounds().x(), bounds().bottom() + 2f);
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
        painter.shadow(bounds(), palette.radius());
        painter.surface(bounds(), palette.radius(), background(), 0);
        paintFocus(painter);
        int index = selected.getAsInt();
        String value = index >= 0 && index < options.size() ? options.get(index) : label;
        painter.text(value, bounds().x() + 6f,
                bounds().y() + (bounds().height() - painter.lineHeight()) / 2f, palette.text());
        float ax = bounds().right() - 12f;
        float ay = bounds().y() + bounds().height() / 2f;
        painter.fill(new dev.skypocket.ui.Rect(ax, ay - 1f, 7f, 1f), palette.muted());
        painter.fill(new dev.skypocket.ui.Rect(ax + 1f, ay, 5f, 1f), palette.muted());
        painter.fill(new dev.skypocket.ui.Rect(ax + 2f, ay + 1f, 3f, 1f), palette.muted());
    }
}
