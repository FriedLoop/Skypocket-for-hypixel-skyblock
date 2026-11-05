package dev.skypocket.ui;

/**
 * Drawing contract consumed by widgets. Coordinates are design units; the host supplies the actual
 * font renderer, colours, rounded-rect implementation and clip implementation.
 */
public interface UiPainter {
    void fill(Rect rect, int color);

    default void fillRounded(Rect rect, float radius, int color) {
        fill(rect, color);
    }

    /** Rounded top corners only, for the first row of a grouped surface. */
    default void fillRoundedTop(Rect rect, float radius, int color) {
        fillRounded(rect, radius, color);
    }

    /** Rounded bottom corners only, for the last row of a grouped surface. */
    default void fillRoundedBottom(Rect rect, float radius, int color) {
        fillRounded(rect, radius, color);
    }

    void outline(Rect rect, int color);

    default void outlineRounded(Rect rect, float radius, int color) {
        outline(rect, color);
    }

    /** Rounded surface with a one-pixel border ring. A zero border skips the ring. */
    default void surface(Rect rect, float radius, int fill, int border) {
        fillRounded(rect, radius, fill);
        if (border != 0) {
            outlineRounded(rect, radius, border);
        }
    }

    /** One-device-pixel rounded outline drawn on top of existing content. */
    default void strokeRounded(Rect rect, float radius, int color) {
        outlineRounded(rect, radius, color);
    }

    /** Pill surface; used by switch tracks and slider rails. */
    default void pill(Rect rect, int fill, int border) {
        float radius = rect.height() * 0.5f;
        if (border == 0) {
            fillRounded(rect, radius, fill);
        } else {
            surface(rect, radius, fill, border);
        }
    }

    /** Soft outer shadow; simple hosts may ignore it. */
    default void shadow(Rect rect, float radius) {
    }

    /** Vertical gradient, top colour to bottom colour. */
    default void fillGradient(Rect rect, int top, int bottom) {
        fill(rect, top);
    }

    /** Horizontal gradient, left colour to right colour. */
    default void fillHorizontalGradient(Rect rect, int left, int right) {
        fill(rect, left);
    }

    void text(String text, float x, float y, int color);

    /** Text at a fraction of the design font size; used for secondary copy. */
    default void textScaled(String text, float x, float y, int color, float scale) {
        text(text, x, y, color);
    }

    /** Text with an explicit weight and size; used by page/group headings. */
    default void textWeighted(String text, float x, float y, int color, float scale,
                              TextWeight weight) {
        textScaled(text, x, y, color, scale);
    }

    float textWidth(String text);

    float lineHeight();

    void pushClip(Rect rect);

    void popClip();
}
