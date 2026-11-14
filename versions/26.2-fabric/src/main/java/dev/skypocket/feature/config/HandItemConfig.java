package dev.skypocket.feature.config;


import dev.skypocket.render.ColorUtil;

/**
 * Persisted state for the <code>hand_item</code> feature.
 *
 * <p>Fields keep the names they had on ModConfig so the rewrite of every read site is an
 * insertion rather than a rename. Defaults are the ones the flat fields carried.
 */
public final class HandItemConfig {
    public boolean handItemDisableReequip = false;
    public boolean handItemDisableSwing = false;
    public boolean handItemEnabled = false;
    public float handItemOffsetX = 0.0f;
    public float handItemOffsetY = 0.0f;
    public float handItemOffsetZ = 0.0f;
    public float handItemRotateX = 0.0f;
    public float handItemRotateY = 0.0f;
    public float handItemRotateZ = 0.0f;
    public float handItemScale = 1.0f;
    public boolean swingIgnoreHaste = false;
    public int swingSpeed = 100;
    /**

     * Clamps and repairs this block's fields after load.
     *
     * <p>Moved out of ModConfig.sanitize() when the fields moved here: a range belongs with the
     * field it constrains, and this class is the only one that knows it. Called from
     * ModConfig.sanitize(), which stays the single entry point.
     */
    public void sanitize() {
        handItemScale = ColorUtil.clamp(handItemScale, 0.1f, 4.0f);
        handItemOffsetX = ColorUtil.clamp(handItemOffsetX, -1f, 1f);
        handItemOffsetY = ColorUtil.clamp(handItemOffsetY, -1f, 1f);
        handItemOffsetZ = ColorUtil.clamp(handItemOffsetZ, -1f, 1f);
        handItemRotateX = ColorUtil.clamp(handItemRotateX, -180f, 180f);
        handItemRotateY = ColorUtil.clamp(handItemRotateY, -180f, 180f);
        handItemRotateZ = ColorUtil.clamp(handItemRotateZ, -180f, 180f);
        swingSpeed = ColorUtil.clamp(swingSpeed, 0, 200);
    }
}
