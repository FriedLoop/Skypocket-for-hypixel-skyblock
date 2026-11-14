package dev.skypocket.feature.modules;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.feature.FeatureModule;
import dev.skypocket.gui.Category;
import dev.skypocket.gui.setting.Setting;

import java.util.List;

/** Custom outline colour for the Ender Dragon. SkyBlock-only. */
public final class DragonGlowFeature implements FeatureModule {

    @Override
    public String id() {
        return "dragon_glow";
    }

    @Override
    public String name() {
        return "末影龙发光";
    }

    @Override
    public Category category() {
        return Category.ESP;
    }
    @Override
    public Object config() {
        return ConfigManager.get().dragonGlowBlock;
    }

    @Override
    public boolean skyBlockOnly() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return ConfigManager.get().dragonGlowBlock.dragonGlowEnabled;
    }

    @Override
    public void setEnabled(boolean value) {
        ConfigManager.get().dragonGlowBlock.dragonGlowEnabled = value;
    }

    @Override
    public List<Setting> settings() {
        var cfg = ConfigManager.get();
        return List.of(
                new Setting.Header("外观", "调整末影龙发光的颜色"),
                new Setting.ColorSetting("发光颜色",
                        () -> cfg.dragonGlowBlock.dragonGlowColor, v -> cfg.dragonGlowBlock.dragonGlowColor = v));
    }
}
