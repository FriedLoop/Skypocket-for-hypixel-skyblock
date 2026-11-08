package dev.skypocket.ui.widget;

/** Colours and radii shared by the widget set. A later theme layer can replace this with tokens. */
public record WidgetPalette(int background, int hover, int pressed, int outline, int focus,
                            int text, int muted, int accent, int field,
                            int switchOn, int switchOff, int onAccent, int description,
                            int scrollbarTrack, int scrollbarThumb, int selection,
                            float radius, float pillRadius) {
    public static final WidgetPalette DARK = new WidgetPalette(
            0xFF2A2F39, 0xFF3A4356, 0xFF536B9C, 0xFF434B5A, 0xFF9FB7FF,
            0xFFFFFFFF, 0xFFB8C0CF, 0xFF6F8FD8, 0xFF20242C,
            0xFF3A9E5F, 0xFF434B5A, 0xFFFFFFFF, 0xFFB8C0CF,
            0x552A2F39, 0xFF6F7A8C, 0x803A9E5F, 4f, 2f);

    /** Convenience for callers that only override colours. */
    public WidgetPalette(int background, int hover, int pressed, int outline, int focus,
                         int text, int muted, int accent) {
        this(background, hover, pressed, outline, focus, text, muted, accent, 0xFF20242C,
                0xFF3A9E5F, 0xFF434B5A, 0xFFFFFFFF, 0xFFB8C0CF,
                0x552A2F39, 0xFF6F7A8C, 0x803A9E5F, 4f, 2f);
    }
}
