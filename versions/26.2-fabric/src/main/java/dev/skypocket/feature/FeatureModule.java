package dev.skypocket.feature;

import dev.skypocket.gui.Category;
import dev.skypocket.gui.setting.Setting;

import java.util.List;

/**
 * One self-contained feature: its identity, its settings, its HUD element and its lifecycle.
 *
 * <p>This is the SPI that replaces the current arrangement, where adding a single feature means
 * touching six files - {@code ModuleDefinitions} (a register method plus a line in
 * {@code registerAll}), {@code ModConfig} (fields plus clamps), {@code HudPositionEditorScreen} (an
 * enum constant, a {@code SPECS} entry and two switch arms), {@code HudPositionSync} (a position
 * mapping), {@code SkyPocketMod} (register/init/tick), and the element's own class. Measured on the
 * {@code thunder_bottle} feature: 7 files and 28 wiring edits around ~120 lines of real logic.
 *
 * <p>The intent is that a feature declares itself here and the framework derives everything else.
 * A feature that overrides nothing beyond the required members is a toggle-only feature.
 *
 * <h2>Status</h2>
 *
 * <p>Phase 0: the interface is defined and {@link FeatureRegistry} exists, but the registry starts
 * empty and {@code ModuleDefinitions} still owns registration. Nothing in the running game reads
 * this package yet, so introducing it changes no behaviour. Features move onto it one at a time,
 * starting with the three simplest.
 */
public interface FeatureModule {

    /**
     * Stable identifier, also the config key and the icon texture name.
     *
     * <p>Must not change once released: it is written to disk and used to look up the feature by
     * name ({@code ModuleRegistry.byId}, the HUD editor's settings card).
     */
    String id();

    /**
     * Display name as a Chinese literal, the i18n key.
     *
     * <p>Passed through {@code Localization.tr} by the framework, so it must match an entry in
     * {@code Localization.EN} when English is active.
     */
    String name();

    /** Which sidebar node the feature's card appears under. */
    Category category();

    /**
     * Whether the feature only means anything on Hypixel SkyBlock.
     *
     * <p>Drives the SB badge on the card. It does <b>not</b> gate the feature's logic: a SkyBlock
     * feature must still call {@code SkyBlockDetector.isActive()} itself, exactly as before.
     */
    default boolean skyBlockOnly() {
        return false;
    }

    /**
     * The feature's own config block, or {@code null} when it has no persisted state.
     *
     * <p>Returning a dedicated object instead of adding fields to {@code ModConfig} is the point:
     * Gson serialises a nested object natively, so each block can carry its own shape, its own
     * defaults and its own migration step without touching a 294-field class.
     *
     * <p>The framework writes the returned instance's fields into {@code skypocket.json} under
     * {@link #id()}. Features must therefore return a stable object (a field, not a fresh instance
     * per call) so in-place writes are observed.
     */
    default Object config() {
        return null;
    }

    /**
     * Declarative settings, rendered into the shared settings page.
     *
     * <p>Same contract as today: a {@link Setting} list, and every setter must go through the
     * {@code ConfigManager.touch()} wrapper or the panel cache will keep showing stale values
     * (DEVELOPMENT.md 5, "the toggle updates very slowly").
     */
    default List<Setting> settings() {
        return List.of();
    }

    /**
     * The HUD element this feature owns, or {@code null} when it has none.
     *
     * <p>When present, the framework derives the position-editor target, the position-sync mapping
     * and the HUD registration from it, so none of those tables need a hand-written entry.
     */
    default HudSpec hud() {
        return null;
    }

    /**
     * Called once during client initialisation, in registration order.
     *
     * <p>Replaces the hand-written {@code XXX.init()} / {@code XXX.register()} lines in
     * {@code SkyPocketMod}. Must not assume anything about other features' initialisation.
     */
    default void init() {
    }

    /**
     * Called once per client tick, after {@link #init()}.
     *
     * <p>Replaces the hand-written {@code ClientTickEvents.END_CLIENT_TICK.register(...)} lines.
     * Keep it cheap: this runs 20 times a second whether or not the feature is enabled, so a
     * feature with expensive work should check {@code isEnabled()} first and return early.
     */
    default void tick() {
    }

    /**
     * Whether the feature is currently on.
     *
     * <p>Most features read one boolean out of their config block, so this is the single hook they
     * override rather than writing the getter themselves. Implementations that need more than a
     * field (a computed state, say) may override {@link #isEnabled()} directly instead.
     */
    boolean isEnabled();

    /**
     * Toggles the feature.
     *
     * <p>Implementations must route the write through the config block so the panel cache
     * invalidates; {@link FeatureRegistry} wraps this call in {@code ConfigManager.touch()}, so an
     * implementation only has to store the value.
     *
     * @throws UnsupportedOperationException when the feature is not togglable
     */
    void setEnabled(boolean value);
}
