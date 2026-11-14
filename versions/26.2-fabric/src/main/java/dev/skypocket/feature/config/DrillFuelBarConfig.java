package dev.skypocket.feature.config;


import dev.skypocket.render.ColorUtil;

/**
 * Persisted state for the <code>drill_fuel_bar</code> feature.
 *
 * <p>Fields keep the names they had on ModConfig so the rewrite of every read site is an
 * insertion rather than a rename. Defaults are the ones the flat fields carried.
 */
public final class DrillFuelBarConfig {
    public int drillFuelBarColor = ColorUtil.argb(255, 0, 170, 0);
    public boolean drillFuelBarEnabled = false;
    public int drillFuelBarHeight = 20;
    public float drillFuelBarScale = 1.0f;
    public int drillFuelBarWidth = 160;
    public int drillFuelBarX = 10;
    public int drillFuelBarY = 150;
    public boolean drillFuelShowNumber = true;
    /**

     * Clamps and repairs this block's fields after load.
     *
     * <p>Moved out of ModConfig.sanitize() when the fields moved here: a range belongs with the
     * field it constrains, and this class is the only one that knows it. Called from
     * ModConfig.sanitize(), which stays the single entry point.
     */
    public void sanitize() {
        drillFuelBarX = ColorUtil.clamp(drillFuelBarX, -100000, 100000);
        drillFuelBarY = ColorUtil.clamp(drillFuelBarY, -100000, 100000);
        drillFuelBarScale = ColorUtil.clamp(drillFuelBarScale, 0.25f, 6f);
        drillFuelBarWidth = ColorUtil.clamp(drillFuelBarWidth, 40, 400);
        drillFuelBarHeight = ColorUtil.clamp(drillFuelBarHeight, 8, 20);
    }
}
