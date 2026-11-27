package dev.skypocket.gui;

import dev.skypocket.gui.widget.ClickGuiStyle;
import dev.skypocket.i18n.Localization;
import dev.skypocket.ui.Style;
import dev.skypocket.ui.UiPainter;
import dev.skypocket.ui.input.PointerEvent;
import dev.skypocket.ui.widget.Control;
import dev.skypocket.ui.widget.WidgetPalette;

import static dev.skypocket.gui.PanelGeometry.SIDEBAR_ITEM_H;
import static dev.skypocket.gui.PanelGeometry.SIDEBAR_W;

/** One category row in the v2 homepage sidebar. */
final class SidebarRow extends Control {
    private final Category category;
    private final float indent;
    private final boolean container;
    private final boolean expanded;
    private final boolean selected;
    private final Runnable action;

    SidebarRow(Category category, float indent, boolean container, boolean expanded,
               boolean selected, Runnable action, WidgetPalette palette) {
        super(Style.builder().width(SIDEBAR_W).height(SIDEBAR_ITEM_H).build(), palette);
        this.category = category;
        this.indent = indent;
        this.container = container;
        this.expanded = expanded;
        this.selected = selected;
        this.action = action;
    }

    @Override
    public boolean focusable() {
        return false;
    }

    @Override
    protected void activate() {
        action.run();
    }

    @Override
    public boolean onPointerDown(PointerEvent event) {
        pressed = true;
        return true;
    }

    @Override
    public boolean onPointerUp(PointerEvent event) {
        if (pressed) {
            action.run();
        }
        pressed = false;
        return true;
    }

    @Override
    public void paint(UiPainter painter) {
        // The selected pill is drawn by ModulesPage so it can slide between rows. Keep this row's
        // hover surface inset horizontally only; no vertical padding, no accent stripe.
        //
        // Suppressed while baking: the static layer records with neutralPaint on, and a baked hover
        // surface stays in the texture forever - a row keeps a highlight the cursor left long ago.
        if (!selected && hovered && !neutralPaint()) {
            float hx = bounds().x() + 16f;
            float hw = bounds().width() - 32f;
            painter.fillRounded(new dev.skypocket.ui.Rect(hx, bounds().y(), hw,
                    bounds().height()), ClickGuiStyle.controlRadius(), ClickGuiStyle.sidebarHover());
        }

        // Every row reserves the icon column, even the "all" row which has no icon. Reserving it
        // rather than collapsing it is what keeps the labels in one vertical line: without that the
        // first row's text would sit 26px left of every other row's.
        int iconSize = PanelGeometry.SIDEBAR_ICON_SIZE;
        IconAtlas.Icon icon = category.icon();
        float iconX = bounds().x() + 25f + indent;
        if (icon != null) {
            float iconY = bounds().y() + (bounds().height() - iconSize) / 2f;
            IconAtlas.paint(painter, icon,
                    new dev.skypocket.ui.Rect(iconX, iconY, iconSize, iconSize),
                    selected ? ClickGuiStyle.text() : ClickGuiStyle.textDim());
        }
        float textX = iconX + iconSize + PanelGeometry.SIDEBAR_ICON_GAP;

        String label = Localization.tr(category.label());
        painter.text(label, textX,
                bounds().y() + (bounds().height() - painter.lineHeight()) / 2f,
                selected ? ClickGuiStyle.text() : ClickGuiStyle.textDim());
        if (container) {
            String arrow = expanded ? "▼" : "►";
            painter.text(arrow, bounds().right() - 25f - painter.textWidth(arrow),
                    bounds().y() + (bounds().height() - painter.lineHeight()) / 2f,
                    selected ? ClickGuiStyle.text() : ClickGuiStyle.textDim());
        }
    }
}
