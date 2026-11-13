package dev.skypocket.feature.modules;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;
import dev.skypocket.feature.FeatureModule;
import dev.skypocket.feature.HudSpec;
import dev.skypocket.gui.Category;
import dev.skypocket.gui.setting.Setting;
import dev.skypocket.hud.DayCountHudElement;

import java.util.List;

/**
 * A HUD element showing the current world day.
 *
 * <p>Phase 2 migration, paired with {@link ClockFeature}: plain text, plain coordinates, one slider.
 * See that class for why these two go first.
 */
public final class DayCountFeature implements FeatureModule {

    @Override
    public String id() {
        return "day_count";
    }

    @Override
    public String name() {
        return "世界天数";
    }

    @Override
    public Category category() {
        return Category.HUD;
    }

    @Override
    public boolean isEnabled() {
        return ConfigManager.get().dayCountEnabled;
    }

    @Override
    public void setEnabled(boolean value) {
        ConfigManager.get().dayCountEnabled = value;
    }

    @Override
    public List<Setting> settings() {
        ModConfig cfg = ConfigManager.get();
        return List.of(
                new Setting.Header("显示", "调整世界天数文字大小"),
                new Setting.SliderSetting("大小", 0.25, 6.0,
                        () -> cfg.dayCountScale, v -> cfg.dayCountScale = (float) v,
                        "%.2f", ModConfig.SCALE_GRID));
    }

    @Override
    public HudSpec hud() {
        return HudSpec.builder()
                .label("世界天数")
                .moduleId(id())
                .target("DAY_COUNT")
                .lines(() -> List.of(DayCountHudElement.format(ConfigManager.get())))
                .x(c -> cfg(c).dayCountX, (c, v) -> cfg(c).dayCountX = v)
                .y(c -> cfg(c).dayCountY, (c, v) -> cfg(c).dayCountY = v)
                .scale(c -> cfg(c).dayCountScale, (c, v) -> cfg(c).dayCountScale = (float) v)
                // Same fallback HudPositionSync uses for this element today (10, 40).
                .defaults(10, 40)
                .visibleWhen(c -> cfg(c).dayCountEnabled)
                .build();
    }

    /** The config object the spec accessors receive. */
    private static ModConfig cfg(Object ignored) {
        return ConfigManager.get();
    }
}
