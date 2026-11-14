package dev.skypocket.feature.config;


import dev.skypocket.render.ColorUtil;

/**
 * Persisted state for the <code>thunder_bottle</code> feature.
 *
 * <p>Fields keep the names they had on ModConfig so the rewrite of every read site is an
 * insertion rather than a rename. Defaults are the ones the flat fields carried.
 */
public final class ThunderBottleConfig {
    public int thunderBottleColor = ColorUtil.argb(255, 0xAA, 0x00, 0xAA);
    public boolean thunderBottleEnabled = false;
    public float thunderBottleScale = 1.0f;
    public int thunderBottleX = 10;
    public int thunderBottleY = 198;
    /**

     * Clamps and repairs this block's fields after load.
     *
     * <p>Moved out of ModConfig.sanitize() when the fields moved here: a range belongs with the
     * field it constrains, and this class is the only one that knows it. Called from
     * ModConfig.sanitize(), which stays the single entry point.
     */
    public void sanitize() {
        thunderBottleX = ColorUtil.clamp(thunderBottleX, -100000, 100000);
        thunderBottleY = ColorUtil.clamp(thunderBottleY, -100000, 100000);
        thunderBottleScale = ColorUtil.clamp(thunderBottleScale, 0.25f, 6f);
    }
}
