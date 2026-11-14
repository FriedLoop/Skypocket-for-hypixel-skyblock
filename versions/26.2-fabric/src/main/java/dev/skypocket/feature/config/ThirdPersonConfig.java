package dev.skypocket.feature.config;

/**
 * Persisted state for the <code>third_person</code> feature.
 *
 * <p>Fields keep the names they had on ModConfig so the rewrite of every read site is an
 * insertion rather than a rename. Defaults are the ones the flat fields carried.
 */
public final class ThirdPersonConfig {
    public boolean thirdPersonThroughWalls = false;
}
