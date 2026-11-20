package dev.skypocket.gui;

import dev.skypocket.gui.widget.ClickGuiStyle;
import dev.skypocket.gui.widget.ThemeStore;
import dev.skypocket.ui.Dimension;
import dev.skypocket.ui.Rect;
import dev.skypocket.ui.Style;
import dev.skypocket.ui.UiPainter;
import dev.skypocket.ui.input.PointerEvent;
import dev.skypocket.ui.widget.Control;
import dev.skypocket.ui.widget.WidgetPalette;

/**
 * One row of the theme drawer's token list: a name, and either a colour swatch with its hex code or
 * the metric's current value.
 *
 * <p>Written as its own control rather than reusing {@code Button} because a button has no selected
 * state - it only knows hover and press - and no room for a trailing swatch. That gap produced a
 * subtle wrong behaviour in the drawer: the selected token got the rounded shape of a menu row while
 * keeping the plain background of an unselected one, so "selected" looked like "hovered with corners".
 *
 * <p>It is also not a {@code SidebarRow}: that class is bound to the homepage sidebar through its
 * Category, indent, expandable-container and arrow, none of which mean anything for a theme token.
 * Sharing a base class would have meant four abstract hooks to save about ten lines of paint code,
 * with the two rows having no reason to change together. The duplication here is deliberate.
 */
final class TokenRow extends Control {
    private static final float SWATCH_W = 20f;
    private static final float SWATCH_H = 14f;
    private static final float GAP = 8f;
    private static final float INSET_X = 6f;

    private final String token;
    private final boolean colorToken;
    private final boolean selected;
    private final Runnable action;

    TokenRow(String token, boolean colorToken, boolean selected, Dimension width, Dimension height,
             Runnable action, WidgetPalette palette) {
        super(Style.builder().width(width).height(height).build(), palette);
        this.token = token;
        this.colorToken = colorToken;
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
        // Both surfaces are inset horizontally so consecutive rows do not read as one continuous
        // band. Suppressed while baking: the static layer records with neutralPaint on, and a baked
        // hover or selection surface would stay in the cached texture after the state changed.
        if (!neutralPaint()) {
            Rect surface = new Rect(bounds().x() + INSET_X, bounds().y(),
                    bounds().width() - INSET_X * 2f, bounds().height());
            if (selected) {
                painter.fillRounded(surface, palette.radius(), ClickGuiStyle.listRowCurrent());
            } else if (hovered && !pressed) {
                painter.fillRounded(surface, palette.radius(), ClickGuiStyle.listRowHover());
            }
        }

        float textY = bounds().y() + (bounds().height() - painter.lineHeight()) / 2f;
        painter.text(token, bounds().x() + INSET_X + GAP, textY,
                selected ? ClickGuiStyle.text() : ClickGuiStyle.textDim());

        if (colorToken) {
            // Swatch, then its hex code to the left of it: the code is the thing being edited, so it
            // has to be readable without opening the picker.
            float swatchX = bounds().right() - INSET_X - GAP - SWATCH_W;
            float swatchY = bounds().y() + (bounds().height() - SWATCH_H) / 2f;
            painter.surface(new Rect(swatchX, swatchY, SWATCH_W, SWATCH_H),
                    palette.pillRadius(), ThemeStore.color(token), 0);
            String hex = String.format("%06X", ThemeStore.color(token) & 0xFFFFFF);
            painter.text(hex, swatchX - GAP - painter.textWidth(hex), textY,
                    ClickGuiStyle.textMuted());
        } else {
            String value = formatMetric(ThemeStore.metric(token));
            painter.text(value, bounds().right() - INSET_X - GAP - painter.textWidth(value), textY,
                    ClickGuiStyle.textMuted());
        }
    }

    /** Whole numbers without a trailing ".0"; other metrics to two places. */
    private static String formatMetric(double value) {
        return Math.abs(value - Math.rint(value)) < 0.0001
                ? Long.toString(Math.round(value))
                : String.format("%.2f", value);
    }
}
