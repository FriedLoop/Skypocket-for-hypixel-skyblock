package dev.skypocket.feature.modules;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.feature.FeatureModule;
import dev.skypocket.gui.Category;
import dev.skypocket.gui.setting.Setting;

import java.util.List;

/** Shows how much charge the thunder, storm or hurricane bottle in the inventory holds. SkyBlock-only. */
public final class ThunderBottleFeature implements FeatureModule {

    @Override
    public String id() {
        return "thunder_bottle";
    }

    @Override
    public String name() {
        return "雷瓶显示";
    }

    @Override
    public Category category() {
        return Category.HUD;
    }

    @Override
    public boolean skyBlockOnly() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return ConfigManager.get().thunderBottleEnabled;
    }

    @Override
    public void setEnabled(boolean value) {
        ConfigManager.get().thunderBottleEnabled = value;
    }

    @Override
    public List<Setting> settings() {
        var cfg = ConfigManager.get();
        return List.of(
                new Setting.Header("显示", "显示背包中的雷瓶、风暴瓶或飓风瓶充能"),
                new Setting.ColorSetting("文字颜色",
                        () -> cfg.thunderBottleColor, v -> cfg.thunderBottleColor = v));
    }
}
