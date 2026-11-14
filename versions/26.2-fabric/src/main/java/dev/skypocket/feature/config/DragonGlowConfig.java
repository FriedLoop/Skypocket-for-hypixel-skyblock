package dev.skypocket.feature.config;

/**
 * Persisted state for the <code>dragon_glow</code> feature.
 *
 * <p>Fields keep the names they had on ModConfig so the rewrite of every read site is an
 * insertion rather than a rename. Defaults are the ones the flat fields carried.
 */
public final class DragonGlowConfig {
    public int dragonGlowColor = dev.skypocket.config.ColorDefaults.DRAGON_GLOW;
    public boolean dragonGlowEnabled = false;
}
