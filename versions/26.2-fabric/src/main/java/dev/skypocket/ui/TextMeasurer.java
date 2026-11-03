package dev.skypocket.ui;

/** Adapter point between the layout core and whichever text renderer the host uses. */
@FunctionalInterface
public interface TextMeasurer {
    Size measure(String text, float maxWidth);
}
