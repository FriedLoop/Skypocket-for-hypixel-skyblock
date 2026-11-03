package dev.skypocket.ui.widget;

import dev.skypocket.ui.Box;
import dev.skypocket.ui.Painted;
import dev.skypocket.ui.Style;
import dev.skypocket.ui.UiPainter;

/** A rounded card that groups rows. The card itself is not interactive. */
public final class Card extends Box implements Painted {
    private final WidgetPalette palette;
    private final boolean elevated;

    public Card(Style style, WidgetPalette palette) {
        this(style, palette, true);
    }

    public Card(Style style, WidgetPalette palette, boolean elevated) {
        super(style);
        this.palette = palette;
        this.elevated = elevated;
    }

    @Override
    public void paint(UiPainter painter) {
        // A page-level card must stay inside the cache bounds. Its shadow would be drawn outside the
        // page rectangle, where the live and cached paths cannot agree (the cache clips it). Inner
        // cards keep their shadow because it stays inside the page.
        if (elevated) {
            painter.shadow(bounds(), palette.radius());
        }
        painter.surface(bounds(), palette.radius(), palette.background(), 0);
    }
}
