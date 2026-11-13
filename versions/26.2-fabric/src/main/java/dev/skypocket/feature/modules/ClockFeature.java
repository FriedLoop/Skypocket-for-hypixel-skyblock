package dev.skypocket.feature.modules;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;
import dev.skypocket.feature.FeatureModule;
import dev.skypocket.feature.HudSpec;
import dev.skypocket.gui.Category;
import dev.skypocket.gui.setting.Setting;
import dev.skypocket.hud.ClockHudElement;

import java.util.List;

/**
 * A clock HUD element showing either 12- or 24-hour time.
 *
 * <p>Phase 2 migration: the first element whose HUD facts come from a {@link HudSpec} instead of the
 * editor's hand-written tables. It and {@link DayCountFeature} are the cleanest candidates - plain
 * text, plain coordinates, no icon and no special measuring path - so that a failure here isolates
 * the spec mechanism from everything else.
 *
 * <p>The config fields stay on {@link ModConfig} for this step, as in Phase 1: {@code ClockHudElement}
 * reads them directly, and moving them would mix a registration change with a storage change.
 */
public final class ClockFeature implements FeatureModule {

    @Override
    public String id() {
        return "clock";
    }

    @Override
    public String name() {
        return "时钟";
    }

    @Override
    public Category category() {
        return Category.HUD;
    }

    @Override
    public boolean isEnabled() {
        return ConfigManager.get().clockEnabled;
    }

    @Override
    public void setEnabled(boolean value) {
        ConfigManager.get().clockEnabled = value;
    }

    @Override
    public List<Setting> settings() {
        ModConfig cfg = ConfigManager.get();
        return List.of(
                new Setting.Header("显示", "设置时间制式与时钟大小"),
                new Setting.BooleanSetting("24 小时制",
                        () -> cfg.clockFormat24h, v -> cfg.clockFormat24h = v),
                new Setting.SliderSetting("时钟大小", 0.25, 6.0,
                        () -> cfg.clockScale, v -> cfg.clockScale = (float) v,
                        "%.2f", ModConfig.SCALE_GRID));
    }

    @Override
    public HudSpec hud() {
        return HudSpec.builder()
                // The same label the editor's switch used to return; Localization.tr is applied by
                // the editor, so an untranslated spec label would still show Chinese in English.
                .label("时钟")
                .moduleId(id())
                .target("CLOCK")
                .lines(() -> List.of(ClockHudElement.formatTime(ConfigManager.get())))
                .x(c -> cfg(c).clockX, (c, v) -> cfg(c).clockX = v)
                .y(c -> cfg(c).clockY, (c, v) -> cfg(c).clockY = v)
                .scale(c -> cfg(c).clockScale, (c, v) -> cfg(c).clockScale = (float) v)
                // Same fallbacks HudPositionSync uses for this element today.
                .defaults(10, 10)
                .visibleWhen(c -> cfg(c).clockEnabled)
                .build();
    }

    /** The config object the spec accessors receive. */
    private static ModConfig cfg(Object ignored) {
        return ConfigManager.get();
    }
}
