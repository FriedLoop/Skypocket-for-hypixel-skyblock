package dev.skypocket.gui;

import java.util.ArrayList;
import java.util.List;

/**
 * Global registry of Click GUI features. Built-in features are registered once via
 * {@link #init()}; future features (IRC, party finder, ...) can call
 * {@link #register(Feature)} without touching the panel code.
 */
public final class ModuleRegistry {
    private static final List<Feature> FEATURES = new ArrayList<>();
    private static boolean initialized = false;

    private ModuleRegistry() {
    }

    public static void register(Feature feature) {
        FEATURES.add(feature);
    }

    public static List<Feature> features() {
        return FEATURES;
    }

    /**
     * The feature with this id, or null.
     *
     * <p>Used by the HUD position editor's settings card: a HUD element belongs to a feature, and the card
     * shows that feature's settings without going through the panel.
     */
    public static Feature byId(String id) {
        for (Feature feature : FEATURES) {
            if (feature.id().equals(id)) {
                return feature;
            }
        }
        return null;
    }

    /** Registers the built-in features exactly once. */
    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        ModuleDefinitions.registerAll();
    }
}
