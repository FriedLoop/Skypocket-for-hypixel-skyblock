package dev.skypocket.feature.config;


import dev.skypocket.render.ColorUtil;

/**
 * Persisted state for the <code>skill_display</code> feature.
 *
 * <p>Fields keep the names they had on ModConfig so the rewrite of every read site is an
 * insertion rather than a rename. Defaults are the ones the flat fields carried.
 */
public final class SkillDisplayConfig {
    public int skillDisplayColor = 0xFFFFFFFF;
    public int skillDisplayDurationMs = 4000;
    public boolean skillDisplayEnabled = false;
    public boolean skillDisplayPercentageOnly = false;
    public float skillDisplayScale = 1.0f;
    public int skillDisplayX = 10;
    public int skillDisplayY = 130;
    /**

     * Clamps and repairs this block's fields after load.
     *
     * <p>Moved out of ModConfig.sanitize() when the fields moved here: a range belongs with the
     * field it constrains, and this class is the only one that knows it. Called from
     * ModConfig.sanitize(), which stays the single entry point.
     */
    public void sanitize() {
        skillDisplayX = ColorUtil.clamp(skillDisplayX, -100000, 100000);
        skillDisplayY = ColorUtil.clamp(skillDisplayY, -100000, 100000);
        skillDisplayScale = ColorUtil.clamp(skillDisplayScale, 0.25f, 6f);
        skillDisplayDurationMs = ColorUtil.clamp(skillDisplayDurationMs, 500, 10_000);
    }
}
