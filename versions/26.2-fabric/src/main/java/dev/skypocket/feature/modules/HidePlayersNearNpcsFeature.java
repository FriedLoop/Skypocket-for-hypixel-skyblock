package dev.skypocket.feature.modules;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.feature.FeatureModule;
import dev.skypocket.gui.Category;
import dev.skypocket.gui.setting.Setting;

import java.util.List;

/** Hides players near SkyBlock NPCs. SkyBlock-only. */
public final class HidePlayersNearNpcsFeature implements FeatureModule {

    @Override
    public String id() {
        return "hide_players_near_npcs";
    }

    @Override
    public String name() {
        return "隐藏 NPC 附近玩家";
    }

    @Override
    public Category category() {
        return Category.ESP;
    }
    @Override
    public Object config() {
        return ConfigManager.get().hidePlayersNearNpcsBlock;
    }

    @Override
    public boolean skyBlockOnly() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return ConfigManager.get().hidePlayersNearNpcsBlock.hidePlayersNearNpcs;
    }

    @Override
    public void setEnabled(boolean value) {
        ConfigManager.get().hidePlayersNearNpcsBlock.hidePlayersNearNpcs = value;
    }

    @Override
    public List<Setting> settings() {
        var cfg = ConfigManager.get();
        return List.of(
                new Setting.Header("行为", "隐藏站在 SkyBlock NPC 附近的真实玩家"),
                new Setting.SliderSetting("NPC 附近范围", 0.5, 8.0,
                        () -> cfg.hidePlayersNearNpcsBlock.hidePlayersNearNpcsRange,
                        v -> cfg.hidePlayersNearNpcsBlock.hidePlayersNearNpcsRange = (float) v, "%.1f", 0.5));
    }
}
