package dev.skypocket.feature.config;

/**
 * Persisted state for the <code>damage_splash_compact</code> feature.
 *
 * <p>Fields keep the names they had on ModConfig so the rewrite of every read site is an
 * insertion rather than a rename. Defaults are the ones the flat fields carried.
 */
public final class DamageSplashCompactConfig {
    public boolean damageSplashCompactEnabled = true;
    public boolean damageSplashEnabled = true;
    public int damageSplashFadeMs = 300;
    public int damageSplashLifetimeMs = 1500;
    public double damageSplashRadius = 1.5;
    public double damageSplashRightOffset = 0.6;
    public double damageSplashRowHeight = 0.3;
    public boolean damageSplashShowCount = false;
    public boolean damageSplashUppercase = true;
    public int damageSplashWindowMs = 1000;
}
