package dev.skypocket.ui;

/**
 * How the kernel measures text it has to draw itself.
 *
 * <p>Layout already goes through {@link TextMeasurer}, but a couple of widgets need to measure inside
 * their own paint - a text field has to know where the caret sits in the string, and its label
 * offsets the editable area. Those calls used the render package's font service directly, which is
 * why {@code ui} still imported {@code render}.
 *
 * <p>This is a single installation point rather than a constructor parameter: the measurer is global
 * (one font stack per game) and threading it through six widget constructors and their callers would
 * add noise to every page for something that never varies at runtime. The host installs it once at
 * startup.
 */
public final class UiText {
    private static TextMeasurer measurer = (text, maxWidth) -> Size.ZERO;

    private UiText() {
    }

    /** Installs the measurer used by widgets that draw text. Called once during client setup. */
    public static void install(TextMeasurer value) {
        measurer = value == null ? (text, maxWidth) -> Size.ZERO : value;
    }

    /** Width of the text at the current font scale. */
    public static float width(String text) {
        if (text == null || text.isEmpty()) {
            return 0f;
        }
        return measurer.measure(text, Float.POSITIVE_INFINITY).width();
    }

    /** Line height of the current font. */
    public static float lineHeight() {
        return measurer.measure("", Float.POSITIVE_INFINITY).height();
    }
}
