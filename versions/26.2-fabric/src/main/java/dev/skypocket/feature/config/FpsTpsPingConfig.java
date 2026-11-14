package dev.skypocket.feature.config;


import dev.skypocket.render.ColorUtil;

/**
 * Persisted state for the <code>fps_tps_ping</code> feature.
 *
 * <p>Fields keep the names they had on ModConfig so the rewrite of every read site is an
 * insertion rather than a rename. Defaults are the ones the flat fields carried.
 */
public final class FpsTpsPingConfig {
    public boolean fpsTpsPingEnabled = false;
    public float fpsTpsPingScale = 1.0f;
    public boolean fpsTpsPingShowFps = true;
    public boolean fpsTpsPingShowPing = true;
    public boolean fpsTpsPingShowTps = true;
    public boolean fpsTpsPingVertical = true;
    public int fpsTpsPingX = 4;
    public int fpsTpsPingY = 4;
    /**

     * Clamps and repairs this block's fields after load.
     *
     * <p>Moved out of ModConfig.sanitize() when the fields moved here: a range belongs with the
     * field it constrains, and this class is the only one that knows it. Called from
     * ModConfig.sanitize(), which stays the single entry point.
     */
    public void sanitize() {
        fpsTpsPingX = ColorUtil.clamp(fpsTpsPingX, -100000, 100000);
        fpsTpsPingY = ColorUtil.clamp(fpsTpsPingY, -100000, 100000);
        fpsTpsPingScale = ColorUtil.clamp(fpsTpsPingScale, 0.25f, 6f);
    }
}
