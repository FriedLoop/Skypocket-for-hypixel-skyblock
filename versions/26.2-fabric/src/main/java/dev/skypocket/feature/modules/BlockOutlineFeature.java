package dev.skypocket.feature.modules;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.feature.FeatureModule;
import dev.skypocket.gui.Category;
import dev.skypocket.gui.setting.Setting;
import dev.skypocket.render.ColorUtil;

import java.util.List;

/** Draws an outline around the block being looked at, optionally tinting its faces. */
public final class BlockOutlineFeature implements FeatureModule {

    @Override
    public String id() {
        return "block_outline";
    }

    @Override
    public String name() {
        return "方块描边";
    }

    @Override
    public Category category() {
        return Category.RENDER;
    }

    @Override
    public boolean isEnabled() {
        return ConfigManager.get().blockOutlineEnabled;
    }

    @Override
    public void setEnabled(boolean value) {
        ConfigManager.get().blockOutlineEnabled = value;
    }

    @Override
    public List<Setting> settings() {
        var cfg = ConfigManager.get();
        return List.of(
                new Setting.Header("外观", "调整描边颜色、厚度与方块面填充"),
                // Colours are stored as four separate channels, so the setting reads them via the
                // getter and writes them back channel by channel. `true` marks it as supporting alpha.
                new Setting.ColorSetting("描边颜色", cfg::getBlockOutlineColorARGB, v -> {
                    cfg.blockOutlineRed = ColorUtil.red(v);
                    cfg.blockOutlineGreen = ColorUtil.green(v);
                    cfg.blockOutlineBlue = ColorUtil.blue(v);
                    cfg.blockOutlineAlpha = ColorUtil.alpha(v);
                }, true),
                new Setting.SliderSetting("描边厚度", 0.5, 6.0,
                        () -> cfg.blockOutlineThickness,
                        v -> cfg.blockOutlineThickness = (float) v, "%.2f", 0.1),
                new Setting.BooleanSetting("方块面调色",
                        () -> cfg.blockOutlineFaceTint, v -> cfg.blockOutlineFaceTint = v),
                new Setting.ColorSetting("面颜色", cfg::getBlockOutlineFaceColorARGB, v -> {
                    cfg.blockOutlineFaceRed = ColorUtil.red(v);
                    cfg.blockOutlineFaceGreen = ColorUtil.green(v);
                    cfg.blockOutlineFaceBlue = ColorUtil.blue(v);
                    cfg.blockOutlineFaceAlpha = ColorUtil.alpha(v);
                }, true));
    }
}
