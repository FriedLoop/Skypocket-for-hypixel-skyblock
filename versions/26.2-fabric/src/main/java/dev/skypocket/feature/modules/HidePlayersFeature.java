package dev.skypocket.feature.modules;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.feature.FeatureModule;
import dev.skypocket.gui.Category;
import dev.skypocket.gui.setting.Setting;

import java.util.List;

/** Hides other players within a configurable range. */
public final class HidePlayersFeature implements FeatureModule {

    @Override
    public String id() {
        return "hide_players";
    }

    @Override
    public String name() {
        return "隐藏附近玩家";
    }

    @Override
    public Category category() {
        return Category.ESP;
    }
    @Override
    public Object config() {
        return ConfigManager.get().hidePlayersBlock;
    }

    @Override
    public boolean isEnabled() {
        return ConfigManager.get().hidePlayersBlock.hideNearbyPlayersEnabled;
    }

    @Override
    public void setEnabled(boolean value) {
        ConfigManager.get().hidePlayersBlock.hideNearbyPlayersEnabled = value;
    }

    @Override
    public List<Setting> settings() {
        var cfg = ConfigManager.get();
        return List.of(
                new Setting.Header("行为", "设置隐藏附近玩家的范围"),
                new Setting.SliderSetting("范围", 0, 10,
                        () -> cfg.hidePlayersBlock.hideNearbyPlayersRange,
                        v -> cfg.hidePlayersBlock.hideNearbyPlayersRange = (float) v, "%.1f", 0.1));
    }
}
