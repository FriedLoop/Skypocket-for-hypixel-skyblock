package dev.skypocket.feature;

import dev.skypocket.gui.Module;
import dev.skypocket.gui.ModuleRegistry;

/**
 * Bridges a {@link FeatureModule} into the existing {@code ModuleRegistry}.
 *
 * <p>The panel still draws {@code Feature} instances, and {@link Module} is the only implementation
 * it needs. Rather than teach the panel about {@code FeatureModule} in the same step that introduces
 * it, each migrated feature is adapted into the same {@code Module} the hand-written code used to
 * build. The panel therefore cannot tell the difference, which is what makes the migration testable
 * one feature at a time.
 *
 * <p>{@link Module}'s constructor wraps the setter in {@code ConfigManager.touch()}, so the panel
 * cache still invalidates correctly. {@link FeatureRegistry#setEnabled} is deliberately NOT used
 * here: going through it as well would call {@code touch()} twice per toggle, which is harmless but
 * misleading about where the responsibility lives.
 */
public final class ModuleAdapter {
    private ModuleAdapter() {
    }

    /**
     * Registers a {@code FeatureModule} as a panel feature.
     *
     * <p>When the feature declares a {@link HudSpec}, it is also attached to the HUD position
     * editor under the target name the spec carries. That is the other half of the SPI: the editor
     * no longer needs a hand-written entry for a migrated element. Attaching happens here rather
     * than in {@link FeatureRegistry} so that a feature without a HUD element costs nothing, and so
     * the editor is only referenced from the one place that already knows about both worlds.
     *
     * @return the adapted module, so the caller can chain {@code markSkyBlockOnly()} exactly as the
     *         hand-written registrations did
     */
    public static Module register(FeatureModule feature) {
        Module module = adapt(feature);
        ModuleRegistry.register(module);
        FeatureRegistry.register(feature);
        attachHud(feature);
        return module;
    }

    /**
     * Attaches the feature's {@link HudSpec} to its position-editor target, if it declares one.
     *
     * <p>The target name comes from the spec's {@code targetName}, which must match the editor's
     * enum constant. A mismatch is logged by the editor and leaves that element on its hand-written
     * entry, so a typo degrades one element instead of crashing the screen.
     */
    private static void attachHud(FeatureModule feature) {
        HudSpec spec = feature.hud();
        if (spec != null) {
            dev.skypocket.gui.HudPositionEditorScreen.derive(spec.targetName(), spec);
        }
    }

    /** Adapts without registering, for callers that need to configure the module first. */
    public static Module adapt(FeatureModule feature) {
        return new Module(feature.id(), feature.name(), feature.category(),
                feature::isEnabled, feature::setEnabled, feature.settings());
    }
}
