package dev.skypocket.feature.config;


import dev.skypocket.render.ColorUtil;

/**
 * Persisted state for the <code>hide_players_near_npcs</code> feature.
 *
 * <p>Fields keep the names they had on ModConfig so the rewrite of every read site is an
 * insertion rather than a rename. Defaults are the ones the flat fields carried.
 */
public final class HidePlayersNearNpcsConfig {
    public boolean hidePlayersNearNpcs = false;
    public float hidePlayersNearNpcsRange = 2.5f;
    /**

     * Clamps and repairs this block's fields after load.
     *
     * <p>Moved out of ModConfig.sanitize() when the fields moved here: a range belongs with the
     * field it constrains, and this class is the only one that knows it. Called from
     * ModConfig.sanitize(), which stays the single entry point.
     */
    public void sanitize() {
        hidePlayersNearNpcsRange = ColorUtil.clamp(hidePlayersNearNpcsRange, 0.5f, 8f);
    }
}
