package dev.skypocket.gui.widget;

import dev.skypocket.render.ClickGuiFonts;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix3x2fStack;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * The colour picker, drawn immediately: saturation/value square, hue and alpha bars, HEX/RGBA fields
 *
 * <p>Geometry, order, presets and markers are the ones the settings page has always used, so the
 * picker looks the same everywhere while nothing behind it is a widget. A widget is painted during
 * {@code super.extractRenderState}, so anything a page or a floating card draws afterwards covers it;
 * drawing the picker ourselves puts it in the same layer as its surroundings, which is what lets it live
 * inside the HUD settings card (and be drawn at any size).
 *
 * <p>The text fields are {@link ImmediateTextField}s for the same reason. They deliberately have no IME
 * preedit: colour codes and hex digits are ASCII, and anything a Chinese IME commits that is not a hex
 * digit is simply rejected.
 */
public final class ImmediateColorPicker {
    private static final dev.skypocket.SkyPocketLog LOGGER =
            dev.skypocket.SkyPocketLog.of("colorPicker");

    // Geometry, unchanged from the widget version so the picker still looks the same.
    private static final int PAD = 8;
    private static final int SQUARE = 132;
    private static final int BAR_W = 18;
    private static final int GAP = 8;
    private static final int FIELD_GAP = 14;
    private static final int HEX_GAP = 8;
    private static final int CHANNEL_GAP = 8;
    private static final int PRESET_GAP_X = 10;
    private static final int PRESET_COLS = 2;
    private static final int PRESET_W = 16;
    private static final int PRESET_H = 16;
    private static final int PRESET_GAP = 3;
    private static final int FIELD_W = 52;
    private static final int FIELD_H = 20;

    private static final int PRESET_BLOCK_W = PRESET_COLS * PRESET_W + (PRESET_COLS - 1) * PRESET_GAP;
    /** Width and height of the whole block, as the owning layout must reserve. */
    public static final int CONTENT_W = PAD + SQUARE + GAP + BAR_W + GAP + BAR_W + FIELD_GAP
            + FIELD_W + PRESET_GAP_X + PRESET_BLOCK_W + PAD;
    public static final int CONTENT_H = PAD * 2 + SQUARE;

    private static final int[] HUE_STOPS = {
            0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF, 0xFF0000FF, 0xFFFF00FF, 0xFFFF0000
    };
    private static final int[] PRESETS = {
            0xFFFFFF, 0xC0C0C0,
            0x606060, 0x000000,
            0xFF0000, 0xFF8000,
            0xFFFF00, 0x00FF00,
            0x00FFFF, 0x0000FF,
            0x8000FF, 0xFF00FF,
            0xFF0080, 0x00FF80
    };

    private enum Zone { NONE, SQUARE, HUE, ALPHA }

    private final IntSupplier getter;
    private final IntConsumer setter;
    private final PickerMemory memory;
    private final ImmediateTextField hex;
    private final ImmediateTextField[] channels = new ImmediateTextField[4];

    private Zone drag = Zone.NONE;
    /** The colour this picker last wrote; see {@link #emit}. */
    private int lastEmitted = Integer.MIN_VALUE;

    public ImmediateColorPicker(IntSupplier getter, IntConsumer setter,
                                PickerMemory memory) {
        this.getter = getter;
        this.setter = setter;
        this.memory = memory;
        this.hex = new ImmediateTextField("picker.hex", "#", hexDigits(getter.getAsInt()), 6,
                c -> c == '#' || Character.digit(c, 16) >= 0);
        String[] names = {"R", "G", "B", "A"};
        int[] shifts = {16, 8, 0, 24};
        for (int i = 0; i < 4; i++) {
            int shift = shifts[i];
            channels[i] = new ImmediateTextField("picker." + names[i], names[i],
                    Integer.toString((getter.getAsInt() >> shift) & 0xFF), 3,
                    c -> Character.isDigit(c));
        }
    }

    // ---- positions ------------------------------------------------------------------------

    private int squareX(int x) {
        return x + PAD;
    }

    private int squareY(int y) {
        return y + PAD;
    }

    private int hueX(int x) {
        return squareX(x) + SQUARE + GAP;
    }

    private int alphaX(int x) {
        return hueX(x) + BAR_W + GAP;
    }

    private int fieldX(int x) {
        return alphaX(x) + BAR_W + FIELD_GAP;
    }

    private int hexFieldY(int y) {
        return squareY(y);
    }

    private int channelFieldY(int y, int index) {
        return squareY(y) + FIELD_H + HEX_GAP + index * (FIELD_H + CHANNEL_GAP);
    }

    private int presetX(int x, int index) {
        return fieldX(x) + FIELD_W + PRESET_GAP_X + (index % PRESET_COLS) * (PRESET_W + PRESET_GAP);
    }

    private int presetY(int y, int index) {
        return squareY(y) + (index / PRESET_COLS) * (PRESET_H + PRESET_GAP);
    }

    // ---- drawing --------------------------------------------------------------------------

    /** Applies a local text scale to the HEX/RGBA fields; the picker geometry is unchanged. */
    public ImmediateColorPicker textScale(float scale) {
        hex.textScale(scale);
        for (ImmediateTextField channel : channels) {
            channel.textScale(scale);
        }
        return this;
    }

    public ImmediateColorPicker screenSpace(boolean value) {
        hex.screenSpace(value);
        for (ImmediateTextField channel : channels) {
            channel.screenSpace(value);
        }
        return this;
    }

    public void draw(GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
        int argb = getter.getAsInt();
        sync(argb, x, y);

        ClickGuiStyle.surface(graphics, x, y, CONTENT_W, CONTENT_H, ClickGuiStyle.field(),
                0);

        float hue = memory.hue();
        float sat = memory.saturation();
        float val = memory.value();

        // Square: flat hue, white along x, black along y.
        int sx = squareX(x);
        int sy = squareY(y);
        graphics.fill(sx - 1, sy - 1, sx + SQUARE + 1, sy + SQUARE + 1, 0);
        graphics.fill(sx, sy, sx + SQUARE, sy + SQUARE, hsvToArgb(hue, 1f, 1f, 255));
        fillHorizontalGradient(graphics, sx, sy, SQUARE, SQUARE, 0xFFFFFFFF, 0x00FFFFFF);
        graphics.fillGradient(sx, sy, sx + SQUARE, sy + SQUARE, 0x00000000, 0xFF000000);

        // Hue bar.
        int hx = hueX(x);
        graphics.fill(hx - 1, sy - 1, hx + BAR_W + 1, sy + SQUARE + 1, 0);
        for (int i = 0; i < HUE_STOPS.length - 1; i++) {
            int top = sy + SQUARE * i / (HUE_STOPS.length - 1);
            int bottom = sy + SQUARE * (i + 1) / (HUE_STOPS.length - 1);
            graphics.fillGradient(hx, top, hx + BAR_W, bottom, HUE_STOPS[i], HUE_STOPS[i + 1]);
        }

        // Alpha bar: checkerboard under an alpha ramp.
        int ax = alphaX(x);
        graphics.fill(ax - 1, sy - 1, ax + BAR_W + 1, sy + SQUARE + 1, 0);
        for (int cy = 0; cy < SQUARE; cy += 8) {
            for (int cx = 0; cx < BAR_W; cx += 8) {
                boolean light = ((cx / 8) + (cy / 8)) % 2 == 0;
                graphics.fill(ax + cx, sy + cy, ax + Math.min(BAR_W, cx + 8),
                        sy + Math.min(SQUARE, cy + 8), light ? 0xFFB0B0B0 : 0xFF6E6E6E);
            }
        }
        graphics.fillGradient(ax, sy, ax + BAR_W, sy + SQUARE, argb & 0x00FFFFFF,
                (argb & 0x00FFFFFF) | 0xFF000000);

        // Markers. Clamped one pixel inside the area: at full saturation/value (or alpha 255) the exact
        // position is the border, and a marker centred on it hangs half outside and reads as misplaced.
        int ringX = sx + Math.min(SQUARE - 1, Math.round(sat * SQUARE));
        int ringY = sy + Math.min(SQUARE - 1, Math.round((1f - val) * SQUARE));
        ring(graphics, ringX, ringY);
        barMarker(graphics, hx, sy + Math.min(SQUARE - 1, Math.round(hue / 360f * SQUARE)));
        barMarker(graphics, ax,
                sy + Math.min(SQUARE - 1, Math.round(((argb >>> 24) & 0xFF) / 255f * SQUARE)));

        // Fields.
        hex.draw(graphics, fieldX(x), hexFieldY(y), FIELD_W, FIELD_H);
        for (int i = 0; i < 4; i++) {
            channels[i].draw(graphics, fieldX(x), channelFieldY(y, i), FIELD_W, FIELD_H);
        }

        // Presets.
        int current = argb & 0xFFFFFF;
        for (int i = 0; i < PRESETS.length; i++) {
            int px = presetX(x, i);
            int py = presetY(y, i);
            boolean hovered = mouseX >= px && mouseX < px + PRESET_W
                    && mouseY >= py && mouseY < py + PRESET_H;
            int border = 0;
            if (hovered) {
                border = 0xFFFFFFFF;
            } else if (PRESETS[i] == current) {
                border = ClickGuiStyle.accent();
            }
            ClickGuiStyle.surface(graphics, px, py, PRESET_W, PRESET_H, 0xFF000000 | PRESETS[i],
                    border);
        }
    }

    private static void ring(GuiGraphicsExtractor graphics, int cx, int cy) {
        graphics.fill(cx - 4, cy - 4, cx + 5, cy + 5, 0xFF000000);
        graphics.fill(cx - 3, cy - 3, cx + 4, cy + 4, 0xFFFFFFFF);
        graphics.fill(cx - 2, cy - 2, cx + 3, cy + 3, 0x40000000);
    }

    private static void barMarker(GuiGraphicsExtractor graphics, int x, int cy) {
        graphics.fill(x - 2, cy - 2, x + BAR_W + 2, cy + 3, 0xFF000000);
        graphics.fill(x - 1, cy - 1, x + BAR_W + 1, cy + 2, 0xFFFFFFFF);
    }

    private static void fillHorizontalGradient(GuiGraphicsExtractor graphics, int x, int y, int w,
                                               int h, int left, int right) {
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.rotate((float) (Math.PI / 2));
        graphics.fillGradient(0, -w, h, 0, right, left);
        pose.popMatrix();
    }

    // ---- interaction ----------------------------------------------------------------------

    /** True when the point was inside the picker and the click was used. */
    public boolean mouseClicked(double mx, double my, int x, int y, boolean shift) {
        int argb = getter.getAsInt();
        if (hex.mouseClicked(mx, my, fieldX(x), hexFieldY(y), FIELD_W, FIELD_H, shift)) {
            blurOthers(hex);
            return true;
        }
        for (int i = 0; i < 4; i++) {
            if (channels[i].mouseClicked(mx, my, fieldX(x), channelFieldY(y, i), FIELD_W, FIELD_H,
                    shift)) {
                blurOthers(channels[i]);
                return true;
            }
        }
        for (int i = 0; i < PRESETS.length; i++) {
            int px = presetX(x, i);
            int py = presetY(y, i);
            if (mx >= px && mx < px + PRESET_W && my >= py && my < py + PRESET_H) {
                applyPreset(PRESETS[i]);
                return true;
            }
        }
        drag = zoneAt(mx, my, x, y);
        if (drag != Zone.NONE) {
            blurFields();
            apply(mx, my, x, y, argb);
            return true;
        }
        return mx >= x && mx < x + CONTENT_W && my >= y && my < y + CONTENT_H;
    }

    public boolean mouseDragged(double mx, double my, int x, int y) {
        ImmediateTextField focused = focusedField();
        if (focused != null && focused.mouseDragged(mx, fieldX(x))) {
            return true;
        }
        if (drag == Zone.NONE) {
            return false;
        }
        apply(mx, my, x, y, getter.getAsInt());
        return true;
    }

    public void mouseReleased() {
        drag = Zone.NONE;
        hex.mouseReleased();
        for (ImmediateTextField channel : channels) {
            channel.mouseReleased();
        }
    }

    /** Routes a typed character to the focused field; true when the picker used it. */
    public boolean charTyped(char c) {
        ImmediateTextField focused = focusedField();
        if (focused == null || !focused.charTyped(c)) {
            return false;
        }
        commitField(focused, false);
        // The settings page draws this picker inside its cached panel, so a keystroke has to invalidate
        // the cache or the new character would not appear until something else changed.
        dev.skypocket.config.ConfigManager.touch();
        return true;
    }

    /** Routes a key press; returns true when the picker used it. */
    public boolean keyPressed(int key, boolean shift, boolean ctrl) {
        ImmediateTextField focused = focusedField();
        if (focused == null) {
            return false;
        }
        // Enter commits and closes the editor for that field - the fallback for a value that is only
        // valid as a whole ("FFFFFF"), where nothing is applied until the text is complete.
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
                || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
            commitField(focused, true);
            focused.blur();
            return true;
        }
        boolean used = focused.keyPressed(key, shift, ctrl);
        if (used) {
            commitField(focused, false);
            dev.skypocket.config.ConfigManager.touch();
        }
        return used;
    }

    public boolean hasFocus() {
        return focusedField() != null;
    }

    public void blur() {
        // Closing the editor commits: whatever is valid in the field is applied, and everything else is
        // normalised back to the current colour instead of being left as typed text.
        ImmediateTextField focused = focusedField();
        if (focused != null) {
            commitField(focused, true);
        }
        blurFields();
    }

    private ImmediateTextField focusedField() {
        if (hex.focused()) {
            return hex;
        }
        for (ImmediateTextField channel : channels) {
            if (channel.focused()) {
                return channel;
            }
        }
        return null;
    }

    private void blurFields() {
        hex.blur();
        for (ImmediateTextField channel : channels) {
            channel.blur();
        }
    }

    private void blurOthers(ImmediateTextField keep) {
        // The field losing focus commits first, so switching from HEX to a channel keeps what was typed.
        if (keep != hex && hex.focused()) {
            commitField(hex, true);
            hex.blur();
        }
        for (ImmediateTextField channel : channels) {
            if (channel != keep && channel.focused()) {
                commitField(channel, true);
                channel.blur();
            }
        }
    }

    /** Writes whatever the field now says; junk keeps the current colour, like the widget did. */
    private void commitField(ImmediateTextField field, boolean allowShort) {
        int argb = getter.getAsInt();
        if (field == hex) {
            Integer parsed = parseHex(field.text(), (argb >>> 24) & 0xFF, allowShort);
            LOGGER.debug("hex field '{}' -> {}", field.text(), parsed);
            // Null, not a negative sentinel: every opaque colour is a negative int (alpha 0xFF puts the
            // sign bit up), so `parsed >= 0` rejected precisely the valid values. That is why the HEX row
            // silently ignored input while the 0-255 channel fields worked.
            if (parsed != null) {
                // Typing is an outside change relative to the square: the marker must follow it.
                adopt(parsed);
                emit(parsed);
            }
            return;
        }
        int[] shifts = {16, 8, 0, 24};
        for (int i = 0; i < 4; i++) {
            if (field != channels[i]) {
                continue;
            }
            int value;
            try {
                value = Integer.parseInt(field.text().trim());
            } catch (NumberFormatException e) {
                return;
            }
            // Clamp rather than reject: typing "300" should land on 255 and say so, not silently keep
            // the old colour while the field shows a value that cannot exist.
            int clamped = Math.max(0, Math.min(255, value));
            if (clamped != value) {
                field.setText(Integer.toString(clamped));
            }
            int mask = 0xFF << shifts[i];
            int next = (argb & ~mask) | (clamped << shifts[i]);
            adopt(next);
            emit(next);
            return;
        }
    }

    private Zone zoneAt(double mx, double my, int x, int y) {
        int sy = squareY(y);
        if (mx >= squareX(x) && mx < squareX(x) + SQUARE && my >= sy && my < sy + SQUARE) {
            return Zone.SQUARE;
        }
        boolean inBarY = my >= sy && my < sy + SQUARE;
        if (inBarY && mx >= hueX(x) && mx < hueX(x) + BAR_W) {
            return Zone.HUE;
        }
        if (inBarY && mx >= alphaX(x) && mx < alphaX(x) + BAR_W) {
            return Zone.ALPHA;
        }
        return Zone.NONE;
    }

    private void apply(double mx, double my, int x, int y, int argb) {
        int sy = squareY(y);
        float hue = memory.hue();
        float sat = memory.saturation();
        float val = memory.value();
        int alpha = (argb >>> 24) & 0xFF;
        switch (drag) {
            case SQUARE -> {
                sat = clamp01((float) ((mx - squareX(x)) / SQUARE));
                val = 1f - clamp01((float) ((my - sy) / SQUARE));
                memory.set(hue, sat, val);
                emit(hsvToArgb(hue, sat, val, alpha));
            }
            case HUE -> {
                hue = clamp01((float) ((my - sy) / SQUARE)) * 360f;
                memory.set(hue, sat, val);
                emit(hsvToArgb(hue, sat, val, alpha));
            }
            case ALPHA -> {
                int next = Math.round(clamp01((float) ((my - sy) / SQUARE)) * 255f);
                emit((next << 24) | (argb & 0xFFFFFF));
            }
            default -> {
            }
        }
    }

    private void applyPreset(int rgb) {
        float[] hsv = toHsv(0xFF000000 | rgb);
        memory.set(hsv[0], hsv[1], hsv[2]);
        emit((getter.getAsInt() & 0xFF000000) | (rgb & 0xFFFFFF));
    }

    /**
     * Writes a colour and remembers it as ours.
     *
     * <p>{@link #sync} re-derives the marker from the colour, which is right for a change made somewhere
     * else but wrong for one we just made: the round trip through 8-bit channels moves saturation by a
     * step or two, so the marker was nudged every frame and visibly jittered - and at value 0 the colour is
     * black, whose saturation reads as 0, which is what threw the marker from the bottom-right corner to
     * the bottom-left.
     */
    private void emit(int argb) {
        lastEmitted = argb;
        setter.accept(argb);
    }

    /** Takes hue/saturation/value from a colour that came from outside (typing, a preset, a reset). */
    private void adopt(int argb) {
        float[] hsv = toHsv(argb);
        // Saturation and value are adopted so white/black land on the square's corner; the hue is kept
        // when it is undefined (greys), otherwise dragging through grey would reset the hue bar.
        float hue = hsv[1] > 0.001f && hsv[2] > 0.001f ? hsv[0] : memory.hue();
        memory.set(hue, hsv[1], hsv[2]);
        lastEmitted = argb;
    }

    /** Equal to within one 8-bit step, i.e. the colour we wrote after rounding. */
    private static boolean sameColor(int a, int b) {
        return a == b
                || (Math.abs(((a >> 16) & 0xFF) - ((b >> 16) & 0xFF)) <= 1
                && Math.abs(((a >> 8) & 0xFF) - ((b >> 8) & 0xFF)) <= 1
                && Math.abs((a & 0xFF) - (b & 0xFF)) <= 1);
    }

    // ---- state sync -----------------------------------------------------------------------

    /** Seeds the memory from the colour and mirrors the fields while they are not being typed in. */
    private void sync(int argb, int x, int y) {
        // Only adopt a colour that is not the one this picker last wrote: during a drag the colour is our
        // own output, and re-deriving the marker from it fed rounding noise back into the cursor position.
        if (!memory.seeded() || !sameColor(argb, lastEmitted)) {
            adopt(argb);
        }
        if (!hex.focused()) {
            hex.setText(hexDigits(argb));
        }
        int[] shifts = {16, 8, 0, 24};
        for (int i = 0; i < 4; i++) {
            if (!channels[i].focused()) {
                channels[i].setText(Integer.toString((argb >> shifts[i]) & 0xFF));
            }
        }
    }

    // ---- colour maths ---------------------------------------------------------------------

    public static String hexOf(int argb) {
        return String.format("#%06X", argb & 0xFFFFFF);
    }

    /** The six hex digits without the leading #, since the field shows # as its label. */
    private static String hexDigits(int argb) {
        return String.format("%06X", argb & 0xFFFFFF);
    }

    /**
     * Parses the field's text into an ARGB colour, or -1 when it is not a complete code.
     *
     * <p>Local rather than borrowed from the widget version: the field holds bare digits (its label
     * carries the {@code #}), and going through the old static helper made the HEX row silently reject
     * everything while the channel fields worked. Accepts 3 digits (expanded, like CSS) or 6.
     */
    private static Integer parseHex(String text, int alpha, boolean allowShort) {
        if (text == null) {
            return null;
        }
        String s = text.trim();
        if (s.startsWith("#")) {
            s = s.substring(1);
        }
        if (s.length() == 3 && allowShort) {
            StringBuilder expanded = new StringBuilder(6);
            for (int i = 0; i < 3; i++) {
                expanded.append(s.charAt(i)).append(s.charAt(i));
            }
            s = expanded.toString();
        }
        if (s.length() != 6) {
            return null;
        }
        try {
            return ((alpha & 0xFF) << 24) | Integer.parseInt(s, 16);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int hsvToArgb(float hue, float sat, float val, int alpha) {
        float h = ((hue % 360f) + 360f) % 360f / 60f;
        int sector = (int) Math.floor(h);
        float f = h - sector;
        float p = val * (1f - sat);
        float q = val * (1f - sat * f);
        float t = val * (1f - sat * (1f - f));
        float r;
        float g;
        float b;
        switch (sector % 6) {
            case 0 -> { r = val; g = t; b = p; }
            case 1 -> { r = q; g = val; b = p; }
            case 2 -> { r = p; g = val; b = t; }
            case 3 -> { r = p; g = q; b = val; }
            case 4 -> { r = t; g = p; b = val; }
            default -> { r = val; g = p; b = q; }
        }
        return ((alpha & 0xFF) << 24) | (Math.round(r * 255f) << 16)
                | (Math.round(g * 255f) << 8) | Math.round(b * 255f);
    }

    /** RGB -> {hue in degrees, saturation, value}. */
    private static float[] toHsv(int argb) {
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        float hue;
        if (delta < 1e-6f) {
            hue = 0f;
        } else if (max == r) {
            hue = 60f * (((g - b) / delta) % 6f);
        } else if (max == g) {
            hue = 60f * (((b - r) / delta) + 2f);
        } else {
            hue = 60f * (((r - g) / delta) + 4f);
        }
        if (hue < 0f) {
            hue += 360f;
        }
        return new float[]{hue, max < 1e-6f ? 0f : delta / max, max};
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
