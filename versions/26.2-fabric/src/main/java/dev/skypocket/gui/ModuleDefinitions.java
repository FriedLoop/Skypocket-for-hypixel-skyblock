package dev.skypocket.gui;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;
import dev.skypocket.gui.setting.Setting;
import dev.skypocket.ui.Style;
import dev.skypocket.ui.widget.CrosshairGrid;
import dev.skypocket.ui.widget.CrosshairPreview;
import dev.skypocket.hud.StatsBarHudElement;
import dev.skypocket.i18n.Localization;
import dev.skypocket.render.AntialiasingRenderer;
import dev.skypocket.render.ColorGradeRenderer;
import dev.skypocket.render.ColorUtil;
import dev.skypocket.render.HotspotOverlayStyle;
import dev.skypocket.render.HotspotType;
import dev.skypocket.skyblock.MiningStrengthTable;
import dev.skypocket.sound.MentionSoundManager;
import dev.skypocket.sound.SoundManager;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/**
 * Defines the built-in Click GUI modules as declarative {@link Setting} lists. Each
 * feature's settings live here instead of inside the panel, and are registered into
 * {@link ModuleRegistry} on client init.
 */
final class ModuleDefinitions {

    private ModuleDefinitions() {
    }

    static void registerAll() {
        ModConfig cfg = ConfigManager.get();
        registerCrosshair(cfg);
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.BlockOutlineFeature());
        // Phase 1 of the feature SPI: these three migrated off the hand-written registrations.
        // They still register into the same ModuleRegistry the panel reads, so nothing visible
        // changes - see dev.skypocket.feature.ModuleAdapter.
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.ThirdPersonFeature());
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.NoHurtCameraFeature());
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.MentionAlertFeature());
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.ChatCompactFeature());
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.DamageSplashFeature());
        // Phase 4 batch 1: the ESP family, all toggle + zero-or-one setting.
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.PlayerGlowFeature());
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.ItemGlowFeature());
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.DragonGlowFeature());
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.HidePlayersFeature());
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.HideSpawnPointPlayersFeature());
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.HidePlayersNearNpcsFeature());
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.HideOtherPlayersPresentsFeature());
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.HitboxFeature());
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.HideLandedArrowsFeature());
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.HideStuckArrowsFeature());
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.FishingHotspotOverlayFeature());
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.LegendarySeaCreatureWarningFeature());
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.DrillFuelFeature());
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.MiningAbilityReminderFeature());
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.MiningBreakPredictFeature());
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.CommissionsFeature());
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.PostProcessFeature());
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.HandItemFeature());
        // Phase 2: these two also own a HUD element, so their HudSpec replaces the editor's
        // hand-written entry for that target.
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.ClockFeature());
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.DayCountFeature());
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.FpsTpsPingFeature());
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.HeldItemNameFeature());
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.StatsDisplayFeature());
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.SkillDisplayFeature());
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.ThunderBottleFeature());
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.ItemPickupLogFeature());
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.AutoSprintFeature());
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.ScrollableTooltipsFeature());
    }

    private static void registerCrosshair(ModConfig cfg) {
        ModuleRegistry.register(new Module("crosshair", "自定义准星", Category.RENDER,
                () -> cfg.crosshairEnabled, v -> cfg.crosshairEnabled = v,
                List.of(
                        new Setting.Header("预览", "实时预览准星形状与像素网格"),
                        new Setting.V2CustomSetting(140, (row, palette) -> {
                            int gridSize = CrosshairGrid.preferredSize();
                            row.add(new CrosshairGrid(cfg.crosshairGrid, cfg::getCrosshairColorARGB,
                                    Style.builder().width(gridSize).height(gridSize).build(), palette));
                            row.add(new CrosshairPreview(cfg, Style.builder()
                                    .height(gridSize).flexGrow(1f).build(), palette));
                        }),
                        new Setting.GroupSetting("外观", List.of(
                                new Setting.BooleanSetting("描边", () -> cfg.crosshairStroke, v -> cfg.crosshairStroke = v),
                                new Setting.SliderSetting("描边宽度", 0.5, 4.0, () -> cfg.crosshairStrokeWidth, v -> cfg.crosshairStrokeWidth = (float) v, "%.1f", 0.1),
                                new Setting.ColorSetting("准星颜色", cfg::getCrosshairColorARGB, v -> {
                                    cfg.crosshairRed = ColorUtil.red(v);
                                    cfg.crosshairGreen = ColorUtil.green(v);
                                    cfg.crosshairBlue = ColorUtil.blue(v);
                                    cfg.crosshairAlpha = ColorUtil.alpha(v);
                                }, true),
                                new Setting.SliderSetting("像素大小", 1, 8, () -> cfg.crosshairPixelSize, v -> cfg.crosshairPixelSize = (float) v, "%.0f", 1),
                                new Setting.SliderSetting("缩放", 0.25, 4.0, () -> cfg.crosshairScale, v -> cfg.crosshairScale = (float) v, "%.2f", ModConfig.SCALE_GRID)))
                        .subtitle("调整准星线条、颜色、像素大小与缩放"),
                        // The switch is the feature's own flag: it was a row of its own before, and
                        // turning it off really does stop the tint (the renderer reads this field).
                        new Setting.GroupSetting("目标变色", () -> cfg.crosshairEntityColorEnabled,
                                v -> cfg.crosshairEntityColorEnabled = v, List.of(
                                new Setting.ColorSetting("友好颜色", () -> cfg.crosshairFriendlyColor, v -> cfg.crosshairFriendlyColor = v),
                                new Setting.ColorSetting("中立颜色", () -> cfg.crosshairNeutralColor, v -> cfg.crosshairNeutralColor = v),
                                new Setting.ColorSetting("敌对颜色", () -> cfg.crosshairHostileColor, v -> cfg.crosshairHostileColor = v),
                                new Setting.ColorSetting("箱子颜色", () -> cfg.crosshairContainerColor, v -> cfg.crosshairContainerColor = v)))
                                .subtitle("按目标类型切换准星颜色")
                )));
    }

    /**
     * SkyBlock mining commissions (only the Dwarven Mines / Crystal Hollows / Glacite Tunnels have
     * them, and the tab list only carries the block there - see {@code CommissionTracker}).
     */
    /**
     * Post-processing: every full-screen pass over the finished world image, as one module with a
     * group per effect - in the order they actually run (see {@code GameRendererMixin}).
     *
     * <p>The module's own switch is the master: off means the whole stack is off. Each group's switch
     * is that effect's existing enable flag, so a config written before the merge keeps meaning exactly
     * what it did - and the per-effect code still reads the same fields.
     */
    /**
     * Anti-aliasing as a post pass (see {@code AntialiasingRenderer}): no SkyBlock gate, it is a
     * plain render feature. The strength picks a parameter set (edge threshold + sub-pixel amount) and
     * the sharpening slider rides along in the same chain, as its last pass - after FXAA, which is
     * what lets it put back the contrast the filter takes out of small text.
     */
    /**
     * Colour grading (see {@code ColorGradeRenderer}): the cheap half of a shader pack's look as one
     * post pass - no geometry pass, no shadow map. The parameters are live - the chain is rebuilt
     * (debounced, see {@code LivePostChain}) whenever one of them changes - so the dropdown writes a
     * whole preset at once and the sliders fine-tune it.
     */
    /**
     * Motion blur (see {@code MotionBlurRenderer}): camera reprojection against the previous frame, so
     * walking and turning smear while a still camera is untouched. The amount is an exposure time in
     * milliseconds rather than a per-frame factor, which is what keeps the look the same at any frame
     * rate. Off by default - it is a look, not a correction.
     */
    /**
     * Bloom (see {@code BloomRenderer}): a bright pass that already blurs, a composite and a blit.
     * Its own module so switching it off costs nothing - the passes only exist while it is on - and
     * its sliders are live like the grade's.
     */
    /**
     * One of the seven *colour* parameters. Moving it also marks the state as "custom": those values
     * no longer match the preset the dropdown names, and saying so is cheaper than leaving the two
     * out of step.
     */
    /**
     * One of the parameters no preset owns (sharpen, fog, AO, bloom). Rebuilding the chain does not
     * care which module a value came from, so these are ordinary sliders.
     */
    /** Applies a preset, then re-lays the page out so the sliders show what it wrote. */
    /**
     * SkyBlock's own numbers over the vanilla health area (see {@code StatsOverlayRenderer}): the
     * classic style redraws them with the HUD's own vocabulary, the modern one as bars. The first
     * setting is the one-click switch between the two the feature asks for; the rest is geometry,
     * colours, and the three plain-text rows.
     *
     * <p>The rows share their colour with the matching bar (defence / speed / vitality), which is why
     * there is one colour per value rather than one per style.
     */
    /**
     * Rebuilds the settings page after the style changed: the rows that belong to the other style are
     * hidden through {@code Setting#visibleWhen}, and the widgets are built once rather than every
     * frame, so the panel has to be laid out again for the change to show.
     */
    /**
     * The vanilla held item tooltip - the item name that fades in above the hotbar after the selected
     * slot changes - made movable and sizable.
     *
     * <p>A general HUD feature rather than a SkyBlock one: it is pure vanilla furniture that is simply
     * the wrong size on a large GUI, so it belongs in {@link Category#HUD} next to the clock and the
     * FPS readout, not under a separate SkyBlock HUD group. Its real controls are the position editor's drag and
     * wheel; the offsets are also exposed here because they are the only way to bring it back after
     * dragging it somewhere silly.
     */
}





