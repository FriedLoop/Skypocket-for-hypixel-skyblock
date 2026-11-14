package dev.skypocket.feature.config;



import dev.skypocket.config.ModConfig;
import dev.skypocket.render.ColorUtil;

/**
 * Persisted state for the <code>hitbox</code> feature.
 *
 * <p>Fields keep the names they had on ModConfig so the rewrite of every read site is an
 * insertion rather than a rename. Defaults are the ones the flat fields carried.
 */
public final class HitboxConfig {
    public int hitboxColor = dev.skypocket.config.ColorDefaults.HITBOX;
    public boolean hitboxEnabled = false;
    public float hitboxLineWidth = 2.0f;
    public float hitboxRange = 16.0f;
    public boolean hitboxShowContainer = true;
    public boolean hitboxShowHostile = true;
    public boolean hitboxShowLineOfSight = false;
    public boolean hitboxShowNeutral = true;
    public boolean hitboxShowOther = true;
    public boolean hitboxShowPlayer = true;
    public boolean hitboxShowProjectile = true;
    public boolean hitboxUnlimitedRange = false;
    public int lookRayColor = dev.skypocket.config.ColorDefaults.LOOK_RAY;
    public float lookRayLength = 8.0f;
    /**

     * Clamps and repairs this block's fields after load.
     *
     * <p>Moved out of ModConfig.sanitize() when the fields moved here: a range belongs with the
     * field it constrains, and this class is the only one that knows it. Called from
     * ModConfig.sanitize(), which stays the single entry point.
     */
    public void sanitize() {
        hitboxRange = ColorUtil.clamp(hitboxRange, 1f, 128f);
        hitboxLineWidth = ColorUtil.clamp(hitboxLineWidth, 0.5f, 8f);
        lookRayLength = ColorUtil.clamp(lookRayLength, 1f, 64f);
    }
}
