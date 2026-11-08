package dev.skypocket.gui.widget;

/**
 * The interface's measurements: row height, control widths, paddings, radii, text scales.
 *
 * <p>Kept beside {@link GuiTheme}'s colours because "consistent" is not only about colour: a page whose
 * rows are 25 and 38 px tall reads as ragged no matter how well the palette matches. Together these two
 * records are the token layer - the studio edits them, the theme file stores them, and the renderers
 * read them instead of hard-coded numbers.
 *
 * <p>Read through {@link ClickGuiStyle} like the colours, so a change applies on the next frame.
 */
public record GuiMetrics(int rowHeight, int controlHeight, int labelInset, int controlWidth,
                        int sectionGap, int sectionTitleHeight, int cardRadius,
                        int switchWidth, int switchHeight, int sliderTrackHeight, int knobSize) {

    /** The values the interface uses today. */
    public static final GuiMetrics DEFAULT = new GuiMetrics(
            26,   // row height - one value for every row, hint or not (hints became tooltips)
            20,   // height of the control inside a row (toggle, slider track area, field)
            8,    // label inset from the row's left edge
            140,  // control column width (sliders/dropdowns); toggles are sized by switchWidth
            8,    // gap above a section title
            14,   // section title height
            4,    // corner radius of cards and controls
            38,   // switch track width
            14,   // switch track height
            4,    // slider track height
            10    // slider knob diameter
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
