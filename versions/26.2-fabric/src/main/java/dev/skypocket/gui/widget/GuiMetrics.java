package dev.skypocket.gui.widget;

/**
 * The only measurements a theme may change: large-surface and control corner radii.
 *
 * <p>Layout sizes are intentionally not theme tokens. They were never consumed by the layout and
 * only made the studio look configurable while doing nothing. Keeping the two radii lets a theme
 * change the visual language without destabilising hit boxes or row heights.
 */
public record GuiMetrics(int cardRadius, int controlRadius) {

    public static final GuiMetrics DEFAULT = new GuiMetrics(
            16,  // cards, panel chrome, tabs and the theme drawer
            8    // buttons, fields, dropdowns and row highlights
    );

    private static GuiMetrics current = DEFAULT;
    private static int generation;

    public static GuiMetrics current() {
        return current;
    }

    public static int generation() {
        return generation;
    }

    public static void use(GuiMetrics metrics) {
        current = metrics == null ? DEFAULT : metrics;
        generation++;
    }
}
