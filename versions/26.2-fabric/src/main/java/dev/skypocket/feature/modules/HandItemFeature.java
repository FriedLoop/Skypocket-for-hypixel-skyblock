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
    public Object config() {
        return ConfigManager.get().handItemBlock;
    }

    @Override
    public boolean isEnabled() {
        return ConfigManager.get().handItemBlock.handItemEnabled;
    }

    @Override
    public void setEnabled(boolean value) {
        ConfigManager.get().handItemBlock.handItemEnabled = value;
    }

    @Override
    public List<Setting> settings() {
        ModConfig cfg = ConfigManager.get();
        return List.of(
                new Setting.Header("外观", "调整手持物品的大小"),
                new Setting.SliderSetting("物品大小", 0.1, 4.0,
                        () -> cfg.handItemBlock.handItemScale,
                        v -> cfg.handItemBlock.handItemScale = (float) v, "%.2f", ModConfig.SCALE_GRID),

                new Setting.Header("动作", "控制挥动、切换和挥砍速度"),
                new Setting.BooleanSetting("禁用挥动动画",
                        () -> cfg.handItemBlock.handItemDisableSwing, v -> cfg.handItemBlock.handItemDisableSwing = v),
                new Setting.BooleanSetting("禁用切换动画",
                        () -> cfg.handItemBlock.handItemDisableReequip, v -> cfg.handItemBlock.handItemDisableReequip = v),
                // Swing speed is an int percentage: 0 means the vanilla speed is used untouched.
                new Setting.SliderSetting("挥砍速度", 0.0, 200.0,
                        () -> cfg.handItemBlock.swingSpeed,
                        v -> cfg.handItemBlock.swingSpeed = (int) Math.round(v), "%.0f%%", 1),
                new Setting.BooleanSetting("忽略急迫等效果",
                        () -> cfg.handItemBlock.swingIgnoreHaste, v -> cfg.handItemBlock.swingIgnoreHaste = v),

                new Setting.Header("位置与旋转", "调整第一人称手部的偏移与旋转"),
                // Offsets are block units, hence the three-decimal readout and 0.01 step.
                new Setting.SliderSetting("位置 X", -1.0, 1.0,
                        () -> cfg.handItemBlock.handItemOffsetX,
                        v -> cfg.handItemBlock.handItemOffsetX = (float) v, "%.3f", 0.01),
                new Setting.SliderSetting("位置 Y", -1.0, 1.0,
                        () -> cfg.handItemBlock.handItemOffsetY,
                        v -> cfg.handItemBlock.handItemOffsetY = (float) v, "%.3f", 0.01),
                new Setting.SliderSetting("位置 Z", -1.0, 1.0,
                        () -> cfg.handItemBlock.handItemOffsetZ,
                        v -> cfg.handItemBlock.handItemOffsetZ = (float) v, "%.3f", 0.01),
                new Setting.SliderSetting("旋转 X", -180.0, 180.0,
                        () -> cfg.handItemBlock.handItemRotateX,
                        v -> cfg.handItemBlock.handItemRotateX = (float) v, "%.0f", 1),
                new Setting.SliderSetting("旋转 Y", -180.0, 180.0,
                        () -> cfg.handItemBlock.handItemRotateY,
                        v -> cfg.handItemBlock.handItemRotateY = (float) v, "%.0f", 1),
                new Setting.SliderSetting("旋转 Z", -180.0, 180.0,
                        () -> cfg.handItemBlock.handItemRotateZ,
                        v -> cfg.handItemBlock.handItemRotateZ = (float) v, "%.0f", 1));
    }
}
