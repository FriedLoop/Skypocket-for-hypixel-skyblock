package dev.skypocket.ui.widget;

import dev.skypocket.ui.Painted;
import dev.skypocket.ui.Style;
import dev.skypocket.ui.UiPainter;
import dev.skypocket.ui.UiElement;

/**
 * A solid colour swatch. The smallest useful proof that {@code Box} cannot draw.
 *
 * <p>Built for the v2 test screen, which needs to show a colour token's value next to its name.
 * {@code Box} is a pure layout container - it implements no {@link Painted} - so a box with a
 * background set in its style paints nothing at all, which is the same trap that left the theme
 * drawer without a background on its first migration attempt.
 */
public final class Swatch extends UiElement implements Painted {
    private final int color;

    public Swatch(int color, Style style) {
        super(style);
        this.color = color;
    }

    @Override
    public void paint(UiPainter painter) {
        painter.fill(bounds(), color);
    }
}
