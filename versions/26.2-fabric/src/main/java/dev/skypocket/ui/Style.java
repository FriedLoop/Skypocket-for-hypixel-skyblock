package dev.skypocket.ui;

import java.util.Objects;

/**
 * Immutable layout style. Width/height are border-box sizes; padding is inside, margin outside.
 * The visual properties (colour, radius, font family) deliberately live elsewhere for now.
 */
public final class Style {
    public enum Direction {
        ROW,
        COLUMN
    }

    public enum Position {
        FLOW,
        ABSOLUTE
    }

    public enum Justify {
        START,
        END,
        CENTER,
        SPACE_BETWEEN,
        SPACE_AROUND,
        SPACE_EVENLY
    }

    public enum Align {
        START,
        END,
        CENTER,
        STRETCH
    }

    public static final Style DEFAULT = builder().build();

    private final Dimension width;
    private final Dimension height;
    private final Dimension minWidth;
    private final Dimension minHeight;
    private final Dimension maxWidth;
    private final Dimension maxHeight;
    private final Insets margin;
    private final Insets padding;
    private final float gap;
    private final Direction direction;
    private final Justify justify;
    private final Align alignItems;
    private final float flexGrow;
    private final float flexShrink;
    private final Dimension flexBasis;
    private final Position position;
    private final Dimension left;
    private final Dimension right;
    private final Dimension top;
    private final Dimension bottom;

    private Style(Builder builder) {
        width = builder.width;
        height = builder.height;
        minWidth = builder.minWidth;
        minHeight = builder.minHeight;
        maxWidth = builder.maxWidth;
        maxHeight = builder.maxHeight;
        margin = builder.margin;
        padding = builder.padding;
        gap = builder.gap;
        direction = builder.direction;
        justify = builder.justify;
        alignItems = builder.alignItems;
        flexGrow = builder.flexGrow;
        flexShrink = builder.flexShrink;
        flexBasis = builder.flexBasis;
        position = builder.position;
        left = builder.left;
        right = builder.right;
        top = builder.top;
        bottom = builder.bottom;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Dimension width() {
        return width;
    }

    public Dimension height() {
        return height;
    }

    public Dimension minWidth() {
        return minWidth;
    }

    public Dimension minHeight() {
        return minHeight;
    }

    public Dimension maxWidth() {
        return maxWidth;
    }

    public Dimension maxHeight() {
        return maxHeight;
    }

    public Insets margin() {
        return margin;
    }

    public Insets padding() {
        return padding;
    }

    public float gap() {
        return gap;
    }

    public Direction direction() {
        return direction;
    }

    public Justify justify() {
        return justify;
    }

    public Align alignItems() {
        return alignItems;
    }

    public float flexGrow() {
        return flexGrow;
    }

    public float flexShrink() {
        return flexShrink;
    }

    public Dimension flexBasis() {
        return flexBasis;
    }

    public Position position() {
        return position;
    }

    public Dimension left() {
        return left;
    }

    public Dimension right() {
        return right;
    }

    public Dimension top() {
        return top;
    }

    public Dimension bottom() {
        return bottom;
    }

    public static final class Builder {
        private Dimension width = Dimension.AUTO;
        private Dimension height = Dimension.AUTO;
        private Dimension minWidth = Dimension.points(0f);
        private Dimension minHeight = Dimension.points(0f);
        private Dimension maxWidth = Dimension.points(Float.POSITIVE_INFINITY);
        private Dimension maxHeight = Dimension.points(Float.POSITIVE_INFINITY);
        private Insets margin = Insets.ZERO;
        private Insets padding = Insets.ZERO;
        private float gap;
        private Direction direction = Direction.ROW;
        private Justify justify = Justify.START;
        private Align alignItems = Align.START;
        private float flexGrow;
        private float flexShrink = 1f;
        private Dimension flexBasis = Dimension.AUTO;
        private Position position = Position.FLOW;
        private Dimension left = Dimension.AUTO;
        private Dimension right = Dimension.AUTO;
        private Dimension top = Dimension.AUTO;
        private Dimension bottom = Dimension.AUTO;

        public Builder from(Style style) {
            width = style.width;
            height = style.height;
            minWidth = style.minWidth;
            minHeight = style.minHeight;
            maxWidth = style.maxWidth;
            maxHeight = style.maxHeight;
            margin = style.margin;
            padding = style.padding;
            gap = style.gap;
            direction = style.direction;
            justify = style.justify;
            alignItems = style.alignItems;
            flexGrow = style.flexGrow;
            flexShrink = style.flexShrink;
            flexBasis = style.flexBasis;
            position = style.position;
            left = style.left;
            right = style.right;
            top = style.top;
            bottom = style.bottom;
            return this;
        }

        public Builder width(Dimension value) {
            width = Objects.requireNonNull(value);
            return this;
        }

        public Builder width(float value) {
            return width(Dimension.points(value));
        }

        public Builder height(Dimension value) {
            height = Objects.requireNonNull(value);
            return this;
        }

        public Builder height(float value) {
            return height(Dimension.points(value));
        }

        public Builder minWidth(float value) {
            minWidth = Dimension.points(value);
            return this;
        }

        public Builder minHeight(float value) {
            minHeight = Dimension.points(value);
            return this;
        }

        public Builder maxWidth(float value) {
            maxWidth = Dimension.points(value);
            return this;
        }

        public Builder maxHeight(float value) {
            maxHeight = Dimension.points(value);
            return this;
        }

        public Builder margin(Insets value) {
            margin = Objects.requireNonNull(value);
            return this;
        }

        public Builder margin(float value) {
            return margin(Insets.all(value));
        }

        public Builder padding(Insets value) {
            padding = Objects.requireNonNull(value);
            return this;
        }

        public Builder padding(float value) {
            return padding(Insets.all(value));
        }

        public Builder gap(float value) {
            gap = Math.max(0f, value);
            return this;
        }

        public Builder direction(Direction value) {
            direction = Objects.requireNonNull(value);
            return this;
        }

        public Builder justify(Justify value) {
            justify = Objects.requireNonNull(value);
            return this;
        }

        public Builder alignItems(Align value) {
            alignItems = Objects.requireNonNull(value);
            return this;
        }

        public Builder flexGrow(float value) {
            flexGrow = Math.max(0f, value);
            return this;
        }

        public Builder flexShrink(float value) {
            flexShrink = Math.max(0f, value);
            return this;
        }

        public Builder flexBasis(Dimension value) {
            flexBasis = Objects.requireNonNull(value);
            return this;
        }

        public Builder position(Position value) {
            position = Objects.requireNonNull(value);
            return this;
        }

        public Builder left(Dimension value) {
            left = Objects.requireNonNull(value);
            return this;
        }

        public Builder right(Dimension value) {
            right = Objects.requireNonNull(value);
            return this;
        }

        public Builder right(float value) {
            return right(Dimension.points(value));
        }

        public Builder top(Dimension value) {
            top = Objects.requireNonNull(value);
            return this;
        }

        public Builder top(float value) {
            return top(Dimension.points(value));
        }

        public Builder bottom(Dimension value) {
            bottom = Objects.requireNonNull(value);
            return this;
        }

        public Builder bottom(float value) {
            return bottom(Dimension.points(value));
        }

        public Builder left(float value) {
            return left(Dimension.points(value));
        }

        public Style build() {
            return new Style(this);
        }
    }
}
