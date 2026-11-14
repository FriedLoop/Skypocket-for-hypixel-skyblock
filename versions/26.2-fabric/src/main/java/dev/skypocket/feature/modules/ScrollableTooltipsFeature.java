package dev.skypocket.feature.modules;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.feature.FeatureModule;
import dev.skypocket.gui.Category;
import dev.skypocket.gui.setting.Setting;

import java.util.List;

/** Lets tooltips scroll when their content does not fit. */
public final class ScrollableTooltipsFeature implements FeatureModule {

    @Override
    public String id() {
        return "scrollable_tooltips";
    }

    @Override
    public String name() {
        return "滚动提示框";
    }

    @Override
    public Category category() {
        return Category.RENDER;
    }
    @Override
    public Object config() {
        return ConfigManager.get().scrollableTooltipsBlock;
    }

    @Override
    public boolean isEnabled() {
        return ConfigManager.get().scrollableTooltipsBlock.scrollableTooltipsEnabled;
    }

    @Override
    public void setEnabled(boolean value) {
        ConfigManager.get().scrollableTooltipsBlock.scrollableTooltipsEnabled = value;
    }

    @Override
    public List<Setting> settings() {
        var cfg = ConfigManager.get();
        return List.of(
                new Setting.Header("行为", "设置提示框的滚动方式与灵敏度"),
                new Setting.BooleanSetting("始终可滚动",
                        () -> cfg.scrollableTooltipsBlock.scrollableTooltipsAlwaysScroll,
                        v -> cfg.scrollableTooltipsBlock.scrollableTooltipsAlwaysScroll = v),
                new Setting.SliderSetting("滚动灵敏度", 0.25, 4.0,
                        () -> cfg.scrollableTooltipsBlock.scrollableTooltipsSensitivity,
                        v -> cfg.scrollableTooltipsBlock.scrollableTooltipsSensitivity = (float) v, "%.2fx", 0.05));
    }
}
