package dev.skypocket.feature.config;


import dev.skypocket.render.ColorUtil;

/**
 * Persisted state for the <code>mention_alert</code> feature.
 *
 * <p>Fields keep the names they had on ModConfig so the rewrite of every read site is an
 * insertion rather than a rename. Defaults are the ones the flat fields carried.
 */
public final class MentionAlertConfig {
    public String mentionAlertCustomText = "";
    public boolean mentionAlertEnabled = false;
    public int mentionAlertNameLength = 16;
    public float mentionAlertPitch = 1.0f;
    public String mentionAlertSound = "minecraft:entity.experience_orb.pickup";
    public float mentionAlertVolume = 1.0f;
    /**

     * Clamps and repairs this block's fields after load.
     *
     * <p>Moved out of ModConfig.sanitize() when the fields moved here: a range belongs with the
     * field it constrains, and this class is the only one that knows it. Called from
     * ModConfig.sanitize(), which stays the single entry point.
     */
    public void sanitize() {
        mentionAlertNameLength = ColorUtil.clamp(mentionAlertNameLength, 1, 16);
        mentionAlertVolume = ColorUtil.clamp(mentionAlertVolume, 0f, 2f);
        mentionAlertPitch = ColorUtil.clamp(mentionAlertPitch, 0.5f, 2f);
        if (mentionAlertSound == null || mentionAlertSound.isEmpty() || mentionAlertSound.equals("ding")) {
            mentionAlertSound = "minecraft:entity.experience_orb.pickup";
        }
    }
}
