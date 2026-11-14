package dev.skypocket.feature.config;


import dev.skypocket.render.ColorUtil;
import dev.skypocket.render.HotspotOverlayStyle;

/**
 * Persisted state for the <code>fishing_hotspot_overlay</code> feature.
 *
 * <p>Fields keep the names they had on ModConfig so the rewrite of every read site is an
 * insertion rather than a rename. Defaults are the ones the flat fields carried.
 */
public final class FishingHotspotOverlayConfig {
    public int hotspotColorDoubleHook = ColorUtil.argb(70, 0x55, 0x55, 0xFF);
    public int hotspotColorFishingSpeed = ColorUtil.argb(70, 0x55, 0xFF, 0xFF);
    public int hotspotColorSeaCreature = ColorUtil.argb(70, 0x00, 0xAA, 0xAA);
    public int hotspotColorTreasure = ColorUtil.argb(70, 0xFF, 0x8C, 0x00);
    public int hotspotColorTrophy = ColorUtil.argb(70, 0xFF, 0xAA, 0x00);
    public int hotspotOverlayColor = ColorUtil.argb(60, 0, 220, 255);
    public boolean hotspotOverlayEnabled = false;
    public float hotspotOverlayHeight = 1.0f;
    public float hotspotOverlayLineWidth = 2.0f;
    public float hotspotOverlayRange = 64.0f;
    public String hotspotOverlayStyle = "无底圆柱（侧面）";
    /**

     * Clamps and repairs this block's fields after load.
     *
     * <p>Moved out of ModConfig.sanitize() when the fields moved here: a range belongs with the
     * field it constrains, and this class is the only one that knows it. Called from
     * ModConfig.sanitize(), which stays the single entry point.
     */
    public void sanitize() {
        hotspotOverlayStyle = dev.skypocket.render.HotspotOverlayStyle
                .byLabel(hotspotOverlayStyle).label();
        hotspotOverlayHeight = ColorUtil.clamp(hotspotOverlayHeight, 0.25f, 256f);
        hotspotOverlayLineWidth = ColorUtil.clamp(hotspotOverlayLineWidth, 0.5f, 4f);
        hotspotOverlayRange = ColorUtil.clamp(hotspotOverlayRange, 8f, 256f);
    }
}
