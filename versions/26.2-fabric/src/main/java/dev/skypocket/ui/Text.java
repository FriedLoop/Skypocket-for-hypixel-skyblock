package dev.skypocket.ui;

import java.util.Objects;

/** A leaf whose intrinsic size comes from the host's TextMeasurer. */
public final class Text extends UiElement {
    private String value;
    private final float fontScale;
    private final TextWeight weight;
    private int color = 0xFFE6EAF2;

    public Text(String value) {
        this(value, Style.DEFAULT);
    }

    public Text(String value, Style style) {
        this(value, style, 1f);
    }

    public Text(String value, Style style, float fontScale) {
        this(value, style, fontScale, TextWeight.REGULAR);
    }

    public Text(String value, Style style, float fontScale, TextWeight weight) {
        super(style);
        this.value = Objects.requireNonNull(value);
        this.fontScale = Math.max(0.1f, fontScale);
        this.weight = Objects.requireNonNull(weight);
    }

    public String value() {
        return value;
    }

    public float fontScale() {
        return fontScale;
    }

    public TextWeight weight() {
        return weight;
    }

    public int color() {
        return color;
    }

    public Text color(int next) {
        color = next;
        return this;
    }

    public Text value(String next) {
        value = Objects.requireNonNull(next);
        return this;
    }
}
