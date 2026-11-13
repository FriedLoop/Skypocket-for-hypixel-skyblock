package dev.skypocket.feature.modules;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.feature.FeatureModule;
import dev.skypocket.gui.Category;
import dev.skypocket.gui.setting.Setting;

import java.util.List;

/** Warns when a legendary or mythic sea creature spawns. SkyBlock-only. */
public final class LegendarySeaCreatureWarningFeature implements FeatureModule {

    @Override
    public String id() {
        return "legendary_sea_creature_warning";
    }

    @Override
    public String name() {
        return "传奇海怪警告";
    }

    @Override
    public Category category() {
        return Category.FISHING;
    }

    @Override
    public boolean skyBlockOnly() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return ConfigManager.get().legendarySeaCreatureWarning;
    }

    @Override
    public void setEnabled(boolean value) {
        ConfigManager.get().legendarySeaCreatureWarning = value;
    }

    @Override
    public List<Setting> settings() {
        var cfg = ConfigManager.get();
        return List.of(
                new Setting.Header("警告", "检测传奇/神话海怪刷新并显示中央警告"),
                new Setting.ColorSetting("警告颜色",
                        () -> cfg.legendaryWarningColor, v -> cfg.legendaryWarningColor = v),
                // Stored in milliseconds, shown in whole seconds. The /1000 and *1000 round-trip is
                // kept exactly as it was: it is what produces the ".0s" readout.
                new Setting.SliderSetting("显示时长", 1, 10,
                        () -> cfg.legendaryWarningDurationMs / 1000.0,
                        v -> cfg.legendaryWarningDurationMs = (int) Math.round(v * 1000),
                        "%.0fs", 1));
    }
}
