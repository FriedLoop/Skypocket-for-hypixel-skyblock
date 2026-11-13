package dev.skypocket.feature;

import dev.skypocket.SkyPocketLog;
import dev.skypocket.config.ConfigManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The set of {@link FeatureModule}s, and the one place that wraps their writes.
 *
 * <p>Two jobs:
 *
 * <ul>
 *   <li>Hold the registered features in a stable order, so the panel's card grid and the HUD
 *       editor's target order do not shuffle between runs.</li>
 *   <li>Route {@link FeatureModule#setEnabled} through {@link ConfigManager#touch()}. That call is
 *       what invalidates the panel cache; without it a toggle visibly lags (DEVELOPMENT.md 5,
 *       "the button updates very slowly"). Today every {@code Module} constructor arranges this by
 *       hand, which is one thing too many for a feature author to remember.</li>
 * </ul>
 *
 * <h2>Status</h2>
 *
 * <p>Phase 0: this class exists and is correct, but nothing is registered in it yet and nothing in
 * the running game reads it. {@code ModuleDefinitions} still owns registration, so introducing this
 * package changes no behaviour. Features move across in Phase 1.
 */
public final class FeatureRegistry {
    private static final SkyPocketLog LOGGER = SkyPocketLog.of("feature");

    /** Insertion-ordered, so registration order is the order everything downstream sees. */
    private static final Map<String, FeatureModule> MODULES = new LinkedHashMap<>();

    private FeatureRegistry() {
    }

    /**
     * Registers a feature.
     *
     * <p>An empty or duplicate id is a programming error and is logged rather than silently
     * overwriting: two features claiming one id would mean the config block and the icon of one
     * shadow the other's, which is close to impossible to notice from the UI.
     */
    public static void register(FeatureModule module) {
        String id = module == null ? null : module.id();
        if (id == null || id.isEmpty()) {
            LOGGER.error("refusing to register a feature with no id: {}", module);
            return;
        }
        if (MODULES.containsKey(id)) {
            LOGGER.error("duplicate feature id '{}' ignored (already registered by {})",
                    id, MODULES.get(id).getClass().getName());
            return;
        }
        MODULES.put(id, module);
    }

    /** Every registered feature, in registration order. */
    public static List<FeatureModule> all() {
        return Collections.unmodifiableList(new ArrayList<>(MODULES.values()));
    }

    /** The feature with this id, or {@code null}. */
    public static FeatureModule byId(String id) {
        return MODULES.get(id);
    }

    /**
     * How many features have registered.
     *
     * <p>Used by the HUD editor to tell "no feature declares a HUD element" apart from "features
     * have not registered yet", which it otherwise cannot distinguish from an empty derived map.
     */
    public static int registeredCount() {
        return MODULES.size();
    }

    /**
     * Toggles a feature and invalidates the panel cache.
     *
     * <p>This is the wrapper described in the class comment: callers use this instead of
     * {@link FeatureModule#setEnabled} so the {@code touch()} cannot be forgotten.
     */
    public static void setEnabled(FeatureModule module, boolean value) {
        if (module == null) {
            return;
        }
        module.setEnabled(value);
        ConfigManager.touch();
    }

    /**
     * Whether this build runs features through the registry.
     *
     * <p>False during Phase 0-1, while {@code ModuleDefinitions} is still the source of truth and
     * the registry holds only the migrated subset. Callers that need "the set of all features" -
     * today only the panel - must keep using {@code ModuleRegistry} until this flips.
     */
    public static boolean isAuthoritative() {
        return false;
    }
}
