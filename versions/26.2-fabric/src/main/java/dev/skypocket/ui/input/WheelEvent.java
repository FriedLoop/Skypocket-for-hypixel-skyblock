package dev.skypocket.ui.input;

/** Wheel event in design coordinates; positive delta scrolls toward the end. */
public record WheelEvent(float x, float y, float delta, int modifiers) {
}
