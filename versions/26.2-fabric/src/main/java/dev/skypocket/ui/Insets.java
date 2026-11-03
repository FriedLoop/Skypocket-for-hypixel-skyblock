package dev.skypocket.ui;

/** Four-sided spacing in design units. */
public record Insets(float top, float right, float bottom, float left) {
    public static final Insets ZERO = new Insets(0f, 0f, 0f, 0f);

    public static Insets all(float value) {
        return new Insets(value, value, value, value);
    }

    public static Insets symmetric(float vertical, float horizontal) {
        return new Insets(vertical, horizontal, vertical, horizontal);
    }

    public float horizontal() {
        return left + right;
    }

    public float vertical() {
        return top + bottom;
    }
}
