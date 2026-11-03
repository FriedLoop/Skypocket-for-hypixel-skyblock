package dev.skypocket.ui.input;

/** IME composition text; an empty text means the composition was cancelled. */
public record PreeditEvent(String text, int cursor) {
    public boolean cancelled() {
        return text.isEmpty();
    }
}
