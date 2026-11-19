package dev.skypocket.ui;

/**
 * The device pixel ratio the UI is being drawn at.
 *
 * <p>A cached element has to know the scale it was baked at, because baking happens at device
 * resolution while drawing happens in design units. Reading that from
 * {@code Minecraft.getInstance().getWindow()} works but pins any caller to the game; this is the
 * same value behind an interface so the cache math can be tested with a fixed scale.
 */
public interface GuiScale {
    /** Assume 1 until the host installs the real value. */
    GuiScale ONE = () -> 1f;

    float get();
}
