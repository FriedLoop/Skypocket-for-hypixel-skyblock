package dev.skypocket.feature.config;


import dev.skypocket.render.ColorUtil;

/**
 * Persisted state for the <code>mining_ability_reminder</code> feature.
 *
 * <p>Fields keep the names they had on ModConfig so the rewrite of every read site is an
 * insertion rather than a rename. Defaults are the ones the flat fields carried.
 */
public final class MiningAbilityReminderConfig {
    public int miningAbilityAvailableColor = ColorUtil.argb(255, 0, 255, 0);
    public int miningAbilityNameColor = ColorUtil.argb(255, 255, 255, 0);
    public boolean miningAbilityReminderEnabled = false;
    public float miningAbilityReminderPitch = 1.0f;
    public String miningAbilityReminderSound = "minecraft:entity.experience_orb.pickup";
    public float miningAbilityReminderVolume = 1.0f;
    public float miningAbilityScale = 1.0f;
    public float miningAbilitySubtitleScale = 1.0f;
    public int miningAbilitySubtitleX = 120;
    public int miningAbilitySubtitleY = 90;
    public int miningAbilityX = 10;
    public int miningAbilityY = 70;
    /**

     * Clamps and repairs this block's fields after load.
     *
     * <p>Moved out of ModConfig.sanitize() when the fields moved here: a range belongs with the
     * field it constrains, and this class is the only one that knows it. Called from
     * ModConfig.sanitize(), which stays the single entry point.
     */
    public void sanitize() {
        miningAbilityReminderVolume = ColorUtil.clamp(miningAbilityReminderVolume, 0f, 2f);
        miningAbilityReminderPitch = ColorUtil.clamp(miningAbilityReminderPitch, 0.5f, 2f);
        miningAbilityX = ColorUtil.clamp(miningAbilityX, -100000, 100000);
        miningAbilityY = ColorUtil.clamp(miningAbilityY, -100000, 100000);
        miningAbilityScale = ColorUtil.clamp(miningAbilityScale, 0.25f, 6f);
        miningAbilitySubtitleX = ColorUtil.clamp(miningAbilitySubtitleX, -100000, 100000);
        miningAbilitySubtitleY = ColorUtil.clamp(miningAbilitySubtitleY, -100000, 100000);
        miningAbilitySubtitleScale = ColorUtil.clamp(miningAbilitySubtitleScale, 0.25f, 6f);
        if (miningAbilityReminderSound == null || miningAbilityReminderSound.isEmpty()) {
            miningAbilityReminderSound = "minecraft:entity.experience_orb.pickup";
        }
    }
}
