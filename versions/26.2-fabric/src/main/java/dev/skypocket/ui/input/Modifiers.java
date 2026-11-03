package dev.skypocket.ui.input;

/** Platform-neutral modifier bits. The host adapter maps its own key state onto these. */
public final class Modifiers {
    public static final int SHIFT = 1;
    public static final int CONTROL = 1 << 1;
    public static final int ALT = 1 << 2;
    public static final int SUPER = 1 << 3;

    private Modifiers() {
    }

    public static boolean shift(int value) {
        return (value & SHIFT) != 0;
    }

    public static boolean control(int value) {
        return (value & CONTROL) != 0;
    }

    public static boolean alt(int value) {
        return (value & ALT) != 0;
    }
}
