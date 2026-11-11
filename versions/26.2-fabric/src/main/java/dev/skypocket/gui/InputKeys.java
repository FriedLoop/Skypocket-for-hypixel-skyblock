package dev.skypocket.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.skypocket.ui.input.Keys;
import net.minecraft.client.input.KeyEvent;

/**
 * Boundary between Minecraft's platform key event and the UI kernel's canonical keys.
 *
 * <p>26.2 uses GLFW key numbers; 26.3 uses SDL3 numbers. The semantic methods on
 * {@link net.minecraft.client.input.InputWithModifiers} and the version-specific
 * {@link InputConstants#KEY_*} constants are the only safe way to name a key here.
 */
public final class InputKeys {
    private InputKeys() {
    }

    public static boolean isEscape(KeyEvent event) {
        return event.isEscape();
    }

    public static boolean isShift(KeyEvent event) {
        int key = event.key();
        return key == InputConstants.KEY_LSHIFT || key == InputConstants.KEY_RSHIFT;
    }

    public static boolean isControl(KeyEvent event) {
        int key = event.key();
        return key == InputConstants.KEY_LCONTROL || key == InputConstants.KEY_RCONTROL;
    }

    public static boolean isBackspace(KeyEvent event) {
        return event.key() == InputConstants.KEY_BACKSPACE;
    }

    /** Canonical key used by {@link dev.skypocket.ui.input.InputRouter}. */
    public static int canonical(KeyEvent event) {
        if (event.isCycleFocus()) {
            return Keys.TAB;
        }
        if (event.isEscape()) {
            return Keys.ESCAPE;
        }
        if (event.isConfirmation()) {
            return Keys.ENTER;
        }
        if (event.isLeft()) {
            return Keys.LEFT;
        }
        if (event.isRight()) {
            return Keys.RIGHT;
        }
        if (event.isUp()) {
            return Keys.UP;
        }
        if (event.isDown()) {
            return Keys.DOWN;
        }
        int key = event.key();
        if (key == InputConstants.KEY_BACKSPACE) {
            return Keys.BACKSPACE;
        }
        if (key == InputConstants.KEY_DELETE) {
            return Keys.DELETE;
        }
        if (key == InputConstants.KEY_A) {
            return Keys.A;
        }
        if (key == InputConstants.KEY_C) {
            return Keys.C;
        }
        if (key == InputConstants.KEY_X) {
            return Keys.X;
        }
        if (key == InputConstants.KEY_V) {
            return Keys.V;
        }
        return event.input();
    }
}
