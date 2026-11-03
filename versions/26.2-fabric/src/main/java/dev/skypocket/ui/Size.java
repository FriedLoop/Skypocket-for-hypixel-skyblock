package dev.skypocket.ui;

/** A width/height pair in design units. */
public record Size(float width, float height) {
    public static final Size ZERO = new Size(0f, 0f);

    public Size {
        width = Math.max(0f, width);
        height = Math.max(0f, height);
    }
}
