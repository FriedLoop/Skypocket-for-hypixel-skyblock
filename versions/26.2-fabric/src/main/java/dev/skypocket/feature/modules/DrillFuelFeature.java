package dev.skypocket.feature.modules;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.feature.FeatureModule;
import dev.skypocket.gui.Category;
import dev.skypocket.gui.setting.Setting;

import java.util.List;

/** Shows the drill's remaining fuel as a bar while a drill is held. SkyBlock-only. */
public final class DrillFuelFeature implements FeatureModule {

    @Override
    public String id() {
        return "drill_fuel_bar";
    }

    @Override
    public String name() {
        return "钻头燃料条";
    }

    @Override
    public Category category() {
        return Category.MINING;
    }

    @Override
    public boolean skyBlockOnly() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return ConfigManager.get().drillFuelBarEnabled;
    }

    @Override
    public void setEnabled(boolean value) {
        ConfigManager.get().drillFuelBarEnabled = value;
    }

    @Override
    public List<Setting> settings() {
        var cfg = ConfigManager.get();
        return List.of(
                new Setting.Header("显示", "手持钻头时显示燃料条，使用现有条形纹理"),
                new Setting.BooleanSetting("显示数字",
                        () -> cfg.drillFuelShowNumber, v -> cfg.drillFuelShowNumber = v),
                new Setting.ColorSetting("燃料条颜色",
                        () -> cfg.drillFuelBarColor, v -> cfg.drillFuelBarColor = v),
                // Width and height are ints; the slider still reports a double and truncates.
                new Setting.SliderSetting("宽度", 40, 400,
                        () -> cfg.drillFuelBarWidth, v -> cfg.drillFuelBarWidth = (int) v,
                        "%.0f", 1),
                new Setting.SliderSetting("高度", 8, 20,
                        () -> cfg.drillFuelBarHeight, v -> cfg.drillFuelBarHeight = (int) v,
                        "%.0f", 1));
    }
}
