package dev.skypocket.ui;

/**
 * Minimal drawing surface consumed by widgets. Coordinates are design units; the host supplies the
 * actual font renderer, colours and clip implementation.
 */
public interface UiPainter {
    void fill(Rect rect, int color);

    void outline(Rect rect, int color);

    void text(String text, float x, float y, int color);

    float textWidth(String text);

    float lineHeight();

    void pushClip(Rect rect);

    void popClip();
}
