package dev.skypocket.feature.modules;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.feature.FeatureModule;
import dev.skypocket.gui.Category;
import dev.skypocket.gui.setting.Setting;
import dev.skypocket.render.HotspotOverlayStyle;
import dev.skypocket.render.HotspotType;

import java.util.ArrayList;
import java.util.List;

/** Draws a cylinder or box over fishing hotspots, colour-coded by the attribute they grant. SkyBlock-only. */
public final class FishingHotspotOverlayFeature implements FeatureModule {

    @Override
    public String id() {
        return "fishing_hotspot_overlay";
    }

    @Override
    public String name() {
        return "钓鱼热点覆盖层";
    }

    @Override
    public Category category() {
        return Category.FISHING;
    }
    @Override
    public Object config() {
        return ConfigManager.get().fishingHotspotOverlayBlock;
    }

    @Override
    public boolean skyBlockOnly() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return ConfigManager.get().fishingHotspotOverlayBlock.hotspotOverlayEnabled;
    }

    @Override
    public void setEnabled(boolean value) {
        ConfigManager.get().fishingHotspotOverlayBlock.hotspotOverlayEnabled = value;
    }

    @Override
    public List<Setting> settings() {
        var cfg = ConfigManager.get();
        List<Setting> settings = new ArrayList<>();
        settings.add(new Setting.Header("显示", "调整热点样式、颜色、高度、线宽与显示距离"));
        settings.add(new Setting.DropdownSetting("样式", HotspotOverlayStyle::labels,
                () -> cfg.fishingHotspotOverlayBlock.hotspotOverlayStyle, v -> cfg.fishingHotspotOverlayBlock.hotspotOverlayStyle = v));
        // One colour per hotspot type, generated from the enum: adding a type adds its setting.
        // `label` is a public field on HotspotType, not a method.
        for (HotspotType t : HotspotType.values()) {
            settings.add(new Setting.ColorSetting(t.label,
                    () -> t.color(cfg), v -> t.setColor(cfg, v)));
        }
        settings.add(new Setting.ColorSetting("未知类型",
                () -> cfg.fishingHotspotOverlayBlock.hotspotOverlayColor, v -> cfg.fishingHotspotOverlayBlock.hotspotOverlayColor = v));
        // Height only applies to the cylinder style; the box style draws a flat outline.
        settings.add(new Setting.SliderSetting("高度", 0.25, 256.0,
                        () -> cfg.fishingHotspotOverlayBlock.hotspotOverlayHeight,
                        v -> cfg.fishingHotspotOverlayBlock.hotspotOverlayHeight = (float) v, "%.2f", 0.05)
                .visibleWhen(() -> HotspotOverlayStyle.byLabel(cfg.fishingHotspotOverlayBlock.hotspotOverlayStyle).isCylinder()));
        settings.add(new Setting.SliderSetting("线宽", 0.5, 4.0,
                () -> cfg.fishingHotspotOverlayBlock.hotspotOverlayLineWidth,
                v -> cfg.fishingHotspotOverlayBlock.hotspotOverlayLineWidth = (float) v, "%.1f", 0.5));
        settings.add(new Setting.SliderSetting("显示距离", 8.0, 256.0,
                () -> cfg.fishingHotspotOverlayBlock.hotspotOverlayRange,
                v -> cfg.fishingHotspotOverlayBlock.hotspotOverlayRange = (float) v, "%.0f", 1.0));
        return settings;
    }
}
