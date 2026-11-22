package dev.skypocket.gui;

import dev.skypocket.ui.Style;
import dev.skypocket.ui.UiPainter;
import dev.skypocket.ui.input.PointerEvent;
import dev.skypocket.ui.widget.Control;
import dev.skypocket.ui.widget.WidgetPalette;

import java.util.function.BooleanSupplier;

/**
 * A back/forward arrow in the top bar.
 *
 * <p>Extracted from {@code ModulesPage} when the navigation moved out of the page tree and into the
 * top bar: the buttons are panel furniture, not page content, so they can no longer be a private
 * inner class of a page.
 *
 * <p>Disabled arrows stay hit-testable on purpose - a click is swallowed rather than falling through
 * to whatever is behind, so the arrow does not become a hole in the bar.
 */
final class NavButton extends Control {
    private final String glyph;
    private final BooleanSupplier enabled;
    private final Runnable action;

    NavButton(String glyph, BooleanSupplier enabled, Runnable action, WidgetPalette palette,
              float x, float y) {
        super(Style.builder().position(Style.Position.ABSOLUTE)
                .left(x).top(y)
                .width(PanelGeometry.NAV_SIZE).height(PanelGeometry.NAV_SIZE).build(),
                palette);
        this.glyph = glyph;
        this.enabled = enabled;
        this.action = action;
    }

    @Override
    public boolean focusable() {
        return false;
    }

    @Override
    protected void activate() {
        if (enabled.getAsBoolean()) {
            action.run();
        }
    }

    @Override
    public boolean onPointerDown(PointerEvent event) {
        if (!enabled.getAsBoolean()) {
            return true;
        }
        pressed = true;
        return true;
    }

    @Override
    public boolean onPointerUp(PointerEvent event) {
        if (pressed) {
            activate();
        }
        pressed = false;
        return true;
    }

    @Override
    public void paint(UiPainter painter) {
        boolean on = enabled.getAsBoolean();
        painter.surface(bounds(), dev.skypocket.gui.widget.ClickGuiStyle.controlRadius(),
                pressed ? palette.pressed()
                        : hovered && on ? palette.hover() : palette.background(), 0);
        painter.text(glyph, bounds().x() + (bounds().width() - painter.textWidth(glyph)) / 2f,
                bounds().y() + (bounds().height() - painter.lineHeight()) / 2f,
                on ? palette.text() : palette.muted());
    }
}
