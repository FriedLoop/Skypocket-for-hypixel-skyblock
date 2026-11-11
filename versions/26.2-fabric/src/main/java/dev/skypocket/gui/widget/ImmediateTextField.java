package dev.skypocket.gui.widget;

import dev.skypocket.render.ClickGuiFonts;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

import java.util.function.Predicate;

/**
 * A single-line text field drawn immediately, with no widget behind it.
 *
 * <p>It exists because widgets and immediate drawing are two different layers: a widget is painted during
 * {@code super.extractRenderState}, so anything the page or a floating card draws afterwards covers it -
 * which is exactly what happened to the colour picker's HEX/RGBA fields inside the HUD settings card. A
 * field that draws itself lives in the same layer as everything around it, which also means it can be
 * scaled or clipped freely.
 *
 * <p>Scope: single line, ASCII-oriented, with the editing people actually expect - caret, selection,
 * shift-selection, home/end, ctrl+A/C/V/X, click and drag to place the caret. It deliberately does not
 * host an IME preedit: for colour codes and search boxes that costs nothing, but it must not be used for
 * text a player types in Chinese (the composition window would not appear).
 */
public final class ImmediateTextField {
    private static final int PAD = 4;
    private static final long BLINK_MS = 500;

    /** Identity used for focus, so two fields cannot both think they are focused. */
    private final String id;
    private final int maxLength;
    private final Predicate<Character> accept;
    /** One-character label drawn inside the field ("#", "R", …), as the widget version did. */
    private final String label;
    /** True between press and release while the pointer is dragging a selection. */
    private boolean selecting;

    private String text;
    private int caret;
    private int anchor;
    private boolean focused;
    private long focusedAt;

    public ImmediateTextField(String id, String text, int maxLength, Predicate<Character> accept) {
        this(id, "", text, maxLength, accept);
    }

    public ImmediateTextField(String id, String label, String text, int maxLength,
                              Predicate<Character> accept) {
        this.id = id;
        this.label = label == null ? "" : label;
        this.text = text == null ? "" : text;
        this.maxLength = maxLength;
        this.accept = accept;
        this.caret = this.text.length();
        this.anchor = this.caret;
    }

    /** X where the value starts, i.e. after the padding and the label. */
    private int textLeft(int x) {
        int labelW = label.isEmpty() ? 0 : ClickGuiFonts.width(label) + 3;
        return x + PAD + labelW;
    }

    public String id() {
        return id;
    }

    public String text() {
        return text;
    }

    public void setText(String value) {
        String next = value == null ? "" : value;
        if (next.length() > maxLength) {
            next = next.substring(0, maxLength);
        }
        text = next;
        caret = Math.min(caret, text.length());
        anchor = caret;
    }

    public boolean focused() {
        return focused;
    }

    public void focus() {
        focused = true;
        focusedAt = System.currentTimeMillis();
    }

    public void blur() {
        focused = false;
    }

    // ---- drawing --------------------------------------------------------------------------

    public void draw(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
        ClickGuiStyle.field(graphics, x, y, w, h, focused, false);
        int textY = y + (h - ClickGuiFonts.lineHeight()) / 2;
        if (!label.isEmpty()) {
            ClickGuiFonts.drawText(graphics, label, x + PAD, textY, ClickGuiStyle.textDim(), true);
        }
        int left = textLeft(x);

        int selFrom = Math.min(caret, anchor);
        int selTo = Math.max(caret, anchor);
        if (focused && selFrom != selTo) {
            int fromX = left + ClickGuiFonts.width(text.substring(0, selFrom));
            int toX = left + ClickGuiFonts.width(text.substring(0, selTo));
            graphics.fill(fromX, y + 2, toX, y + h - 2, ClickGuiStyle.selection());
        }
        ClickGuiFonts.drawText(graphics, text, left, textY, ClickGuiStyle.text(), true);

        if (focused) {
            // Blink only while nothing is being typed at this instant; a caret that flickers off right
            // when you start typing reads as a glitch.
            long phase = (System.currentTimeMillis() - focusedAt) % (BLINK_MS * 2);
            if (phase < BLINK_MS) {
                int caretX = left + ClickGuiFonts.width(text.substring(0, caret));
                graphics.fill(caretX, y + 3, caretX + 1, y + h - 3, ClickGuiStyle.text());
            }
        }
    }

    // ---- keyboard -------------------------------------------------------------------------

    /** Appends a typed character; false when the field is not focused or the character is rejected. */
    public boolean charTyped(char c) {
        if (!focused) {
            return false;
        }
        if (accept != null && !accept.test(c)) {
            return false;
        }
        // Room is measured against what is left after the selection, not the raw length: a full field
        // with everything selected must still accept a character, or "select all and type" does nothing.
        if (room() <= 0) {
            return false;
        }
        replaceSelection(String.valueOf(c));
        return true;
    }

    /** Characters that still fit, counting the selection as already removed. */
    private int room() {
        return maxLength - (text.length() - selectedLength());
    }

    private int selectedLength() {
        return Math.abs(caret - anchor);
    }

    /** Handles a key press; false when the field is not focused or the key is not one it uses. */
    public boolean keyPressed(int key, boolean shift, boolean ctrl) {
        if (!focused) {
            return false;
        }
        switch (key) {
            case InputConstants.KEY_BACKSPACE -> {
                if (clearSelection()) {
                    return true;
                }
                if (caret > 0) {
                    text = text.substring(0, caret - 1) + text.substring(caret);
                    caret--;
                    anchor = caret;
                }
                return true;
            }
            case InputConstants.KEY_DELETE -> {
                if (clearSelection()) {
                    return true;
                }
                if (caret < text.length()) {
                    text = text.substring(0, caret) + text.substring(caret + 1);
                    anchor = caret;
                }
                return true;
            }
            case InputConstants.KEY_LEFT -> {
                moveCaret(caret - 1, shift);
                return true;
            }
            case InputConstants.KEY_RIGHT -> {
                moveCaret(caret + 1, shift);
                return true;
            }
            case InputConstants.KEY_A -> {
                if (ctrl) {
                    anchor = 0;
                    caret = text.length();
                    return true;
                }
                return false;
            }
            case InputConstants.KEY_C -> {
                if (ctrl) {
                    setClipboard(selected());
                    return true;
                }
                return false;
            }
            case InputConstants.KEY_X -> {
                if (ctrl) {
                    setClipboard(selected());
                    clearSelection();
                    return true;
                }
                return false;
            }
            case InputConstants.KEY_V -> {
                if (ctrl) {
                    String pasted = clipboard();
                    StringBuilder keep = new StringBuilder();
                    for (int i = 0; i < pasted.length() && keep.length() < maxLength; i++) {
                        char c = pasted.charAt(i);
                        if (accept == null || accept.test(c)) {
                            keep.append(c);
                        }
                    }
                    if (keep.isEmpty()) {
                        return true;
                    }
                    // Pasting into a full field replaces it: that is what someone pasting a colour code
                    // into a six-digit field expects, and it is the only way the paste can land at all.
                    if (room() <= 0) {
                        text = "";
                        caret = 0;
                        anchor = 0;
                    }
                    replaceSelection(keep.toString());
                    return true;
                }
                return false;
            }
            default -> {
                return false;
            }
        }
    }

    private void moveCaret(int to, boolean extend) {
        caret = Math.max(0, Math.min(text.length(), to));
        if (!extend) {
            anchor = caret;
        }
    }

    private String selected() {
        int from = Math.min(caret, anchor);
        int to = Math.max(caret, anchor);
        return text.substring(from, to);
    }

    private boolean clearSelection() {
        int from = Math.min(caret, anchor);
        int to = Math.max(caret, anchor);
        if (from == to) {
            return false;
        }
        text = text.substring(0, from) + text.substring(to);
        caret = from;
        anchor = from;
        return true;
    }

    private void replaceSelection(String insert) {
        clearSelection();
        int room = Math.max(0, maxLength - text.length());
        if (room <= 0) {
            return;
        }
        String clipped = insert.length() > room ? insert.substring(0, room) : insert;
        text = text.substring(0, caret) + clipped + text.substring(caret);
        caret += clipped.length();
        anchor = caret;
    }

    // ---- mouse ----------------------------------------------------------------------------

    /** Focuses the field and places the caret; returns true when the point was inside it. */
    public boolean mouseClicked(double mx, double my, int x, int y, int w, int h, boolean shift) {
        if (mx < x || mx >= x + w || my < y || my >= y + h) {
            return false;
        }
        focus();
        int index = indexAt(mx, x);
        caret = index;
        if (!shift) {
            anchor = index;
        }
        // Press-and-drag extends the selection, which is how a value gets replaced wholesale.
        selecting = true;
        return true;
    }

    /** Extends the selection while the button stays down; false when this field is not selecting. */
    public boolean mouseDragged(double mx, int x) {
        if (!focused || !selecting) {
            return false;
        }
        caret = indexAt(mx, x);
        return true;
    }

    /** Ends a press-and-drag selection. */
    public void mouseReleased() {
        selecting = false;
    }

    private int indexAt(double mx, int x) {
        int target = (int) Math.round(mx) - textLeft(x);
        if (target <= 0) {
            return 0;
        }
        for (int i = 1; i <= text.length(); i++) {
            int width = ClickGuiFonts.width(text.substring(0, i));
            if (target < width) {
                int previous = ClickGuiFonts.width(text.substring(0, i - 1));
                return target - previous < (width - previous) / 2 ? i - 1 : i;
            }
        }
        return text.length();
    }

    // ---- platform -------------------------------------------------------------------------

    private static boolean down(int key) {
//#if MC>=260300
        // 26.3 dropped GLFW; the platform key query moved to InputConstants (no window handle).
        return InputConstants.isKeyDown(key);
//#else
        long handle = GLFW.glfwGetCurrentContext();
        return handle != 0 && GLFW.glfwGetKey(handle, key) == GLFW.GLFW_PRESS;
//#endif
    }

    /**
     * Clipboard through the game's own handler.
     *
     * <p>Calling {@code glfwSetClipboardString} directly looked reasonable but does nothing here: the
     * write is accepted and the text never appears on the system clipboard (cutting a value cleared the
     * field and left the clipboard empty), so {@code Ctrl+C} and {@code Ctrl+V} had nothing to work with.
     * MC's handler is the one that actually works, because it is the same path the chat screen uses.
     */
    private static String clipboard() {
        try {
            String value = net.minecraft.client.Minecraft.getInstance().keyboardHandler.getClipboard();
            return value == null ? "" : value;
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static void setClipboard(String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        try {
            net.minecraft.client.Minecraft.getInstance().keyboardHandler.setClipboard(value);
        } catch (RuntimeException ignored) {
            // Nothing sensible to do: the field keeps its text either way.
        }
    }
}
