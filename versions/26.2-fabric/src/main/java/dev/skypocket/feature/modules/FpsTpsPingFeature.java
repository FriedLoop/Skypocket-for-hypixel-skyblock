package dev.skypocket.feature.modules;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;
import dev.skypocket.feature.FeatureModule;
import dev.skypocket.gui.Category;
import dev.skypocket.gui.setting.Setting;

import java.util.List;

/** Shows FPS, TPS and ping, horizontally or vertically. */
public final class FpsTpsPingFeature implements FeatureModule {

    @Override
    public String id() {
        return "fps_tps_ping";
    }

    @Override
    public String name() {
        return "FPS/TPS/Ping";
    }

    @Override
    public Category category() {
        return Category.HUD;
    }
    @Override
    public Object config() {
        return ConfigManager.get().fpsTpsPingBlock;
    }

    @Override
    public boolean isEnabled() {
        return ConfigManager.get().fpsTpsPingBlock.fpsTpsPingEnabled;
    }

    @Override
    public void setEnabled(boolean value) {
        ConfigManager.get().fpsTpsPingBlock.fpsTpsPingEnabled = value;
    }

    @Override
    public List<Setting> settings() {
        ModConfig cfg = ConfigManager.get();
        return List.of(
                new Setting.Header("显示", "选择显示项目、大小与排版"),
                new Setting.BooleanSetting("FPS",
                        () -> cfg.fpsTpsPingBlock.fpsTpsPingShowFps, v -> cfg.fpsTpsPingBlock.fpsTpsPingShowFps = v),
                new Setting.BooleanSetting("TPS",
                        () -> cfg.fpsTpsPingBlock.fpsTpsPingShowTps, v -> cfg.fpsTpsPingBlock.fpsTpsPingShowTps = v),
                new Setting.BooleanSetting("Ping",
                        () -> cfg.fpsTpsPingBlock.fpsTpsPingShowPing, v -> cfg.fpsTpsPingBlock.fpsTpsPingShowPing = v),
                new Setting.SliderSetting("大小", 0.25, 6.0,
                        () -> cfg.fpsTpsPingBlock.fpsTpsPingScale,
                        v -> cfg.fpsTpsPingBlock.fpsTpsPingScale = (float) v, "%.2f", ModConfig.SCALE_GRID),
                // A boolean field shown as a two-option choice: index 0 is vertical. The explicit
                // touch() is the original code's, and it matters because a ChoiceSetting does not go
                // through the usual setter wrapper.
                new Setting.ChoiceSetting("排版", List.of("竖向排版", "横向排版"),
                        () -> cfg.fpsTpsPingBlock.fpsTpsPingVertical ? 0 : 1,
                        index -> {
                            cfg.fpsTpsPingBlock.fpsTpsPingVertical = index == 0;
                            ConfigManager.touch();
                        }));
    }
}
