package dev.skypocket.ui.widget;

import dev.skypocket.ui.Box;
import dev.skypocket.ui.Dimension;
import dev.skypocket.ui.LayoutEngine;
import dev.skypocket.ui.Rect;
import dev.skypocket.ui.Style;
import dev.skypocket.ui.TextMeasurer;
import dev.skypocket.ui.UiPainter;
import dev.skypocket.ui.input.KeyEvent;
import dev.skypocket.ui.input.Keys;
import dev.skypocket.ui.input.PointerEvent;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * A colour row with a v2 picker popup. The popup mirrors the old ImmediateColorPicker: SV square,
 * hue/alpha bars, preset swatches and HEX/RGBA text fields. It is a separate layer, like Dropdown,
 * so it can escape the page's scroll clip.
 */
public final class ColorField extends Control {
    private final String label;
    private final IntSupplier value;
    private final IntConsumer setter;
    private final Card popup;
    private final ColorPickerPanel panel;
    private final TextField hex;
    private final TextField[] channels = new TextField[4];
    private boolean open;

    public ColorField(String label, IntSupplier value, IntConsumer setter) {
        this(label, value, setter, "Close", Style.builder()
                .width(Dimension.percent(100f)).height(20f).build(), WidgetPalette.DARK);
    }

    public ColorField(String label, IntSupplier value, IntConsumer setter, String closeLabel,
                      Style style, WidgetPalette palette) {
        super(style, palette);
        this.label = label;
        this.value = value;
        this.setter = setter;

        popup = new Card(Style.builder()
                .width(ColorPickerPanel.CONTENT_W)
                .height(ColorPickerPanel.CONTENT_H)
                .padding(0f)
                .build(), palette);
        popup.visible(false);
        panel = new ColorPickerPanel(value, this::setColor, Style.builder()
                .position(Style.Position.ABSOLUTE)
                .left(0f).top(0f)
                .width(ColorPickerPanel.CONTENT_W)
                .height(ColorPickerPanel.CONTENT_H)
                .build(), palette);
        popup.add(panel);

        hex = field("", 8, "#", palette);
        channels[0] = field("", 36, "R", palette);
        channels[1] = field("", 64, "G", palette);
        channels[2] = field("", 92, "B", palette);
        channels[3] = field("", 120, "A", palette);
        hex.onChange(this::applyHex);
        int[] shifts = {16, 8, 0, 24};
        for (int i = 0; i < channels.length; i++) {
            int shift = shifts[i];
            channels[i].onChange(text -> applyChannel(shift, text));
            popup.add(channels[i]);
        }
        popup.add(hex);
        seed(value.getAsInt());
    }

    private TextField field(String placeholder, int y, String label, WidgetPalette palette) {
        return new TextField(placeholder, label, Style.builder()
                .position(Style.Position.ABSOLUTE)
                .left(ColorPickerPanel.FIELD_X)
                .top(y)
                .width(ColorPickerPanel.FIELD_W)
                .height(ColorPickerPanel.FIELD_H)
                .build(), palette);
    }

    public Box popup() {
        return popup;
    }

    public boolean isOpen() {
        return open;
    }

    public void open() {
        seed(value.getAsInt());
        open = true;
        popup.visible(true);
    }

    public void close() {
        open = false;
        popup.visible(false);
    }

    /** Closes only when focus left both the row and its popup. */
    public void closeIfFocusOutside(dev.skypocket.ui.UiElement focused) {
        if (!open) {
            return;
        }
        if (focused == null
                || (!focused.isDescendantOf(this) && !focused.isDescendantOf(popup))) {
            close();
        }
    }

    /** Lays the popup inside the page viewport, above the row when there is no room below. */
    public void layoutPopup(TextMeasurer measurer, float viewWidth, float viewHeight) {
        if (!open) {
            return;
        }
        syncFields();
        float popupWidth = ColorPickerPanel.CONTENT_W;
        float popupHeight = ColorPickerPanel.CONTENT_H;
        LayoutEngine.layout(popup, popupWidth, popupHeight, measurer);
        // Snap to design pixels like the page cache does; otherwise the live popup can sit 1px off
        // the cached color row even though both use the same page origin.
        float anchorX = Math.round(visualX());
        float anchorY = Math.round(visualY() + bounds().height() + 2f);
        float x = Math.max(0f, Math.min(anchorX, Math.max(0f, viewWidth - popupWidth)));
        float y = anchorY >= 0f && anchorY + popupHeight <= viewHeight
                ? anchorY
                : Math.round(visualY() - popupHeight - 2f);
        // Never let a field's clip cross the page/render area; that is what made RenderPearl reject
        // the scissor while the wheel was scrolling an open popup near the bottom/top edge.
        y = Math.max(0f, Math.min(y, Math.max(0f, viewHeight - popupHeight)));
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
    public boolean onPointerDown(PointerEvent event) {
        pressed = true;
        return true;
    }

    @Override
    public boolean onPointerUp(PointerEvent event) {
        if (pressed) {
            activate();
        }
        pressed = false;
        return true;
    }

    private void seed(int argb) {
        panel.seed(argb);
        syncFields();
    }

    private void setColor(int argb) {
        setter.accept(argb);
    }

    private void applyHex(String text) {
        String s = text.trim();
        if (s.startsWith("#")) {
            s = s.substring(1);
        }
        if (s.length() != 6) {
            return;
        }
        try {
            int next = (value.getAsInt() & 0xFF000000) | Integer.parseInt(s, 16);
            panel.adopt(next);
            setColor(next);
        } catch (NumberFormatException ignored) {
        }
    }

    private void applyChannel(int shift, String text) {
        int parsed;
        try {
            parsed = Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return;
        }
        int clamped = Math.max(0, Math.min(255, parsed));
        int mask = 0xFF << shift;
        int next = (value.getAsInt() & ~mask) | (clamped << shift);
        panel.adopt(next);
        setColor(next);
    }

    public void syncFields() {
        int argb = value.getAsInt();
        if (!hex.isFocused()) {
            hex.text(String.format("%06X", argb & 0xFFFFFF));
        }
        int[] shifts = {16, 8, 0, 24};
        for (int i = 0; i < channels.length; i++) {
            if (!channels[i].isFocused()) {
                channels[i].text(Integer.toString((argb >> shifts[i]) & 0xFF));
            }
        }
    }

    @Override
    public void paint(UiPainter painter) {
        paintRowBackground(painter);
        paintFocus(painter);
        painter.text(label, bounds().x() + 6f,
                bounds().y() + (bounds().height() - painter.lineHeight()) / 2f, palette.text());
        int argb = value.getAsInt();
        float sx = bounds().right() - 24f;
        float sy = bounds().y() + 4f;
        painter.fillRounded(new Rect(sx, sy, 18f, bounds().height() - 8f), 3f, argb);
        String hexText = String.format("#%08X", argb);
        painter.text(hexText, sx - painter.textWidth(hexText) - 6f,
                bounds().y() + (bounds().height() - painter.lineHeight()) / 2f, palette.muted());
    }
}
