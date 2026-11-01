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
    public static final int PANEL_W = 700;
    /** Panel height; the panel is a 5:3 rectangle (700 x 420). */
    public static final int PANEL_H = PANEL_W * 3 / 5;
    public static final int ROUND = 8;

    // ---- tab bar and its two main tabs ----
    public static final int TAB_BAR_H = 40;
    public static final int TAB_W = 76;
    public static final int TAB_H = 22;
    /** Top edge of the main tabs. */
    public static final int TAB_Y = 9;
    /** Gap between the two main tabs. */
    public static final int TAB_GAP = 8;
    /** Distance from the panel's right edge to the last tab. */
    public static final int TAB_MARGIN = 16;

    // ---- cards ----
    public static final int CARD_W = 160;
    public static final int CARD_H = 100;
    public static final int CARD_GAP = 12;
    public static final int ICON_SIZE = 32;
    /** Horizontal padding of the card grid inside the content pane. */
    private static final int CARD_GRID_PAD = 20;

    /**
     * Card contents, in panel-local units relative to the card's top-left.
     *
     * <p>Deliberately in one place: the icon, title and the two card buttons are drawn by the
     * renderer and hit-tested by {@link #hoveredCardKey} / the screen's click handler, and
     * hard-coding the same offsets in both places meant that changing one silently broke the other
     * (the interaction area drifting away from what is drawn).
     */
    public static final int CARD_PAD = 10;
    public static final int CARD_TITLE_X = CARD_PAD + ICON_SIZE + CARD_PAD;
    public static final int CARD_TITLE_Y = 16;
    /** Title width: up to the card's right padding. */
    public static final int CARD_TITLE_MAX_W = CARD_W - CARD_TITLE_X - CARD_PAD;
    public static final int CARD_BTN_X = CARD_PAD;
    public static final int CARD_BTN_Y = 48;
    public static final int CARD_BTN_W = CARD_W - CARD_PAD * 2;
    public static final int CARD_BTN_H = 18;
    public static final int CARD_STATUS_X = CARD_PAD;
    public static final int CARD_STATUS_Y = 72;
    public static final int CARD_STATUS_W = CARD_W - CARD_PAD * 2;
    public static final int CARD_STATUS_H = 16;

    // ---- sidebar ----
    public static final int SIDEBAR_W = 150;
    public static final int SIDEBAR_ITEM_H = 22;
    /** Top of the first sidebar row (below the tab bar). */
    public static final int SIDEBAR_TOP = TAB_BAR_H + 8;

    // ---- search box (after SIDEBAR_W: SEARCH_X/W are derived from it) ----
    public static final int SEARCH_Y = 48;
    /** Back/forward buttons sit where the search box used to start. */
    public static final int NAV_SIZE = 18;
    public static final int NAV_GAP = 4;
    public static final int NAV_X = SIDEBAR_W + 16;
    public static final int SEARCH_X = NAV_X + (NAV_SIZE + NAV_GAP) * 2;
    public static final int SEARCH_W = PANEL_W - SIDEBAR_W - 32 - (NAV_SIZE + NAV_GAP) * 2;
    public static final int SEARCH_H = 18;

    /** Top of the content area on the modules tab (below the search box). */
    public static final int CONTENT_TOP_MODULES = 76;
    /** Top of the content area on the settings tab / a feature's detail page. */
    public static final int CONTENT_TOP_SETTINGS = 52;

    // ---- footer ----
    public static final int FOOTER_H = 36;
    /** The content scissor ends exactly at the footer's top edge, so cards are cut there. */
    public static final int CONTENT_BOTTOM = FOOTER_H;
    /** Footer entry button (opens the unified HUD position editor): 3/4 of the sidebar width,
     *  centred under the sidebar. */
    public static final int FOOTER_BTN_W = SIDEBAR_W * 3 / 4;
    public static final int FOOTER_BTN_X = (SIDEBAR_W - FOOTER_BTN_W) / 2;
    public static final int FOOTER_BTN_H = 18;

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

    /** Top edge of card row {@code row} (0-based) for the current scroll offset. */
    public static int cardRowTop(PanelView view, int row) {
        return contentTop(view) - view.scrollY() + row * (CARD_H + CARD_GAP);
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

    /**
     * Panel-local point inside the card grid's clip rectangle, i.e. what the content scissor
     * actually shows.
     *
     * <p>Cards are laid out past both ends of the clip - rows scrolled out of view are only
     * clipped, never moved - so the hit tests and the hover must use this rectangle. Otherwise
     * cards that are not even visible respond to the mouse: those under the bottom tab strip,
     * those scrolled under the search bar, and (because nothing checks the panel's own bounds)
     * any point outside the GUI at all.
     */
    public static boolean inCardArea(PanelView view, double lx, double ly) {
        return inBody(lx, ly) && lx >= SIDEBAR_W && ly >= contentTop(view);
    }

    /**
     * Row hit test, half-open on the far edge: the rows are laid out back to back, so a point on
     * the shared boundary must belong to exactly one of them. With the far edge inclusive it
     * matched both - the row ending there and the one starting there - which drew the hover
     * highlight over two categories at once.
     */
    public static boolean sidebarRowHit(double y, int rowY) {
        return y >= rowY && y < rowY + SIDEBAR_ITEM_H;
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

    /** 0 = none, 1 = modules tab, 2 = settings tab. */
    public static int hoveredTabKey(int lmx, int lmy) {
        if (lmy < TAB_Y || lmy > TAB_Y + TAB_H || !inPanel(lmx, lmy)) {
            return 0;
        }
        if (lmx >= tabX() && lmx <= tabX() + TAB_W) {
            return 1;
        }
        if (lmx >= tab2X() && lmx <= tab2X() + TAB_W) {
            return 2;
        }
        return 0;
    }

    public static boolean hoveredFooterButton(int lmx, int lmy) {
        int btnY = footerBtnY();
        return inPanel(lmx, lmy) && lmx >= FOOTER_BTN_X && lmx <= FOOTER_BTN_X + FOOTER_BTN_W
                && lmy >= btnY && lmy <= btnY + FOOTER_BTN_H;
    }

    public static boolean insideSearchBox(int lmx, int lmy) {
        return inBody(lmx, lmy) && lmx >= SEARCH_X && lmx <= SEARCH_X + SEARCH_W
                && lmy >= SEARCH_Y && lmy <= SEARCH_Y + SEARCH_H;
    }

    /**
     * Sentinel for "the cursor is not on the panel at all".
     *
     * <p>Anything the content scissor clips away has to stay non-interactive, and the drawing code
     * is handed the same coordinates that were hit-tested. Pushing those out of range makes every
     * bounds test in the widget layer fail without adding a check to each widget (see
     * {@code OneClickScreen#drawPanel} and {@link PanelCacheKey}).
     */
    public static final int FAR_AWAY = Integer.MIN_VALUE / 2;

    /** Row index of the hovered sidebar entry, or -1. */
    public static int hoveredSidebarKey(PanelView view, int lmx, int lmy) {
        if (lmx > SIDEBAR_W) {
            return -1;
        }
        return inBody(lmx, lmy) ? sidebarRowAt(view, lmy) : -1;
    }

    /**
     * Sidebar row index at panel-local {@code ly}, or -1. Rows are laid out back to back and follow
     * {@link #sidebarRowHit}, so a point on a boundary belongs to exactly one row.
     *
     * <p>Which rows exist depends on the view: a container only lists its children while it holds
     * the selection, which is why this needs the view at all.
     */
    public static int sidebarRowAt(PanelView view, double ly) {
        int y = SIDEBAR_TOP;
        int row = 0;
        if (sidebarRowHit(ly, y)) {
            return row;
        }
        y += SIDEBAR_ITEM_H;
        for (Category c : Category.topLevel()) {
            row++;
            if (sidebarRowHit(ly, y)) {
                return row;
            }
            y += SIDEBAR_ITEM_H;
            if (c.isContainer() && view.selected() != null && c.contains(view.selected())) {
                for (Category child : c.children()) {
                    row++;
                    if (sidebarRowHit(ly, y)) {
                        return row;
                    }
                    y += SIDEBAR_ITEM_H;
                }
            }
        }
        return -1;
    }

    /**
     * Category shown on sidebar row {@code row} - row 0 is "All", which is not a category - or null
     * when the row does not exist. Mirrors {@link #sidebarRowAt}'s traversal, so it has to be called
     * with the same view.
     */
    public static Category sidebarCategory(PanelView view, int row) {
        if (row <= 0) {
            return null;
        }
        int r = 0;
        for (Category c : Category.topLevel()) {
            if (++r == row) {
                return c;
            }
            if (c.isContainer() && view.selected() != null && c.contains(view.selected())) {
                for (Category child : c.children()) {
                    if (++r == row) {
                        return child;
                    }
                }
            }
        }
        return null;
    }

    /** Which part of a card a point falls on. */
    public enum CardPart {
        BODY, SETTINGS_BUTTON, STATUS_BAR
    }

    /** The card under a point: its index in {@link #visibleFeatures} plus the part that was hit. */
    public record CardHit(int index, CardPart part) {
    }

    /**
     * Card under a panel-local point, or null when there is none.
     *
     * <p>The single source for "which card, and which part of it": the hover signature, the click
     * handler and (through them) the drawing all ask this rather than each re-deriving the row and
     * column arithmetic - the same reason the card's inner offsets are constants here.
     */
    public static CardHit cardAt(PanelView view, double lx, double ly) {
        if (!inCardArea(view, lx, ly)) {
            return null;
        }
        List<Feature> list = visibleFeatures(view);
        int cols = columns();
        int index = 0;
        while (index < list.size()) {
            int rowCount = Math.min(cols, list.size() - index);
            int left = cardRowLeft(rowCount);
            int row = index / cols;
            int cy = cardRowTop(view, row);
            for (int j = 0; j < rowCount; j++) {
                int cx = left + j * (CARD_W + CARD_GAP);
                if (lx >= cx && lx <= cx + CARD_W && ly >= cy && ly <= cy + CARD_H) {
                    return new CardHit(index, cardPart(cx, cy, lx, ly));
                }
                index++;
            }
        }
        return null;
    }

    /** Which part of the card at {@code (cx, cy)} holds the point; the parts do not overlap. */
    private static CardPart cardPart(int cx, int cy, double lx, double ly) {
        if (ly >= cy + CARD_STATUS_Y && ly <= cy + CARD_STATUS_Y + CARD_STATUS_H) {
            return CardPart.STATUS_BAR;
        }
        if (lx >= cx + CARD_BTN_X && lx <= cx + CARD_BTN_X + CARD_BTN_W
                && ly >= cy + CARD_BTN_Y && ly <= cy + CARD_BTN_Y + CARD_BTN_H) {
            return CardPart.SETTINGS_BUTTON;
        }
        return CardPart.BODY;
    }

    /** Hovered card index (doubled, +1 when its settings button is hovered), or -1. */
    public static int hoveredCardKey(PanelView view, int lmx, int lmy) {
        CardHit hit = cardAt(view, lmx, lmy);
        if (hit == null) {
            return -1;
        }
        return hit.index() * 2 + (hit.part() == CardPart.SETTINGS_BUTTON ? 1 : 0);
    }

    /** Top y of the selected sidebar row (the animated highlight's target). */
    public static int sidebarTargetY(PanelView view) {
        int y = SIDEBAR_TOP;
        if (view.selected() == null) {
            return y; // "All"
        }
        y += SIDEBAR_ITEM_H;
        for (Category c : Category.topLevel()) {
            if (view.selected() == c) {
                return y;
            }
            y += SIDEBAR_ITEM_H;
            if (c.isContainer() && c.contains(view.selected())) {
                for (Category child : c.children()) {
                    if (view.selected() == child) {
                        return y;
                    }
                    y += SIDEBAR_ITEM_H;
                }
            }
        }
        return y;
    }
}
