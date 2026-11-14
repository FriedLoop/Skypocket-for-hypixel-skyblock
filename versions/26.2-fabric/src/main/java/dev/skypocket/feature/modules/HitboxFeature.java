package dev.skypocket.feature.modules;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;
import dev.skypocket.feature.FeatureModule;
import dev.skypocket.gui.Category;
import dev.skypocket.gui.setting.Setting;

import java.util.List;

/** Draws entity hitboxes, optionally filtered by entity type and range. */
public final class HitboxFeature implements FeatureModule {

    @Override
    public String id() {
        return "hitbox";
    }

    @Override
    public String name() {
        return "碰撞箱";
    }

    @Override
    public Category category() {
        return Category.ESP;
    }
    @Override
    public Object config() {
        return ConfigManager.get().hitboxBlock;
    }

    @Override
    public boolean isEnabled() {
        return ConfigManager.get().hitboxBlock.hitboxEnabled;
    }

    @Override
    public void setEnabled(boolean value) {
        ConfigManager.get().hitboxBlock.hitboxEnabled = value;
    }

    @Override
    public List<Setting> settings() {
        ModConfig cfg = ConfigManager.get();
        return List.of(
                new Setting.Header("显示", "调整碰撞箱颜色、线宽和视线"),
                new Setting.ColorSetting("颜色",
                        () -> cfg.hitboxBlock.hitboxColor, v -> cfg.hitboxBlock.hitboxColor = v),
                new Setting.SliderSetting("线宽", 0.5, 8.0,
                        () -> cfg.hitboxBlock.hitboxLineWidth,
                        v -> cfg.hitboxBlock.hitboxLineWidth = (float) v, "%.1f", 0.5),
                new Setting.BooleanSetting("实体视线",
                        () -> cfg.hitboxBlock.hitboxShowLineOfSight, v -> cfg.hitboxBlock.hitboxShowLineOfSight = v),
                new Setting.ColorSetting("视线颜色",
                        () -> cfg.hitboxBlock.lookRayColor, v -> cfg.hitboxBlock.lookRayColor = v),
                new Setting.SliderSetting("视线长度", 1.0, 64.0,
                        () -> cfg.hitboxBlock.lookRayLength,
                        v -> cfg.hitboxBlock.lookRayLength = (float) v, "%.1f", 0.5),

                new Setting.Header("过滤", "选择要显示碰撞箱的实体类型"),
                new Setting.BooleanSetting("玩家",
                        () -> cfg.hitboxBlock.hitboxShowPlayer, v -> cfg.hitboxBlock.hitboxShowPlayer = v),
                new Setting.BooleanSetting("箭矢",
                        () -> cfg.hitboxBlock.hitboxShowProjectile, v -> cfg.hitboxBlock.hitboxShowProjectile = v),
                new Setting.BooleanSetting("敌对生物",
                        () -> cfg.hitboxBlock.hitboxShowHostile, v -> cfg.hitboxBlock.hitboxShowHostile = v),
                new Setting.BooleanSetting("中立生物",
                        () -> cfg.hitboxBlock.hitboxShowNeutral, v -> cfg.hitboxBlock.hitboxShowNeutral = v),
                new Setting.BooleanSetting("箱子",
                        () -> cfg.hitboxBlock.hitboxShowContainer, v -> cfg.hitboxBlock.hitboxShowContainer = v),
                new Setting.BooleanSetting("其他",
                        () -> cfg.hitboxBlock.hitboxShowOther, v -> cfg.hitboxBlock.hitboxShowOther = v),

                new Setting.Header("范围", "控制碰撞箱的显示距离"),
                new Setting.BooleanSetting("不限距离",
                        () -> cfg.hitboxBlock.hitboxUnlimitedRange, v -> cfg.hitboxBlock.hitboxUnlimitedRange = v),
                new Setting.SliderSetting("范围", 1.0, 128.0,
                        () -> cfg.hitboxBlock.hitboxRange,
                        v -> cfg.hitboxBlock.hitboxRange = (float) v, "%.0f", 1));
    }
}
