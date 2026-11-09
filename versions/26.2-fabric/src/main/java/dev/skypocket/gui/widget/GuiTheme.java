package dev.skypocket.gui.widget;

/**
 * The Click GUI's semantic palette.
 *
 * <p>These are the only colours a theme exposes. Component states (hover, selected row,
 * disabled control, floating-card alpha, scrollbars, selection) are derived by
 * {@link ClickGuiStyle}; keeping them out of the record is what stops the token set from
 * growing back into one entry per widget.
 *
 * <p>{@code window} and {@code chrome} are stored as RGB in spirit; their alpha is the
 * player's "background opacity" setting and is combined at draw time. The remaining tokens
 * carry their own alpha.
 */
public record GuiTheme(int window, int chrome, int surface, int control,
                       int field, int fieldInner,
                       int primary, int primaryHover, int onPrimary,
                       int textPrimary, int textSecondary, int textMuted,
                       int switchOn, int switchOff) {

    /** The palette the interface has always used. */
    public static final GuiTheme DARK = new GuiTheme(
            0xFF202020,   // panel body
            0xFF141414,   // top bar, sidebar, footer, tab base
            0xFF292929,   // cards / page surfaces
            0xFF3A3A3A,   // buttons, normal controls, list rows
            0xFF1C1C1C,   // input fields
            0xFF141414,   // value box inside a field
            0xFF3A9E5F,   // primary fill
            0xFF4BC27A,   // primary fill hover
            0xFFFFFFFF,   // foreground on primary
            0xFFFFFFFF,   // primary text
            0xFFBBBBBB,   // secondary text / descriptions
            0xFF666666,   // muted text / disabled text
            0xFF2F7F4C,   // switch track on
            0xFF232323);  // switch track off

    /** A light palette with the same semantic roles. */
    public static final GuiTheme LIGHT = new GuiTheme(
            0xFFEDEDED,
            0xFFD8D8D8,
            0xFFFFFFFF,
            0xFFE8E8E8,
            0xFFF7F7F7,
            0xFFF1F1F1,
            0xFF3A9E5F,
            0xFF4BC27A,
            0xFFFFFFFF,
            0xFF1A1A1A,
            0xFF4F4F4F,
            0xFF8A8A8A,
            0xFF2C7A49,
            0xFFCDCDCD);

    private static GuiTheme current = DARK;
    private static int generation;

    /** The palette in use. */
    public static GuiTheme current() {
        return current;
    }

    /** Bumped whenever the palette changes, so pages holding a palette snapshot rebuild. */
    public static int generation() {
        return generation;
    }

    /** Switches the palette; the next frame draws with it. */
    public static void use(GuiTheme theme) {
        current = theme == null ? DARK : theme;
        generation++;
    }
}
