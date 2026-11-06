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
        if (selected) {
            painter.fill(bounds(), ClickGuiStyle.sidebarSelected());
            painter.fill(new dev.skypocket.ui.Rect(bounds().x(), bounds().y(), 2f,
                    bounds().height()), ClickGuiStyle.tabSelected());
        } else if (hovered) {
            painter.fill(bounds(), ClickGuiStyle.sidebarHover());
        }

        String label = category == null ? Localization.tr("全部")
                : Localization.tr(category.label());
        painter.text(label, bounds().x() + 12f + indent,
                bounds().y() + (bounds().height() - painter.lineHeight()) / 2f,
                selected ? ClickGuiStyle.text() : ClickGuiStyle.textDim());
        if (container) {
            String arrow = expanded ? "▼" : "►";
            painter.text(arrow, bounds().right() - 10f - painter.textWidth(arrow),
                    bounds().y() + (bounds().height() - painter.lineHeight()) / 2f,
                    selected ? ClickGuiStyle.text() : ClickGuiStyle.textDim());
        }
    }
}
