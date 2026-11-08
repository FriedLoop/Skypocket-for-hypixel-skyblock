package dev.skypocket.gui.widget;

import dev.skypocket.render.GuiPixels;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The single source of truth for the Click GUI's control surfaces - buttons, fields, list backgrounds,
 * slider tracks - i.e. the corner radius and the colour palette.
 *
 * <p>Every control is a rounded rect with a 1px border ring and an inset fill: exactly the two-piece
 * structure the cards use. Draw controls through these helpers only. When the settings widgets drew
 * their own flat fills with hard outlines they looked like a different UI than the cards' rounded
 * buttons - which is what this class exists to prevent.
 *
 * <p>Border first, then the inset fill: both are opaque, so a corner pixel covered by both is just a
 * blend of two opaque colours and never doubles a translucent background.
 *
 * <p>The palette values live in {@link GuiTheme} and are read through the methods below. They used to be
 * {@code static final int} fields; they are methods now so that switching the theme takes effect on the
 * next frame instead of on the next class load.
 */
public final class ClickGuiStyle {
    /** Corner radius of a small control; the same value the cards' buttons use. */
    public static final int RADIUS = 4;
    /** Corner radius of a rounded pill (the slider track). */
    public static final int PILL = 2;

    private ClickGuiStyle() {
    }

    // ---- Panel chrome (the panel itself, its tabs, sidebar and cards) ----

    /** RGB only: the alpha is the player's background-opacity setting, combined at draw time. */
    public static int panelRgb() {
        return GuiTheme.current().panelRgb();
    }

    public static int tabBarRgb() {
        return GuiTheme.current().tabBarRgb();
    }

    public static int sidebarRgb() {
        return GuiTheme.current().sidebarRgb();
    }

    public static int sidebarDividerRgb() {
        return GuiTheme.current().sidebarDividerRgb();
    }

    public static int tab() {
        return GuiTheme.current().tab();
    }

    public static int tabHover() {
        return GuiTheme.current().tabHover();
    }

    public static int tabSelected() {
        return GuiTheme.current().tabSelected();
    }

    public static int sidebarHover() {
        return GuiTheme.current().sidebarHover();
    }

    public static int sidebarSelected() {
        return GuiTheme.current().sidebarSelected();
    }

    public static int card() {
        return GuiTheme.current().card();
    }

    public static int cardHover() {
        return GuiTheme.current().cardHover();
    }

    // ---- Controls ----

    /** Secondary/neutral button - the same grey as the cards' "设置" button. */
    public static int button() {
        return GuiTheme.current().cardSurface();
    }

    public static int buttonHover() {
        return GuiTheme.current().cardSurfaceHover();
    }

    /** Primary/accent button (ON, selected) - the mod's green, shared with the tabs. */
    public static int accent() {
        return GuiTheme.current().accent();
    }

    public static int accentHover() {
        return GuiTheme.current().accentHover();
    }

    /** A button with nothing to configure. */
    public static int disabled() {
        return GuiTheme.current().disabled();
    }

    public static int disabledText() {
        return GuiTheme.current().disabledText();
    }

    /** Off/destructive state of the card's status button. */
    public static int danger() {
        return GuiTheme.current().danger();
    }

    /** Field-like surfaces: text fields, dropdowns, the search box, option lists. */
    public static int field() {
        return GuiTheme.current().field();
    }

    public static int fieldHover() {
        return GuiTheme.current().fieldHover();
    }

    /** The value box inside a labelled text field. */
    public static int fieldInner() {
        return GuiTheme.current().fieldInner();
    }

    /** The dark ring every control shares. */
    /** Option-list rows (kept flat: they are laid out back to back). */
    public static int listRowHover() {
        return GuiTheme.current().listRowHover();
    }

    public static int listRowCurrent() {
        return GuiTheme.current().listRowCurrent();
    }

    /**
     * The on/off switch: its track in both states, and its ring.
     *
     * <p>{@code switchOn} is its own token rather than the accent: an ON row's surface is the accent,
     * so a track painted with the accent disappeared into it. Both are adjustable in the theme studio.
     */
    public static int switchOn() {
        return GuiTheme.current().switchOn();
    }

    public static int switchOff() {
        return GuiTheme.current().switchOff();
    }

    /** Section titles in the settings pages - its own token so it can differ from the accent. */
    public static int sectionTitle() {
        return GuiTheme.current().sectionTitle();
    }

    public static int onAccent() {
        return GuiTheme.current().onAccent();
    }

    public static int description() {
        return GuiTheme.current().description();
    }

    public static int selection() {
        return GuiTheme.current().selection();
    }

    public static int scrollbarTrack() {
        return GuiTheme.current().scrollbarTrack();
    }

    public static int scrollbarThumb() {
        return GuiTheme.current().scrollbarThumb();
    }

    /**
     * The floating card in the HUD position editor. Separate from {@link #card()} because the card sits
     * over the game and is drawn translucent, and because its body text is one step softer than the
     * panel's.
     */
    public static int cardFloat() {
        return GuiTheme.current().cardFloat();
    }

    public static int cardFloatTitle() {
        return GuiTheme.current().cardFloatTitle();
    }

    public static int cardText() {
        return GuiTheme.current().cardText();
    }

    public static int text() {
        return GuiTheme.current().text();
    }

    public static int textDim() {
        return GuiTheme.current().textDim();
    }

    public static int textMuted() {
        return GuiTheme.current().textMuted();
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
     * A drop shadow behind a rounded surface.
     *
     * <p>Strokes are gone, so depth comes from three things: a slightly darker fill than what is behind it,
     * a shadow, and the shadow's offset. There is no blur available at this layer, so the soft edge is two
     * stacked translucent layers nudged down by a pixel.
     */
    public static void shadow(GuiGraphicsExtractor g, int x, int y, int w, int h, int radius) {
        GuiPixels.fillRoundedRect(g, x - 2, y + 2, w + 4, h + 2, radius + 2,
                GuiTheme.current().shadow());
        GuiPixels.fillRoundedRect(g, x - 1, y + 1, w + 2, h + 1, radius + 1,
                GuiTheme.current().shadowSoft());
    }
    /** A neutral or accent button in its hovered/plain state. */
    public static void button(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean hovered,
                              boolean accent) {
        surface(g, x, y, w, h, fillFor(hovered, accent), 0);
    }

    /** Text field / dropdown / search box. */
    public static void field(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean hovered,
                             boolean focused) {
        // No outline: focus is shown by the caret and the accent colour, not by a stroke.
        surface(g, x, y, w, h, hovered ? fieldHover() : field(), focused ? accent() : 0);
    }

    /** Rounded pill (slider track): a 4px band has no room for a border ring. */
    public static void pill(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        GuiPixels.fillRoundedRect(g, x, y, w, h, Math.min(PILL, Math.max(1, h / 2)), color);
    }

    /** The fill colour of a neutral/accent button in the given state. */
    public static int fillFor(boolean hovered, boolean accent) {
        if (accent) {
            return hovered ? accentHover() : accent();
        }
        return hovered ? buttonHover() : button();
    }

}
