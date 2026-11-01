package dev.skypocket.gui;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.i18n.Localization;
import dev.skypocket.render.ClickGuiFonts;
import dev.skypocket.gui.widget.ClickGuiStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.List;

import static dev.skypocket.gui.PanelGeometry.*;
import static dev.skypocket.render.GuiPixels.fillDeviceToRow;
import static dev.skypocket.render.GuiPixels.fillRoundedBorderedRect;
import static dev.skypocket.render.GuiPixels.fillRoundedBottomToRow;
import static dev.skypocket.render.GuiPixels.fillRoundedRect;
import static dev.skypocket.render.GuiPixels.fillRoundedTopRect;

/**
 * Draws the Click GUI panel: the translucent shell, the main tabs, the search box, the sidebar,
 * the card grid and the footer strip.
 *
 * <p>Split out of {@code OneClickScreen} so the screen keeps the lifecycle and the event routing
 * while all painting lives here. Everything these methods need is in the {@link PanelView} snapshot
 * (plus the sidebar's animated highlight, which is animation state rather than panel content), so
 * they are static and read no screen fields.
 *
 * <p>The widget pages (settings tab, feature detail) are <em>not</em> drawn here: their widgets
 * belong to the screen, which wraps that call in the content scissor (see
 * {@code OneClickScreen.drawPanel}).
 */
public final class PanelRenderer {
    /** Left indent of a child category label inside the sidebar. */
    private static final int SIDEBAR_CHILD_INDENT = 14;

    // Colors (RGB; the panel/tab-bar background opacity is configurable).
    /** Divider between the sidebar and the content pane. */

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
        fillRoundedTopRect(graphics, 0, 0, PANEL_W, TAB_BAR_H, ROUND, tabBarColor());
        int bodyY = TAB_BAR_H;
        int footerRow = contentBottomRow();
        int bodyColor = panelBackgroundColor();
        if (showsSidebar(view)) {
            int alpha = ConfigManager.get().clickGuiBackgroundAlpha << 24;
            // Flat body: the old vertical gradient made the top brighter than the bottom, which read
            // as uneven lighting next to the flat cards and rows.
            // footerRow is a DEVICE row index (the row-anchored helpers convert it);
            // handing it to a design-space fill made the body reach far below the panel.
            graphics.fill(SIDEBAR_W, bodyY, PANEL_W, PANEL_H - FOOTER_H, bodyColor);
            // The sidebar borders the panel's rounded left edge and the footer strip, so both
            // share its rows (and the same device row at the bottom).
            fillDeviceToRow(graphics, 0, TAB_BAR_H, SIDEBAR_W - 1, footerRow, alpha | ClickGuiStyle.sidebarRgb());
            fillDeviceToRow(graphics, SIDEBAR_W - 1, TAB_BAR_H, 1, footerRow,
                    alpha | ClickGuiStyle.sidebarDividerRgb());
        } else {
            graphics.fill(0, bodyY, PANEL_W, PANEL_H - FOOTER_H, bodyColor);
        }
        fillRoundedBottomToRow(graphics, 0, PANEL_W, ROUND, tabBarColor(), footerRow, PANEL_H);
    }

    /** The two main tabs, top right. */
    public static void drawTabs(GuiGraphicsExtractor graphics, PanelView view, int mouseX, int mouseY) {
        drawMainTab(graphics, tabX(), TAB_Y, Localization.tr("功能"), !view.settingsTab(), mouseX, mouseY);
        drawMainTab(graphics, tab2X(), TAB_Y, Localization.tr("设置"), view.settingsTab(), mouseX, mouseY);
    }

    /** Search box above the card grid (modules tab only). */
    public static void drawSearch(GuiGraphicsExtractor graphics, PanelView view, int mouseX, int mouseY) {
        ClickGuiStyle.field(graphics, SEARCH_X, SEARCH_Y, SEARCH_W, SEARCH_H,
                insideSearchBox(mouseX, mouseY), false);
        String shown = view.search().isEmpty() ? Localization.tr("搜索...") : view.search();
        ClickGuiFonts.drawText(graphics, shown, SEARCH_X + 6, SEARCH_Y + 5,
                view.search().isEmpty() ? ClickGuiStyle.textMuted() : ClickGuiStyle.text(), false);
    }

    /**
     * The scrollable card grid. Cards are laid out past the clip (see
     * {@link PanelGeometry#inCardArea}), so the hover has to be masked here instead of inside
     * {@code renderCard}: a card hidden under the footer strip, or under the search bar when
     * scrolled, must not light up.
     */
    public static void drawCards(GuiGraphicsExtractor graphics, PanelView view, int mouseX, int mouseY) {
        if (!inCardArea(view, mouseX, mouseY)) {
            mouseX = Integer.MIN_VALUE / 2;
            mouseY = Integer.MIN_VALUE / 2;
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
                Feature f = list.get(index);
                int cx = left + j * (CARD_W + CARD_GAP);
                renderCard(graphics, f, cx, cy, mouseX, mouseY);
                index++;
            }
        }
    }

    /**
     * Two-level navigation sidebar: "All", the top-level categories, and the expanded children of
     * the selected container. {@code highlightY} is the animated selection bar's y, which is
     * animation state owned by the screen.
     */
    public static void drawSidebar(GuiGraphicsExtractor graphics, PanelView view, float highlightY,
                                   int mouseX, int mouseY) {
        // Same reason as drawCards: the rows are not clipped by anything, but the ones below the
        // footer strip must not react to the mouse.
        if (mouseX > SIDEBAR_W || !inBody(mouseX, mouseY)) {
            mouseX = Integer.MIN_VALUE / 2;
            mouseY = Integer.MIN_VALUE / 2;
        }
        // The selection highlight is one bar whose y slides between rows; its position is advanced
        // once per frame by the screen (it is part of the cache key).
        int hy = Math.round(highlightY);
        graphics.fill(0, hy, SIDEBAR_W, hy + SIDEBAR_ITEM_H, ClickGuiStyle.sidebarSelected());
        graphics.fill(0, hy, 2, hy + SIDEBAR_ITEM_H, ClickGuiStyle.tabSelected());

        int y = SIDEBAR_TOP;
        y = drawSidebarItem(graphics, view, null, 0, y, mouseX, mouseY); // All
        for (Category c : Category.topLevel()) {
            y = drawSidebarItem(graphics, view, c, 0, y, mouseX, mouseY);
            if (c.isContainer() && view.selected() != null && c.contains(view.selected())) {
                for (Category child : c.children()) {
                    y = drawSidebarItem(graphics, view, child, SIDEBAR_CHILD_INDENT, y, mouseX, mouseY);
                }
            }
        }
    }

    /** Bottom footer strip with the unified HUD position editor entry button. */
    public static void drawFooter(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int btnY = footerBtnY();
        boolean hover = mouseX >= FOOTER_BTN_X && mouseX <= FOOTER_BTN_X + FOOTER_BTN_W
                && mouseY >= btnY && mouseY <= btnY + FOOTER_BTN_H;
        ClickGuiStyle.button(graphics, FOOTER_BTN_X, btnY, FOOTER_BTN_W, FOOTER_BTN_H, hover, false);
        ClickGuiFonts.drawText(graphics, Localization.tr("HUD 位置调整"),
                FOOTER_BTN_X + 8, btnY + 5, ClickGuiStyle.text(), false);
    }

    // ---- internals -------------------------------------------------------------------------

    /** Whether the panel shows the sidebar (i.e. the modules list, not a settings page). */
    private static boolean showsSidebar(PanelView view) {
        // The sidebar stays while a module is open: the settings live in the card area, not over the
        // sidebar, so hiding it here made the settings look like a full-width page.
        return !view.settingsTab();
    }

    private static void drawMainTab(GuiGraphicsExtractor graphics, int x, int y, String label,
                                    boolean selected, int mouseX, int mouseY) {
        boolean hover = !selected && mouseX >= x && mouseX <= x + TAB_W && mouseY >= y && mouseY <= y + TAB_H;
        int bg = selected ? ClickGuiStyle.tabSelected() : (hover ? ClickGuiStyle.tabHover() : ClickGuiStyle.tab());
        fillRoundedRect(graphics, x, y, TAB_W, TAB_H, ClickGuiStyle.RADIUS, bg);
        ClickGuiFonts.drawText(graphics, label,
                x + (TAB_W - ClickGuiFonts.width(label)) / 2,
                y + (TAB_H - ClickGuiFonts.lineHeight()) / 2,
                selected ? ClickGuiStyle.text() : ClickGuiStyle.textDim(), true);
    }

    private static int drawSidebarItem(GuiGraphicsExtractor graphics, PanelView view, Category cat,
                                       int indent, int y, int mouseX, int mouseY) {
        boolean isAll = cat == null;
        boolean container = !isAll && cat.isContainer();
        boolean expanded = container && view.selected() != null && cat.contains(view.selected());
        boolean highlight;
        if (isAll) {
            highlight = view.selected() == null;
        } else if (container) {
            highlight = expanded;
        } else {
            highlight = view.selected() == cat;
        }
        boolean hover = mouseX >= 0 && mouseX <= SIDEBAR_W && sidebarRowHit(mouseY, y);

        // The selected highlight is the single animated bar drawn in drawSidebar;
        // here only the hover background for non-selected rows is drawn.
        if (!highlight && hover) {
            graphics.fill(0, y, SIDEBAR_W, y + SIDEBAR_ITEM_H, ClickGuiStyle.sidebarHover());
        }

        int textY = y + (SIDEBAR_ITEM_H - ClickGuiFonts.lineHeight()) / 2;
        int textColor = highlight ? ClickGuiStyle.text() : ClickGuiStyle.textDim();
        String label = isAll ? Localization.tr("全部") : Localization.tr(cat.label());
        ClickGuiFonts.drawText(graphics, label, indent + 12, textY, textColor, true);
        if (container) {
            // Expand indicator sits at the row's right edge, beside the label.
            String arrow = expanded ? "▼" : "►";
            ClickGuiFonts.drawText(graphics, arrow, SIDEBAR_W - 10 - ClickGuiFonts.width(arrow),
                    textY, textColor, true);
        }
        return y + SIDEBAR_ITEM_H;
    }

    private static void renderCard(GuiGraphicsExtractor graphics, Feature m, int cx, int cy,
                                   int mouseX, int mouseY) {
        boolean hover = mouseX >= cx && mouseX <= cx + CARD_W && mouseY >= cy && mouseY <= cy + CARD_H;
        // Rounded card: a full rounded rect with a border ring of uniform width. The ring is laid
        // out in device pixels (see fillRoundedBorderedRect) - building it as "outer rect + rect
        // inset by one design unit" snapped each side on its own and made the ring uneven.
        // No ring: the card separates from the panel by fill and a drop shadow.
        ClickGuiStyle.shadow(graphics, cx, cy, CARD_W, CARD_H, ROUND);
        dev.skypocket.render.GuiPixels.fillRoundedRect(graphics, cx, cy, CARD_W, CARD_H, ROUND,
                hover ? ClickGuiStyle.cardHover() : ClickGuiStyle.card());

        Identifier tex = ModuleIcon.texture(m.id());
        if (!Minecraft.getInstance().getResourceManager().getResourceStack(tex).isEmpty()) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, tex, cx + CARD_PAD, cy + CARD_PAD, 0, 0,
                    ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
        } else {
            graphics.fill(cx + CARD_PAD, cy + CARD_PAD, cx + CARD_PAD + ICON_SIZE,
                    cy + CARD_PAD + ICON_SIZE, ClickGuiStyle.accent());
            String letter = m.name().isEmpty() ? "?" : m.name().substring(0, 1);
            ClickGuiFonts.drawText(graphics, letter,
                    cx + CARD_PAD + (ICON_SIZE - ClickGuiFonts.width(letter)) / 2,
                    cy + CARD_PAD + (ICON_SIZE - ClickGuiFonts.lineHeight()) / 2, ClickGuiStyle.text(),
                true);
        }

        // Truncate long names so they never spill past the card's right edge (where the next card
        // would be drawn over them).
        ClickGuiFonts.drawText(graphics, fitTitle(m.name(), CARD_TITLE_MAX_W),
                cx + CARD_TITLE_X, cy + CARD_TITLE_Y, ClickGuiStyle.text(), true);

        int btnX = cx + CARD_BTN_X;
        int btnY = cy + CARD_BTN_Y;
        int btnW = CARD_BTN_W;
        // A feature without settings shows a clearly disabled button, so its card is visually
        // distinct from a configurable feature.
        boolean configurable = m.hasSettings();
        String options = Localization.tr(configurable ? "设置" : "无设置");
        if (configurable) {
            boolean btnHover = mouseX >= btnX && mouseX <= btnX + btnW
                    && mouseY >= btnY && mouseY <= btnY + CARD_BTN_H;
            ClickGuiStyle.button(graphics, btnX, btnY, btnW, CARD_BTN_H, btnHover, false);
        } else {
            ClickGuiStyle.surface(graphics, btnX, btnY, btnW, CARD_BTN_H,
                    ClickGuiStyle.disabled(), 0);
        }
        ClickGuiFonts.drawText(graphics, options,
                btnX + (btnW - ClickGuiFonts.width(options)) / 2, btnY + 5,
                configurable ? ClickGuiStyle.text() : ClickGuiStyle.disabledText(), false);

        boolean on = m.isEnabled();
        int stX = cx + CARD_STATUS_X;
        int stY = cy + CARD_STATUS_Y;
        int stW = CARD_STATUS_W;
        // The same accent green the toggles and tabs use, so "on" reads the same everywhere.
        ClickGuiStyle.surface(graphics, stX, stY, stW, CARD_STATUS_H,
                on ? ClickGuiStyle.accent() : ClickGuiStyle.danger(), 0);
        String status = on ? "ENABLED" : "DISABLED";
        ClickGuiFonts.drawText(graphics, status,
                stX + (stW - ClickGuiFonts.width(status)) / 2, stY + 4, ClickGuiStyle.text(), true);
    }

    /** Ellipsizes a card title so it fits the given design-pixel width. */
    private static String fitTitle(String text, int maxWidth) {
        if (ClickGuiFonts.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "…";
        int end = text.length();
        while (end > 1 && ClickGuiFonts.width(text.substring(0, end) + ellipsis) > maxWidth) {
            end--;
        }
        return text.substring(0, end) + ellipsis;
    }

    private static int panelBackgroundColor() {
        return (ConfigManager.get().clickGuiBackgroundAlpha << 24) | ClickGuiStyle.panelRgb();
    }

    private static int tabBarColor() {
        return (ConfigManager.get().clickGuiBackgroundAlpha << 24) | ClickGuiStyle.tabBarRgb();
    }

    /** Scales a colour's RGB channels (alpha kept), for subtle surface shading. */
    private static int shade(int argb, float factor) {
        int a = (argb >>> 24) & 0xFF;
        int r = Math.min(255, Math.round(((argb >> 16) & 0xFF) * factor));
        int g = Math.min(255, Math.round(((argb >> 8) & 0xFF) * factor));
        int b = Math.min(255, Math.round((argb & 0xFF) * factor));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
