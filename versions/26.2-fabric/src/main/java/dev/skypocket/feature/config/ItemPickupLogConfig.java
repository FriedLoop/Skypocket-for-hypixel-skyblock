package dev.skypocket.feature.config;


import dev.skypocket.render.ColorUtil;

/**
 * Persisted state for the <code>item_pickup_log</code> feature.
 *
 * <p>Fields keep the names they had on ModConfig so the rewrite of every read site is an
 * insertion rather than a rename. Defaults are the ones the flat fields carried.
 */
public final class ItemPickupLogConfig {
    public int pickupLogDurationMs = 5000;
    public boolean pickupLogEnabled = false;
    public float pickupLogScale = 1.0f;
    public int pickupLogTextColor = 0xFFFFFFFF;
    public int pickupLogX = 10;
    public int pickupLogY = 220;
    /**

     * Clamps and repairs this block's fields after load.
     *
     * <p>Moved out of ModConfig.sanitize() when the fields moved here: a range belongs with the
     * field it constrains, and this class is the only one that knows it. Called from
     * ModConfig.sanitize(), which stays the single entry point.
     */
    public void sanitize() {
        pickupLogX = ColorUtil.clamp(pickupLogX, -100000, 100000);
        pickupLogY = ColorUtil.clamp(pickupLogY, -100000, 100000);
        pickupLogScale = ColorUtil.clamp(pickupLogScale, 0.25f, 6f);
        pickupLogDurationMs = ColorUtil.clamp(pickupLogDurationMs, 1000, 15_000);
    }
}
