package dev.skypocket.gui;

import java.util.ArrayList;
import java.util.List;

/**
 * Built-in easing curves for the Click GUI open/close animation. Each curve maps a
 * linear progress {@code t} in [0, 1] to an eased value (mostly in [0, 1]; back /
 * elastic / bounce briefly overshoot or undershoot, which is intentional).
 */
public enum Easing {
    LINEAR("linear"),
    EASE("ease"),
    EASE_IN("ease-in"),
    EASE_OUT("ease-out"),
    EASE_IN_OUT("ease-in-out"),
    QUAD_IN("quad-in"),
    QUAD_OUT("quad-out"),
    QUAD_IN_OUT("quad-in-out"),
    CUBIC_IN("cubic-in"),
    CUBIC_OUT("cubic-out"),
    CUBIC_IN_OUT("cubic-in-out"),
    EXPO_IN("expo-in"),
    EXPO_OUT("expo-out"),
    EXPO_IN_OUT("expo-in-out"),
    BACK_OUT("back-out"),
    ELASTIC_OUT("elastic-out"),
    BOUNCE_OUT("bounce-out");

    private static final float BACK_C1 = 1.70158f;
    private static final float BACK_C3 = BACK_C1 + 1f;
    private static final float ELASTIC_C4 = (float) (2.0 * Math.PI / 3.0);
    private static final float BOUNCE_N1 = 7.5625f;
    private static final float BOUNCE_D1 = 2.75f;

    public final String id;

    Easing(String id) {
        this.id = id;
    }

    /** Eased progress for a linear {@code t} (clamped to [0, 1]). */
    public float apply(float t) {
        t = Math.max(0f, Math.min(1f, t));
        switch (this) {
            case LINEAR:
                return t;
            case EASE:
                return t * t * (3f - 2f * t); // smoothstep
            case EASE_IN:
            case QUAD_IN:
                return t * t;
            case EASE_OUT:
            case QUAD_OUT:
                return 1f - (1f - t) * (1f - t);
            case EASE_IN_OUT:
            case QUAD_IN_OUT:
                return t < 0.5f ? 2f * t * t : 1f - (float) Math.pow(-2f * t + 2f, 2) / 2f;
            case CUBIC_IN:
                return t * t * t;
            case CUBIC_OUT:
                return 1f - (float) Math.pow(1f - t, 3);
            case CUBIC_IN_OUT:
                return t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
            case EXPO_IN:
                return t == 0f ? 0f : (float) Math.pow(2f, 10f * t - 10f);
            case EXPO_OUT:
                return t == 1f ? 1f : 1f - (float) Math.pow(2f, -10f * t);
            case EXPO_IN_OUT:
                if (t == 0f) {
                    return 0f;
                }
                if (t == 1f) {
                    return 1f;
                }
                return t < 0.5f
                        ? (float) Math.pow(2f, 20f * t - 10f) / 2f
                        : (2f - (float) Math.pow(2f, -20f * t + 10f)) / 2f;
            case BACK_OUT:
                return 1f + BACK_C3 * (float) Math.pow(t - 1f, 3) + BACK_C1 * (float) Math.pow(t - 1f, 2);
            case ELASTIC_OUT:
                if (t == 0f) {
                    return 0f;
                }
                if (t == 1f) {
                    return 1f;
                }
                return (float) Math.pow(2f, -10f * t) * (float) Math.sin((t * 10f - 0.75f) * ELASTIC_C4) + 1f;
            case BOUNCE_OUT:
                return bounceOut(t);
            default:
                return t;
        }
    }

    private static float bounceOut(float t) {
        if (t < 1f / BOUNCE_D1) {
            return BOUNCE_N1 * t * t;
        } else if (t < 2f / BOUNCE_D1) {
            t -= 1.5f / BOUNCE_D1;
            return BOUNCE_N1 * t * t + 0.75f;
        } else if (t < 2.5f / BOUNCE_D1) {
            t -= 2.25f / BOUNCE_D1;
            return BOUNCE_N1 * t * t + 0.9375f;
        } else {
            t -= 2.625f / BOUNCE_D1;
            return BOUNCE_N1 * t * t + 0.984375f;
        }
    }

    /** Curve for an id, or {@link #EASE_OUT} when unknown. */
    public static Easing byId(String id) {
        if (id != null) {
            for (Easing e : values()) {
                if (e.id.equals(id)) {
                    return e;
                }
            }
        }
        return EASE_OUT;
    }

    /** All curve ids, for the settings dropdown. */
    public static List<String> ids() {
        List<String> list = new ArrayList<>(values().length);
        for (Easing e : values()) {
            list.add(e.id);
        }
        return list;
    }
}
