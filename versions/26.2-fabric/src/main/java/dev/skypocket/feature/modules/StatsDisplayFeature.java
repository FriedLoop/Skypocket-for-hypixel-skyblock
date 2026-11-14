package dev.skypocket.feature.modules;

import dev.skypocket.config.ColorDefaults;
import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;
import dev.skypocket.feature.FeatureModule;
import dev.skypocket.gui.Category;
import dev.skypocket.gui.OneClickScreen;
import dev.skypocket.gui.setting.Setting;
import dev.skypocket.hud.StatsBarHudElement;
import dev.skypocket.i18n.Localization;
import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * SkyBlock's own numbers over the vanilla health area (see {@code StatsOverlayRenderer}): the classic
 * style redraws them with the HUD's own vocabulary, the modern one as bars.
 *
 * <p>The first setting is the switch between the two styles; everything after it is geometry, colours
 * and the plain-text rows. Rows that only belong to one style are gated with {@code visibleWhen} and
 * live in contiguous blocks at the end, because a conditional block sitting between two unconditional
 * ones makes everything below it shift when the style changes.
 *
 * <p>The rows share their colour with the matching bar (defence / speed / vitality), which is why there
 * is one colour per value rather than one per style. SkyBlock-only.
 */
public final class StatsDisplayFeature implements FeatureModule {

    @Override
    public String id() {
        return "stats_display";
    }

    @Override
    public String name() {
        return "玩家数值显示";
    }

    @Override
    public Category category() {
        return Category.HUD;
    }

    @Override
    public boolean skyBlockOnly() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return ConfigManager.get().statsEnabled;
    }

    @Override
    public void setEnabled(boolean value) {
        ConfigManager.get().statsEnabled = value;
    }

    @Override
    public List<Setting> settings() {
        ModConfig cfg = ConfigManager.get();
        return List.of(
                new Setting.Header("显示风格",
                        "经典沿用原版图标，现代改用条；切换后只显示对应风格的设置"),
                new Setting.ChoiceSetting("风格", List.of("经典", "现代"),
                        () -> cfg.statsModernStyle ? 1 : 0,
                        index -> {
                            cfg.statsModernStyle = index == 1;
                            rebuildPanelAfterStyleChange();
                        }, true),

                // The vanilla HUD is its own card: the style switch above only chooses this module's
                // presentation and must not visually own the vanilla-element toggles.
                new Setting.Header("原版元素", "隐藏原版元素，避免和本模块重复绘制"),
                new Setting.BooleanSetting("隐藏原版生命条",
                        () -> cfg.statsHideVanillaHealth, v -> cfg.statsHideVanillaHealth = v)
                        .hint("本模块会自己画血量，留着会两层叠在一起"),
                new Setting.BooleanSetting("隐藏原版饥饿条",
                        () -> cfg.statsHideVanillaFood, v -> cfg.statsHideVanillaFood = v)
                        .hint("饥饿那一行被法力占用"),
                new Setting.BooleanSetting("隐藏原版空气泡",
                        () -> cfg.statsHideVanillaAir, v -> cfg.statsHideVanillaAir = v)
                        .hint("它会正好落在法力的第二行上"),
                new Setting.BooleanSetting("隐藏原版盔甲条",
                        () -> cfg.statsHideVanillaArmor, v -> cfg.statsHideVanillaArmor = v)
                        .hint("它和本模块的布局不冲突，所以默认留着"),
                new Setting.BooleanSetting("隐藏原版动作栏",
                        () -> cfg.statsHideVanillaActionBar, v -> cfg.statsHideVanillaActionBar = v)
                        .hint("只隐藏绘制：数值仍然在读取，显示照常更新"),

                // Style-independent rows first, style-specific blocks last.
                new Setting.Header("颜色", "现代风格的条与经典风格的数值文字共用同一套"),
                new Setting.ColorSetting("生命颜色",
                        () -> cfg.statsHealthColor, v -> cfg.statsHealthColor = v),
                new Setting.ColorSetting("吸收颜色",
                        () -> cfg.statsAbsorptionColor, v -> cfg.statsAbsorptionColor = v),
                new Setting.ColorSetting("法力颜色",
                        () -> cfg.statsManaColor, v -> cfg.statsManaColor = v),
                new Setting.ColorSetting("溢出法力颜色",
                        () -> cfg.statsOverflowColor, v -> cfg.statsOverflowColor = v),
                new Setting.ColorSetting("空气颜色",
                        () -> cfg.statsAirColor, v -> cfg.statsAirColor = v),
                new Setting.ColorSetting("防御颜色",
                        () -> cfg.statsDefenseColor, v -> cfg.statsDefenseColor = v),
                new Setting.ColorSetting("速度颜色",
                        () -> cfg.statsSpeedColor, v -> cfg.statsSpeedColor = v),
                new Setting.ColorSetting("活力颜色",
                        () -> cfg.statsVitalityColor, v -> cfg.statsVitalityColor = v),

                // Classic-only: the modern style renders these values inside its bars, so separate
                // text rows would duplicate the same responsibility.
                new Setting.Header("经典文字行", "仅经典风格；现代风格的条已经直接显示这些数值")
                        .visibleWhen(() -> !cfg.statsModernStyle),
                new Setting.BooleanSetting("防御文字",
                        () -> cfg.statsDefenseEnabled, v -> cfg.statsDefenseEnabled = v)
                        .visibleWhen(() -> !cfg.statsModernStyle),
                new Setting.BooleanSetting("速度文字",
                        () -> cfg.statsSpeedEnabled, v -> cfg.statsSpeedEnabled = v)
                        .visibleWhen(() -> !cfg.statsModernStyle),
                new Setting.BooleanSetting("活力文字",
                        () -> cfg.statsVitalityEnabled, v -> cfg.statsVitalityEnabled = v)
                        .visibleWhen(() -> !cfg.statsModernStyle),
                new Setting.SliderSetting("防御文字大小", 0.25, 6.0,
                        () -> cfg.statsDefenseScale,
                        v -> cfg.statsDefenseScale = (float) v, "%.2f", ModConfig.SCALE_GRID)
                        .visibleWhen(() -> !cfg.statsModernStyle),
                new Setting.SliderSetting("速度文字大小", 0.25, 6.0,
                        () -> cfg.statsSpeedScale,
                        v -> cfg.statsSpeedScale = (float) v, "%.2f", ModConfig.SCALE_GRID)
                        .visibleWhen(() -> !cfg.statsModernStyle),
                new Setting.SliderSetting("活力文字大小", 0.25, 6.0,
                        () -> cfg.statsVitalityScale,
                        v -> cfg.statsVitalityScale = (float) v, "%.2f", ModConfig.SCALE_GRID)
                        .visibleWhen(() -> !cfg.statsModernStyle),

                new Setting.Header("经典布局",
                        "原版图标 + 精确数值；两行文字的位置在「HUD 位置调整」页里拖")
                        .visibleWhen(() -> !cfg.statsModernStyle),
                new Setting.BooleanSetting("法力不足时闪烁",
                        () -> cfg.statsManaBlink, v -> cfg.statsManaBlink = v)
                        .hint("剩余不足两格时按原版节奏闪烁")
                        .visibleWhen(() -> !cfg.statsModernStyle),
                new Setting.BooleanSetting("生命数值",
                        () -> cfg.statsHealthTextEnabled, v -> cfg.statsHealthTextEnabled = v)
                        .hint("20 格图标表达不了一个点；这里给出精确值与吸收量")
                        .visibleWhen(() -> !cfg.statsModernStyle),
                new Setting.SliderSetting("生命数值大小", 0.25, 6.0,
                        () -> cfg.statsHealthTextScale,
                        v -> cfg.statsHealthTextScale = (float) v, "%.2f", ModConfig.SCALE_GRID)
                        .visibleWhen(() -> !cfg.statsModernStyle),
                new Setting.BooleanSetting("法力数值",
                        () -> cfg.statsManaTextEnabled, v -> cfg.statsManaTextEnabled = v)
                        .hint("精确值与溢出法力")
                        .visibleWhen(() -> !cfg.statsModernStyle),
                new Setting.SliderSetting("法力数值大小", 0.25, 6.0,
                        () -> cfg.statsManaTextScale,
                        v -> cfg.statsManaTextScale = (float) v, "%.2f", ModConfig.SCALE_GRID)
                        .visibleWhen(() -> !cfg.statsModernStyle),

                // Modern-only: the geometry all six bars share, then a switch and a size for each.
                // Position comes from the HUD position editor, where every bar is its own target.
                new Setting.Header("现代条",
                        "位置在「HUD 位置调整」页里逐条拖拽与缩放，这里只管粗细与开关")
                        .visibleWhen(() -> cfg.statsModernStyle),
                new Setting.BooleanSetting("条上显示数值",
                        () -> cfg.statsBarShowValues, v -> cfg.statsBarShowValues = v)
                        .visibleWhen(() -> cfg.statsModernStyle),
                new Setting.BooleanSetting("生命条",
                        () -> cfg.statsHealthBarEnabled, v -> cfg.statsHealthBarEnabled = v)
                        .hint("吸收血量作为第二层叠在它上面")
                        .visibleWhen(() -> cfg.statsModernStyle),
                new Setting.SliderSetting("生命条大小", 0.25, 6.0,
                        () -> cfg.statsHealthBarScale,
                        v -> cfg.statsHealthBarScale = (float) v, "%.2f", ModConfig.SCALE_GRID)
                        .visibleWhen(() -> cfg.statsModernStyle),
                new Setting.BooleanSetting("法力条",
                        () -> cfg.statsManaBarEnabled, v -> cfg.statsManaBarEnabled = v)
                        .visibleWhen(() -> cfg.statsModernStyle),
                new Setting.SliderSetting("法力条大小", 0.25, 6.0,
                        () -> cfg.statsManaBarScale,
                        v -> cfg.statsManaBarScale = (float) v, "%.2f", ModConfig.SCALE_GRID)
                        .visibleWhen(() -> cfg.statsModernStyle),
                new Setting.BooleanSetting("防御条",
                        () -> cfg.statsDefenseBarEnabled, v -> cfg.statsDefenseBarEnabled = v)
                        .visibleWhen(() -> cfg.statsModernStyle),
                new Setting.SliderSetting("防御条大小", 0.25, 6.0,
                        () -> cfg.statsDefenseBarScale,
                        v -> cfg.statsDefenseBarScale = (float) v, "%.2f", ModConfig.SCALE_GRID)
                        .visibleWhen(() -> cfg.statsModernStyle),
                new Setting.BooleanSetting("速度条",
                        () -> cfg.statsSpeedBarEnabled, v -> cfg.statsSpeedBarEnabled = v)
                        .visibleWhen(() -> cfg.statsModernStyle),
                new Setting.SliderSetting("速度条大小", 0.25, 6.0,
                        () -> cfg.statsSpeedBarScale,
                        v -> cfg.statsSpeedBarScale = (float) v, "%.2f", ModConfig.SCALE_GRID)
                        .visibleWhen(() -> cfg.statsModernStyle),
                new Setting.BooleanSetting("活力条",
                        () -> cfg.statsVitalityBarEnabled, v -> cfg.statsVitalityBarEnabled = v)
                        .visibleWhen(() -> cfg.statsModernStyle),
                new Setting.SliderSetting("活力条大小", 0.25, 6.0,
                        () -> cfg.statsVitalityBarScale,
                        v -> cfg.statsVitalityBarScale = (float) v, "%.2f", ModConfig.SCALE_GRID)
                        .visibleWhen(() -> cfg.statsModernStyle),
                new Setting.BooleanSetting("空气条",
                        () -> cfg.statsAirBarEnabled, v -> cfg.statsAirBarEnabled = v)
                        .visibleWhen(() -> cfg.statsModernStyle),
                new Setting.SliderSetting("空气条大小", 0.25, 6.0,
                        () -> cfg.statsAirBarScale,
                        v -> cfg.statsAirBarScale = (float) v, "%.2f", ModConfig.SCALE_GRID)
                        .visibleWhen(() -> cfg.statsModernStyle),

                // Advanced is its own card: a different kind of operation from the bar switches above.
                new Setting.Header("重置与恢复", "重置条位置与恢复默认颜色")
                        .visibleWhen(() -> cfg.statsModernStyle),
                new Setting.ActionSetting(() -> Localization.tr("重置条位置"),
                        () -> StatsBarHudElement.resetToDefaults(cfg))
                        .visibleWhen(() -> cfg.statsModernStyle),
                new Setting.ActionSetting(() -> Localization.tr("恢复默认颜色"),
                        () -> {
                            cfg.statsHealthColor = ColorDefaults.STATS_HEALTH;
                            cfg.statsAbsorptionColor = ColorDefaults.STATS_ABSORPTION;
                            cfg.statsManaColor = ColorDefaults.STATS_MANA;
                            cfg.statsOverflowColor = ColorDefaults.STATS_OVERFLOW;
                            cfg.statsAirColor = ColorDefaults.STATS_AIR;
                            cfg.statsDefenseColor = ColorDefaults.STATS_DEFENSE;
                            cfg.statsSpeedColor = ColorDefaults.STATS_SPEED;
                            cfg.statsVitalityColor = ColorDefaults.STATS_VITALITY;
                            rebuildPanelAfterStyleChange();
                        })
                        .visibleWhen(() -> cfg.statsModernStyle));
    }

    /**
     * Rebuilds the settings page after the style changed.
     *
     * <p>The rows belonging to the other style are hidden through {@code Setting#visibleWhen}, and the
     * widgets are built once rather than every frame, so the panel has to be laid out again for the
     * change to show.
     */
    private static void rebuildPanelAfterStyleChange() {
        Minecraft mc = Minecraft.getInstance();
        mc.schedule(() -> {
            if (mc.gui.screen() instanceof OneClickScreen screen) {
                screen.rebuildAfterSettingChange();
            }
        });
    }
}
