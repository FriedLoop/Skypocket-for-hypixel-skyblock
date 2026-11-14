package dev.skypocket.feature.modules;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.feature.FeatureModule;
import dev.skypocket.gui.Category;
import dev.skypocket.gui.setting.Setting;

import java.util.List;

/** Hides Jerry presents that are neither addressed to nor sent by the player. SkyBlock-only. */
public final class HideOtherPlayersPresentsFeature implements FeatureModule {

    @Override
    public String id() {
        return "hide_other_players_presents";
    }

    @Override
    public String name() {
        return "隐藏其他玩家礼物";
    }

    @Override
    public Category category() {
        return Category.ESP;
    }
    @Override
    public Object config() {
        return ConfigManager.get().hideOtherPlayersPresentsBlock;
    }

    @Override
    public boolean skyBlockOnly() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return ConfigManager.get().hideOtherPlayersPresentsBlock.hideOtherPlayersPresents;
    }

    @Override
    public void setEnabled(boolean value) {
        ConfigManager.get().hideOtherPlayersPresentsBlock.hideOtherPlayersPresents = value;
    }

    @Override
    public List<Setting> settings() {
        return List.of(
                new Setting.Header("行为", "隐藏既不是给你的、也不是你送出的 Jerry 礼物"));
    }
}
