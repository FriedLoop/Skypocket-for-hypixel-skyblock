package dev.skypocket.ui;

import java.util.Objects;

/** A leaf whose intrinsic size comes from the host's TextMeasurer. */
public final class Text extends UiElement {
    private String value;

    public Text(String value) {
        this(value, Style.DEFAULT);
    }

    public Text(String value, Style style) {
        super(style);
        this.value = Objects.requireNonNull(value);
    }

    public String value() {
        return value;
    }

    public Text value(String next) {
        value = Objects.requireNonNull(next);
        return this;
    }
}
