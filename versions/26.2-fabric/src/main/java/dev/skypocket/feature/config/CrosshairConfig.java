package dev.skypocket.feature.config;



import dev.skypocket.config.ModConfig;
import dev.skypocket.render.ColorUtil;

/**
 * Persisted state for the <code>crosshair</code> feature.
 *
 * <p>Fields keep the names they had on ModConfig so the rewrite of every read site is an
 * insertion rather than a rename. Defaults are the ones the flat fields carried.
 */
public final class CrosshairConfig {
    public int crosshairAlpha = 255;
    public int crosshairBlue = 255;
    public int crosshairContainerColor = ColorUtil.argb(255, 0, 255, 255);
    public boolean crosshairEnabled = true;
    public boolean crosshairEntityColorEnabled = false;
    public int crosshairFriendlyColor = ColorUtil.argb(255, 0, 255, 0);
    public int crosshairGreen = 255;
    public boolean[] crosshairGrid = ModConfig.defaultCrosshair();
    public int crosshairHostileColor = ColorUtil.argb(255, 255, 0, 0);
    public int crosshairNeutralColor = ColorUtil.argb(255, 255, 255, 0);
    public float crosshairPixelSize = 3.0f;
    public int crosshairRed = 255;
    public float crosshairScale = 1.0f;
    public boolean crosshairStroke = false;
    public float crosshairStrokeWidth = 1.0f;
    /**

     * Clamps and repairs this block's fields after load.
     *
     * <p>Moved out of ModConfig.sanitize() when the fields moved here: a range belongs with the
     * field it constrains, and this class is the only one that knows it. Called from
     * ModConfig.sanitize(), which stays the single entry point.
     */
    public void sanitize() {
        if (crosshairGrid == null || crosshairGrid.length != ModConfig.GRID * ModConfig.GRID) {
            crosshairGrid = ModConfig.defaultCrosshair();
        }
        crosshairRed = ColorUtil.clamp(crosshairRed, 0, 255);
        crosshairGreen = ColorUtil.clamp(crosshairGreen, 0, 255);
        crosshairBlue = ColorUtil.clamp(crosshairBlue, 0, 255);
        crosshairAlpha = ColorUtil.clamp(crosshairAlpha, 0, 255);
        crosshairPixelSize = ColorUtil.clamp(crosshairPixelSize, 1f, 3f);
        crosshairScale = ColorUtil.clamp(crosshairScale, 0.25f, 4f);
        crosshairStrokeWidth = ColorUtil.clamp(crosshairStrokeWidth, 0.5f, 4f);
    }
}
