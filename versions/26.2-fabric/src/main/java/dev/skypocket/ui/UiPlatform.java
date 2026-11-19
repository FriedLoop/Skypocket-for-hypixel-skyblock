package dev.skypocket.ui;

/**
 * The platform services the kernel needs but cannot implement.
 *
 * <p>Three things are unavoidable at the boundary: measuring text the way the painter will draw it,
 * the size of a design unit on screen, and the text clipboard. None of them vary at runtime, and all
 * three would otherwise force every widget that needs one to import the game.
 *
 * <p>Installed once during client setup from {@code ui.mc}. Until then each falls back to a neutral
 * default, so a headless test or a widget constructed early does not fail - it just measures zero.
 */
public final class UiPlatform {
    private static volatile TextMeasurer text = (value, maxWidth) -> Size.ZERO;
    private static volatile GuiScale guiScale = GuiScale.ONE;
    private static volatile Clipboard clipboard = Clipboard.NONE;

    private UiPlatform() {
    }

    public static void install(TextMeasurer measurer, GuiScale scale, Clipboard text) {
        UiPlatform.text = measurer == null ? (v, w) -> Size.ZERO : measurer;
        UiPlatform.guiScale = scale == null ? GuiScale.ONE : scale;
        UiPlatform.clipboard = text == null ? Clipboard.NONE : text;
    }

    /** Width of the text at the current font, in design units. */
    public static float textWidth(String value) {
        if (value == null || value.isEmpty()) {
            return 0f;
        }
        return text.measure(value, Float.POSITIVE_INFINITY).width();
    }

    /** Line height at the current font. */
    public static float lineHeight() {
        return text.measure("", Float.POSITIVE_INFINITY).height();
    }

    /** Device pixels per design unit. */
    public static float guiScale() {
        return Math.max(1.0e-4f, guiScale.get());
    }

    public static Clipboard clipboard() {
        return clipboard;
    }
}
