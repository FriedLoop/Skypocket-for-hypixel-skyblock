package dev.skypocket.feature.modules;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.feature.FeatureModule;
import dev.skypocket.gui.Category;
import dev.skypocket.gui.setting.Setting;
import dev.skypocket.sound.MentionSoundManager;
import dev.skypocket.sound.SoundManager;

import java.util.ArrayList;
import java.util.List;

/** Alerts when someone mentions the player in chat, with a configurable sound. */
public final class MentionAlertFeature implements FeatureModule {

    @Override
    public String id() {
        return "mention_alert";
    }

    @Override
    public String name() {
        return "聊天提及提醒";
    }

    @Override
    public Category category() {
        return Category.NOTIFICATION;
    }
    @Override
    public Object config() {
        return ConfigManager.get().mentionAlertBlock;
    }

    @Override
    public boolean isEnabled() {
        return ConfigManager.get().mentionAlertBlock.mentionAlertEnabled;
    }

    @Override
    public void setEnabled(boolean value) {
        ConfigManager.get().mentionAlertBlock.mentionAlertEnabled = value;
    }

    @Override
    public List<Setting> settings() {
        var cfg = ConfigManager.get();
        List<Setting> settings = new ArrayList<>();
        settings.add(new Setting.Header("提醒", "设置提及音效、文本与 ID 匹配长度"));
        settings.add(new Setting.DropdownSetting("提醒音效", SoundManager::listAllSounds,
                () -> cfg.mentionAlertBlock.mentionAlertSound, v -> cfg.mentionAlertBlock.mentionAlertSound = v));
        // Stored as a 0..2 gain but shown as a percentage, hence the *100 / /100 round-trip.
        settings.add(new Setting.SliderSetting("音量", 0, 200,
                () -> cfg.mentionAlertBlock.mentionAlertVolume * 100,
                v -> cfg.mentionAlertBlock.mentionAlertVolume = (float) (v / 100.0), "%.0f%%", 1));
        settings.add(new Setting.SliderSetting("音高", 0.5, 2.0,
                () -> cfg.mentionAlertBlock.mentionAlertPitch,
                v -> cfg.mentionAlertBlock.mentionAlertPitch = (float) v, "%.2f", 0.05));
        settings.add(new Setting.ActionSetting("快捷上传自定义音频",
                MentionSoundManager::pickAndImport));
        settings.add(new Setting.TextFieldSetting("自定义提醒文本",
                () -> cfg.mentionAlertBlock.mentionAlertCustomText, v -> cfg.mentionAlertBlock.mentionAlertCustomText = v, "在此输入文本"));
        settings.add(new Setting.SliderSetting("ID 检索长度", 1, 16,
                () -> cfg.mentionAlertBlock.mentionAlertNameLength,
                v -> cfg.mentionAlertBlock.mentionAlertNameLength = (int) Math.round(v), "%.0f", 1));
        return settings;
    }
}
