package dev.skypocket.ui.widget;

import dev.skypocket.ui.Box;
import dev.skypocket.ui.Dimension;
import dev.skypocket.ui.LayoutEngine;
import dev.skypocket.ui.Style;
import dev.skypocket.ui.TextMeasurer;
import dev.skypocket.ui.UiElement;
import dev.skypocket.ui.UiPainter;
import dev.skypocket.ui.input.KeyEvent;
import dev.skypocket.ui.input.Keys;
import dev.skypocket.ui.input.PointerEvent;
import dev.skypocket.ui.input.WheelEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * A compact dropdown. The option list is a separate, searchable, virtualized popup layer so it can
 * escape the parent's clipping and scroll viewport without building one widget per option.
 */
public final class Dropdown extends Control {
    private static final int MAX_VISIBLE_ROWS = 8;
    private static final float ROW_H = 20f;
    private static final float SEARCH_H = 18f;
    private static final float GAP = 2f;
    private static final float PAD = 2f;

    private final String label;
    private final List<String> options;
    private final IntSupplier selected;
    private final IntConsumer onChange;
    private final Card popup;
    private final WidgetPalette popupPalette;
    private final TextField search;
    private final OptionsList optionList;
    private final List<Integer> matches = new ArrayList<>();
    private float maxOptionWidth = -1f;
    private int scrollRow;
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
        popupPalette = new WidgetPalette(
                palette.field(), palette.hover(), palette.pressed(), palette.outline(),
                palette.focus(), palette.text(), palette.muted(), palette.accent());
        boolean hasSearch = this.options.size() > MAX_VISIBLE_ROWS;
        float initialRows = Math.min(MAX_VISIBLE_ROWS, Math.max(1, this.options.size()));
        float initialHeight = PAD * 2 + (hasSearch ? SEARCH_H + GAP : 0) + initialRows * ROW_H;
        popup = new Card(Style.builder()
                .direction(Style.Direction.COLUMN)
                .width(style.width())
                .height(initialHeight)
                .padding(PAD)
                .gap(GAP)
                .build(), popupPalette);
        popup.visible(false);

        search = hasSearch ? new TextField(dev.skypocket.i18n.Localization.tr("搜索..."), Style.builder()
                .width(Dimension.percent(100f)).height(SEARCH_H).build(), popupPalette) : null;
        if (search != null) {
            search.onChange(this::filter);
            popup.add(search);
        }
        optionList = new OptionsList();
        popup.add(optionList);
        filter("");
    }

    public Box popup() {
        return popup;
    }

    public boolean isOpen() {
        return open;
    }

    public void open() {
        open = true;
        if (search != null) {
            search.text("");
        }
        filter("");
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

    private void filter(String value) {
        String query = value == null ? "" : value.trim().toLowerCase();
        matches.clear();
        for (int i = 0; i < options.size(); i++) {
            if (query.isEmpty() || options.get(i).toLowerCase().contains(query)) {
                matches.add(i);
            }
        }
        scrollRow = 0;
        rebuildWindow();
    }

    private void rebuildWindow() {
        for (UiElement child : new ArrayList<>(optionList.children())) {
            optionList.remove(child);
        }
        int end = Math.min(matches.size(), scrollRow + MAX_VISIBLE_ROWS);
        for (int row = scrollRow; row < end; row++) {
            int index = matches.get(row);
            Button.SurfaceMode mode = matches.size() == 1 ? Button.SurfaceMode.MENU_ONLY
                    : row == 0 ? Button.SurfaceMode.MENU_TOP
                    : row == matches.size() - 1 ? Button.SurfaceMode.MENU_BOTTOM
                    : Button.SurfaceMode.MENU_MIDDLE;
            optionList.add(new Button(options.get(index), () -> select(index),
                    Style.builder().width(Dimension.percent(100f)).height(ROW_H).build(),
                    popupPalette, mode));
        }
    }

    private int visibleRows() {
        return Math.max(1, Math.min(MAX_VISIBLE_ROWS, matches.size()));
    }

    private float popupHeight() {
        return PAD * 2 + (search == null ? 0f : SEARCH_H + GAP) + visibleRows() * ROW_H;
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
        float height = popupHeight();
        float listH = visibleRows() * ROW_H;
        popup.style(Style.builder().from(popup.style()).width(width).height(height).build());
        optionList.style(Style.builder().from(optionList.style()).height(listH).build());
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
        if (maxOptionWidth < 0f) {
            maxOptionWidth = 0f;
            for (String option : options) {
                maxOptionWidth = Math.max(maxOptionWidth, painter.textWidth(option));
            }
        }
        float fieldH = Math.min(16f, Math.max(10f, bounds().height() - 4f));
        float fieldW = Math.min(Math.max(36f, bounds().width() - labelW - 18f),
                maxOptionWidth + arrowW + 14f);
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

    /** The visible window; it owns at most MAX_VISIBLE_ROWS buttons and handles wheel paging. */
    private final class OptionsList extends Control {
        private OptionsList() {
            super(Style.builder()
                    .width(Dimension.percent(100f))
                    .height(MAX_VISIBLE_ROWS * ROW_H)
                    .direction(Style.Direction.COLUMN)
                    .build(), popupPalette);
        }

        @Override
        public boolean focusable() {
            return false;
        }

        @Override
        protected void activate() {
            // No activation of its own; option buttons handle clicks.
        }

        @Override
        public boolean onWheel(WheelEvent event) {
            if (matches.size() <= MAX_VISIBLE_ROWS) {
                return false;
            }
            int direction = event.delta() > 0f ? -1 : 1;
            int next = Math.max(0, Math.min(matches.size() - MAX_VISIBLE_ROWS, scrollRow + direction));
            if (next == scrollRow) {
                return false;
            }
            scrollRow = next;
            rebuildWindow();
            return true;
        }

        @Override
        public void paint(UiPainter painter) {
            // The popup Card supplies the surface; option buttons paint their own rows.
        }
    }
}
