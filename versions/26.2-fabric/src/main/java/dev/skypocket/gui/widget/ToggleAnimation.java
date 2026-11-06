package dev.skypocket.gui.widget;

/**
 * The progress of one animated on/off switch.
 *
 * <p><b>Held by the owning setting, not by the widget.</b> Rows are rebuilt on every scroll, so a
 * widget holding its own progress would come back already at the new value and the switch would never
 * appear to move - the same reason the colour picker keeps its expanded state on the setting.
 *
 * <p><b>Time-driven rather than per-draw.</b> A transition that only advanced while it was drawn would
 * stall off-screen and then resume from the old value. Reading the clock means it completes either way.
 */
public final class ToggleAnimation {
    /** Seconds for a full sweep; in the same range as the panel's own open/close animation. */
    private static final float DURATION = 0.14f;

    private float from;
    private float to;
    private long startedNanos;
    private boolean started;

    /** Starts a transition toward {@code on} unless one is already heading there. */
    public void to(boolean on) {
        float target = on ? 1f : 0f;
        if (!started) {
            // First sight of this switch: appear in its resting position, do not animate from off.
            started = true;
            from = target;
            to = target;
            return;
        }
        if (target == to) {
            return;
        }
        from = progress();
        to = target;
        startedNanos = System.nanoTime();
    }

    /** Current progress: 0 = off, 1 = on. */
    public float progress() {
        if (!started || from == to) {
            return to;
        }
        float t = (System.nanoTime() - startedNanos) / (DURATION * 1.0e9f);
        if (t >= 1f) {
            from = to;
            return to;
        }
        // Ease-out, the same feel as the panel's own animations.
        float eased = 1f - (1f - t) * (1f - t);
        return from + (to - from) * eased;
    }
}
