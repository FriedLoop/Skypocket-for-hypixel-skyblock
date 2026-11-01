package dev.skypocket.gui.widget;

import dev.skypocket.i18n.Localization;
import dev.skypocket.render.ClickGuiFonts;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.function.Consumer;

/**
 * A minimal single-line text field. A single global field can be focused at a time;
 * {@link dev.skypocket.gui.OneClickScreen} routes {@code charTyped}/{@code keyPressed}
 * to the focused field and clears focus when the user clicks elsewhere.
 */
public class TextFieldWidget extends BaseWidget {
    private static final int MAX_LENGTH = 32;
    /**
     * Minimum width of the label column, so fields stacked on top of each other line up even when
     * one of them has a one-character label (the picker's {@code #} / {@code R} / {@code G} /
     * {@code B} / {@code A} boxes): otherwise each value box starts right after its own label and
     * the column looks ragged.
     */
    private static final int MIN_LABEL_W = 10;

    private static TextFieldWidget focused;

    private final String label;
    private final String placeholder;
    private final Consumer<String> onChange;
    private final StringBuilder text = new StringBuilder();

    public TextFieldWidget(int x, int y, int width, int height, String label, String initial, Consumer<String> onChange) {
        this(x, y, width, height, label, initial, Localization.tr("(留空则用游戏ID)"), onChange);
    }

    /** Full form with an explicit gray placeholder shown while the field is empty. */
    public TextFieldWidget(int x, int y, int width, int height, String label, String initial,
                           String placeholder, Consumer<String> onChange) {
        super(x, y, width, height);
        this.label = label;
        this.placeholder = placeholder;
        this.onChange = onChange;
        if (initial != null) {
            text.append(initial);
        }
    }

    public String text() {
        return text.toString();
    }

    public boolean isEditing() {
        return focused == this;
    }

    public void append(String s) {
        if (s == null || text.length() >= MAX_LENGTH) {
            return;
        }
        int room = MAX_LENGTH - text.length();
        if (s.length() > room) {
            s = s.substring(0, room);
        }
        text.append(s);
        onChange.accept(text.toString());
    }

    public void backspace() {
        if (text.length() > 0) {
            text.setLength(text.length() - 1);
            onChange.accept(text.toString());
        }
    }

    /**
     * Replaces the text without firing {@code onChange}, for a field whose value is owned elsewhere
     * and merely mirrored here (the colour picker's hex and channel boxes: dragging the square has
     * to update them).
     *
     * <p>Does nothing while this field is focused, so a half-typed value is never overwritten under
     * the cursor - and once focus is lost the next sync restores the canonical text, which is also
     * how junk input is discarded.
     */
    public void syncValue(String value) {
        if (value == null || isEditing() || value.contentEquals(text)) {
            return;
        }
        text.setLength(0);
        text.append(value);
    }

    public boolean contains(double x, double y) {
        return x >= getX() && x <= getX() + width && y >= getY() && y <= getY() + height;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubled) {
        if (event.button() == 0) {
            focus(this);
        }
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        boolean editing = isEditing();
        ClickGuiStyle.field(graphics, getX(), getY(), width, height, hovered(mouseX, mouseY), editing);

        ClickGuiFonts.drawText(graphics, label, getX() + 6,
                getY() + (height - ClickGuiFonts.lineHeight()) / 2 - 1, ClickGuiStyle.textDim(), true);

        int boxX = getX() + 6 + Math.max(ClickGuiFonts.width(label), MIN_LABEL_W) + 8;
        int boxW = getX() + width - boxX - 5;
        ClickGuiStyle.surface(graphics, boxX, getY() + 2, boxW, height - 4,
                ClickGuiStyle.fieldInner(), 0);

        String value = text.toString();
        String shown = value.isEmpty() ? placeholder : value;
        ClickGuiFonts.drawText(graphics, shown, boxX + 5,
                getY() + (height - ClickGuiFonts.lineHeight()) / 2 - 1,
                value.isEmpty() ? ClickGuiStyle.textMuted() : ClickGuiStyle.text(), true);

        if (editing) {
            int cx = boxX + 5 + ClickGuiFonts.width(value) + 1;
            graphics.fill(cx, getY() + 5, cx + 1, getY() + height - 5, ClickGuiStyle.text());
        }
    }

    /** Focus this field (unfocusing any other) for keyboard input. */
    private static void focus(TextFieldWidget field) {
        if (focused != null) {
            focused.setFocused(false);
        }
        focused = field;
        if (field != null) {
            field.setFocused(true);
        }
    }

    /**
     * Gives keyboard focus to {@code field}. Public for a widget that covers this one and therefore
     * receives its clicks first (MC hands a click to the first child
     * whose bounds contain it, which is the one added first - the colour block).
     */
    public static void focusField(TextFieldWidget field) {
        focus(field);
    }

    public static void clearFocus() {
        focus(null);
    }

    public static TextFieldWidget focusedField() {
        return focused;
    }
}
