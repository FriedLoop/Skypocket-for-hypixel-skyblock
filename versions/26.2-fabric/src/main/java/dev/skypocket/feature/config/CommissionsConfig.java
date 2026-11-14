package dev.skypocket.feature.config;


import dev.skypocket.render.ColorUtil;

/**
 * Persisted state for the <code>commissions</code> feature.
 *
 * <p>Fields keep the names they had on ModConfig so the rewrite of every read site is an
 * insertion rather than a rename. Defaults are the ones the flat fields carried.
 */
public final class CommissionsConfig {
    public int commissionsDoneColor = ColorUtil.argb(255, 85, 255, 85);
    public boolean commissionsEnabled = false;
    public int commissionsHeaderColor = ColorUtil.argb(255, 85, 85, 255);
    public boolean commissionsHideDone = false;
    public boolean commissionsPendingFirst = false;
    public float commissionsScale = 1.0f;
    public int commissionsStartColor = ColorUtil.argb(255, 255, 85, 85);
    public int commissionsX = 10;
    public int commissionsY = 120;
    /**

     * Clamps and repairs this block's fields after load.
     *
     * <p>Moved out of ModConfig.sanitize() when the fields moved here: a range belongs with the
     * field it constrains, and this class is the only one that knows it. Called from
     * ModConfig.sanitize(), which stays the single entry point.
     */
    public void sanitize() {
        commissionsX = ColorUtil.clamp(commissionsX, -100000, 100000);
        commissionsY = ColorUtil.clamp(commissionsY, -100000, 100000);
        commissionsScale = ColorUtil.clamp(commissionsScale, 0.25f, 6f);
    }
}
