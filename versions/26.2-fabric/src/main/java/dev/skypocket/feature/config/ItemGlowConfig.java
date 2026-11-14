package dev.skypocket.feature.config;

/**
 * Persisted state for the <code>item_glow</code> feature.
 *
 * <p>Fields keep the names they had on ModConfig so the rewrite of every read site is an
 * insertion rather than a rename. Defaults are the ones the flat fields carried.
 */
public final class ItemGlowConfig {
    public boolean itemGlowEnabled = false;
}
