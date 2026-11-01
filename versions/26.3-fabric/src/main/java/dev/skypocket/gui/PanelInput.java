package dev.skypocket.gui;

import static dev.skypocket.gui.PanelGeometry.CardHit;
import static dev.skypocket.gui.PanelGeometry.CardPart;
import static dev.skypocket.gui.PanelGeometry.FOOTER_BTN_H;
import static dev.skypocket.gui.PanelGeometry.FOOTER_BTN_W;
import static dev.skypocket.gui.PanelGeometry.FOOTER_BTN_X;
import static dev.skypocket.gui.PanelGeometry.SIDEBAR_W;
import static dev.skypocket.gui.PanelGeometry.TAB_H;
import static dev.skypocket.gui.PanelGeometry.TAB_W;
import static dev.skypocket.gui.PanelGeometry.TAB_Y;
import static dev.skypocket.gui.PanelGeometry.cardAt;
import static dev.skypocket.gui.PanelGeometry.footerBtnY;
import static dev.skypocket.gui.PanelGeometry.inBody;
import static dev.skypocket.gui.PanelGeometry.inPanel;
import static dev.skypocket.gui.PanelGeometry.sidebarCategory;
import static dev.skypocket.gui.PanelGeometry.sidebarRowAt;
import static dev.skypocket.gui.PanelGeometry.tab2X;
import static dev.skypocket.gui.PanelGeometry.tabX;
import static dev.skypocket.gui.PanelGeometry.visibleFeatures;

/**
 * What a click in the panel means, as opposed to what the screen then does about it.
 *
 * <p>Split out of {@code OneClickScreen}: the click handlers there (<em>is</em> the point on the
 * settings tab? <em>which</em> sidebar row? <em>which</em> card, and its status bar or its button?)
 * are pure questions about a point and the panel's layout - the same kind of question
 * {@link PanelGeometry} already answers for hover - while the reactions (switch page, select a
 * category, open a detail page, toggle a feature) touch the screen's state. Mixing both in the event
 * methods meant every event override carried a copy of the layout arithmetic and of the "is this
 * region interactive at all" rules.
 *
 * <p>So a click is resolved here, once, into a {@link Hit}, and the screen maps that intent onto an
 * action. The order of the regions below is the order the screen always used, because they can
 * overlap by a pixel (the column at {@code lx == SIDEBAR_W} belongs to the sidebar, not the grid).
 */
public final class PanelInput {
    /** What a click landed on. */
    public enum Kind {
        /** Nothing in the panel chrome; the point belongs to the widget layer (or to nothing). */
        NONE,
        MODULES_TAB,
        SETTINGS_TAB,
        /** The footer's HUD position editor entry. */
        FOOTER_BUTTON,
        SIDEBAR,
        /** A card that has settings: open its detail page. */
        CARD_OPEN,
        /** A card: flip the feature's enabled state. */
        CARD_TOGGLE,
        /** A card without settings: consume the click, do nothing. */
        CARD_BODY
    }

    /**
     * A click's intent: what was hit, plus the value it carries.
     *
     * @param category the sidebar category for {@link Kind#SIDEBAR} (null = the "All" row)
     * @param feature  the feature for the three {@code CARD_*} kinds
     */
    public record Hit(Kind kind, Category category, Feature feature) {
        private static final Hit NONE = new Hit(Kind.NONE, null, null);
    }

    private PanelInput() {
    }

    /** Resolves a panel-local click into an intent; the caller performs it. */
    public static Hit hit(PanelView view, double lx, double ly) {
        // Main tabs (the top strip).
        if (inPanel(lx, ly) && ly >= TAB_Y && ly <= TAB_Y + TAB_H) {
            if (lx >= tabX() && lx <= tabX() + TAB_W) {
                return new Hit(Kind.MODULES_TAB, null, null);
            }
            if (lx >= tab2X() && lx <= tab2X() + TAB_W) {
                return new Hit(Kind.SETTINGS_TAB, null, null);
            }
        }

        // Footer entry button.
        int btnY = footerBtnY();
        if (inPanel(lx, ly) && lx >= FOOTER_BTN_X && lx <= FOOTER_BTN_X + FOOTER_BTN_W
                && ly >= btnY && ly <= btnY + FOOTER_BTN_H) {
            return new Hit(Kind.FOOTER_BUTTON, null, null);
        }

        // The settings tab is a widget page: its rows own their hit tests.
        if (view.settingsTab()) {
            return Hit.NONE;
        }

        // Sidebar rows, on the card grid and while a module is open (the sidebar stays visible now).
        if (lx <= SIDEBAR_W && inBody(lx, ly)) {
            int row = sidebarRowAt(view, ly);
            if (row >= 0) {
                return new Hit(Kind.SIDEBAR, sidebarCategory(view, row), null);
            }
        }

        if (view.viewingFeature() != null) {
            // A module's settings fill the card pane and answer for themselves.
            return Hit.NONE;
        }

        // Cards: the status band toggles the feature, any other part opens the detail page - but
        // only when there are settings to show, otherwise the click is swallowed.
        CardHit card = cardAt(view, lx, ly);
        if (card != null) {
            Feature feature = visibleFeatures(view).get(card.index());
            if (card.part() == CardPart.STATUS_BAR) {
                return new Hit(Kind.CARD_TOGGLE, null, feature);
            }
            return new Hit(feature.hasSettings() ? Kind.CARD_OPEN : Kind.CARD_BODY, null, feature);
        }

        return Hit.NONE;
    }
}
