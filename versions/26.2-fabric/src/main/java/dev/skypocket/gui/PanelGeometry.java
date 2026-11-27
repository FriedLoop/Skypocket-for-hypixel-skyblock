package dev.skypocket.gui;

import java.util.ArrayList;
import java.util.List;

import static dev.skypocket.render.GuiPixels.deviceStep;
import static dev.skypocket.render.GuiPixels.scissorRow;

/**
 * Where everything in the Click GUI panel is, and what the mouse is over.
 *
 * <p>Split out of {@code OneClickScreen} (see {@link PanelView}): the panel's design constants and
 * every pure "what is where / what is under the cursor" query live here, so the drawing code, the
 * hit tests and the cache key all ask the same source instead of each re-deriving positions. Every
 * method is static and reads only its arguments, which makes the geometry testable in isolation.
 */
public final class PanelGeometry {
    // ---- panel ----
    // The design canvas is calibrated directly in current design units.
    public static final int PANEL_W = 1348;
    public static final int PANEL_H = 810;
    public static final int ROUND = 16;

    // ---- tab bar ----
    /**
     * Height of the top strip.
     *
     * <p>The strip holds identity only - logo and title. The two main tabs that used to sit at its
     * right end were removed when the settings entry moved to the footer, so none of the tab's own
     * geometry (width, height, gap, margin) is left to describe.
     */
    public static final int TAB_BAR_H = 78;

    // ---- cards ----
    public static final int CARD_W = 308;
    public static final int CARD_H = 194;
    public static final int CARD_GAP = 24;
    public static final int ICON_SIZE = 62;
    /** Horizontal padding of the card grid inside the content pane. */
    private static final int CARD_GRID_PAD = 40;

    /**
     * Card contents, in panel-local units relative to the card's top-left.
     *
     * <p>Deliberately in one place: the icon, title and the two card buttons are drawn by the
     * renderer and hit-tested by {@link #hoveredCardKey} / the screen's click handler, and
     * hard-coding the same offsets in both places meant that changing one silently broke the other
     * (the interaction area drifting away from what is drawn).
     */
    public static final int CARD_PAD = 20;
    public static final int CARD_TITLE_X = CARD_PAD + ICON_SIZE + CARD_PAD;
    public static final int CARD_TITLE_Y = 32;
    /** Title width: up to the card's right padding. */
    public static final int CARD_TITLE_MAX_W = CARD_W - CARD_TITLE_X - CARD_PAD;
    public static final int CARD_BTN_X = CARD_PAD;
    public static final int CARD_BTN_Y = 92;
    public static final int CARD_BTN_W = CARD_W - CARD_PAD * 2;
    public static final int CARD_BTN_H = 36;
    public static final int CARD_STATUS_X = CARD_PAD;
    public static final int CARD_STATUS_Y = 140;
    public static final int CARD_STATUS_W = CARD_W - CARD_PAD * 2;
    public static final int CARD_STATUS_H = 32;

    // ---- sidebar ----
    public static final int SIDEBAR_W = 290;
    public static final int SIDEBAR_ITEM_H = 44;

    /**
     * Icon size in a sidebar row.
     *
     * <p>Same 24px the footer button uses, which is what the icon set is drawn for. The row is 44px
     * tall, so this leaves 10px of air above and below.
     */
    public static final int SIDEBAR_ICON_SIZE = 24;

    /** Gap between a sidebar row's icon and its label. */
    public static final int SIDEBAR_ICON_GAP = 10;
    /** Top of the first sidebar row (below the tab bar). */
    public static final int SIDEBAR_TOP = TAB_BAR_H + 16;

    // ---- search box and navigation ----
    //
    // They belong to the top bar now, not to the content column: the bar holds identity (logo, title)
    // on the left and the navigation tools on the right, and the content column starts directly below
    // it. Their x positions are unchanged - the sidebar still decides where the column begins.
    public static final int SEARCH_H = 36;
    /** Vertically centred in the top bar. */
    public static final int SEARCH_Y = (TAB_BAR_H - SEARCH_H) / 2;
    /** Back/forward buttons sit where the search box used to start. */
    public static final int NAV_SIZE = 36;
    public static final int NAV_GAP = 8;
    public static final int NAV_X = SIDEBAR_W + 32;
    public static final int SEARCH_X = NAV_X + (NAV_SIZE + NAV_GAP) * 2;
    public static final int SEARCH_W = PANEL_W - SIDEBAR_W - 64 - (NAV_SIZE + NAV_GAP) * 2;

    /**
     * Top of the content area.
     *
     * <p>Both pages start here. The value used to differ per page because the search box lived inside
     * the content column and shorter pages pulled their content up beside it; the search box moved to
     * the top bar, so nothing is left above the content except the bar itself.
     */
    public static final int CONTENT_TOP = TAB_BAR_H + 16;

    // ---- footer ----
    public static final int FOOTER_H = 70;
    /** The content scissor ends exactly at the footer's top edge, so cards are cut there. */
    public static final int CONTENT_BOTTOM = FOOTER_H;
    /**
     * Footer buttons, aligned to the sidebar's selected-row highlight.
     *
     * <p>The highlight is drawn by the homepage as {@code Rect(16, y, SIDEBAR_W - 32, ...)}, so the
     * row it marks spans x=16..274. The two footer buttons start and end on that same span, which is
     * what makes them read as belonging to the sidebar column rather than floating.
     *
     * <pre>
     *   16                                        274
     *   [ HUD 位置 ][8][ gear ]   214 + 8 + 36 = 258
     * </pre>
     */
    public static final int FOOTER_ROW_LEFT = 16;
    public static final int FOOTER_ROW_WIDTH = SIDEBAR_W - 32;
    public static final int FOOTER_BTN_H = 36;
    /** Square, so its height and width are both {@link #FOOTER_BTN_H}. */
    public static final int FOOTER_GEAR_SIZE = FOOTER_BTN_H;

    /**
     * Gap between the gear button's edge and the icon inside it.
     *
     * <p>Leaves a 24px icon in a 36px button. 24 is the size the icon set is designed for: the
     * sources are Lucide's 24-unit grid, and the atlas is rendered at that size. It is also the
     * largest an icon can be in this button while staying clear of the rounded corners.
     */
    public static final int FOOTER_GEAR_INSET = 6;
    /** Gap between the two footer buttons. */
    public static final int FOOTER_BTN_GAP = 8;
    public static final int FOOTER_BTN_W =
            FOOTER_ROW_WIDTH - FOOTER_GEAR_SIZE - FOOTER_BTN_GAP;
    public static final int FOOTER_BTN_X = FOOTER_ROW_LEFT;
    public static final int FOOTER_GEAR_X = FOOTER_BTN_X + FOOTER_BTN_W + FOOTER_BTN_GAP;

    private PanelGeometry() {
    }

    // ---- fixed positions -------------------------------------------------------------------

    /** Top of the footer's entry button, centred in the footer strip. */
    public static int footerBtnY() {
        return PANEL_H - FOOTER_H + (FOOTER_H - FOOTER_BTN_H) / 2;
    }

    /** Width of the card/settings pane to the right of the sidebar. */
    public static int contentWidth() {
        return PANEL_W - SIDEBAR_W;
    }

    /** Columns of cards that fit in the content pane. */
    public static int columns() {
        return Math.max(1, (contentWidth() - CARD_GRID_PAD * 2 + CARD_GAP) / (CARD_W + CARD_GAP));
    }

    /** Left edge of a card row holding {@code rowCount} cards (rows are centred). */
    public static int cardRowLeft(int rowCount) {
        int rowW = rowCount * (CARD_W + CARD_GAP) - CARD_GAP;
        return SIDEBAR_W + (contentWidth() - rowW) / 2;
    }

    // ---- content area ----------------------------------------------------------------------

    /**
     * Top of the content area: directly under the top bar.
     *
     * <p>Takes a view for historical reasons - it used to differ per page, and used to sit below the
     * search box. Both of those are gone: the search box moved into the top bar, and every page starts
     * at the same row.
     */
    public static int contentTop(PanelView view) {
        return CONTENT_TOP;
    }

    /**
     * Device row (device pixels below the panel's top) where the content area ends - the row the
     * content scissor really reaches.
     *
     * <p>A scissor can only end on a whole gui-scaled unit, so its row is a multiple of
     * {@code guiScale} device pixels. That is why the body gradient, the sidebar pieces, the footer
     * strip and the scissor itself are all drawn against <em>this device row</em> rather than a
     * design coordinate: a design coordinate snaps to a device row of its own, generally one pixel
     * away from the scissor's, and the round trip design -> device -> design cannot recover it.
     */
    public static int contentBottomRow() {
        return scissorRow(PANEL_H - CONTENT_BOTTOM);
    }

    /** Bottom of the content area in design units, for hit tests; see {@link #contentBottomRow()}. */
    public static int contentBottom() {
        return Math.round(contentBottomRow() / deviceStep());
    }

    /**
     * Largest scroll offset: the content height (which depends on the page) minus what fits.
     * {@code settingsHeight} is the screen's settings-widget stack height, which depends on the
     * built widgets rather than on this class.
     */
    public static int maxScroll(PanelView view, int settingsHeight) {
        int visible = contentBottom() - contentTop(view);
        int content;
        if (view.viewingFeature() == null) {
            int count = visibleFeatures(view).size();
            int rows = (count + columns() - 1) / columns();
            content = rows * (CARD_H + CARD_GAP);
        } else {
            // 30 for the title line plus the 28 the rows are offset by, so the last row can be reached.
            content = 30 + 28 + view.viewingFeature().settingsHeight();
        }
        return Math.max(0, content - visible);
    }

    // ---- hit tests -------------------------------------------------------------------------

    /** Panel-local point inside the panel's rectangle. */
    public static boolean inPanel(double lx, double ly) {
        return lx >= 0 && lx < PANEL_W && ly >= 0 && ly < PANEL_H;
    }

    /**
     * Panel-local point inside the panel body: the panel minus the two translucent strips
     * (the tab bar above, the footer below).
     */
    public static boolean inBody(double lx, double ly) {
        return inPanel(lx, ly) && ly >= TAB_BAR_H && ly < contentBottom();
    }

    /** Features matching the selected category and the search text. */
    public static List<Feature> visibleFeatures(PanelView view) {
        String q = view.search().trim().toLowerCase();
        List<Feature> result = new ArrayList<>();
        for (Feature f : view.features()) {
            boolean matchCategory = view.selected() == null || view.selected().contains(f.category());
            boolean matchSearch = q.isEmpty() || f.matchesName(q) || f.matchesAnySetting(q);
            if (matchCategory && matchSearch) {
                result.add(f);
            }
        }
        return result;
    }

}
