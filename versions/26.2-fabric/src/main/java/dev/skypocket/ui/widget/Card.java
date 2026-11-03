package dev.skypocket.ui.widget;

import dev.skypocket.ui.Box;
import dev.skypocket.ui.Painted;
import dev.skypocket.ui.Style;
import dev.skypocket.ui.UiPainter;

/** A rounded card that groups rows. The card itself is not interactive. */
public final class Card extends Box implements Painted {
    private final WidgetPalette palette;

    public Card(Style style, WidgetPalette palette) {
        super(style);
        this.palette = palette;
    }

    @Override
    public void paint(UiPainter painter) {
        painter.shadow(bounds(), palette.radius());
        painter.surface(bounds(), palette.radius(), palette.background(), 0);
    }
}
