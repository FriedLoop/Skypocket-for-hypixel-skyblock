package dev.skypocket.feature.modules;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.feature.FeatureModule;
import dev.skypocket.gui.Category;
import dev.skypocket.gui.setting.Setting;

import java.util.List;

/** Lists items picked up or lost recently, with counts. SkyBlock-only. */
public final class ItemPickupLogFeature implements FeatureModule {

    @Override
    public String id() {
        return "item_pickup_log";
    }

    @Override
    public String name() {
        return "物品拾取记录";
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
        return ConfigManager.get().pickupLogEnabled;
    }

    @Override
    public void setEnabled(boolean value) {
        ConfigManager.get().pickupLogEnabled = value;
    }

    @Override
    public List<Setting> settings() {
        var cfg = ConfigManager.get();
        return List.of(
                new Setting.Header("显示", "显示最近拾取或失去的物品及数量"),
                new Setting.ColorSetting("文字颜色",
                        () -> cfg.pickupLogTextColor, v -> cfg.pickupLogTextColor = v),
                new Setting.SliderSetting("显示时长", 1, 15,
                        () -> cfg.pickupLogDurationMs / 1000.0,
                        v -> cfg.pickupLogDurationMs = (int) Math.round(v * 1000),
                        "%.0fs", 1));
    }
}
