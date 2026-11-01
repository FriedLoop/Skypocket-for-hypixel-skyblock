package dev.skypocket.render;

/**
 * Small color helpers shared by the HUD renderers and the config UI.
 * No native GL here - pure math only.
 */
public final class ColorUtil {
    private ColorUtil() {
    }

    public static int argb(int a, int r, int g, int b) {
        return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    /** ABGR packing, as expected by {@code NativeImage.setPixelRGBA}. */
    public static int abgr(int a, int r, int g, int b) {
        return ((a & 0xFF) << 24) | ((b & 0xFF) << 16) | ((g & 0xFF) << 8) | (r & 0xFF);
    }

    public static int rgb(int r, int g, int b) {
        return argb(255, r, g, b);
    }

    public static int red(int argb) {
        return (argb >> 16) & 0xFF;
    }

    public static int green(int argb) {
        return (argb >> 8) & 0xFF;
    }

    public static int blue(int argb) {
        return argb & 0xFF;
    }

    public static int alpha(int argb) {
        return (argb >>> 24) & 0xFF;
    }

    /** HSV -> RGB components. hue in [0,360), sat/value in [0,1]. */
    public static int[] hsvToRgb(float hue, float saturation, float value) {
        float c = value * saturation;
        float x = c * (1f - Math.abs((hue / 60f) % 2f - 1f));
        float m = value - c;
        float r = 0f, g = 0f, b = 0f;
        if (hue < 60f) {
            r = c; g = x;
        } else if (hue < 120f) {
            r = x; g = c;
        } else if (hue < 180f) {
            g = c; b = x;
        } else if (hue < 240f) {
            g = x; b = c;
        } else if (hue < 300f) {
            r = x; b = c;
        } else {
            r = c; b = x;
        }
        return new int[]{
                Math.round((r + m) * 255f),
                Math.round((g + m) * 255f),
                Math.round((b + m) * 255f)
        };
    }

    public static int hsv(int alpha, float hue, float saturation, float value) {
        int[] c = hsvToRgb(hue, saturation, value);
        return argb(alpha, c[0], c[1], c[2]);
    }

    /** RGB -> HSV. Returns {hue[0,360), sat[0,1], val[0,1]}. */
    public static float[] rgbToHsv(int r, int g, int b) {
        float rr = r / 255f, gg = g / 255f, bb = b / 255f;
        float max = Math.max(rr, Math.max(gg, bb));
        float min = Math.min(rr, Math.min(gg, bb));
        float v = max;
        float d = max - min;
        float s = max == 0f ? 0f : d / max;
        float h;
        if (d == 0f) {
            h = 0f;
        } else if (max == rr) {
            h = 60f * (((gg - bb) / d) % 6f);
        } else if (max == gg) {
            h = 60f * (((bb - rr) / d) + 2f);
        } else {
            h = 60f * (((rr - gg) / d) + 4f);
        }
        if (h < 0f) {
            h += 360f;
        }
        return new float[]{h, s, v};
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
