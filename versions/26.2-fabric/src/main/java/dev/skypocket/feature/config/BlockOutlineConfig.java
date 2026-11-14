package dev.skypocket.feature.config;


import dev.skypocket.render.ColorUtil;

/**
 * Persisted state for the <code>block_outline</code> feature.
 *
 * <p>Fields keep the names they had on ModConfig so the rewrite of every read site is an
 * insertion rather than a rename. Defaults are the ones the flat fields carried.
 */
public final class BlockOutlineConfig {
    public int blockOutlineAlpha = 160;
    public int blockOutlineBlue = 0;
    public boolean blockOutlineEnabled = true;
    public int blockOutlineFaceAlpha = 60;
    public int blockOutlineFaceBlue = 255;
    public int blockOutlineFaceGreen = 160;
    public int blockOutlineFaceRed = 0;
    public boolean blockOutlineFaceTint = false;
    public int blockOutlineGreen = 0;
    public int blockOutlineRed = 0;
    public float blockOutlineThickness = 1.0f;
    /**

     * Clamps and repairs this block's fields after load.
     *
     * <p>Moved out of ModConfig.sanitize() when the fields moved here: a range belongs with the
     * field it constrains, and this class is the only one that knows it. Called from
     * ModConfig.sanitize(), which stays the single entry point.
     */
    public void sanitize() {
        blockOutlineRed = ColorUtil.clamp(blockOutlineRed, 0, 255);
        blockOutlineGreen = ColorUtil.clamp(blockOutlineGreen, 0, 255);
        blockOutlineBlue = ColorUtil.clamp(blockOutlineBlue, 0, 255);
        blockOutlineAlpha = ColorUtil.clamp(blockOutlineAlpha, 0, 255);
        blockOutlineThickness = ColorUtil.clamp(blockOutlineThickness, 0.5f, 6.0f);
        blockOutlineFaceRed = ColorUtil.clamp(blockOutlineFaceRed, 0, 255);
        blockOutlineFaceGreen = ColorUtil.clamp(blockOutlineFaceGreen, 0, 255);
        blockOutlineFaceBlue = ColorUtil.clamp(blockOutlineFaceBlue, 0, 255);
        blockOutlineFaceAlpha = ColorUtil.clamp(blockOutlineFaceAlpha, 0, 255);
    }
}
