package dev.skypocket.gui.widget;

/**
 * The Click GUI's palette: every colour the interface paints itself with.
 *
 * <p>Kept apart from the colours a player can edit (see {@code ColorDefaults}): those describe what a
 * <em>feature</em> draws and live in the config, while these describe the interface, are not player data,
 * and should change together when the theme changes.
 *
 * <p>Read through {@link ClickGuiStyle} - that class is the façade every widget already uses, so a widget
 * never needs to know whether it is looking at the palette directly or through a future override.
 * {@link #current()} is a mutable redirect rather than a compile-time constant: a theme switch has to
 * take effect without restarting, which is why the constants in {@code ClickGuiStyle} became methods.
 *
 * <p>The panel's own backgrounds are stored as RGB only, because their alpha is the player's
 * "background opacity" setting and is combined at draw time.
 */
public record GuiTheme(int panelRgb, int tabBarRgb, int sidebarRgb, int sidebarDividerRgb,
                       int tab, int tabHover, int tabSelected,
                       int sidebarHover, int sidebarSelected,
                       int card, int cardHover,
                       int cardSurface, int cardSurfaceHover,
                       int accent, int accentHover, int danger,
                       int disabled, int disabledText,
                       int field, int fieldHover, int fieldInner,
                       int listRowHover, int listRowCurrent,
                       int switchOff,
                       int text, int textDim, int textMuted,
                       int cardFloat, int cardFloatTitle, int cardText, int switchOn,
                       int sectionTitle) {

    /** The palette the interface has always used. */
    public static final GuiTheme DARK = new GuiTheme(
            0x202020,     // panel body (alpha comes from the opacity setting)
            0x0C0C0C,     // column tab bar
            0x1A1A1A,     // sidebar
            0x0F0F0F,     // divider between sidebar and content
            0xFF1A1A1A,   // column tab
            0xFF2C2C2C,   // ... hovered
            0xFF3A9E5F,   // ... selected
            0xFF262626,   // sidebar row, hovered
            0xFF2E2E2E,   // sidebar row, selected
            0xFF292929,   // module card
            0xFF333333,   // ... hovered
            0xFF3A3A3A,   // neutral button / control surface
            0xFF4A4A4A,   // ... hovered
            0xFF3A9E5F,   // accent (on, selected)
            0xFF4BC27A,   // ... hovered
            0xFFE74C3C,   // danger
            0xFF242424,   // disabled surface
            0xFF6A6A6A,   // disabled text
            0xFF1C1C1C,   // field surface
            0xFF262626,   // ... hovered
            0xFF141414,   // value box inside a field
            0xFF333333,   // option-list row, hovered
            0xFF2E4A38,   // option-list row, current
            0xFF232323,   // switch track, off
            0xFFFFFFFF,   // text
            0xFFBBBBBB,   // secondary text
            0xFF666666,   // muted text
            0xFF181818,   // floating card body (drawn with its own alpha)
            0xFF262626,   // ... title strip
            0xFFDDDDDD,   // ... body text
            // Darker than the accent on purpose: an ON row's surface is the accent itself, so a track
            // painted with the accent was invisible on it. Separate token = adjustable in the studio.
            0xFF2F7F4C,
            0xFF3A9E5F);  // section titles (was hard-wired to the accent)

    /** A light palette: same structure, inverted surfaces, same accent. */
    public static final GuiTheme LIGHT = new GuiTheme(
            0xEDEDED,
            0xD8D8D8,
            0xE9E9E9,
            0xBFBFBF,
            0xFFDCDCDC,
            0xFFD2D2D2,
            0xFF3A9E5F,
            0xFFE4E4E4,
            0xFFD6D6D6,
            0xFFFFFFFF,
            0xFFF4F4F4,
            0xFFE8E8E8,
            0xFFDCDCDC,
            0xFF3A9E5F,
            0xFF4BC27A,
            0xFFD64535,
            0xFFEFEFEF,
            0xFF9A9A9A,
            0xFFFFFFFF,
            0xFFF7F7F7,
            0xFFF1F1F1,
            0xFFEDEDED,
            0xFFD8ECDE,
            0xFFCDCDCD,
            0xFF1A1A1A,
            0xFF4F4F4F,
            0xFF8A8A8A,
            0xFFFFFFFF,
            0xFFECECEC,
            0xFF333333,
            0xFF2C7A49,
            0xFF2F7F4C);

    private static GuiTheme current = DARK;

    /** The palette in use. */
    public static GuiTheme current() {
        return current;
    }

    /** Switches the palette; the next frame draws with it. */
    public static void use(GuiTheme theme) {
        current = theme == null ? DARK : theme;
    }

}
