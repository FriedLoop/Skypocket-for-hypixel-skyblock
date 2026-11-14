package dev.skypocket.feature.config;


import dev.skypocket.render.ColorUtil;

/**
 * Persisted state for the <code>day_count</code> feature.
 *
 * <p>Fields keep the names they had on ModConfig so the rewrite of every read site is an
 * insertion rather than a rename. Defaults are the ones the flat fields carried.
 */
public final class DayCountConfig {
    public boolean dayCountEnabled = false;
    public float dayCountScale = 1.0f;
    public int dayCountX = 10;
    public int dayCountY = 40;
    /**

     * Clamps and repairs this block's fields after load.
     *
     * <p>Moved out of ModConfig.sanitize() when the fields moved here: a range belongs with the
     * field it constrains, and this class is the only one that knows it. Called from
     * ModConfig.sanitize(), which stays the single entry point.
     */
    public void sanitize() {
        dayCountX = ColorUtil.clamp(dayCountX, -100000, 100000);
        dayCountY = ColorUtil.clamp(dayCountY, -100000, 100000);
        dayCountScale = ColorUtil.clamp(dayCountScale, 0.25f, 6f);
    }
}
