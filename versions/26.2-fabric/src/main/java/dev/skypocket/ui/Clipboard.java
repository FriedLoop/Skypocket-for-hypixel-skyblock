package dev.skypocket.ui;

/**
 * Platform text clipboard.
 *
 * <p>A text field has to support copy and paste, but reaching for
 * {@code Minecraft.getInstance().keyboardHandler} from inside a widget drags the whole game onto the
 * widget's dependency list. The kernel declares the operation; {@code ui.mc} supplies the game's
 * implementation at startup, and the tests substitute a map.
 *
 * <p>One installation point rather than a constructor argument, for the same reason as
 * {@link UiPlatform}: there is one clipboard per process and threading it through every text field
 * constructor and its callers would be noise.
 */
public interface Clipboard {
    /** No-op clipboard, used before the host installs one and in tests. */
    Clipboard NONE = new Clipboard() {
        @Override
        public String get() {
            return "";
        }

        @Override
        public void set(String text) {
        }
    };

    /** Current contents, never null - an unavailable clipboard reads as empty. */
    String get();

    void set(String text);
}
