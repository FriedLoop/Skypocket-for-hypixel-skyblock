package dev.skypocket.feature.modules;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;
import dev.skypocket.crosshair.CrosshairPresetPanel;
import dev.skypocket.feature.FeatureModule;
import dev.skypocket.gui.Category;
import dev.skypocket.gui.Ui2Theme;
import dev.skypocket.gui.setting.Setting;
import dev.skypocket.render.ColorUtil;
import dev.skypocket.ui.Box;
import dev.skypocket.ui.Dimension;
import dev.skypocket.ui.Style;
import dev.skypocket.ui.widget.CrosshairGrid;
import dev.skypocket.ui.widget.CrosshairPreview;

import java.util.List;

/**
 * The custom crosshair: a 16x16 bitmap drawn instead of the vanilla one, with a per-target tint.
 *
 * <p>This is the only feature with a {@code V2CustomSetting}, so it is the only one that needs the
 * host context: its first card is a three-column editor (grid, preview, preset panel) rather than a
 * list of rows, and the preset panel owns a dropdown whose popup has to join the page's own layer
 * stack.
 */
public final class CrosshairFeature implements FeatureModule {

    /**
     * Held in a field, not created per {@link #settings()} call.
     *
     * <p>The panel carries the half-typed preset name and the selected preset across page rebuilds.
     * A fresh instance per call would lose both the moment any unrelated setting triggered a
     * rebuild - which is precisely the state a {@code Setting} cannot hold, because settings are
     * re-read from the config and a name being typed is not in the config.
     */
    private final CrosshairPresetPanel presetPanel;

    public CrosshairFeature() {
        this.presetPanel = new CrosshairPresetPanel(ConfigManager.get(), Ui2Theme.palette());
    }

    @Override
    public String id() {
        return "crosshair";
    }

    @Override
    public String name() {
        return "自定义准星";
    }

    @Override
    public Category category() {
        return Category.RENDER;
    }

    @Override
    public boolean isEnabled() {
        return ConfigManager.get().crosshairEnabled;
    }

    @Override
    public void setEnabled(boolean value) {
        ConfigManager.get().crosshairEnabled = value;
    }

    @Override
    public List<Setting> settings() {
        ModConfig cfg = ConfigManager.get();
        return List.of(
                new Setting.Header("预览", "实时预览准星形状与像素网格"),
                // Three columns: grid, preview, preset panel. The row is 300 tall and the first two
                // are square at preferredSize(); the panel takes the rest.
                new Setting.V2CustomSetting(300, (row, palette, host) -> {
                    int gridSize = CrosshairGrid.preferredSize();
                    row.style(Style.builder()
                            .width(Dimension.percent(100f))
                            .height(300f)
                            .direction(Style.Direction.ROW)
                            .gap(15f)
                            .alignItems(Style.Align.CENTER)
                            .build());
                    // flexShrink(0) on both canvases: they are the items that would otherwise give up
                    // space when the row overflows, and giving it up there is exactly wrong - the grid
                    // must stay square for its cells to line up, and the preview has to match it.
                    row.add(new CrosshairGrid(cfg.crosshairGrid, cfg::getCrosshairColorARGB,
                            Style.builder().width(gridSize).height(gridSize)
                                    .flexShrink(0f).build(), palette));
                    row.add(new CrosshairPreview(cfg, Style.builder()
                            .width(gridSize).height(gridSize)
                            .flexShrink(0f).build(), palette));

                    // flexBasis(0) + grow, not width(100%): the panel's basis is the space left over,
                    // not the whole row. A percentage width made its basis the full row width, so the
                    // three children overflowed and the shrink pass squeezed the two canvases.
                    Box panel = new Box(Style.builder()
                            .flexBasis(Dimension.points(0f))
                            .flexGrow(1f)
                            .height(300f)
                            .direction(Style.Direction.COLUMN)
                            .gap(8f)
                            .build());
                    presetPanel.build(panel, host);
                    row.add(panel);
                }),

                new Setting.GroupSetting("外观", List.of(
                        new Setting.BooleanSetting("描边",
                                () -> cfg.crosshairStroke, v -> cfg.crosshairStroke = v),
                        new Setting.SliderSetting("描边宽度", 0.5, 4.0,
                                () -> cfg.crosshairStrokeWidth,
                                v -> cfg.crosshairStrokeWidth = (float) v, "%.1f", 0.1),
                        new Setting.ColorSetting("准星颜色", cfg::getCrosshairColorARGB, v -> {
                            cfg.crosshairRed = ColorUtil.red(v);
                            cfg.crosshairGreen = ColorUtil.green(v);
                            cfg.crosshairBlue = ColorUtil.blue(v);
                            cfg.crosshairAlpha = ColorUtil.alpha(v);
                        }, true),
                        // Upper bound 3, matching ModConfig.sanitize's clamp: a larger cell would be
                        // clamped on the next load and the slider would snap back.
                        new Setting.SliderSetting("像素大小", 1, 3,
                                () -> cfg.crosshairPixelSize,
                                v -> cfg.crosshairPixelSize = (float) v, "%.0f", 1),
                        new Setting.SliderSetting("缩放", 0.25, 4.0,
                                () -> cfg.crosshairScale,
                                v -> cfg.crosshairScale = (float) v, "%.2f", ModConfig.SCALE_GRID)))
                        .subtitle("调整准星线条、颜色、像素大小与缩放"),

                // The switch is the feature's own flag: the renderer reads this field, so turning it
                // off really does stop the tint.
                new Setting.GroupSetting("目标变色", () -> cfg.crosshairEntityColorEnabled,
                        v -> cfg.crosshairEntityColorEnabled = v, List.of(
                        new Setting.ColorSetting("友好颜色",
                                () -> cfg.crosshairFriendlyColor, v -> cfg.crosshairFriendlyColor = v),
                        new Setting.ColorSetting("中立颜色",
                                () -> cfg.crosshairNeutralColor, v -> cfg.crosshairNeutralColor = v),
                        new Setting.ColorSetting("敌对颜色",
                                () -> cfg.crosshairHostileColor, v -> cfg.crosshairHostileColor = v),
                        new Setting.ColorSetting("箱子颜色",
                                () -> cfg.crosshairContainerColor,
                                v -> cfg.crosshairContainerColor = v)))
                        .subtitle("按目标类型切换准星颜色"));
    }
}
