package dev.skypocket.gui;

import static dev.skypocket.gui.PanelGeometry.FOOTER_BTN_H;
import static dev.skypocket.gui.PanelGeometry.FOOTER_BTN_W;
import static dev.skypocket.gui.PanelGeometry.FOOTER_BTN_X;
import static dev.skypocket.gui.PanelGeometry.FOOTER_GEAR_SIZE;
import static dev.skypocket.gui.PanelGeometry.FOOTER_GEAR_X;
import static dev.skypocket.gui.PanelGeometry.footerBtnY;
import static dev.skypocket.gui.PanelGeometry.inPanel;

/**
 * Click intent for the shell chrome that is not part of a v2 page: the footer's two buttons.
 *
 * <p>The homepage and module-page sidebar/search/navigation are hit-tested by {@code ModulesPage}
 * through the v2 input router.
 *
 * <p>The main tabs used to live here. They were removed when the settings entry moved to the footer,
 * which is why only the footer cases remain - the top bar now holds identity only.
 *
 * <p>This is the single source for the footer's hit boxes: {@link PanelRenderer} draws from the same
 * geometry constants, so a button cannot be painted in one place and clicked in another.
 */
public final class PanelInput {
    /** What a click landed on. */
    public enum Kind {
        /** Nothing in the shell chrome; the point belongs to the page layer (or to nothing). */
        NONE,
        /** The footer's HUD position editor entry. */
        FOOTER_BUTTON,
        /** The footer's settings entry. */
        FOOTER_SETTINGS
    }

    /** A click's intent. */
    public record Hit(Kind kind) {
        private static final Hit NONE = new Hit(Kind.NONE);
    }

    private PanelInput() {
    }

    /** Resolves a panel-local click into a shell intent; the caller performs it. */
    public static Hit hit(double lx, double ly) {
        if (!inPanel(lx, ly)) {
            return Hit.NONE;
        }
        int btnY = footerBtnY();
        if (ly >= btnY && ly <= btnY + FOOTER_BTN_H) {
            if (lx >= FOOTER_BTN_X && lx <= FOOTER_BTN_X + FOOTER_BTN_W) {
                return new Hit(Kind.FOOTER_BUTTON);
            }
            if (lx >= FOOTER_GEAR_X && lx <= FOOTER_GEAR_X + FOOTER_GEAR_SIZE) {
                return new Hit(Kind.FOOTER_SETTINGS);
            }
        }
        return Hit.NONE;
    }
}
