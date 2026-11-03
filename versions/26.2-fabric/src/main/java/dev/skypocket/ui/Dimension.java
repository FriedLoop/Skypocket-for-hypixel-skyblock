package dev.skypocket.ui;

/** A length that can be automatic, fixed in design units, or a percentage of the parent. */
public record Dimension(float value, Unit unit) {
    public enum Unit {
        AUTO,
        POINT,
        PERCENT
    }

    public static final Dimension AUTO = new Dimension(0f, Unit.AUTO);

    public Dimension {
        if (unit == Unit.PERCENT) {
            value = Math.max(0f, value);
        }
        if (unit == Unit.POINT) {
            value = Math.max(0f, value);
        }
    }

    public static Dimension points(float value) {
        return new Dimension(value, Unit.POINT);
    }

    public static Dimension percent(float value) {
        return new Dimension(value, Unit.PERCENT);
    }

    public boolean isAuto() {
        return unit == Unit.AUTO;
    }

    /** Resolves against a parent's content size; NaN means "not definite". */
    public float resolve(float parentSize) {
        return switch (unit) {
            case AUTO -> Float.NaN;
            case POINT -> value;
            case PERCENT -> parentSize == Float.POSITIVE_INFINITY ? Float.NaN
                    : parentSize * value * 0.01f;
        };
    }
}
