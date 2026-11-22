package dev.skypocket.gui;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.i18n.Localization;
import dev.skypocket.gui.widget.ClickGuiStyle;
import dev.skypocket.ui.Rect;
import dev.skypocket.ui.mc.McUiPainter;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import static dev.skypocket.gui.PanelGeometry.*;
import static dev.skypocket.render.GuiPixels.fillDeviceToRow;
import static dev.skypocket.render.GuiPixels.fillRoundedBottomToRow;
import static dev.skypocket.render.GuiPixels.fillRoundedTopRect;

/**
 * Draws the Click GUI shell: the translucent panel body, the two main tabs and the footer strip.
 * The homepage and module chrome are v2 element trees ({@link ModulesPage}); settings content is
 * {@link SettingsPage}.
 */
public final class PanelRenderer {

    private PanelRenderer() {
    }

    /**
     * The panel's translucent shell: dark tab strip, lighter body, sidebar area and footer
     * strip.
     *
     * <p>Dark main-tab strip (top corners rounded), lighter body, and dark footer strip (bottom
     * corners rounded). Disjoint pieces, so a translucent background is never blended twice. In
     * the modules list the sidebar area is its own piece (not painted over the body), so at
     * partial opacity its color matches the tab/footer strips instead of double-blending darker.
     *
     * <p>Everything that ends at the content area's bottom edge - the body gradient, the sidebar
     * pieces and the footer strip - is pinned to {@link PanelGeometry#contentBottomRow()}, the
     * exact device row the content scissor stops on. Drawing them from design coordinates would
     * snap them one pixel away from the scissor and leave a sliver of body colour between the cut
     * cards and the bar.
     */
    public static void drawBackground(GuiGraphicsExtractor graphics, PanelView view) {
        fillRoundedTopRect(graphics, 0, 0, PANEL_W, TAB_BAR_H, ClickGuiStyle.cardRadius(), tabBarColor());
        int bodyY = TAB_BAR_H;
        int footerRow = contentBottomRow();
        int bodyColor = panelBackgroundColor();
        if (showsSidebar(view)) {
            int alpha = ConfigManager.get().clickGuiBackgroundAlpha << 24;
            // Flat body: the old vertical gradient made the top brighter than the bottom, which read
            // as uneven lighting next to the flat cards and rows.
            // footerRow is a DEVICE row index (the row-anchored helpers convert it);
            // handing it to a design-space fill made the body reach far below the panel.
            // Pin the body to the exact device row the content scissor and footer use. A
            // design-space fill rounds independently and leaves a 1px seam at 0.6/0.9 scales.
            fillDeviceToRow(graphics, SIDEBAR_W, bodyY, PANEL_W - SIDEBAR_W, footerRow, bodyColor);
            // The sidebar borders the panel's rounded left edge and the footer strip, so both
            // share its rows (and the same device row at the bottom).
            fillDeviceToRow(graphics, 0, TAB_BAR_H, SIDEBAR_W - 1, footerRow, alpha | ClickGuiStyle.sidebarRgb());
            fillDeviceToRow(graphics, SIDEBAR_W - 1, TAB_BAR_H, 1, footerRow,
                    alpha | ClickGuiStyle.sidebarDividerRgb());
        } else {
            fillDeviceToRow(graphics, 0, bodyY, PANEL_W, footerRow, bodyColor);
        }
        fillRoundedBottomToRow(graphics, 0, PANEL_W, ClickGuiStyle.cardRadius(), tabBarColor(), footerRow, PANEL_H);
    }

    /**
     * Bottom footer strip: the HUD position entry and the settings button.
     *
     * <p>Both are full buttons rather than painted glyphs - each gets a surface, a hover state and a
     * hit box of its own, because both are clickable. The settings entry used to be a main tab in the
     * top bar; it moved here so the top bar holds only identity (logo and title) and the bottom bar
     * holds the actions.
     */
    public static void drawFooter(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        McUiPainter painter = new McUiPainter(graphics);
        int btnY = footerBtnY();

        boolean hudHover = mouseX >= FOOTER_BTN_X && mouseX <= FOOTER_BTN_X + FOOTER_BTN_W
                && mouseY >= btnY && mouseY <= btnY + FOOTER_BTN_H;
        painter.surface(new Rect(FOOTER_BTN_X, btnY, FOOTER_BTN_W, FOOTER_BTN_H),
                ClickGuiStyle.controlRadius(),
                hudHover ? ClickGuiStyle.buttonHover() : ClickGuiStyle.button(), 0);
        String hud = Localization.tr("HUD 位置调整");
        painter.text(hud, FOOTER_BTN_X + (FOOTER_BTN_W - painter.textWidth(hud)) / 2f,
                btnY + (FOOTER_BTN_H - painter.lineHeight()) / 2f, ClickGuiStyle.text());

        boolean gearHover = mouseX >= FOOTER_GEAR_X && mouseX <= FOOTER_GEAR_X + FOOTER_GEAR_SIZE
                && mouseY >= btnY && mouseY <= btnY + FOOTER_GEAR_SIZE;
        int gearBg = gearHover ? ClickGuiStyle.buttonHover() : ClickGuiStyle.button();
        Rect gearBox = new Rect(FOOTER_GEAR_X, btnY, FOOTER_GEAR_SIZE, FOOTER_GEAR_SIZE);
        painter.surface(gearBox, ClickGuiStyle.controlRadius(), gearBg, 0);
        // A drawn gear, not a glyph: neither Inter nor Noto Sans SC carries U+2699. The hole is
        // punched with the button's own background, which is why the current fill is passed in.
        GearIcon.paint(painter, gearBox, ClickGuiStyle.text(), gearBg);
    }

    // ---- internals -------------------------------------------------------------------------

    /**
     * Whether the panel shows the sidebar.
     *
     * <p>Always true now. It used to depend on {@code settingsTab}, because the panel-wide settings
     * page took the full width and hid the sidebar. That page moved into the content area, so it
     * keeps the sidebar like every other page and the conditional has nothing left to decide.
     */
    private static boolean showsSidebar(PanelView view) {
        return true;
    }

    private static int panelBackgroundColor() {
        return (ConfigManager.get().clickGuiBackgroundAlpha << 24) | ClickGuiStyle.panelRgb();
    }

    private static int tabBarColor() {
        return (ConfigManager.get().clickGuiBackgroundAlpha << 24) | ClickGuiStyle.tabBarRgb();
    }
}
