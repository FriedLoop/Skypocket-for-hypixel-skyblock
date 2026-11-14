package dev.skypocket.feature.modules;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.feature.FeatureModule;
import dev.skypocket.gui.Category;
import dev.skypocket.gui.setting.Setting;

import java.util.List;

/** Shows the skill that just gained XP, with current progress. SkyBlock-only. */
public final class SkillDisplayFeature implements FeatureModule {

    @Override
    public String id() {
        return "skill_display";
    }

    @Override
    public String name() {
        return "技能进度显示";
    }

    @Override
    public Category category() {
        return Category.HUD;
    }
    @Override
    public Object config() {
        return ConfigManager.get().skillDisplayBlock;
    }

    @Override
    public boolean skyBlockOnly() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return ConfigManager.get().skillDisplayBlock.skillDisplayEnabled;
    }

    @Override
    public void setEnabled(boolean value) {
        ConfigManager.get().skillDisplayBlock.skillDisplayEnabled = value;
    }

    @Override
    public List<Setting> settings() {
        var cfg = ConfigManager.get();
        return List.of(
                new Setting.Header("显示",
                        "显示最近获得经验的技能、当前 XP 和下一级需求，图标使用原版物品"),
                new Setting.BooleanSetting("仅显示百分比",
                        () -> cfg.skillDisplayBlock.skillDisplayPercentageOnly,
                        v -> cfg.skillDisplayBlock.skillDisplayPercentageOnly = v),
                new Setting.ColorSetting("文字颜色",
                        () -> cfg.skillDisplayBlock.skillDisplayColor, v -> cfg.skillDisplayBlock.skillDisplayColor = v),
                new Setting.SliderSetting("显示时长", 1, 8,
                        () -> cfg.skillDisplayBlock.skillDisplayDurationMs / 1000.0,
                        v -> cfg.skillDisplayBlock.skillDisplayDurationMs = (int) Math.round(v * 1000),
                        "%.0fs", 1));
    }
}
