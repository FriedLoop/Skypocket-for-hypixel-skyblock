package dev.skypocket.gui;

import dev.skypocket.gui.widget.ClickGuiStyle;
import dev.skypocket.ui.widget.WidgetPalette;

/** Maps the existing ClickGUI theme tokens onto the reusable widget palette. */
public final class Ui2Theme {
    private Ui2Theme() {
    }

    public static WidgetPalette palette() {
        return new WidgetPalette(
                ClickGuiStyle.button(),
                ClickGuiStyle.buttonHover(),
                ClickGuiStyle.accent(),
                ClickGuiStyle.switchOff(),
                ClickGuiStyle.accent(),
                ClickGuiStyle.text(),
                ClickGuiStyle.textMuted(),
                ClickGuiStyle.accent(),
                ClickGuiStyle.field(),
                ClickGuiStyle.switchOn(),
                ClickGuiStyle.switchOff(),
                ClickGuiStyle.onAccent(),
                ClickGuiStyle.description(),
                ClickGuiStyle.scrollbarTrack(),
                ClickGuiStyle.scrollbarThumb(),
                ClickGuiStyle.selection(),
                ClickGuiStyle.controlRadius(),
                ClickGuiStyle.PILL);
    }

    /**
     * Palette for a floating surface, where the background is the drawer itself rather than the
     * panel behind it.
     *
     * <p>Differs from {@link #palette()} in one field, and it matters: the plain palette uses the
     * button colour for card backgrounds. On a page that is fine - a card sits on the panel, and its
     * buttons sit on the card, so both layers are visible. A drawer has only one layer, so a button
     * painted with the same colour as the surface it rests on disappears entirely, leaving its label
     * apparently floating. The floating surface uses the translucent chrome colour instead, which is
     * what distinguishes a drawer from the game behind it.
     */
    public static WidgetPalette floatingPalette() {
        WidgetPalette base = palette();
        return new WidgetPalette(
                ClickGuiStyle.cardFloat(),
                base.hover(),
                base.pressed(),
                base.outline(),
                base.focus(),
                base.text(),
                base.muted(),
                base.accent(),
                base.field(),
                base.switchOn(),
                base.switchOff(),
                base.onAccent(),
                base.description(),
                base.scrollbarTrack(),
                base.scrollbarThumb(),
                base.selection(),
                base.radius(),
                base.pillRadius());
    }
}
