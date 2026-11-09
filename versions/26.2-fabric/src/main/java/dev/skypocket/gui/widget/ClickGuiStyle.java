package dev.skypocket.gui.widget;

import dev.skypocket.render.GuiPixels;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The single source of truth for the Click GUI's controls.
 *
 * <p>{@link GuiTheme} exposes only semantic colours: window, chrome, surface, control, field,
 * primary, text and switch states. Component states (hover, selected rows, disabled surfaces,
 * floating-card alpha, selection and scrollbars) are derived here. The old method names remain
 * because callers already read naturally; they now delegate to a much smaller palette.
 */
public final class ClickGuiStyle {
    /** Corner radius of a small control; the same value the cards' buttons use. */
    public static final int RADIUS = 4;
    /** Corner radius of a rounded pill (the slider track). */
    public static final int PILL = 2;

    private ClickGuiStyle() {
    }

    // ---- Panel chrome ----

    /** RGB only: the alpha is the player's background-opacity setting, combined at draw time. */
    public static int panelRgb() {
        return GuiTheme.current().window() & 0xFFFFFF;
    }

    public static int tabBarRgb() {
        return GuiTheme.current().chrome() & 0xFFFFFF;
    }

    public static int sidebarRgb() {
        return GuiTheme.current().chrome() & 0xFFFFFF;
    }

    public static int sidebarDividerRgb() {
        return mix(GuiTheme.current().chrome(), GuiTheme.current().window(), 0.5f) & 0xFFFFFF;
    }

    public static int tab() {
        return GuiTheme.current().chrome();
    }

    public static int tabHover() {
        return hover(GuiTheme.current().chrome());
    }

    public static int tabSelected() {
        return GuiTheme.current().primary();
    }

    public static int sidebarHover() {
        return rowHover();
    }

    public static int sidebarSelected() {
        return rowSelected();
    }

    public static int card() {
        return GuiTheme.current().surface();
    }

    public static int cardHover() {
        return hover(GuiTheme.current().surface());
    }

    // ---- Controls ----

    /** Secondary/neutral button - the same grey as the cards' "设置" button. */
    public static int button() {
        return GuiTheme.current().control();
    }

    public static int buttonHover() {
        return hover(GuiTheme.current().control());
    }

    /** Primary fill for enabled/selected states. Never use it for text. */
    public static int accent() {
        return GuiTheme.current().primary();
    }

    public static int accentHover() {
        return GuiTheme.current().primaryHover();
    }

    /** A surface with nothing to configure. */
    public static int disabled() {
        return mix(GuiTheme.current().surface(), GuiTheme.current().window(), 0.45f);
    }

    public static int disabledText() {
        return GuiTheme.current().textMuted();
    }

    /** Field-like surfaces: text fields, dropdowns, the search box, option lists. */
    public static int field() {
        return GuiTheme.current().field();
    }

    public static int fieldHover() {
        return hover(GuiTheme.current().field());
    }

    /** The value box inside a labelled text field. */
    public static int fieldInner() {
        return GuiTheme.current().fieldInner();
    }

    /** Option-list rows (kept flat: they are laid out back to back). */
    public static int listRowHover() {
        return rowHover();
    }

    public static int listRowCurrent() {
        return rowSelected();
    }

    /**
     * The on/off switch: its track in both states, and its ring.
     *
     * <p>{@code switchOn} is its own token rather than the primary: an ON row's surface is the
     * primary colour, so a track painted with the primary disappeared into it.
     */
    public static int switchOn() {
        return GuiTheme.current().switchOn();
    }

    public static int switchOff() {
        return GuiTheme.current().switchOff();
    }

    /** Section titles use the primary colour. */
    public static int sectionTitle() {
        return GuiTheme.current().primary();
    }

    public static int onAccent() {
        return GuiTheme.current().onPrimary();
    }

    public static int description() {
        return GuiTheme.current().textSecondary();
    }

    public static int selection() {
        return alpha(GuiTheme.current().primary(), 0x66);
    }

    public static int scrollbarTrack() {
        return alpha(GuiTheme.current().textPrimary(), 0x33);
    }

    public static int scrollbarThumb() {
        return GuiTheme.current().textMuted();
    }

    /** The floating card in the HUD position editor. */
    public static int cardFloat() {
        return alpha(GuiTheme.current().surface(), 0xF4);
    }

    public static int cardFloatTitle() {
        return alpha(GuiTheme.current().chrome(), 0xF4);
    }

    public static int cardText() {
        return GuiTheme.current().textSecondary();
    }

    public static int text() {
        return GuiTheme.current().textPrimary();
    }

    public static int textDim() {
        return GuiTheme.current().textSecondary();
    }

    public static int textMuted() {
        return GuiTheme.current().textMuted();
    }

    /** Row state for the sidebar and option lists. */
    public static int rowHover() {
        return hover(GuiTheme.current().control());
    }

    public static int rowSelected() {
        return mix(GuiTheme.current().surface(), GuiTheme.current().primary(), 0.18f);
    }

    /**
     * Rounded surface with a border ring of uniform width (the cards' structure). The ring is built in
     * device pixels by {@code fillRoundedBorderedRect}: drawing it as "outer rect, then the same rect
     * inset by one design unit" makes the four sides snap independently and come out uneven - a button
     * whose right border is thinner than its left is exactly that bug.
     */
    public static void surface(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill, int border) {
        GuiPixels.fillRoundedBorderedRect(g, x, y, w, h, RADIUS, fill, border);
    }

    /**
     * A drop shadow behind a rounded surface. The soft edge is two stacked translucent layers nudged
     * down by a pixel; the alpha is deliberately not themed, so a theme cannot make cards glow.
     */
    public static void shadow(GuiGraphicsExtractor g, int x, int y, int w, int h, int radius) {
        GuiPixels.fillRoundedRect(g, x - 2, y + 2, w + 4, h + 2, radius + 2, 0x26000000);
        GuiPixels.fillRoundedRect(g, x - 1, y + 1, w + 2, h + 1, radius + 1, 0x33000000);
    }

    /** A neutral or primary button in its hovered/plain state. */
    public static void button(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean hovered,
                              boolean accent) {
        surface(g, x, y, w, h, fillFor(hovered, accent), 0);
    }

    /** Text field / dropdown / search box. */
    public static void field(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean hovered,
                             boolean focused) {
        // No outline: focus is shown by the caret and the primary colour, not by a stroke.
        surface(g, x, y, w, h, hovered ? fieldHover() : field(), focused ? accent() : 0);
    }

    /** Rounded pill (slider track): a 4px band has no room for a border ring. */
    public static void pill(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        GuiPixels.fillRoundedRect(g, x, y, w, h, Math.min(PILL, Math.max(1, h / 2)), color);
    }

    /** The fill colour of a neutral/primary button in the given state. */
    public static int fillFor(boolean hovered, boolean accent) {
        if (accent) {
            return hovered ? accentHover() : accent();
        }
        return hovered ? buttonHover() : button();
    }

    /** A state that moves away from the base surface: lighter in dark themes, darker in light ones. */
    public static int hover(int base) {
        int target = luminance(base) < 0.45f ? 0xFFFFFFFF : 0xFF000000;
        return mix(base, target, 0.10f);
    }

    /** ARGB linear mix, used only for derived states. */
    public static int mix(int from, int to, float t) {
        float clamped = Math.max(0f, Math.min(1f, t));
        int a = (int) (((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * clamped);
        int r = (int) (((from >>> 16) & 0xFF) + (((to >>> 16) & 0xFF) - ((from >>> 16) & 0xFF)) * clamped);
        int g = (int) (((from >>> 8) & 0xFF) + (((to >>> 8) & 0xFF) - ((from >>> 8) & 0xFF)) * clamped);
        int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * clamped);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static int alpha(int color, int alpha) {
        return ((alpha & 0xFF) << 24) | (color & 0xFFFFFF);
    }

    private static float luminance(int color) {
        float r = ((color >>> 16) & 0xFF) / 255f;
        float g = ((color >>> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        return 0.2126f * r + 0.7152f * g + 0.0722f * b;
    }
}
