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
    // The design canvas is calibrated directly in new design units: 700 x 420
    // was the old 1.75 baseline, so it is 1225 x 735 here.
    public static final int PANEL_W = 1348;
    public static final int PANEL_H = 810;
    public static final int ROUND = 16;

    // ---- tab bar and its two main tabs ----
    public static final int TAB_BAR_H = 78;
    public static final int TAB_W = 146;
    public static final int TAB_H = 44;
    /** Top edge of the main tabs. */
    public static final int TAB_Y = 18;
    /** Gap between the two main tabs. */
    public static final int TAB_GAP = 16;
    /** Distance from the panel's right edge to the last tab. */
    public static final int TAB_MARGIN = 32;

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
    /** Top of the first sidebar row (below the tab bar). */
    public static final int SIDEBAR_TOP = TAB_BAR_H + 16;

    // ---- search box (after SIDEBAR_W: SEARCH_X/W are derived from it) ----
    public static final int SEARCH_Y = 92;
    /** Back/forward buttons sit where the search box used to start. */
    public static final int NAV_SIZE = 36;
    public static final int NAV_GAP = 8;
    public static final int NAV_X = SIDEBAR_W + 32;
    public static final int SEARCH_X = NAV_X + (NAV_SIZE + NAV_GAP) * 2;
    public static final int SEARCH_W = PANEL_W - SIDEBAR_W - 64 - (NAV_SIZE + NAV_GAP) * 2;
    public static final int SEARCH_H = 36;

    /** Top of the content area on the modules tab (below the search box). */
    public static final int CONTENT_TOP_MODULES = 146;
    /** Top of the content area on the settings tab / a feature's detail page. */
    public static final int CONTENT_TOP_SETTINGS = 100;

    // ---- footer ----
    public static final int FOOTER_H = 70;
    /** The content scissor ends exactly at the footer's top edge, so cards are cut there. */
    public static final int CONTENT_BOTTOM = FOOTER_H;
    /** Footer entry button (opens the unified HUD position editor): 3/4 of the sidebar width,
     *  centred under the sidebar. */
    public static final int FOOTER_BTN_W = 218;
    public static final int FOOTER_BTN_X = (SIDEBAR_W - FOOTER_BTN_W) / 2;
    public static final int FOOTER_BTN_H = 36;

    private PanelGeometry() {
    }

    // ---- fixed positions -------------------------------------------------------------------

    /** Left edge of the first main tab. */
    public static int tabX() {
        return PANEL_W - TAB_MARGIN - TAB_W * 2 - TAB_GAP;
    }

    /** Left edge of the second main tab. */
    public static int tab2X() {
        return tabX() + TAB_W + TAB_GAP;
    }

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

    /** Top of the content area: below the search box, or right under the tab bar. */
    public static int contentTop(PanelView view) {
        // A module's settings live in the card pane now, under the same fixed search bar, so they share
        // the modules tab's top edge.
        return view.settingsTab() ? CONTENT_TOP_SETTINGS : CONTENT_TOP_MODULES;
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
        if (view.settingsTab()) {
            content = settingsHeight;
        } else if (view.viewingFeature() == null) {
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
