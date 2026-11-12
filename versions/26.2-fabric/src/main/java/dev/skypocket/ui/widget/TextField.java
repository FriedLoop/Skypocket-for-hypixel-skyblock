package dev.skypocket.ui.widget;

import dev.skypocket.render.ClickGuiFonts;
import dev.skypocket.ui.Rect;
import dev.skypocket.ui.Style;
import dev.skypocket.ui.UiPainter;
import dev.skypocket.ui.input.KeyEvent;
import dev.skypocket.ui.input.Keys;
import dev.skypocket.ui.input.Modifiers;
import dev.skypocket.ui.input.PointerEvent;
import dev.skypocket.ui.input.PreeditEvent;
import dev.skypocket.ui.input.TextInputEvent;

import java.util.Objects;
import java.util.function.Consumer;

/** Single-line editable text field with cursor movement and IME pre-edit support. */
public final class TextField extends Control {
    private final String placeholder;
    private final String label;
    private boolean labelOutside;
    private final StringBuilder value = new StringBuilder();
    private int cursor;
    private int anchor;
    private boolean selecting;
    private String preedit = "";
    private Consumer<String> changeHandler = text -> {
    };

    public TextField(String placeholder) {
        this(placeholder, Style.builder().width(220f).height(22f).build(), WidgetPalette.DARK);
    }

    public TextField(String placeholder, Style style, WidgetPalette palette) {
        this(placeholder, null, style, palette);
    }

    public TextField(String placeholder, String label, Style style, WidgetPalette palette) {
        super(style, palette);
        this.placeholder = Objects.requireNonNull(placeholder);
        this.label = label;
    }

    public TextField onChange(Consumer<String> handler) {
        changeHandler = Objects.requireNonNull(handler);
        return this;
    }

    /** Draws the label on the left and confines the editable field to the right side. */
    public TextField labelOutside(boolean value) {
        labelOutside = value;
        return this;
    }

    private void changed() {
        changeHandler.accept(value.toString());
    }

    public String text() {
        return value.toString();
    }

    public TextField text(String next) {
        value.setLength(0);
        value.append(next == null ? "" : next);
        cursor = value.length();
        anchor = cursor;
        return this;
    }

    public int cursor() {
        return cursor;
    }

    @Override
    protected void activate() {
        // Clicking focuses through InputRouter; no additional action.
    }

    @Override
    public void onFocusChanged(boolean focused) {
        super.onFocusChanged(focused);
        if (focused) {
            cursor = value.length();
            anchor = cursor;
        } else {
            preedit = "";
            selecting = false;
            pressed = false;
            anchor = cursor;
        }
    }

    @Override
    public boolean onPointerDown(PointerEvent event) {
        // Place the caret at the closest character boundary instead of always jumping to the end.
        cursor = indexAt(event.x());
        if (!Modifiers.shift(event.modifiers())) {
            anchor = cursor;
        }
        selecting = true;
        pressed = true;
        preedit = "";
        return true;
    }

    @Override
    public boolean onPointerMove(PointerEvent event) {
        if (!selecting) {
            return false;
        }
        cursor = indexAt(event.x());
        return true;
    }

    private int indexAt(float mouseX) {
        float localX = mouseX - textStartX();
        int best = 0;
        float bestDistance = Math.abs(localX);
        for (int i = 1; i <= value.length(); i++) {
            float width = ClickGuiFonts.width(value.substring(0, i));
            float distance = Math.abs(localX - width);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    private float textStartX() {
        if (!labelOutside) {
            float x = bounds().x() + 11f;
            if (label != null && !label.isEmpty()) {
                x += ClickGuiFonts.width(label) + 3f;
            }
            return x;
        }
        float labelWidth = label == null || label.isEmpty()
                ? 0f : ClickGuiFonts.width(label);
        return outsideFieldBounds(labelWidth).x() + 11f;
    }

    @Override
    public boolean onPointerUp(PointerEvent event) {
        selecting = false;
        pressed = false;
        return true;
    }

    @Override
    public boolean onTextInput(TextInputEvent event) {
        if (event.text().isEmpty()) {
            return false;
        }
        replaceSelection(event.text());
        preedit = "";
        changed();
        return true;
    }

    @Override
    public boolean onPreedit(PreeditEvent event) {
        preedit = event.cancelled() ? "" : event.text();
        return true;
    }

    @Override
    public boolean onKeyDown(KeyEvent event) {
        boolean control = Modifiers.control(event.modifiers());
        boolean shift = Modifiers.shift(event.modifiers());
        int key = event.key();

        if (control) {
            switch (key) {
                case Keys.A -> {
                    anchor = 0;
                    cursor = value.length();
                    return true;
                }
                case Keys.C -> {
                    if (!hasSelection()) {
                        return false;
                    }
                    setClipboard(selected());
                    return true;
                }
                case Keys.X -> {
                    if (!hasSelection()) {
                        return false;
                    }
                    setClipboard(selected());
                    deleteSelection();
                    preedit = "";
                    changed();
                    return true;
                }
                case Keys.V -> {
                    String pasted = clipboard();
                    if (pasted.isEmpty()) {
                        return true;
                    }
                    replaceSelection(pasted);
                    preedit = "";
                    changed();
                    return true;
                }
                default -> {
                }
            }
        }

        switch (key) {
            case Keys.BACKSPACE -> {
                if (deleteSelection()) {
                    preedit = "";
                    changed();
                } else if (cursor > 0) {
                    value.deleteCharAt(cursor - 1);
                    cursor--;
                    anchor = cursor;
                    preedit = "";
                    changed();
                }
                return true;
            }
            case Keys.DELETE -> {
                if (deleteSelection()) {
                    preedit = "";
                    changed();
                } else if (cursor < value.length()) {
                    value.deleteCharAt(cursor);
                    anchor = cursor;
                    preedit = "";
                    changed();
                }
                return true;
            }
            case Keys.LEFT -> {
                moveCaret(cursor - 1, shift);
                return true;
            }
            case Keys.RIGHT -> {
                moveCaret(cursor + 1, shift);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private void moveCaret(int to, boolean extend) {
        cursor = Math.max(0, Math.min(value.length(), to));
        if (!extend) {
            anchor = cursor;
        }
    }

    private boolean hasSelection() {
        return cursor != anchor;
    }

    private String selected() {
        int from = Math.min(cursor, anchor);
        int to = Math.max(cursor, anchor);
        return value.substring(from, to);
    }

    private boolean deleteSelection() {
        int from = Math.min(cursor, anchor);
        int to = Math.max(cursor, anchor);
        if (from == to) {
            return false;
        }
        value.delete(from, to);
        cursor = from;
        anchor = from;
        return true;
    }

    private void replaceSelection(String insert) {
        deleteSelection();
        value.insert(cursor, insert);
        cursor += insert.length();
        anchor = cursor;
    }

    private static String clipboard() {
        try {
            String value = net.minecraft.client.Minecraft.getInstance().keyboardHandler.getClipboard();
            return value == null ? "" : value;
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static void setClipboard(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        try {
            net.minecraft.client.Minecraft.getInstance().keyboardHandler.setClipboard(text);
        } catch (RuntimeException ignored) {
            // Keep the field usable even if the platform clipboard is unavailable.
        }
    }

    @Override
    public void paint(UiPainter painter) {
        float outsideLabelW = labelOutside && label != null && !label.isEmpty()
                ? painter.textWidth(label) : 0f;
        Rect field = labelOutside ? outsideFieldBounds(outsideLabelW) : bounds();
        // Hover belongs to the page cache's base image; only focus/caret are dynamic. Suppressing
        // hover under neutralPaint made it flash once on the frame that recorded the page and then
        // disappear from the baked texture.
        int border = focused && !neutralPaint() ? palette.focus()
                : hovered ? palette.text() : 0;
        // Fill with the smooth mask, then overlay the independent 2px device-space ring.
        painter.surface(field, palette.radius(), palette.field(), 0);
        if (border != 0) {
            painter.strokeRounded(field, palette.radius(), border);
        }
        String shown = value.toString();
        float textY = field.y() + (field.height() - painter.lineHeight()) / 2f;
        float textX = field.x() + 11f;
        if (labelOutside && label != null && !label.isEmpty()) {
            painter.text(label, bounds().x() + 11f,
                    bounds().y() + (bounds().height() - painter.lineHeight()) / 2f, palette.text());
        } else if (label != null && !label.isEmpty()) {
            painter.text(label, textX, textY, palette.muted());
            textX += painter.textWidth(label) + 3f;
        }
        painter.pushClip(field);
        if (focused && hasSelection()) {
            int from = Math.min(cursor, anchor);
            int to = Math.max(cursor, anchor);
            float selectionX = textX + painter.textWidth(shown.substring(0, from));
            float selectionW = painter.textWidth(shown.substring(from, to));
            painter.fill(new Rect(selectionX, textY, Math.max(1f, selectionW),
                    painter.lineHeight()), palette.selection());
        }
        if (shown.isEmpty() && preedit.isEmpty() && placeholder != null && !placeholder.isEmpty()) {
            painter.text(placeholder, textX, textY, palette.muted());
        } else {
            // User-entered text must never be treated as a translation key.
            painter.textRaw(shown, textX, textY, palette.text());
        }

        float caretX = textX + painter.textWidth(shown.substring(0, cursor));
        if (!preedit.isEmpty()) {
            painter.textRaw(preedit, textX + painter.textWidth(shown), textY, palette.accent());
            painter.fill(new Rect(textX + painter.textWidth(shown), textY + painter.lineHeight(),
                    painter.textWidth(preedit), 1f), palette.accent());
            caretX = textX + painter.textWidth(shown) + painter.textWidth(preedit);
        }
        if (caretVisible()) {
            painter.fill(new Rect(caretX, field.y() + 8f, 1f, field.height() - 15f),
                    palette.text());
        }
        painter.popClip();
    }

    /** A 500 ms on/off caret keeps cached and non-cached pages visually identical. */
    private boolean caretVisible() {
        return focused && !neutralPaint() && (System.currentTimeMillis() / 500L) % 2L == 0L;
    }

    private Rect outsideFieldBounds(float labelWidth) {
        float fieldH = Math.min(31f, Math.max(20f, bounds().height() - 8f));
        float fieldX = bounds().x() + 11f + labelWidth + 15f;
        float fieldW = Math.max(77f, bounds().right() - 11f - fieldX);
        return new Rect(fieldX, bounds().y() + (bounds().height() - fieldH) / 2f,
                fieldW, fieldH);
    }
}
