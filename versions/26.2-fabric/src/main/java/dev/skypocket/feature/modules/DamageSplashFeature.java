package dev.skypocket.feature.modules;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.feature.FeatureModule;
import dev.skypocket.gui.Category;
import dev.skypocket.gui.setting.Setting;

import java.util.ArrayList;
import java.util.List;

/**
 * Merges and reformats SkyBlock damage splashes.
 *
 * <p>Note the id/name mismatch with the class: the module id is
 * {@code damage_splash_compact} but the toggle is {@code damageSplashEnabled}, because the
 * "compact" part is one of several behaviours this feature controls. Both are kept exactly as the
 * hand-written registration had them.
 */
public final class DamageSplashFeature implements FeatureModule {

    @Override
    public String id() {
        return "damage_splash_compact";
    }

    @Override
    public String name() {
        return "伤害飘字";
    }

    @Override
    public Category category() {
        return Category.COMBAT;
    }

    @Override
    public boolean skyBlockOnly() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return ConfigManager.get().damageSplashEnabled;
    }

    @Override
    public void setEnabled(boolean value) {
        ConfigManager.get().damageSplashEnabled = value;
    }

    @Override
    public List<Setting> settings() {
        var cfg = ConfigManager.get();
        List<Setting> settings = new ArrayList<>();
        settings.add(new Setting.Header("行为", "控制伤害飘字的合并、归属与显示次数"));
        settings.add(new Setting.BooleanSetting("合并",
                () -> cfg.damageSplashCompactEnabled, v -> cfg.damageSplashCompactEnabled = v));
        settings.add(new Setting.BooleanSetting("大写格式化",
                () -> cfg.damageSplashUppercase, v -> cfg.damageSplashUppercase = v));
        settings.add(new Setting.BooleanSetting("显示次数",
                () -> cfg.damageSplashShowCount, v -> cfg.damageSplashShowCount = v));
        settings.add(new Setting.SliderSetting("归属半径", 0.5, 3.0,
                () -> cfg.damageSplashRadius,
                v -> cfg.damageSplashRadius = v, "%.1f", 0.1));
        settings.add(new Setting.SliderSetting("合并窗口(毫秒)", 200, 3000,
                () -> cfg.damageSplashWindowMs,
                v -> cfg.damageSplashWindowMs = (int) Math.round(v), "%.0f", 100));

        settings.add(new Setting.Header("显示", "控制伤害飘字的显示时长、淡出与排版"));
        settings.add(new Setting.SliderSetting("显示时长(毫秒)", 300, 5000,
                () -> cfg.damageSplashLifetimeMs,
                v -> cfg.damageSplashLifetimeMs = (int) Math.round(v), "%.0f", 100));
        settings.add(new Setting.SliderSetting("淡出(毫秒)", 0, 1500,
                () -> cfg.damageSplashFadeMs,
                v -> cfg.damageSplashFadeMs = (int) Math.round(v), "%.0f", 50));
        settings.add(new Setting.SliderSetting("右侧偏移", 0.0, 2.0,
                () -> cfg.damageSplashRightOffset,
                v -> cfg.damageSplashRightOffset = v, "%.1f", 0.1));
        // Row height steps in 0.05 while displaying one decimal: two clicks look identical on the
        // readout but differ in layout, which is how the original was configured.
        settings.add(new Setting.SliderSetting("行高", 0.1, 0.8,
                () -> cfg.damageSplashRowHeight,
                v -> cfg.damageSplashRowHeight = v, "%.1f", 0.05));
        return settings;
    }
}
