package dev.skypocket.ui.widget;

import dev.skypocket.ui.Rect;
import dev.skypocket.ui.Style;
import dev.skypocket.ui.UiPainter;
import dev.skypocket.ui.input.PointerEvent;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * The v2 equivalent of the old ImmediateColorPicker: a saturation/value square, hue and alpha
 * bars, and the preset swatches. HEX/RGBA editors live beside it as v2 TextFields.
 */
public final class ColorPickerPanel extends Control {
    static final int PAD = 8;
    static final int SQUARE = 132;
    static final int BAR_W = 18;
    static final int GAP = 8;
    static final int FIELD_X = 206;
    static final int FIELD_W = 52;
    static final int FIELD_H = 20;
    static final int CHANNEL_STEP = 28;
    static final int PRESET_X = 266;
    static final int PRESET_Y = 8;
    static final int PRESET_W = 16;
    static final int PRESET_H = 16;
    static final int PRESET_GAP = 3;
    static final int PRESET_COLS = 2;

    static final int CONTENT_W = 309;
    static final int CONTENT_H = 148;

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
    private Zone drag = Zone.NONE;
    private int lastEmitted = Integer.MIN_VALUE;
    private float hue;
    private float saturation = 1f;
    private float value = 1f;
    private boolean seeded;
    private float mouseX;
    private float mouseY;

    ColorPickerPanel(IntSupplier getter, IntConsumer setter, Style style, WidgetPalette palette) {
        super(style, palette);
        this.getter = getter;
        this.setter = setter;
    }

    void seed(int argb) {
        adopt(argb);
        lastEmitted = argb;
    }

    @Override
    protected void activate() {
        // Pointer interaction is handled directly by the picker.
    }

    @Override
    public boolean acceptsPointer() {
        return true;
    }

    @Override
    public void onHoverChanged(boolean hovered) {
        if (!hovered) {
            // The pointer moved to a field or outside the popup; do not keep a preset hover ring.
            mouseX = -1f;
            mouseY = -1f;
        }
    }

    @Override
    public boolean onPointerDown(PointerEvent event) {
        float x = event.x() - ancestorOffsetX();
        float y = event.y() - ancestorOffsetY();
        mouseX = x;
        mouseY = y;
        drag = zoneAt(x, y);
        if (drag == Zone.NONE) {
            for (int i = 0; i < PRESETS.length; i++) {
                float px = presetX(i);
                float py = presetY(i);
                if (x >= px && x < px + PRESET_W && y >= py && y < py + PRESET_H) {
                    applyPreset(PRESETS[i]);
                    return true;
                }
            }
        }
        if (drag != Zone.NONE) {
            pressed = true;
            apply(x, y);
            return true;
        }
        return false;
    }

    @Override
    public boolean onPointerMove(PointerEvent event) {
        float x = event.x() - ancestorOffsetX();
        float y = event.y() - ancestorOffsetY();
        mouseX = x;
        mouseY = y;
        if (pressed && drag != Zone.NONE) {
            apply(x, y);
            return true;
        }
        return false;
    }

    @Override
    public boolean onPointerUp(PointerEvent event) {
        pressed = false;
        drag = Zone.NONE;
        return true;
    }

    private void applyPreset(int rgb) {
        int next = (getter.getAsInt() & 0xFF000000) | (rgb & 0xFFFFFF);
        adopt(next);
        emit(next);
    }

    private Zone zoneAt(float x, float y) {
        int sx = PAD;
        int sy = PAD;
        if (x >= sx && x < sx + SQUARE && y >= sy && y < sy + SQUARE) {
            return Zone.SQUARE;
        }
        if (y >= sy && y < sy + SQUARE) {
            if (x >= hueX() && x < hueX() + BAR_W) {
                return Zone.HUE;
            }
            if (x >= alphaX() && x < alphaX() + BAR_W) {
                return Zone.ALPHA;
            }
        }
        return Zone.NONE;
    }

    private void apply(float x, float y) {
        int argb = getter.getAsInt();
        switch (drag) {
            case SQUARE -> {
                saturation = clamp01((x - PAD) / SQUARE);
                value = 1f - clamp01((y - PAD) / SQUARE);
                emit(hsvToArgb(hue, saturation, value, (argb >>> 24) & 0xFF));
            }
            case HUE -> {
                hue = clamp01((y - PAD) / SQUARE) * 360f;
                emit(hsvToArgb(hue, saturation, value, (argb >>> 24) & 0xFF));
            }
            case ALPHA -> {
                int alpha = Math.round(clamp01((y - PAD) / SQUARE) * 255f);
                emit((alpha << 24) | (argb & 0xFFFFFF));
            }
            default -> {
            }
        }
    }

    private void emit(int argb) {
        lastEmitted = argb;
        setter.accept(argb);
    }

    void adopt(int argb) {
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        float nextHue;
        if (delta < 1e-6f) {
            nextHue = hue;
        } else if (max == r) {
            nextHue = 60f * (((g - b) / delta) % 6f);
        } else if (max == g) {
            nextHue = 60f * (((b - r) / delta) + 2f);
        } else {
            nextHue = 60f * (((r - g) / delta) + 4f);
        }
        if (nextHue < 0f) {
            nextHue += 360f;
        }
        if (delta > 1e-6f || !seeded) {
            hue = nextHue;
        }
        saturation = max < 1e-6f ? 0f : delta / max;
        value = max;
        seeded = true;
        lastEmitted = argb;
    }

    private int hueX() {
        return PAD + SQUARE + GAP;
    }

    private int alphaX() {
        return hueX() + BAR_W + GAP;
    }

    private int presetX(int index) {
        return PRESET_X + (index % PRESET_COLS) * (PRESET_W + PRESET_GAP);
    }

    private int presetY(int index) {
        return PRESET_Y + (index / PRESET_COLS) * (PRESET_H + PRESET_GAP);
    }

    @Override
    public void paint(UiPainter painter) {
        int argb = getter.getAsInt();
        if (!pressed && argb != lastEmitted) {
            adopt(argb);
        }
        float bx = bounds().x();
        float by = bounds().y();

        // Saturation/value square: hue base, white gradient along x, black gradient along y.
        Rect square = new Rect(bx + PAD, by + PAD, SQUARE, SQUARE);
        painter.fill(square, hsvToArgb(hue, 1f, 1f, 255));
        painter.fillHorizontalGradient(square, 0xFFFFFFFF, 0x00FFFFFF);
        painter.fillGradient(square, 0x00000000, 0xFF000000);

        // Hue bar.
        float hx = bx + hueX();
        for (int i = 0; i < HUE_STOPS.length - 1; i++) {
            float top = by + PAD + SQUARE * i / (float) (HUE_STOPS.length - 1);
            float bottom = by + PAD + SQUARE * (i + 1) / (float) (HUE_STOPS.length - 1);
            painter.fillGradient(new Rect(hx, top, BAR_W, bottom - top),
                    HUE_STOPS[i], HUE_STOPS[i + 1]);
        }

        // Alpha bar: checkerboard beneath the alpha ramp.
        float ax = bx + alphaX();
        for (int cy = 0; cy < SQUARE; cy += 8) {
            for (int cx = 0; cx < BAR_W; cx += 8) {
                boolean light = ((cx / 8) + (cy / 8)) % 2 == 0;
                painter.fill(new Rect(ax + cx, by + PAD + cy,
                        Math.min(8, BAR_W - cx), Math.min(8, SQUARE - cy)),
                        light ? 0xFFB0B0B0 : 0xFF6E6E6E);
            }
        }
        painter.fillGradient(new Rect(ax, by + PAD, BAR_W, SQUARE),
                argb & 0x00FFFFFF, (argb & 0x00FFFFFF) | 0xFF000000);

        // Markers.
        float ringX = bx + PAD + Math.min(SQUARE - 1, saturation * SQUARE);
        float ringY = by + PAD + Math.min(SQUARE - 1, (1f - value) * SQUARE);
        ring(painter, ringX, ringY);
        barMarker(painter, hx, by + PAD + Math.min(SQUARE - 1, hue / 360f * SQUARE));
        barMarker(painter, ax, by + PAD + Math.min(SQUARE - 1,
                ((argb >>> 24) & 0xFF) / 255f * SQUARE));

        // Presets.
        int current = argb & 0xFFFFFF;
        for (int i = 0; i < PRESETS.length; i++) {
            float px = bx + presetX(i);
            float py = by + presetY(i);
            boolean selected = PRESETS[i] == current;
            boolean hovered = mouseX >= presetX(i) && mouseX < presetX(i) + PRESET_W
                    && mouseY >= presetY(i) && mouseY < presetY(i) + PRESET_H;
            // Selection owns the border; hover only applies to unselected swatches. Otherwise a
            // quick click leaves a white hover ring that looks like a stale selection.
            int border = selected ? palette.accent() : hovered ? 0xFFFFFFFF : 0;
            if (border != 0) {
                painter.outlineRounded(new Rect(px - 1f, py - 1f, PRESET_W + 2f, PRESET_H + 2f),
                        3f, border);
            }
            painter.fillRounded(new Rect(px, py, PRESET_W, PRESET_H), 2f, 0xFF000000 | PRESETS[i]);
        }
    }

    private static void ring(UiPainter painter, float cx, float cy) {
        painter.fill(new Rect(cx - 4f, cy - 4f, 9f, 9f), 0xFF000000);
        painter.fill(new Rect(cx - 3f, cy - 3f, 7f, 7f), 0xFFFFFFFF);
        painter.fill(new Rect(cx - 2f, cy - 2f, 5f, 5f), 0x40000000);
    }

    private static void barMarker(UiPainter painter, float x, float cy) {
        painter.fill(new Rect(x - 2f, cy - 2f, 22f, 5f), 0xFF000000);
        painter.fill(new Rect(x - 1f, cy - 1f, 20f, 3f), 0xFFFFFFFF);
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static int hsvToArgb(float hue, float saturation, float value, int alpha) {
        float h = ((hue % 360f) + 360f) % 360f / 60f;
        int sector = (int) Math.floor(h);
        float f = h - sector;
        float p = value * (1f - saturation);
        float q = value * (1f - saturation * f);
        float t = value * (1f - saturation * (1f - f));
        float r;
        float g;
        float b;
        switch (sector % 6) {
            case 0 -> { r = value; g = t; b = p; }
            case 1 -> { r = q; g = value; b = p; }
            case 2 -> { r = p; g = value; b = t; }
            case 3 -> { r = p; g = q; b = value; }
            case 4 -> { r = t; g = p; b = value; }
            default -> { r = value; g = p; b = q; }
        }
        return ((alpha & 0xFF) << 24) | (Math.round(r * 255f) << 16)
                | (Math.round(g * 255f) << 8) | Math.round(b * 255f);
    }
}
