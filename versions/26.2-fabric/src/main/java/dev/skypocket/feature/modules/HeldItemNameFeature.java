package dev.skypocket.feature.modules;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;
import dev.skypocket.feature.FeatureModule;
import dev.skypocket.gui.Category;
import dev.skypocket.gui.setting.Setting;

import java.util.List;

/** Shows the held item's name above the hotbar, with adjustable scale and offset. */
public final class HeldItemNameFeature implements FeatureModule {

    @Override
    public String id() {
        return "held_item_name";
    }

    @Override
    public String name() {
        return "手持物品名";
    }

    @Override
    public Category category() {
        return Category.HUD;
    }
    @Override
    public Object config() {
        return ConfigManager.get().heldItemNameBlock;
    }

    @Override
    public boolean isEnabled() {
        return ConfigManager.get().heldItemNameBlock.heldItemNameEnabled;
    }

    @Override
    public void setEnabled(boolean value) {
        ConfigManager.get().heldItemNameBlock.heldItemNameEnabled = value;
    }

    @Override
    public List<Setting> settings() {
        var cfg = ConfigManager.get();
        return List.of(
                new Setting.Header("位置与大小", "调整手持物品名的缩放与屏幕偏移"),
                new Setting.SliderSetting("缩放", 0.25, 4.0,
                        () -> cfg.heldItemNameBlock.heldItemNameScale,
                        v -> cfg.heldItemNameBlock.heldItemNameScale = (float) v, "%.2f", ModConfig.SCALE_GRID),
                // Offsets rather than a position: this element is drawn by vanilla above the hotbar
                // and has to keep following it, so what is stored is a delta from that baseline.
                new Setting.SliderSetting("水平偏移", -400, 400,
                        () -> cfg.heldItemNameBlock.heldItemNameOffsetX,
                        v -> cfg.heldItemNameBlock.heldItemNameOffsetX = (int) Math.round(v), "%.0f", 1),
                new Setting.SliderSetting("垂直偏移", -400, 400,
                        () -> cfg.heldItemNameBlock.heldItemNameOffsetY,
                        v -> cfg.heldItemNameBlock.heldItemNameOffsetY = (int) Math.round(v), "%.0f", 1));
    }
}
