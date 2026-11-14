package dev.skypocket.feature.config;

/**
 * Persisted state for the <code>no_hurt_camera</code> feature.
 *
 * <p>Fields keep the names they had on ModConfig so the rewrite of every read site is an
 * insertion rather than a rename. Defaults are the ones the flat fields carried.
 */
public final class NoHurtCameraConfig {
    public boolean noHurtCameraEnabled = false;
}
