package dev.skypocket.feature.config;

/**
 * Persisted state for the {@code hide_stuck_arrows} feature.
 *
 * <p>One of the first per-feature config blocks: it exists to prove the shape before the other 34
 * features are moved onto it. The point of a block is that a feature owns its own fields instead of
 * adding another entry to a 311-field {@code ModConfig} - and, later, that a block can be moved
 * wholesale into the feature's own class.
 *
 * <p>Gson serialises this as a nested object under the feature's {@code id()}, and a field missing
 * from the file keeps the initialiser below, which is the same contract {@code ModConfig} already
 * relies on for its own defaults.
 *
 * <p>Fields are plain and public like {@code ModConfig}'s: the settings page writes through setters
 * that call {@code ConfigManager.touch()}, and readers read the field directly. Getters here would
 * add a layer without changing what can go wrong.
 */
public final class HideStuckArrowsConfig {
    /** Hide arrows stuck in the player's body. */
    public boolean enabled = false;
}
