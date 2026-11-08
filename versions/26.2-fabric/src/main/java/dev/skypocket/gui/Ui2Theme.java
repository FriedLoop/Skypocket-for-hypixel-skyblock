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
                ClickGuiStyle.RADIUS,
                ClickGuiStyle.PILL);
    }
}
