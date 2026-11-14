package dev.skypocket.feature.modules;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.feature.FeatureModule;
import dev.skypocket.gui.Category;
import dev.skypocket.gui.setting.Setting;

import java.util.List;

/** Hides real players standing at the Hub spawn point. SkyBlock-only. */
public final class HideSpawnPointPlayersFeature implements FeatureModule {

    @Override
    public String id() {
        return "hide_spawn_point_players";
    }

    @Override
    public String name() {
        return "隐藏出生点玩家";
    }

    @Override
    public Category category() {
        return Category.ESP;
    }
    @Override
    public Object config() {
        return ConfigManager.get().hideSpawnPointPlayersBlock;
    }

    @Override
    public boolean skyBlockOnly() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return ConfigManager.get().hideSpawnPointPlayersBlock.hideSpawnPointPlayers;
    }

    @Override
    public void setEnabled(boolean value) {
        ConfigManager.get().hideSpawnPointPlayersBlock.hideSpawnPointPlayers = value;
    }

    @Override
    public List<Setting> settings() {
        var cfg = ConfigManager.get();
        return List.of(
                new Setting.Header("行为", "隐藏站在 Hub 出生点的真实玩家"),
                new Setting.SliderSetting("出生点范围", 0.1, 3.0,
                        () -> cfg.hideSpawnPointPlayersBlock.hideSpawnPointRadius,
                        v -> cfg.hideSpawnPointPlayersBlock.hideSpawnPointRadius = (float) v, "%.1f", 0.1));
    }
}
