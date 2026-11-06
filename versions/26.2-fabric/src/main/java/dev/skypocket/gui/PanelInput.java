package dev.skypocket.gui;

import static dev.skypocket.gui.PanelGeometry.FOOTER_BTN_H;
import static dev.skypocket.gui.PanelGeometry.FOOTER_BTN_W;
import static dev.skypocket.gui.PanelGeometry.FOOTER_BTN_X;
import static dev.skypocket.gui.PanelGeometry.TAB_H;
import static dev.skypocket.gui.PanelGeometry.TAB_W;
import static dev.skypocket.gui.PanelGeometry.TAB_Y;
import static dev.skypocket.gui.PanelGeometry.footerBtnY;
import static dev.skypocket.gui.PanelGeometry.inPanel;
import static dev.skypocket.gui.PanelGeometry.tab2X;
import static dev.skypocket.gui.PanelGeometry.tabX;

/**
 * Click intent for the shell chrome that is not part of a v2 page: the two main tabs and the
 * footer's HUD-editor button. The homepage and module-page sidebar/search/navigation are hit-tested
 * by {@code ModulesPage} through the v2 input router.
 */
public final class PanelInput {
    /** What a click landed on. */
    public enum Kind {
        /** Nothing in the shell chrome; the point belongs to the page layer (or to nothing). */
        NONE,
        MODULES_TAB,
        SETTINGS_TAB,
        /** The footer's HUD position editor entry. */
        FOOTER_BUTTON
    }

    /** A click's intent. */
    public record Hit(Kind kind) {
        private static final Hit NONE = new Hit(Kind.NONE);
    }

    private PanelInput() {
    }

    /** Resolves a panel-local click into a shell intent; the caller performs it. */
    public static Hit hit(double lx, double ly) {
        if (inPanel(lx, ly) && ly >= TAB_Y && ly <= TAB_Y + TAB_H) {
            if (lx >= tabX() && lx <= tabX() + TAB_W) {
                return new Hit(Kind.MODULES_TAB);
            }
            if (lx >= tab2X() && lx <= tab2X() + TAB_W) {
                return new Hit(Kind.SETTINGS_TAB);
            }
        }

        int btnY = footerBtnY();
        if (inPanel(lx, ly) && lx >= FOOTER_BTN_X && lx <= FOOTER_BTN_X + FOOTER_BTN_W
                && ly >= btnY && ly <= btnY + FOOTER_BTN_H) {
            return new Hit(Kind.FOOTER_BUTTON);
        }
        return Hit.NONE;
    }
}
