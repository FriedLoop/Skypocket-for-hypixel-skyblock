package dev.skypocket.ui.widget;

import dev.skypocket.ui.Rect;
import dev.skypocket.ui.Style;
import dev.skypocket.ui.UiPainter;
import dev.skypocket.ui.input.KeyEvent;
import dev.skypocket.ui.input.Keys;
import dev.skypocket.ui.input.PointerEvent;
import dev.skypocket.ui.input.PreeditEvent;
import dev.skypocket.ui.input.TextInputEvent;

import java.util.Objects;

/** Single-line editable text field with cursor movement and IME pre-edit support. */
public final class TextField extends Control {
    private final String placeholder;
    private final StringBuilder value = new StringBuilder();
    private int cursor;
    private String preedit = "";

    public TextField(String placeholder) {
        this(placeholder, Style.builder().width(220f).height(22f).build(), WidgetPalette.DARK);
    }

    public TextField(String placeholder, Style style, WidgetPalette palette) {
        super(style, palette);
        this.placeholder = Objects.requireNonNull(placeholder);
    }

    public String text() {
        return value.toString();
    }

    public TextField text(String next) {
        value.setLength(0);
        value.append(next == null ? "" : next);
        cursor = value.length();
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
        } else {
            preedit = "";
        }
    }

    @Override
    public boolean onPointerDown(PointerEvent event) {
        cursor = value.length();
        return true;
    }

    @Override
    public boolean onPointerUp(PointerEvent event) {
        pressed = false;
        return true;
    }

    @Override
    public boolean onTextInput(TextInputEvent event) {
        if (event.text().isEmpty()) {
            return false;
        }
        value.insert(cursor, event.text());
        cursor += event.text().length();
        preedit = "";
        return true;
    }

    @Override
    public boolean onPreedit(PreeditEvent event) {
        preedit = event.cancelled() ? "" : event.text();
        return true;
    }

    @Override
    public boolean onKeyDown(KeyEvent event) {
        switch (event.key()) {
            case Keys.BACKSPACE -> {
                if (cursor > 0) {
                    value.deleteCharAt(cursor - 1);
                    cursor--;
                }
                preedit = "";
                return true;
            }
            case Keys.DELETE -> {
                if (cursor < value.length()) {
                    value.deleteCharAt(cursor);
                }
                preedit = "";
                return true;
            }
            case Keys.LEFT -> {
                cursor = Math.max(0, cursor - 1);
                return true;
            }
            case Keys.RIGHT -> {
                cursor = Math.min(value.length(), cursor + 1);
                return true;
            }
            case Keys.HOME -> {
                cursor = 0;
                return true;
            }
            case Keys.END -> {
                cursor = value.length();
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    @Override
    public void paint(UiPainter painter) {
        painter.fill(bounds(), background());
        paintFocus(painter);
        painter.pushClip(bounds());

        String shown = value.toString();
        float textX = bounds().x() + 6f;
        float textY = bounds().y() + (bounds().height() - painter.lineHeight()) / 2f;
        if (shown.isEmpty() && preedit.isEmpty()) {
            painter.text(placeholder, textX, textY, palette.muted());
        } else {
            painter.text(shown, textX, textY, palette.text());
        }

        float caretX = textX + painter.textWidth(shown.substring(0, cursor));
        if (!preedit.isEmpty()) {
            painter.text(preedit, textX + painter.textWidth(shown), textY, palette.accent());
            painter.fill(new Rect(textX + painter.textWidth(shown), textY + painter.lineHeight(), 
                    painter.textWidth(preedit), 1f), palette.accent());
            caretX = textX + painter.textWidth(shown) + painter.textWidth(preedit);
        }
        if (focused) {
            painter.fill(new Rect(caretX, bounds().y() + 4f, 1f, bounds().height() - 8f),
                    palette.text());
        }
        painter.popClip();
    }
}
