package dev.skypocket.ui.input;

/** Keyboard event. Key codes follow Keys and the host's key-code convention. */
public record KeyEvent(int key, int scancode, int modifiers, boolean pressed) {
}
