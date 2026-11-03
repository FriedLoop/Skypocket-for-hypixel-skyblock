package dev.skypocket.ui.input;

/** Pointer event in design coordinates. Button -1 means movement without a pressed button. */
public record PointerEvent(float x, float y, int button, int modifiers) {
    public static PointerEvent move(float x, float y, int modifiers) {
        return new PointerEvent(x, y, -1, modifiers);
    }

    public boolean isLeft() {
        return button == 0;
    }

    public boolean isRight() {
        return button == 1;
    }

    public boolean isMiddle() {
        return button == 2;
    }
}
