package dev.skypocket.feature.modules;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.feature.FeatureModule;
import dev.skypocket.gui.Category;
import dev.skypocket.gui.setting.Setting;

import java.util.List;

/** Outlines the local player in third person with a configurable colour. */
public final class PlayerGlowFeature implements FeatureModule {

    @Override
    public String id() {
        return "player_glow";
    }

    @Override
    public String name() {
        return "人物发光";
    }

    @Override
    public Category category() {
        return Category.ESP;
    }
    @Override
    public Object config() {
        return ConfigManager.get().playerGlowBlock;
    }

    @Override
    public boolean isEnabled() {
        return ConfigManager.get().playerGlowBlock.playerGlowEnabled;
    }

    @Override
    public void setEnabled(boolean value) {
        ConfigManager.get().playerGlowBlock.playerGlowEnabled = value;
    }

    @Override
    public List<Setting> settings() {
        var cfg = ConfigManager.get();
        return List.of(
                new Setting.Header("外观", "调整玩家发光的颜色"),
                new Setting.ColorSetting("发光颜色",
                        () -> cfg.playerGlowBlock.playerGlowColor, v -> cfg.playerGlowBlock.playerGlowColor = v));
    }
}
