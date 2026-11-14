package dev.skypocket.feature.config;


import dev.skypocket.render.ColorUtil;

/**
 * Persisted state for the <code>scrollable_tooltips</code> feature.
 *
 * <p>Fields keep the names they had on ModConfig so the rewrite of every read site is an
 * insertion rather than a rename. Defaults are the ones the flat fields carried.
 */
public final class ScrollableTooltipsConfig {
    public boolean scrollableTooltipsAlwaysScroll = false;
    public boolean scrollableTooltipsEnabled = true;
    public float scrollableTooltipsSensitivity = 1.0f;
    /**

     * Clamps and repairs this block's fields after load.
     *
     * <p>Moved out of ModConfig.sanitize() when the fields moved here: a range belongs with the
     * field it constrains, and this class is the only one that knows it. Called from
     * ModConfig.sanitize(), which stays the single entry point.
     */
    public void sanitize() {
        scrollableTooltipsSensitivity = ColorUtil.clamp(scrollableTooltipsSensitivity, 0.25f, 4.0f);
    }
}
