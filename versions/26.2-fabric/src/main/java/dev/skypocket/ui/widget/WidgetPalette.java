package dev.skypocket.ui.widget;

/** Colours shared by the first widget set. A later theme layer can replace this with token lookups. */
public record WidgetPalette(int background, int hover, int pressed, int outline, int focus,
                            int text, int muted, int accent) {
    public static final WidgetPalette DARK = new WidgetPalette(
            0xFF2A2F39, 0xFF3A4356, 0xFF536B9C, 0xFF434B5A, 0xFF9FB7FF,
            0xFFFFFFFF, 0xFFB8C0CF, 0xFF6F8FD8);
}
