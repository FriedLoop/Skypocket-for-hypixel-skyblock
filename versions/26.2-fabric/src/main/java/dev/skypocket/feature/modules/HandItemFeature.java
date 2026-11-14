package dev.skypocket.feature.modules;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;
import dev.skypocket.feature.FeatureModule;
import dev.skypocket.gui.Category;
import dev.skypocket.gui.setting.Setting;

import java.util.List;

/** Resizes and repositions the first-person hand item. */
public final class HandItemFeature implements FeatureModule {

    @Override
    public String id() {
        return "hand_item";
    }

    @Override
    public String name() {
        return "手持物品动画";
    }

    @Override
    public Category category() {
        return Category.RENDER;
    }

    @Override
    public boolean isEnabled() {
        return ConfigManager.get().handItemEnabled;
    }

    @Override
    public void setEnabled(boolean value) {
        ConfigManager.get().handItemEnabled = value;
    }

    @Override
    public List<Setting> settings() {
        ModConfig cfg = ConfigManager.get();
        return List.of(
                new Setting.Header("外观", "调整手持物品的大小"),
                new Setting.SliderSetting("物品大小", 0.1, 4.0,
                        () -> cfg.handItemScale,
                        v -> cfg.handItemScale = (float) v, "%.2f", ModConfig.SCALE_GRID),

                new Setting.Header("动作", "控制挥动、切换和挥砍速度"),
                new Setting.BooleanSetting("禁用挥动动画",
                        () -> cfg.handItemDisableSwing, v -> cfg.handItemDisableSwing = v),
                new Setting.BooleanSetting("禁用切换动画",
                        () -> cfg.handItemDisableReequip, v -> cfg.handItemDisableReequip = v),
                // Swing speed is an int percentage: 0 means the vanilla speed is used untouched.
                new Setting.SliderSetting("挥砍速度", 0.0, 200.0,
                        () -> cfg.swingSpeed,
                        v -> cfg.swingSpeed = (int) Math.round(v), "%.0f%%", 1),
                new Setting.BooleanSetting("忽略急迫等效果",
                        () -> cfg.swingIgnoreHaste, v -> cfg.swingIgnoreHaste = v),

                new Setting.Header("位置与旋转", "调整第一人称手部的偏移与旋转"),
                // Offsets are block units, hence the three-decimal readout and 0.01 step.
                new Setting.SliderSetting("位置 X", -1.0, 1.0,
                        () -> cfg.handItemOffsetX,
                        v -> cfg.handItemOffsetX = (float) v, "%.3f", 0.01),
                new Setting.SliderSetting("位置 Y", -1.0, 1.0,
                        () -> cfg.handItemOffsetY,
                        v -> cfg.handItemOffsetY = (float) v, "%.3f", 0.01),
                new Setting.SliderSetting("位置 Z", -1.0, 1.0,
                        () -> cfg.handItemOffsetZ,
                        v -> cfg.handItemOffsetZ = (float) v, "%.3f", 0.01),
                new Setting.SliderSetting("旋转 X", -180.0, 180.0,
                        () -> cfg.handItemRotateX,
                        v -> cfg.handItemRotateX = (float) v, "%.0f", 1),
                new Setting.SliderSetting("旋转 Y", -180.0, 180.0,
                        () -> cfg.handItemRotateY,
                        v -> cfg.handItemRotateY = (float) v, "%.0f", 1),
                new Setting.SliderSetting("旋转 Z", -180.0, 180.0,
                        () -> cfg.handItemRotateZ,
                        v -> cfg.handItemRotateZ = (float) v, "%.0f", 1));
    }
}
