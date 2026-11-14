package dev.skypocket.feature.config;


import dev.skypocket.render.ColorUtil;

/**
 * Persisted state for the <code>legendary_sea_creature_warning</code> feature.
 *
 * <p>Fields keep the names they had on ModConfig so the rewrite of every read site is an
 * insertion rather than a rename. Defaults are the ones the flat fields carried.
 */
public final class LegendarySeaCreatureWarningConfig {
    public boolean legendarySeaCreatureWarning = false;
    public int legendaryWarningColor = ColorUtil.argb(255, 255, 60, 60);
    public int legendaryWarningDurationMs = 4000;
    /**

     * Clamps and repairs this block's fields after load.
     *
     * <p>Moved out of ModConfig.sanitize() when the fields moved here: a range belongs with the
     * field it constrains, and this class is the only one that knows it. Called from
     * ModConfig.sanitize(), which stays the single entry point.
     */
    public void sanitize() {
        legendaryWarningDurationMs = ColorUtil.clamp(legendaryWarningDurationMs, 500, 10_000);
    }
}
