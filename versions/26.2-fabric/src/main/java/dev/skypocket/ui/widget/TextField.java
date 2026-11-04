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
import java.util.function.Consumer;

/** Single-line editable text field with cursor movement and IME pre-edit support. */
public final class TextField extends Control {
    private final String placeholder;
    private final String label;
    private boolean labelOutside;
    private final StringBuilder value = new StringBuilder();
    private int cursor;
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
        switch (event.key()) {
            case Keys.BACKSPACE -> {
                if (cursor > 0) {
                    value.deleteCharAt(cursor - 1);
                    cursor--;
                    changed();
                }
                preedit = "";
                return true;
            }
            case Keys.DELETE -> {
                if (cursor < value.length()) {
                    value.deleteCharAt(cursor);
                    changed();
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
        float outsideLabelW = labelOutside && label != null && !label.isEmpty()
                ? painter.textWidth(label) : 0f;
        Rect field = labelOutside ? outsideFieldBounds(outsideLabelW) : bounds();
        int border = 0;
        if (!neutralPaint()) {
            border = focused ? palette.focus() : hovered ? palette.text() : 0;
        }
        // Fill with the smooth mask, then overlay the independent 2px device-space ring.
        painter.surface(field, palette.radius(), palette.field(), 0);
        if (border != 0) {
            painter.strokeRounded(field, palette.radius(), border);
        }
        String shown = value.toString();
        float textY = field.y() + (field.height() - painter.lineHeight()) / 2f;
        float textX = field.x() + 6f;
        if (labelOutside && label != null && !label.isEmpty()) {
            painter.text(label, bounds().x() + 6f,
                    bounds().y() + (bounds().height() - painter.lineHeight()) / 2f, palette.text());
        } else if (label != null && !label.isEmpty()) {
            painter.text(label, textX, textY, palette.muted());
            textX += painter.textWidth(label) + 3f;
        }
        painter.pushClip(field);
        if (shown.isEmpty() && preedit.isEmpty() && placeholder != null && !placeholder.isEmpty()) {
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
        if (focused && !neutralPaint()) {
            painter.fill(new Rect(caretX, field.y() + 4f, 1f, field.height() - 8f),
                    palette.text());
        }
        painter.popClip();
    }

    private Rect outsideFieldBounds(float labelWidth) {
        float fieldH = Math.min(16f, Math.max(10f, bounds().height() - 4f));
        float fieldX = bounds().x() + 6f + labelWidth + 8f;
        float fieldW = Math.max(40f, bounds().right() - 6f - fieldX);
        return new Rect(fieldX, bounds().y() + (bounds().height() - fieldH) / 2f,
                fieldW, fieldH);
    }
}
