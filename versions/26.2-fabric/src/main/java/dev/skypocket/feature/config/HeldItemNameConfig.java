package dev.skypocket.feature.config;


import dev.skypocket.render.ColorUtil;

/**
 * Persisted state for the <code>held_item_name</code> feature.
 *
 * <p>Fields keep the names they had on ModConfig so the rewrite of every read site is an
 * insertion rather than a rename. Defaults are the ones the flat fields carried.
 */
public final class HeldItemNameConfig {
    public boolean heldItemNameEnabled = true;
    public int heldItemNameOffsetX = 0;
    public int heldItemNameOffsetY = 0;
    public float heldItemNameScale = 1.0f;
    /**

     * Clamps and repairs this block's fields after load.
     *
     * <p>Moved out of ModConfig.sanitize() when the fields moved here: a range belongs with the
     * field it constrains, and this class is the only one that knows it. Called from
     * ModConfig.sanitize(), which stays the single entry point.
     */
    public void sanitize() {
        heldItemNameOffsetX = ColorUtil.clamp(heldItemNameOffsetX, -100000, 100000);
        heldItemNameOffsetY = ColorUtil.clamp(heldItemNameOffsetY, -100000, 100000);
        heldItemNameScale = ColorUtil.clamp(heldItemNameScale, 0.25f, 6f);
    }
}
