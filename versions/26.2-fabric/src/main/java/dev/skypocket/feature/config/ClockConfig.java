package dev.skypocket.feature.config;


import dev.skypocket.render.ColorUtil;

/**
 * Persisted state for the <code>clock</code> feature.
 *
 * <p>Fields keep the names they had on ModConfig so the rewrite of every read site is an
 * insertion rather than a rename. Defaults are the ones the flat fields carried.
 */
public final class ClockConfig {
    public boolean clockEnabled = true;
    public boolean clockFormat24h = true;
    public float clockScale = 1.0f;
    public int clockX = 10;
    public int clockY = 10;
    /**

     * Clamps and repairs this block's fields after load.
     *
     * <p>Moved out of ModConfig.sanitize() when the fields moved here: a range belongs with the
     * field it constrains, and this class is the only one that knows it. Called from
     * ModConfig.sanitize(), which stays the single entry point.
     */
    public void sanitize() {
        clockX = ColorUtil.clamp(clockX, -100000, 100000);
        clockY = ColorUtil.clamp(clockY, -100000, 100000);
        clockScale = ColorUtil.clamp(clockScale, 0.25f, 6f);
    }
}
