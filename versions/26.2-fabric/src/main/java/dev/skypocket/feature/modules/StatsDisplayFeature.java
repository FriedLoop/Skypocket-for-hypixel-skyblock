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
    public Object config() {
        return ConfigManager.get().statsDisplayBlock;
    }

    @Override
    public boolean skyBlockOnly() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return ConfigManager.get().statsDisplayBlock.statsEnabled;
    }

    @Override
    public void setEnabled(boolean value) {
        ConfigManager.get().statsDisplayBlock.statsEnabled = value;
    }

    @Override
    public List<Setting> settings() {
        ModConfig cfg = ConfigManager.get();
        return List.of(
                new Setting.Header("显示风格",
                        "经典沿用原版图标，现代改用条；切换后只显示对应风格的设置"),
                new Setting.ChoiceSetting("风格", List.of("经典", "现代"),
                        () -> cfg.statsDisplayBlock.statsModernStyle ? 1 : 0,
                        index -> {
                            cfg.statsDisplayBlock.statsModernStyle = index == 1;
                            rebuildPanelAfterStyleChange();
                        }, true),

                // The vanilla HUD is its own card: the style switch above only chooses this module's
                // presentation and must not visually own the vanilla-element toggles.
                new Setting.Header("原版元素", "隐藏原版元素，避免和本模块重复绘制"),
                new Setting.BooleanSetting("隐藏原版生命条",
                        () -> cfg.statsDisplayBlock.statsHideVanillaHealth, v -> cfg.statsDisplayBlock.statsHideVanillaHealth = v)
                        .hint("本模块会自己画血量，留着会两层叠在一起"),
                new Setting.BooleanSetting("隐藏原版饥饿条",
                        () -> cfg.statsDisplayBlock.statsHideVanillaFood, v -> cfg.statsDisplayBlock.statsHideVanillaFood = v)
                        .hint("饥饿那一行被法力占用"),
                new Setting.BooleanSetting("隐藏原版空气泡",
                        () -> cfg.statsDisplayBlock.statsHideVanillaAir, v -> cfg.statsDisplayBlock.statsHideVanillaAir = v)
                        .hint("它会正好落在法力的第二行上"),
                new Setting.BooleanSetting("隐藏原版盔甲条",
                        () -> cfg.statsDisplayBlock.statsHideVanillaArmor, v -> cfg.statsDisplayBlock.statsHideVanillaArmor = v)
                        .hint("它和本模块的布局不冲突，所以默认留着"),
                new Setting.BooleanSetting("隐藏原版动作栏",
                        () -> cfg.statsDisplayBlock.statsHideVanillaActionBar, v -> cfg.statsDisplayBlock.statsHideVanillaActionBar = v)
                        .hint("只隐藏绘制：数值仍然在读取，显示照常更新"),

                // Style-independent rows first, style-specific blocks last.
                new Setting.Header("颜色", "现代风格的条与经典风格的数值文字共用同一套"),
                new Setting.ColorSetting("生命颜色",
                        () -> cfg.statsDisplayBlock.statsHealthColor, v -> cfg.statsDisplayBlock.statsHealthColor = v),
                new Setting.ColorSetting("吸收颜色",
                        () -> cfg.statsDisplayBlock.statsAbsorptionColor, v -> cfg.statsDisplayBlock.statsAbsorptionColor = v),
                new Setting.ColorSetting("法力颜色",
                        () -> cfg.statsDisplayBlock.statsManaColor, v -> cfg.statsDisplayBlock.statsManaColor = v),
                new Setting.ColorSetting("溢出法力颜色",
                        () -> cfg.statsDisplayBlock.statsOverflowColor, v -> cfg.statsDisplayBlock.statsOverflowColor = v),
                new Setting.ColorSetting("空气颜色",
                        () -> cfg.statsDisplayBlock.statsAirColor, v -> cfg.statsDisplayBlock.statsAirColor = v),
                new Setting.ColorSetting("防御颜色",
                        () -> cfg.statsDisplayBlock.statsDefenseColor, v -> cfg.statsDisplayBlock.statsDefenseColor = v),
                new Setting.ColorSetting("速度颜色",
                        () -> cfg.statsDisplayBlock.statsSpeedColor, v -> cfg.statsDisplayBlock.statsSpeedColor = v),
                new Setting.ColorSetting("活力颜色",
                        () -> cfg.statsDisplayBlock.statsVitalityColor, v -> cfg.statsDisplayBlock.statsVitalityColor = v),

                // Classic-only: the modern style renders these values inside its bars, so separate
                // text rows would duplicate the same responsibility.
                new Setting.Header("经典文字行", "仅经典风格；现代风格的条已经直接显示这些数值")
                        .visibleWhen(() -> !cfg.statsDisplayBlock.statsModernStyle),
                new Setting.BooleanSetting("防御文字",
                        () -> cfg.statsDisplayBlock.statsDefenseEnabled, v -> cfg.statsDisplayBlock.statsDefenseEnabled = v)
                        .visibleWhen(() -> !cfg.statsDisplayBlock.statsModernStyle),
                new Setting.BooleanSetting("速度文字",
                        () -> cfg.statsDisplayBlock.statsSpeedEnabled, v -> cfg.statsDisplayBlock.statsSpeedEnabled = v)
                        .visibleWhen(() -> !cfg.statsDisplayBlock.statsModernStyle),
                new Setting.BooleanSetting("活力文字",
                        () -> cfg.statsDisplayBlock.statsVitalityEnabled, v -> cfg.statsDisplayBlock.statsVitalityEnabled = v)
                        .visibleWhen(() -> !cfg.statsDisplayBlock.statsModernStyle),
                new Setting.SliderSetting("防御文字大小", 0.25, 6.0,
                        () -> cfg.statsDisplayBlock.statsDefenseScale,
                        v -> cfg.statsDisplayBlock.statsDefenseScale = (float) v, "%.2f", ModConfig.SCALE_GRID)
                        .visibleWhen(() -> !cfg.statsDisplayBlock.statsModernStyle),
                new Setting.SliderSetting("速度文字大小", 0.25, 6.0,
                        () -> cfg.statsDisplayBlock.statsSpeedScale,
                        v -> cfg.statsDisplayBlock.statsSpeedScale = (float) v, "%.2f", ModConfig.SCALE_GRID)
                        .visibleWhen(() -> !cfg.statsDisplayBlock.statsModernStyle),
                new Setting.SliderSetting("活力文字大小", 0.25, 6.0,
                        () -> cfg.statsDisplayBlock.statsVitalityScale,
                        v -> cfg.statsDisplayBlock.statsVitalityScale = (float) v, "%.2f", ModConfig.SCALE_GRID)
                        .visibleWhen(() -> !cfg.statsDisplayBlock.statsModernStyle),

                new Setting.Header("经典布局",
                        "原版图标 + 精确数值；两行文字的位置在「HUD 位置调整」页里拖")
                        .visibleWhen(() -> !cfg.statsDisplayBlock.statsModernStyle),
                new Setting.BooleanSetting("法力不足时闪烁",
                        () -> cfg.statsDisplayBlock.statsManaBlink, v -> cfg.statsDisplayBlock.statsManaBlink = v)
                        .hint("剩余不足两格时按原版节奏闪烁")
                        .visibleWhen(() -> !cfg.statsDisplayBlock.statsModernStyle),
                new Setting.BooleanSetting("生命数值",
                        () -> cfg.statsDisplayBlock.statsHealthTextEnabled, v -> cfg.statsDisplayBlock.statsHealthTextEnabled = v)
                        .hint("20 格图标表达不了一个点；这里给出精确值与吸收量")
                        .visibleWhen(() -> !cfg.statsDisplayBlock.statsModernStyle),
                new Setting.SliderSetting("生命数值大小", 0.25, 6.0,
                        () -> cfg.statsDisplayBlock.statsHealthTextScale,
                        v -> cfg.statsDisplayBlock.statsHealthTextScale = (float) v, "%.2f", ModConfig.SCALE_GRID)
                        .visibleWhen(() -> !cfg.statsDisplayBlock.statsModernStyle),
                new Setting.BooleanSetting("法力数值",
                        () -> cfg.statsDisplayBlock.statsManaTextEnabled, v -> cfg.statsDisplayBlock.statsManaTextEnabled = v)
                        .hint("精确值与溢出法力")
                        .visibleWhen(() -> !cfg.statsDisplayBlock.statsModernStyle),
                new Setting.SliderSetting("法力数值大小", 0.25, 6.0,
                        () -> cfg.statsDisplayBlock.statsManaTextScale,
                        v -> cfg.statsDisplayBlock.statsManaTextScale = (float) v, "%.2f", ModConfig.SCALE_GRID)
                        .visibleWhen(() -> !cfg.statsDisplayBlock.statsModernStyle),

                // Modern-only: the geometry all six bars share, then a switch and a size for each.
                // Position comes from the HUD position editor, where every bar is its own target.
                new Setting.Header("现代条",
                        "位置在「HUD 位置调整」页里逐条拖拽与缩放，这里只管粗细与开关")
                        .visibleWhen(() -> cfg.statsDisplayBlock.statsModernStyle),
                new Setting.BooleanSetting("条上显示数值",
                        () -> cfg.statsDisplayBlock.statsBarShowValues, v -> cfg.statsDisplayBlock.statsBarShowValues = v)
                        .visibleWhen(() -> cfg.statsDisplayBlock.statsModernStyle),
                new Setting.BooleanSetting("生命条",
                        () -> cfg.statsDisplayBlock.statsHealthBarEnabled, v -> cfg.statsDisplayBlock.statsHealthBarEnabled = v)
                        .hint("吸收血量作为第二层叠在它上面")
                        .visibleWhen(() -> cfg.statsDisplayBlock.statsModernStyle),
                new Setting.SliderSetting("生命条大小", 0.25, 6.0,
                        () -> cfg.statsDisplayBlock.statsHealthBarScale,
                        v -> cfg.statsDisplayBlock.statsHealthBarScale = (float) v, "%.2f", ModConfig.SCALE_GRID)
                        .visibleWhen(() -> cfg.statsDisplayBlock.statsModernStyle),
                new Setting.BooleanSetting("法力条",
                        () -> cfg.statsDisplayBlock.statsManaBarEnabled, v -> cfg.statsDisplayBlock.statsManaBarEnabled = v)
                        .visibleWhen(() -> cfg.statsDisplayBlock.statsModernStyle),
                new Setting.SliderSetting("法力条大小", 0.25, 6.0,
                        () -> cfg.statsDisplayBlock.statsManaBarScale,
                        v -> cfg.statsDisplayBlock.statsManaBarScale = (float) v, "%.2f", ModConfig.SCALE_GRID)
                        .visibleWhen(() -> cfg.statsDisplayBlock.statsModernStyle),
                new Setting.BooleanSetting("防御条",
                        () -> cfg.statsDisplayBlock.statsDefenseBarEnabled, v -> cfg.statsDisplayBlock.statsDefenseBarEnabled = v)
                        .visibleWhen(() -> cfg.statsDisplayBlock.statsModernStyle),
                new Setting.SliderSetting("防御条大小", 0.25, 6.0,
                        () -> cfg.statsDisplayBlock.statsDefenseBarScale,
                        v -> cfg.statsDisplayBlock.statsDefenseBarScale = (float) v, "%.2f", ModConfig.SCALE_GRID)
                        .visibleWhen(() -> cfg.statsDisplayBlock.statsModernStyle),
                new Setting.BooleanSetting("速度条",
                        () -> cfg.statsDisplayBlock.statsSpeedBarEnabled, v -> cfg.statsDisplayBlock.statsSpeedBarEnabled = v)
                        .visibleWhen(() -> cfg.statsDisplayBlock.statsModernStyle),
                new Setting.SliderSetting("速度条大小", 0.25, 6.0,
                        () -> cfg.statsDisplayBlock.statsSpeedBarScale,
                        v -> cfg.statsDisplayBlock.statsSpeedBarScale = (float) v, "%.2f", ModConfig.SCALE_GRID)
                        .visibleWhen(() -> cfg.statsDisplayBlock.statsModernStyle),
                new Setting.BooleanSetting("活力条",
                        () -> cfg.statsDisplayBlock.statsVitalityBarEnabled, v -> cfg.statsDisplayBlock.statsVitalityBarEnabled = v)
                        .visibleWhen(() -> cfg.statsDisplayBlock.statsModernStyle),
                new Setting.SliderSetting("活力条大小", 0.25, 6.0,
                        () -> cfg.statsDisplayBlock.statsVitalityBarScale,
                        v -> cfg.statsDisplayBlock.statsVitalityBarScale = (float) v, "%.2f", ModConfig.SCALE_GRID)
                        .visibleWhen(() -> cfg.statsDisplayBlock.statsModernStyle),
                new Setting.BooleanSetting("空气条",
                        () -> cfg.statsDisplayBlock.statsAirBarEnabled, v -> cfg.statsDisplayBlock.statsAirBarEnabled = v)
                        .visibleWhen(() -> cfg.statsDisplayBlock.statsModernStyle),
                new Setting.SliderSetting("空气条大小", 0.25, 6.0,
                        () -> cfg.statsDisplayBlock.statsAirBarScale,
                        v -> cfg.statsDisplayBlock.statsAirBarScale = (float) v, "%.2f", ModConfig.SCALE_GRID)
                        .visibleWhen(() -> cfg.statsDisplayBlock.statsModernStyle),

                // Advanced is its own card: a different kind of operation from the bar switches above.
                new Setting.Header("重置与恢复", "重置条位置与恢复默认颜色")
                        .visibleWhen(() -> cfg.statsDisplayBlock.statsModernStyle),
                new Setting.ActionSetting(() -> Localization.tr("重置条位置"),
                        () -> StatsBarHudElement.resetToDefaults(cfg))
                        .visibleWhen(() -> cfg.statsDisplayBlock.statsModernStyle),
                new Setting.ActionSetting(() -> Localization.tr("恢复默认颜色"),
                        () -> {
                            cfg.statsDisplayBlock.statsHealthColor = ColorDefaults.STATS_HEALTH;
                            cfg.statsDisplayBlock.statsAbsorptionColor = ColorDefaults.STATS_ABSORPTION;
                            cfg.statsDisplayBlock.statsManaColor = ColorDefaults.STATS_MANA;
                            cfg.statsDisplayBlock.statsOverflowColor = ColorDefaults.STATS_OVERFLOW;
                            cfg.statsDisplayBlock.statsAirColor = ColorDefaults.STATS_AIR;
                            cfg.statsDisplayBlock.statsDefenseColor = ColorDefaults.STATS_DEFENSE;
                            cfg.statsDisplayBlock.statsSpeedColor = ColorDefaults.STATS_SPEED;
                            cfg.statsDisplayBlock.statsVitalityColor = ColorDefaults.STATS_VITALITY;
                            rebuildPanelAfterStyleChange();
                        })
                        .visibleWhen(() -> cfg.statsDisplayBlock.statsModernStyle));
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
