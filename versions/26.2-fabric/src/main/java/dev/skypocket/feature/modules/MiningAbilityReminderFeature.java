package dev.skypocket.feature.modules;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;
import dev.skypocket.feature.FeatureModule;
import dev.skypocket.gui.Category;
import dev.skypocket.gui.setting.Setting;
import dev.skypocket.sound.SoundManager;

import java.util.ArrayList;
import java.util.List;

/** Reminds the player when the pickaxe ability comes off cooldown. SkyBlock-only. */
public final class MiningAbilityReminderFeature implements FeatureModule {

    @Override
    public String id() {
        return "mining_ability_reminder";
    }

    @Override
    public String name() {
        return "挖矿技能提醒";
    }

    @Override
    public Category category() {
        return Category.MINING;
    }
    @Override
    public Object config() {
        return ConfigManager.get().miningAbilityReminderBlock;
    }

    @Override
    public boolean skyBlockOnly() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return ConfigManager.get().miningAbilityReminderBlock.miningAbilityReminderEnabled;
    }

    @Override
    public void setEnabled(boolean value) {
        ConfigManager.get().miningAbilityReminderBlock.miningAbilityReminderEnabled = value;
    }

    @Override
    public List<Setting> settings() {
        ModConfig cfg = ConfigManager.get();
        List<Setting> settings = new ArrayList<>();
        settings.add(new Setting.Header("提醒", "调整技能名与可用颜色、缩放和提醒音效"));
        settings.add(new Setting.ColorSetting("技能名颜色",
                () -> cfg.miningAbilityReminderBlock.miningAbilityNameColor, v -> cfg.miningAbilityReminderBlock.miningAbilityNameColor = v));
        settings.add(new Setting.ColorSetting("可用颜色",
                () -> cfg.miningAbilityReminderBlock.miningAbilityAvailableColor, v -> cfg.miningAbilityReminderBlock.miningAbilityAvailableColor = v));
        settings.add(new Setting.SliderSetting("冷却缩放", 0.25, 6.0,
                () -> cfg.miningAbilityReminderBlock.miningAbilityScale,
                v -> cfg.miningAbilityReminderBlock.miningAbilityScale = (float) v, "%.2f", ModConfig.SCALE_GRID));
        settings.add(new Setting.SliderSetting("提醒缩放", 0.25, 6.0,
                () -> cfg.miningAbilityReminderBlock.miningAbilitySubtitleScale,
                v -> cfg.miningAbilityReminderBlock.miningAbilitySubtitleScale = (float) v, "%.2f", ModConfig.SCALE_GRID));
        settings.add(new Setting.DropdownSetting("提醒音效", SoundManager::listAllSounds,
                () -> cfg.miningAbilityReminderBlock.miningAbilityReminderSound,
                v -> cfg.miningAbilityReminderBlock.miningAbilityReminderSound = v));
        // Volume is stored as a 0..2 gain and shown as 0..200%.
        settings.add(new Setting.SliderSetting("音量", 0, 200,
                () -> cfg.miningAbilityReminderBlock.miningAbilityReminderVolume * 100,
                v -> cfg.miningAbilityReminderBlock.miningAbilityReminderVolume = (float) (v / 100.0), "%.0f%%", 1));
        settings.add(new Setting.SliderSetting("音高", 0.5, 2.0,
                () -> cfg.miningAbilityReminderBlock.miningAbilityReminderPitch,
                v -> cfg.miningAbilityReminderBlock.miningAbilityReminderPitch = (float) v, "%.2f", 0.05));
        return settings;
    }
}
