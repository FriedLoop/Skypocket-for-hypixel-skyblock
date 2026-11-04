package dev.skypocket.gui;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;
import dev.skypocket.gui.setting.Setting;
import dev.skypocket.gui.widget.CrosshairPreviewWidget;
import dev.skypocket.gui.widget.PixelGridWidget;
import dev.skypocket.hud.StatsBarHudElement;
import dev.skypocket.i18n.Localization;
import dev.skypocket.render.AntialiasingRenderer;
import dev.skypocket.render.ColorGradeRenderer;
import dev.skypocket.render.ColorUtil;
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
        registerBlockOutline(cfg);
        registerThirdPerson(cfg);
        registerNoHurtCamera(cfg);
        registerMentionAlert(cfg);
        registerChatCompact(cfg);
        registerSplashCompact(cfg);
        registerPlayerGlow(cfg);
        registerDragonGlow(cfg);
        registerHidePlayers(cfg);
        registerHitbox(cfg);
        registerHideLandedArrows(cfg);
        registerHideStuckArrows(cfg);
        registerFishingHotspotOverlay(cfg);
        registerMiningAbilityReminder(cfg);
        registerMiningBreakPredict(cfg);
        registerCommissions(cfg);
        registerPostProcess(cfg);
        registerHandItem(cfg);
        registerClock(cfg);
        registerDayCount(cfg);
        registerFpsTpsPing(cfg);
        registerHeldItemName(cfg);
        registerStatsDisplay(cfg);
        registerAutoSprint(cfg);
        registerScrollableTooltips(cfg);
    }

    private static void registerCrosshair(ModConfig cfg) {
        ModuleRegistry.register(new Module("crosshair", "自定义准星", Category.RENDER,
                () -> cfg.crosshairEnabled, v -> cfg.crosshairEnabled = v,
                List.of(
                        new Setting.CustomSetting(140, (x, y, add) -> {
                            add.accept(new PixelGridWidget(x, y, ModConfig.GRID, 8, cfg.crosshairGrid, cfg::getCrosshairColorARGB));
                            add.accept(new CrosshairPreviewWidget(x + 146, y, Setting.WIDGET_WIDTH - 146, 136, cfg));
                        }),
                        new Setting.GroupSetting("准星外观", List.of(
                                new Setting.BooleanSetting("描边", () -> cfg.crosshairStroke, v -> cfg.crosshairStroke = v),
                                new Setting.SliderSetting("描边宽度", 0.5, 4.0, () -> cfg.crosshairStrokeWidth, v -> cfg.crosshairStrokeWidth = (float) v, "%.1f", 0.1),
                                new Setting.ColorSetting("准星颜色", cfg::getCrosshairColorARGB, v -> {
                                    cfg.crosshairRed = ColorUtil.red(v);
                                    cfg.crosshairGreen = ColorUtil.green(v);
                                    cfg.crosshairBlue = ColorUtil.blue(v);
                                    cfg.crosshairAlpha = ColorUtil.alpha(v);
                                }, true),
                                new Setting.SliderSetting("像素大小", 1, 8, () -> cfg.crosshairPixelSize, v -> cfg.crosshairPixelSize = (float) v, "%.0f", 1),
                                new Setting.SliderSetting("缩放", 0.25, 4.0, () -> cfg.crosshairScale, v -> cfg.crosshairScale = (float) v, "%.2f", ModConfig.SCALE_GRID))),
                        // The switch is the feature's own flag: it was a row of its own before, and
                        // turning it off really does stop the tint (the renderer reads this field).
                        new Setting.GroupSetting("瞄准目标变色", () -> cfg.crosshairEntityColorEnabled,
                                v -> cfg.crosshairEntityColorEnabled = v, List.of(
                                new Setting.ColorSetting("友好颜色", () -> cfg.crosshairFriendlyColor, v -> cfg.crosshairFriendlyColor = v),
                                new Setting.ColorSetting("中立颜色", () -> cfg.crosshairNeutralColor, v -> cfg.crosshairNeutralColor = v),
                                new Setting.ColorSetting("敌对颜色", () -> cfg.crosshairHostileColor, v -> cfg.crosshairHostileColor = v),
                                new Setting.ColorSetting("箱子颜色", () -> cfg.crosshairContainerColor, v -> cfg.crosshairContainerColor = v)))
                )));
    }

    private static void registerBlockOutline(ModConfig cfg) {
        ModuleRegistry.register(new Module("block_outline", "方块描边", Category.RENDER,
                () -> cfg.blockOutlineEnabled, v -> cfg.blockOutlineEnabled = v,
                List.of(
                        new Setting.ColorSetting("描边颜色", cfg::getBlockOutlineColorARGB, v -> {
                            cfg.blockOutlineRed = ColorUtil.red(v);
                            cfg.blockOutlineGreen = ColorUtil.green(v);
                            cfg.blockOutlineBlue = ColorUtil.blue(v);
                            cfg.blockOutlineAlpha = ColorUtil.alpha(v);
                        }, true),
                        new Setting.SliderSetting("描边厚度", 0.5, 6.0, () -> cfg.blockOutlineThickness, v -> cfg.blockOutlineThickness = (float) v, "%.2f", 0.1),
                        new Setting.BooleanSetting("方块面调色", () -> cfg.blockOutlineFaceTint, v -> cfg.blockOutlineFaceTint = v),
                        new Setting.ColorSetting("面颜色", cfg::getBlockOutlineFaceColorARGB, v -> {
                            cfg.blockOutlineFaceRed = ColorUtil.red(v);
                            cfg.blockOutlineFaceGreen = ColorUtil.green(v);
                            cfg.blockOutlineFaceBlue = ColorUtil.blue(v);
                            cfg.blockOutlineFaceAlpha = ColorUtil.alpha(v);
                        }, true)
                )));
    }

    private static void registerThirdPerson(ModConfig cfg) {
        ModuleRegistry.register(new Module("third_person", "第三人称穿墙", Category.RENDER,
                () -> cfg.thirdPersonThroughWalls, v -> cfg.thirdPersonThroughWalls = v, List.of()));
    }

    private static void registerMentionAlert(ModConfig cfg) {
        List<Setting> settings = new ArrayList<>();
        settings.add(new Setting.DropdownSetting("提醒音效", SoundManager::listAllSounds,
                () -> cfg.mentionAlertSound, v -> cfg.mentionAlertSound = v));
        settings.add(new Setting.SliderSetting("音量", 0, 200, () -> cfg.mentionAlertVolume * 100,
                v -> cfg.mentionAlertVolume = (float) (v / 100.0), "%.0f%%", 1));
        settings.add(new Setting.SliderSetting("音高", 0.5, 2.0, () -> cfg.mentionAlertPitch,
                v -> cfg.mentionAlertPitch = (float) v, "%.2f", 0.05));
        settings.add(new Setting.ActionSetting("快捷上传自定义音频", MentionSoundManager::pickAndImport));
        settings.add(new Setting.TextFieldSetting("自定义检索文本", () -> cfg.mentionAlertCustomText, v -> cfg.mentionAlertCustomText = v));
        settings.add(new Setting.SliderSetting("ID 检索长度", 1, 16, () -> cfg.mentionAlertNameLength,
                v -> cfg.mentionAlertNameLength = (int) Math.round(v), "%.0f", 1));
        ModuleRegistry.register(new Module("mention_alert", "聊天提及提醒", Category.NOTIFICATION,
                () -> cfg.mentionAlertEnabled, v -> cfg.mentionAlertEnabled = v, settings));
    }

    private static void registerSplashCompact(ModConfig cfg) {
        List<Setting> settings = new ArrayList<>();
        settings.add(new Setting.BooleanSetting("合并", () -> cfg.damageSplashCompactEnabled,
                v -> cfg.damageSplashCompactEnabled = v));
        settings.add(new Setting.SliderSetting("归属半径", 0.5, 3.0,
                () -> cfg.damageSplashRadius,
                v -> cfg.damageSplashRadius = v, "%.1f", 0.1));
        settings.add(new Setting.SliderSetting("合并窗口(毫秒)", 200, 3000,
                () -> cfg.damageSplashWindowMs,
                v -> cfg.damageSplashWindowMs = (int) Math.round(v), "%.0f", 100));
        settings.add(new Setting.BooleanSetting("大写格式化", () -> cfg.damageSplashUppercase,
                v -> cfg.damageSplashUppercase = v));
        settings.add(new Setting.BooleanSetting("显示次数", () -> cfg.damageSplashShowCount,
                v -> cfg.damageSplashShowCount = v));
        settings.add(new Setting.SliderSetting("显示时长(毫秒)", 300, 5000,
                () -> cfg.damageSplashLifetimeMs,
                v -> cfg.damageSplashLifetimeMs = (int) Math.round(v), "%.0f", 100));
        settings.add(new Setting.SliderSetting("淡出(毫秒)", 0, 1500,
                () -> cfg.damageSplashFadeMs,
                v -> cfg.damageSplashFadeMs = (int) Math.round(v), "%.0f", 50));
        settings.add(new Setting.SliderSetting("右侧偏移", 0.0, 2.0,
                () -> cfg.damageSplashRightOffset,
                v -> cfg.damageSplashRightOffset = v, "%.1f", 0.1));
        settings.add(new Setting.SliderSetting("行高", 0.1, 0.8,
                () -> cfg.damageSplashRowHeight,
                v -> cfg.damageSplashRowHeight = v, "%.1f", 0.05));
        ModuleRegistry.register(new Module("damage_splash_compact", "伤害飘字", Category.SKYBLOCK_COMBAT,
                () -> cfg.damageSplashEnabled, v -> cfg.damageSplashEnabled = v, settings));
    }

    private static void registerChatCompact(ModConfig cfg) {
        List<Setting> settings = new ArrayList<>();
        settings.add(new Setting.SliderSetting("压缩窗口(秒)", 1, 120,
                () -> cfg.chatCompactTimeoutSeconds,
                v -> cfg.chatCompactTimeoutSeconds = (int) Math.round(v), "%.0f", 1));
        ModuleRegistry.register(new Module("chat_compact", "聊天消息压缩", Category.NOTIFICATION,
                () -> cfg.chatCompactEnabled, v -> cfg.chatCompactEnabled = v, settings));
    }

    private static void registerMiningAbilityReminder(ModConfig cfg) {
        List<Setting> settings = new ArrayList<>();
        settings.add(new Setting.ColorSetting("技能名颜色", () -> cfg.miningAbilityNameColor,
                v -> cfg.miningAbilityNameColor = v));
        settings.add(new Setting.ColorSetting("可用颜色", () -> cfg.miningAbilityAvailableColor,
                v -> cfg.miningAbilityAvailableColor = v));
        settings.add(new Setting.DropdownSetting("提醒音效", SoundManager::listAllSounds,
                () -> cfg.miningAbilityReminderSound, v -> cfg.miningAbilityReminderSound = v));
        settings.add(new Setting.SliderSetting("音量", 0, 200, () -> cfg.miningAbilityReminderVolume * 100,
                v -> cfg.miningAbilityReminderVolume = (float) (v / 100.0), "%.0f%%", 1));
        settings.add(new Setting.SliderSetting("音高", 0.5, 2.0, () -> cfg.miningAbilityReminderPitch,
                v -> cfg.miningAbilityReminderPitch = (float) v, "%.2f", 0.05));
        ModuleRegistry.register(new Module("mining_ability_reminder", "挖矿技能提醒", Category.SKYBLOCK_MINING,
                () -> cfg.miningAbilityReminderEnabled, v -> cfg.miningAbilityReminderEnabled = v, settings));
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
    private static void registerPostProcess(ModConfig cfg) {
        List<Setting> settings = new ArrayList<>();
        settings.add(new Setting.GroupSetting("抗锯齿", () -> cfg.antialiasingEnabled,
                v -> cfg.antialiasingEnabled = v, antialiasingSettings(cfg)));
        settings.add(new Setting.GroupSetting("动态模糊", () -> cfg.motionBlurEnabled,
                v -> cfg.motionBlurEnabled = v, motionBlurSettings(cfg)));
        settings.add(new Setting.GroupSetting("画面调色", () -> cfg.colorGradeEnabled,
                v -> cfg.colorGradeEnabled = v, colorGradeSettings(cfg)));
        settings.add(new Setting.GroupSetting("泛光", () -> cfg.bloomEnabled,
                v -> cfg.bloomEnabled = v, bloomSettings(cfg)));
        ModuleRegistry.register(new Module("post_process", "后处理", Category.RENDER,
                () -> cfg.postProcessEnabled, v -> cfg.postProcessEnabled = v, settings));
    }

    /**
     * Anti-aliasing as a post pass (see {@code AntialiasingRenderer}): no SkyBlock gate, it is a
     * plain render feature. The strength picks a parameter set (edge threshold + sub-pixel amount) and
     * the sharpening slider rides along in the same chain, as its last pass - after FXAA, which is
     * what lets it put back the contrast the filter takes out of small text.
     */
    private static List<Setting> antialiasingSettings(ModConfig cfg) {
        List<Setting> settings = new ArrayList<>();
        settings.add(new Setting.DropdownSetting("强度", AntialiasingRenderer::presets,
                () -> cfg.antialiasingStrength, v -> cfg.antialiasingStrength = v));
        settings.add(effectParameter("锐化", 0.00, 1.50, 0.05, "%.2f",
                () -> cfg.antialiasingSharpen, v -> cfg.antialiasingSharpen = (float) v));
        settings.add(new Setting.BooleanSetting("光影下也生效", () -> cfg.antialiasingWithShaders,
                v -> cfg.antialiasingWithShaders = v));
        settings.add(new Setting.BooleanSetting("开界面时跳过", () -> cfg.antialiasingSkipScreens,
                v -> cfg.antialiasingSkipScreens = v));
        return settings;
    }

    /**
     * Colour grading (see {@code ColorGradeRenderer}): the cheap half of a shader pack's look as one
     * post pass - no geometry pass, no shadow map. The parameters are live - the chain is rebuilt
     * (debounced, see {@code LivePostChain}) whenever one of them changes - so the dropdown writes a
     * whole preset at once and the sliders fine-tune it.
     */
    private static List<Setting> colorGradeSettings(ModConfig cfg) {
        List<Setting> settings = new ArrayList<>();
        settings.add(new Setting.DropdownSetting("套用预设", ColorGradeRenderer::presets,
                () -> cfg.colorGradePreset, id -> applyColorGradePreset(cfg, id)));
        settings.add(colorParameter(cfg, "曝光", 0.80, 1.60, 0.01, "%.2f",
                () -> cfg.colorGradeExposure, v -> cfg.colorGradeExposure = (float) v));
        settings.add(colorParameter(cfg, "对比度", 0.80, 1.60, 0.01, "%.2f",
                () -> cfg.colorGradeContrast, v -> cfg.colorGradeContrast = (float) v));
        settings.add(colorParameter(cfg, "饱和度", 0.50, 1.80, 0.01, "%.2f",
                () -> cfg.colorGradeSaturation, v -> cfg.colorGradeSaturation = (float) v));
        settings.add(colorParameter(cfg, "伽马", 0.80, 1.25, 0.01, "%.2f",
                () -> cfg.colorGradeGamma, v -> cfg.colorGradeGamma = (float) v));
        settings.add(colorParameter(cfg, "黑位", -0.02, 0.03, 0.001, "%.3f",
                () -> cfg.colorGradeLift, v -> cfg.colorGradeLift = (float) v));
        settings.add(colorParameter(cfg, "色温", 0.00, 1.00, 0.01, "%.2f",
                () -> cfg.colorGradeTemperature, v -> cfg.colorGradeTemperature = (float) v));
        settings.add(colorParameter(cfg, "暗角", 0.00, 1.00, 0.01, "%.2f",
                () -> cfg.colorGradeVignette, v -> cfg.colorGradeVignette = (float) v));
        settings.add(new Setting.BooleanSetting("开界面时跳过", () -> cfg.colorGradeSkipScreens,
                v -> cfg.colorGradeSkipScreens = v));
        return settings;
    }

    /**
     * Motion blur (see {@code MotionBlurRenderer}): camera reprojection against the previous frame, so
     * walking and turning smear while a still camera is untouched. The amount is an exposure time in
     * milliseconds rather than a per-frame factor, which is what keeps the look the same at any frame
     * rate. Off by default - it is a look, not a correction.
     */
    private static List<Setting> motionBlurSettings(ModConfig cfg) {
        List<Setting> settings = new ArrayList<>();
        settings.add(effectParameter("强度", 0.00, 1.00, 0.05, "%.2f",
                () -> cfg.motionBlurStrength, v -> cfg.motionBlurStrength = (float) v));
        settings.add(effectParameter("曝光", 1, 50, 1, "%.0f",
                () -> cfg.motionBlurExposureMs, v -> cfg.motionBlurExposureMs = (float) v));
        settings.add(effectParameter("最大半径", 4, 64, 2, "%.0f",
                () -> cfg.motionBlurMaxRadius, v -> cfg.motionBlurMaxRadius = (float) v));
        settings.add(new Setting.BooleanSetting("开界面时跳过", () -> cfg.motionBlurSkipScreens,
                v -> cfg.motionBlurSkipScreens = v));
        settings.add(new Setting.BooleanSetting("调试：显示速度", () -> cfg.motionBlurDebug,
                v -> cfg.motionBlurDebug = v));
        return settings;
    }

    /**
     * Bloom (see {@code BloomRenderer}): a bright pass that already blurs, a composite and a blit.
     * Its own module so switching it off costs nothing - the passes only exist while it is on - and
     * its sliders are live like the grade's.
     */
    private static List<Setting> bloomSettings(ModConfig cfg) {
        List<Setting> settings = new ArrayList<>();
        settings.add(effectParameter("强度", 0.00, 2.00, 0.05, "%.2f",
                () -> cfg.bloomStrength, v -> cfg.bloomStrength = (float) v));
        settings.add(effectParameter("阈值", 0.00, 1.00, 0.02, "%.2f",
                () -> cfg.bloomThreshold, v -> cfg.bloomThreshold = (float) v));
        settings.add(effectParameter("半径", 1.0, 64.0, 1.0, "%.1f",
                () -> cfg.bloomRadius, v -> cfg.bloomRadius = (float) v));
        return settings;
    }

    /**
     * One of the seven *colour* parameters. Moving it also marks the state as "custom": those values
     * no longer match the preset the dropdown names, and saying so is cheaper than leaving the two
     * out of step.
     */
    private static Setting.SliderSetting colorParameter(ModConfig cfg, String label, double min,
                                                        double max, double step, String format,
                                                        DoubleSupplier getter, DoubleConsumer setter) {
        return new Setting.SliderSetting(label, min, max, getter, v -> {
            setter.accept(v);
            cfg.colorGradePreset = ColorGradeRenderer.CUSTOM;
        }, format, step);
    }

    /**
     * One of the parameters no preset owns (sharpen, fog, AO, bloom). Rebuilding the chain does not
     * care which module a value came from, so these are ordinary sliders.
     */
    private static Setting.SliderSetting effectParameter(String label, double min, double max,
                                                         double step, String format,
                                                         DoubleSupplier getter, DoubleConsumer setter) {
        return new Setting.SliderSetting(label, min, max, getter, setter, format, step);
    }

    /** Applies a preset, then re-lays the page out so the sliders show what it wrote. */
    private static void applyColorGradePreset(ModConfig cfg, String id) {
        ColorGradeRenderer.applyPreset(cfg, id);
        Minecraft mc = Minecraft.getInstance();
        mc.schedule(() -> {
            if (mc.gui.screen() instanceof OneClickScreen screen) {
                screen.rebuildAfterSettingChange();
            }
        });
    }

    private static void registerCommissions(ModConfig cfg) {
        List<Setting> settings = new ArrayList<>();
        settings.add(new Setting.BooleanSetting("隐藏已完成的委托", () -> cfg.commissionsHideDone,
                v -> cfg.commissionsHideDone = v));
        settings.add(new Setting.BooleanSetting("未完成的置顶", () -> cfg.commissionsPendingFirst,
                v -> cfg.commissionsPendingFirst = v));
        settings.add(new Setting.ColorSetting("标题颜色", () -> cfg.commissionsHeaderColor,
                v -> cfg.commissionsHeaderColor = v));
        settings.add(new Setting.ColorSetting("未完成颜色", () -> cfg.commissionsStartColor,
                v -> cfg.commissionsStartColor = v));
        settings.add(new Setting.ColorSetting("完成颜色", () -> cfg.commissionsDoneColor,
                v -> cfg.commissionsDoneColor = v));
        ModuleRegistry.register(new Module("commissions", "挖矿委托", Category.SKYBLOCK_MINING,
                () -> cfg.commissionsEnabled, v -> cfg.commissionsEnabled = v, settings));
    }

    private static void registerMiningBreakPredict(ModConfig cfg) {
        List<Setting> settings = new ArrayList<>();
        settings.add(new Setting.BooleanSetting("准星进度圆环", () -> cfg.miningBreakRingEnabled,
                v -> cfg.miningBreakRingEnabled = v));
        settings.add(new Setting.SliderSetting("圆环半径", 6, 120, () -> cfg.miningBreakRingRadius,
                v -> cfg.miningBreakRingRadius = (float) v, "%.0f", 1));
        settings.add(new Setting.SliderSetting("圆环线宽", 1, 8, () -> cfg.miningBreakRingWidth,
                v -> cfg.miningBreakRingWidth = (float) v, "%.1f", 0.5));
        settings.add(new Setting.ColorSetting("进度起始颜色", () -> cfg.miningBreakRingColorStart,
                v -> cfg.miningBreakRingColorStart = v, true));
        settings.add(new Setting.ColorSetting("进度结束颜色", () -> cfg.miningBreakRingColorEnd,
                v -> cfg.miningBreakRingColorEnd = v, true));
        settings.add(new Setting.BooleanSetting("方块轮廓", () -> cfg.miningBreakOutlineEnabled,
                v -> cfg.miningBreakOutlineEnabled = v));
        settings.add(new Setting.ColorSetting("轮廓颜色", () -> cfg.miningBreakOutlineColor,
                v -> cfg.miningBreakOutlineColor = v, true));
        settings.add(new Setting.SliderSetting("提示偏移(Tick)", -6, 3, () -> cfg.miningBreakBiasTicks,
                v -> cfg.miningBreakBiasTicks = (int) Math.round(v), "%.0f", 1));
        settings.add(new Setting.DropdownSetting("提醒音效", SoundManager::listAllSounds,
                () -> cfg.miningBreakSound, v -> cfg.miningBreakSound = v));
        settings.add(new Setting.SliderSetting("音量", 0, 200, () -> cfg.miningBreakSoundVolume * 100,
                v -> cfg.miningBreakSoundVolume = (float) (v / 100.0), "%.0f%%", 1));
        settings.add(new Setting.SliderSetting("音高", 0.5, 2.0, () -> cfg.miningBreakSoundPitch,
                v -> cfg.miningBreakSoundPitch = (float) v, "%.2f", 0.05));
        // Calibration and diagnostics are not everyday settings: folded into one group so the page shows
        // the six things a player normally touches and nothing else.
        List<Setting> advanced = new ArrayList<>();
        advanced.add(new Setting.ActionSetting(
                () -> Localization.tr("重置矿物强度识别") + " (" + MiningStrengthTable.size() + ")",
                MiningStrengthTable::clear));
        advanced.add(new Setting.DropdownSetting("取整方式",
                () -> List.of("ceil", "floor", "round"),
                () -> cfg.miningTickRounding, v -> cfg.miningTickRounding = v));
        advanced.add(new Setting.BooleanSetting("自动校准速度刻度", () -> cfg.miningAutoScale,
                v -> cfg.miningAutoScale = v));
        advanced.add(new Setting.BooleanSetting("相位对齐", () -> cfg.miningPhaseAlign,
                v -> cfg.miningPhaseAlign = v));
        advanced.add(new Setting.DropdownSetting("对准时刻",
                () -> List.of("zero", "server", "client"),
                () -> cfg.miningAlignTarget, v -> cfg.miningAlignTarget = v));
        advanced.add(new Setting.SliderSetting("最小破坏 tick", 1, 20,
                () -> cfg.miningMinBreakTicks,
                v -> cfg.miningMinBreakTicks = (int) Math.round(v), "%.0f", 1));
        advanced.add(new Setting.BooleanSetting("调试信息", () -> cfg.miningBreakDebugHud,
                v -> cfg.miningBreakDebugHud = v));
        settings.add(new Setting.GroupSetting("高级", advanced));
        ModuleRegistry.register(new Module("mining_break_predict", "挖矿破坏预测", Category.SKYBLOCK_MINING,
                () -> cfg.miningBreakPredictEnabled, v -> cfg.miningBreakPredictEnabled = v, settings));
    }

    private static void registerNoHurtCamera(ModConfig cfg) {
        ModuleRegistry.register(new Module("no_hurt_camera", "受伤镜头不抖动", Category.RENDER,
                () -> cfg.noHurtCameraEnabled, v -> cfg.noHurtCameraEnabled = v, List.of()));
    }

    private static void registerPlayerGlow(ModConfig cfg) {
        ModuleRegistry.register(new Module("player_glow", "人物发光", Category.RENDER,
                () -> cfg.playerGlowEnabled, v -> cfg.playerGlowEnabled = v,
                List.of(
                        new Setting.ColorSetting("发光颜色", () -> cfg.playerGlowColor, v -> cfg.playerGlowColor = v)
                )));
    }

    private static void registerDragonGlow(ModConfig cfg) {
        ModuleRegistry.register(new Module("dragon_glow", "末影龙发光", Category.SKYBLOCK_COMBAT,
                () -> cfg.dragonGlowEnabled, v -> cfg.dragonGlowEnabled = v,
                List.of(
                        new Setting.ColorSetting("发光颜色", () -> cfg.dragonGlowColor, v -> cfg.dragonGlowColor = v)
                )));
    }

    private static void registerHidePlayers(ModConfig cfg) {
        ModuleRegistry.register(new Module("hide_players", "隐藏附近玩家", Category.RENDER,
                () -> cfg.hideNearbyPlayersEnabled, v -> cfg.hideNearbyPlayersEnabled = v,
                List.of(
                        new Setting.SliderSetting("范围", 0, 10, () -> cfg.hideNearbyPlayersRange,
                                v -> cfg.hideNearbyPlayersRange = (float) v, "%.1f", 0.1)
                )));
    }

    private static void registerHitbox(ModConfig cfg) {
        ModuleRegistry.register(new Module("hitbox", "碰撞箱", Category.RENDER,
                () -> cfg.hitboxEnabled, v -> cfg.hitboxEnabled = v,
                List.of(
                        new Setting.ColorSetting("颜色", () -> cfg.hitboxColor, v -> cfg.hitboxColor = v),
                        new Setting.SliderSetting("线宽", 0.5, 8.0, () -> cfg.hitboxLineWidth, v -> cfg.hitboxLineWidth = (float) v, "%.1f", 0.5),
                        new Setting.BooleanSetting("玩家", () -> cfg.hitboxShowPlayer, v -> cfg.hitboxShowPlayer = v),
                        new Setting.BooleanSetting("箭矢", () -> cfg.hitboxShowProjectile, v -> cfg.hitboxShowProjectile = v),
                        new Setting.BooleanSetting("敌对生物", () -> cfg.hitboxShowHostile, v -> cfg.hitboxShowHostile = v),
                        new Setting.BooleanSetting("中立生物", () -> cfg.hitboxShowNeutral, v -> cfg.hitboxShowNeutral = v),
                        new Setting.BooleanSetting("箱子", () -> cfg.hitboxShowContainer, v -> cfg.hitboxShowContainer = v),
                        new Setting.BooleanSetting("其他", () -> cfg.hitboxShowOther, v -> cfg.hitboxShowOther = v),
                        new Setting.BooleanSetting("实体视线", () -> cfg.hitboxShowLineOfSight, v -> cfg.hitboxShowLineOfSight = v),
                        new Setting.ColorSetting("视线颜色", () -> cfg.lookRayColor, v -> cfg.lookRayColor = v),
                        new Setting.SliderSetting("视线长度", 1.0, 64.0, () -> cfg.lookRayLength, v -> cfg.lookRayLength = (float) v, "%.1f", 0.5),
                        new Setting.BooleanSetting("不限距离", () -> cfg.hitboxUnlimitedRange, v -> cfg.hitboxUnlimitedRange = v),
                        new Setting.SliderSetting("范围", 1.0, 128.0, () -> cfg.hitboxRange, v -> cfg.hitboxRange = (float) v, "%.0f", 1)
                )));
    }

    private static void registerHideLandedArrows(ModConfig cfg) {
        ModuleRegistry.register(new Module("hide_landed_arrows", "隐藏落地箭矢", Category.RENDER,
                () -> cfg.hideLandedArrows, v -> cfg.hideLandedArrows = v, List.of()));
    }

    private static void registerHideStuckArrows(ModConfig cfg) {
        ModuleRegistry.register(new Module("hide_stuck_arrows", "隐藏插体箭矢", Category.RENDER,
                () -> cfg.hideStuckArrows, v -> cfg.hideStuckArrows = v, List.of()));
    }

    private static void registerFishingHotspotOverlay(ModConfig cfg) {
        List<Setting> settings = new ArrayList<>();
        for (HotspotType t : HotspotType.values()) {
            settings.add(new Setting.ColorSetting(t.label, () -> t.color(cfg), v -> t.setColor(cfg, v)));
        }
        settings.add(new Setting.ColorSetting("未知类型", () -> cfg.hotspotOverlayColor, v -> cfg.hotspotOverlayColor = v));
        settings.add(new Setting.SliderSetting("高度", 0.25, 256.0, () -> cfg.hotspotOverlayHeight, v -> cfg.hotspotOverlayHeight = (float) v, "%.2f", 0.05));
        settings.add(new Setting.SliderSetting("线宽", 0.5, 4.0, () -> cfg.hotspotOverlayLineWidth, v -> cfg.hotspotOverlayLineWidth = (float) v, "%.1f", 0.5));
        settings.add(new Setting.SliderSetting("显示距离", 8.0, 256.0, () -> cfg.hotspotOverlayRange, v -> cfg.hotspotOverlayRange = (float) v, "%.0f", 1.0));
        ModuleRegistry.register(new Module("fishing_hotspot_overlay", "钓鱼热点覆盖层", Category.SKYBLOCK_FISHING,
                () -> cfg.hotspotOverlayEnabled, v -> cfg.hotspotOverlayEnabled = v, settings));
    }

    private static void registerScrollableTooltips(ModConfig cfg) {
        ModuleRegistry.register(new Module("scrollable_tooltips", "滚动提示框", Category.RENDER,
                () -> cfg.scrollableTooltipsEnabled, v -> cfg.scrollableTooltipsEnabled = v,
                List.of(
                        new Setting.BooleanSetting("始终可滚动", () -> cfg.scrollableTooltipsAlwaysScroll, v -> cfg.scrollableTooltipsAlwaysScroll = v),
                        new Setting.SliderSetting("滚动灵敏度", 0.25, 4.0, () -> cfg.scrollableTooltipsSensitivity,
                                v -> cfg.scrollableTooltipsSensitivity = (float) v, "%.2fx", 0.05)
                )));
    }

    private static void registerHandItem(ModConfig cfg) {
        ModuleRegistry.register(new Module("hand_item", "手持物品动画", Category.RENDER,
                () -> cfg.handItemEnabled, v -> cfg.handItemEnabled = v,
                List.of(
                        new Setting.SliderSetting("物品大小", 0.1, 4.0, () -> cfg.handItemScale, v -> cfg.handItemScale = (float) v, "%.2f", ModConfig.SCALE_GRID),
                        new Setting.BooleanSetting("禁用挥动动画", () -> cfg.handItemDisableSwing, v -> cfg.handItemDisableSwing = v),
                        new Setting.BooleanSetting("禁用切换动画", () -> cfg.handItemDisableReequip, v -> cfg.handItemDisableReequip = v),
                        new Setting.SliderSetting("挥砍速度", 0.0, 200.0, () -> cfg.swingSpeed, v -> cfg.swingSpeed = (int) Math.round(v), "%.0f%%", 1),
                        new Setting.BooleanSetting("忽略急迫等效果", () -> cfg.swingIgnoreHaste, v -> cfg.swingIgnoreHaste = v),
                        new Setting.SliderSetting("位置 X", -1.0, 1.0, () -> cfg.handItemOffsetX, v -> cfg.handItemOffsetX = (float) v, "%.3f", 0.01),
                        new Setting.SliderSetting("位置 Y", -1.0, 1.0, () -> cfg.handItemOffsetY, v -> cfg.handItemOffsetY = (float) v, "%.3f", 0.01),
                        new Setting.SliderSetting("位置 Z", -1.0, 1.0, () -> cfg.handItemOffsetZ, v -> cfg.handItemOffsetZ = (float) v, "%.3f", 0.01),
                        new Setting.SliderSetting("旋转 X", -180.0, 180.0, () -> cfg.handItemRotateX, v -> cfg.handItemRotateX = (float) v, "%.0f", 1),
                        new Setting.SliderSetting("旋转 Y", -180.0, 180.0, () -> cfg.handItemRotateY, v -> cfg.handItemRotateY = (float) v, "%.0f", 1),
                        new Setting.SliderSetting("旋转 Z", -180.0, 180.0, () -> cfg.handItemRotateZ, v -> cfg.handItemRotateZ = (float) v, "%.0f", 1)
                )));
    }

    private static void registerClock(ModConfig cfg) {
        ModuleRegistry.register(new Module("clock", "时钟", Category.HUD,
                () -> cfg.clockEnabled, v -> cfg.clockEnabled = v,
                List.of(
                        new Setting.BooleanSetting("24 小时制", () -> cfg.clockFormat24h, v -> cfg.clockFormat24h = v),
                        new Setting.SliderSetting("时钟大小", 0.25, 6.0, () -> cfg.clockScale, v -> cfg.clockScale = (float) v, "%.2f", ModConfig.SCALE_GRID)
                )));
    }

    private static void registerDayCount(ModConfig cfg) {
        ModuleRegistry.register(new Module("day_count", "世界天数", Category.HUD,
                () -> cfg.dayCountEnabled, v -> cfg.dayCountEnabled = v,
                List.of(
                        new Setting.SliderSetting("大小", 0.25, 6.0, () -> cfg.dayCountScale, v -> cfg.dayCountScale = (float) v, "%.2f", ModConfig.SCALE_GRID)
                )));
    }

    private static void registerFpsTpsPing(ModConfig cfg) {
        ModuleRegistry.register(new Module("fps_tps_ping", "FPS/TPS/Ping", Category.HUD,
                () -> cfg.fpsTpsPingEnabled, v -> cfg.fpsTpsPingEnabled = v,
                List.of(
                        new Setting.BooleanSetting("FPS", () -> cfg.fpsTpsPingShowFps, v -> cfg.fpsTpsPingShowFps = v),
                        new Setting.BooleanSetting("TPS", () -> cfg.fpsTpsPingShowTps, v -> cfg.fpsTpsPingShowTps = v),
                        new Setting.BooleanSetting("Ping", () -> cfg.fpsTpsPingShowPing, v -> cfg.fpsTpsPingShowPing = v),
                        new Setting.SliderSetting("大小", 0.25, 6.0, () -> cfg.fpsTpsPingScale, v -> cfg.fpsTpsPingScale = (float) v, "%.2f", ModConfig.SCALE_GRID),
                        new Setting.ActionSetting(
                                () -> Localization.tr(cfg.fpsTpsPingVertical ? "切换到横向排版" : "切换到竖向排版"),
                                () -> cfg.fpsTpsPingVertical = !cfg.fpsTpsPingVertical)
                )));
    }

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
    private static void rebuildPanelAfterStyleChange() {
        Minecraft mc = Minecraft.getInstance();
        mc.schedule(() -> {
            if (mc.gui.screen() instanceof OneClickScreen screen) {
                screen.rebuildAfterSettingChange();
            }
        });
    }

    /**
     * The vanilla held item tooltip - the item name that fades in above the hotbar after the selected
     * slot changes - made movable and sizable.
     *
     * <p>A general HUD feature rather than a SkyBlock one: it is pure vanilla furniture that is simply
     * the wrong size on a large GUI, so it belongs in {@link Category#HUD} next to the clock and the
     * FPS readout, not under {@code SKYBLOCK_HUD}. Its real controls are the position editor's drag and
     * wheel; the offsets are also exposed here because they are the only way to bring it back after
     * dragging it somewhere silly.
     */
    private static void registerHeldItemName(ModConfig cfg) {
        ModuleRegistry.register(new Module("held_item_name", "手持物品名", Category.HUD,
                () -> cfg.heldItemNameEnabled, v -> cfg.heldItemNameEnabled = v,
                List.of(
                        new Setting.SliderSetting("缩放", 0.25, 4.0, () -> cfg.heldItemNameScale,
                                v -> cfg.heldItemNameScale = (float) v, "%.2f", ModConfig.SCALE_GRID),
                        new Setting.SliderSetting("水平偏移", -400, 400, () -> cfg.heldItemNameOffsetX,
                                v -> cfg.heldItemNameOffsetX = (int) Math.round(v), "%.0f", 1),
                        new Setting.SliderSetting("垂直偏移", -400, 400, () -> cfg.heldItemNameOffsetY,
                                v -> cfg.heldItemNameOffsetY = (int) Math.round(v), "%.0f", 1))));
    }

    private static void registerStatsDisplay(ModConfig cfg) {
        ModuleRegistry.register(new Module("stats_display", "玩家数值显示", Category.SKYBLOCK_HUD,
                () -> cfg.statsEnabled, v -> cfg.statsEnabled = v,
                List.of(
                        new Setting.Header("风格", "经典沿用原版图标，现代改用条；切换后只显示对应风格的设置"),
                        new Setting.ChoiceSetting("风格", List.of("经典", "现代"),
                                () -> cfg.statsModernStyle ? 1 : 0,
                                index -> {
                                    cfg.statsModernStyle = index == 1;
                                    rebuildPanelAfterStyleChange();
                                }),
                        // Vanilla HUD is its own card: the style switch above only chooses the module's
                        // presentation and must not visually own the vanilla-element toggles.
                        new Setting.Header("原版 HUD", "隐藏原版元素，避免和本模块重复绘制"),
                        new Setting.BooleanSetting("隐藏原版生命条",
                                () -> cfg.statsHideVanillaHealth,
                                v -> cfg.statsHideVanillaHealth = v)
                                .hint("本模块会自己画血量，留着会两层叠在一起"),
                        new Setting.BooleanSetting("隐藏原版饥饿条",
                                () -> cfg.statsHideVanillaFood,
                                v -> cfg.statsHideVanillaFood = v)
                                .hint("饥饿那一行被法力占用"),
                        new Setting.BooleanSetting("隐藏原版空气泡",
                                () -> cfg.statsHideVanillaAir,
                                v -> cfg.statsHideVanillaAir = v)
                                .hint("它会正好落在法力的第二行上"),
                        new Setting.BooleanSetting("隐藏原版盔甲条",
                                () -> cfg.statsHideVanillaArmor,
                                v -> cfg.statsHideVanillaArmor = v)
                                .hint("它和本模块的布局不冲突，所以默认留着"),
                        new Setting.BooleanSetting("隐藏原版动作栏",
                                () -> cfg.statsHideVanillaActionBar,
                                v -> cfg.statsHideVanillaActionBar = v)
                                .hint("只隐藏绘制：数值仍然在读取，显示照常更新"),
                        // Style-independent rows come first, style-specific blocks last: otherwise a
                        // conditional block sat between two unconditional ones and switching the style
                        // shifted everything below it, including rows that have nothing to do with it.
                        new Setting.Header("颜色", "现代风格的条与经典风格的数值文字共用同一套"),
                        new Setting.ColorSetting("生命颜色", () -> cfg.statsHealthColor,
                                v -> cfg.statsHealthColor = v),
                        new Setting.ColorSetting("吸收颜色", () -> cfg.statsAbsorptionColor,
                                v -> cfg.statsAbsorptionColor = v),
                        new Setting.ColorSetting("法力颜色", () -> cfg.statsManaColor,
                                v -> cfg.statsManaColor = v),
                        new Setting.ColorSetting("溢出法力颜色", () -> cfg.statsOverflowColor,
                                v -> cfg.statsOverflowColor = v),
                        new Setting.ColorSetting("空气颜色", () -> cfg.statsAirColor,
                                v -> cfg.statsAirColor = v),
                        new Setting.ColorSetting("防御颜色", () -> cfg.statsDefenseColor,
                                v -> cfg.statsDefenseColor = v),
                        new Setting.ColorSetting("速度颜色", () -> cfg.statsSpeedColor,
                                v -> cfg.statsSpeedColor = v),
                        new Setting.ColorSetting("活力颜色", () -> cfg.statsVitalityColor,
                                v -> cfg.statsVitalityColor = v),
                        // Classic-only: the modern style renders these values inside its bars, so
                        // separate text rows would duplicate the same responsibility.
                        new Setting.Header("文字行", "仅经典风格；现代风格的条已经直接显示这些数值")
                                .visibleWhen(() -> !cfg.statsModernStyle),
                        new Setting.BooleanSetting("防御文字", () -> cfg.statsDefenseEnabled,
                                v -> cfg.statsDefenseEnabled = v)
                                .visibleWhen(() -> !cfg.statsModernStyle),
                        new Setting.BooleanSetting("速度文字", () -> cfg.statsSpeedEnabled,
                                v -> cfg.statsSpeedEnabled = v)
                                .visibleWhen(() -> !cfg.statsModernStyle),
                        new Setting.BooleanSetting("活力文字", () -> cfg.statsVitalityEnabled,
                                v -> cfg.statsVitalityEnabled = v)
                                .visibleWhen(() -> !cfg.statsModernStyle),
                        new Setting.SliderSetting("防御文字大小", 0.25, 6.0, () -> cfg.statsDefenseScale,
                                v -> cfg.statsDefenseScale = (float) v, "%.2f", ModConfig.SCALE_GRID)
                                .visibleWhen(() -> !cfg.statsModernStyle),
                        new Setting.SliderSetting("速度文字大小", 0.25, 6.0, () -> cfg.statsSpeedScale,
                                v -> cfg.statsSpeedScale = (float) v, "%.2f", ModConfig.SCALE_GRID)
                                .visibleWhen(() -> !cfg.statsModernStyle),
                        new Setting.SliderSetting("活力文字大小", 0.25, 6.0, () -> cfg.statsVitalityScale,
                                v -> cfg.statsVitalityScale = (float) v, "%.2f", ModConfig.SCALE_GRID)
                                .visibleWhen(() -> !cfg.statsModernStyle),
                        new Setting.Header("经典风格", "原版图标 + 精确数值；两行文字的位置在「HUD 位置调整」页里拖")
                                .visibleWhen(() -> !cfg.statsModernStyle),
                        // Classic-only: blink the mana container when mana runs low.
                        new Setting.BooleanSetting("法力不足时闪烁", () -> cfg.statsManaBlink,
                                v -> cfg.statsManaBlink = v)
                                .hint("剩余不足两格时按原版节奏闪烁")
                                .visibleWhen(() -> !cfg.statsModernStyle),
                        new Setting.BooleanSetting("生命数值", () -> cfg.statsHealthTextEnabled,
                                v -> cfg.statsHealthTextEnabled = v)
                                .hint("20 格图标表达不了一个点；这里给出精确值与吸收量")
                                .visibleWhen(() -> !cfg.statsModernStyle),
                        new Setting.SliderSetting("生命数值大小", 0.25, 6.0, () -> cfg.statsHealthTextScale,
                                v -> cfg.statsHealthTextScale = (float) v, "%.2f", ModConfig.SCALE_GRID)
                                .visibleWhen(() -> !cfg.statsModernStyle),
                        new Setting.BooleanSetting("法力数值", () -> cfg.statsManaTextEnabled,
                                v -> cfg.statsManaTextEnabled = v)
                                .hint("精确值与溢出法力")
                                .visibleWhen(() -> !cfg.statsModernStyle),
                        new Setting.SliderSetting("法力数值大小", 0.25, 6.0, () -> cfg.statsManaTextScale,
                                v -> cfg.statsManaTextScale = (float) v, "%.2f", ModConfig.SCALE_GRID)
                                .visibleWhen(() -> !cfg.statsModernStyle),
                        // Modern-only: the geometry all six bars share, then a switch and a size for
                        // each. Position comes from the HUD position editor, where every bar is its
                        // own target; these rows are for switching them on and for sizing them
                        // without leaving the panel.
                        new Setting.Header("现代风格 · 条",
                                "位置在「HUD 位置调整」页里逐条拖拽与缩放，这里只管粗细与开关")
                                .visibleWhen(() -> cfg.statsModernStyle),
                        new Setting.BooleanSetting("条上显示数值", () -> cfg.statsBarShowValues,
                                v -> cfg.statsBarShowValues = v)
                                .visibleWhen(() -> cfg.statsModernStyle),
                        new Setting.BooleanSetting("生命条", () -> cfg.statsHealthBarEnabled,
                                v -> cfg.statsHealthBarEnabled = v)
                                .hint("吸收血量作为第二层叠在它上面")
                                .visibleWhen(() -> cfg.statsModernStyle),
                        new Setting.SliderSetting("生命条大小", 0.25, 6.0, () -> cfg.statsHealthBarScale,
                                v -> cfg.statsHealthBarScale = (float) v, "%.2f", ModConfig.SCALE_GRID)
                                .visibleWhen(() -> cfg.statsModernStyle),
                        new Setting.BooleanSetting("法力条", () -> cfg.statsManaBarEnabled,
                                v -> cfg.statsManaBarEnabled = v)
                                .visibleWhen(() -> cfg.statsModernStyle),
                        new Setting.SliderSetting("法力条大小", 0.25, 6.0, () -> cfg.statsManaBarScale,
                                v -> cfg.statsManaBarScale = (float) v, "%.2f", ModConfig.SCALE_GRID)
                                .visibleWhen(() -> cfg.statsModernStyle),
                        new Setting.BooleanSetting("防御条", () -> cfg.statsDefenseBarEnabled,
                                v -> cfg.statsDefenseBarEnabled = v)
                                .visibleWhen(() -> cfg.statsModernStyle),
                        new Setting.SliderSetting("防御条大小", 0.25, 6.0, () -> cfg.statsDefenseBarScale,
                                v -> cfg.statsDefenseBarScale = (float) v, "%.2f", ModConfig.SCALE_GRID)
                                .visibleWhen(() -> cfg.statsModernStyle),
                        new Setting.BooleanSetting("速度条", () -> cfg.statsSpeedBarEnabled,
                                v -> cfg.statsSpeedBarEnabled = v)
                                .visibleWhen(() -> cfg.statsModernStyle),
                        new Setting.SliderSetting("速度条大小", 0.25, 6.0, () -> cfg.statsSpeedBarScale,
                                v -> cfg.statsSpeedBarScale = (float) v, "%.2f", ModConfig.SCALE_GRID)
                                .visibleWhen(() -> cfg.statsModernStyle),
                        new Setting.BooleanSetting("活力条", () -> cfg.statsVitalityBarEnabled,
                                v -> cfg.statsVitalityBarEnabled = v)
                                .visibleWhen(() -> cfg.statsModernStyle),
                        new Setting.SliderSetting("活力条大小", 0.25, 6.0, () -> cfg.statsVitalityBarScale,
                                v -> cfg.statsVitalityBarScale = (float) v, "%.2f", ModConfig.SCALE_GRID)
                                .visibleWhen(() -> cfg.statsModernStyle),
                        new Setting.BooleanSetting("空气条", () -> cfg.statsAirBarEnabled,
                                v -> cfg.statsAirBarEnabled = v)
                                .visibleWhen(() -> cfg.statsModernStyle),
                        new Setting.SliderSetting("空气条大小", 0.25, 6.0, () -> cfg.statsAirBarScale,
                                v -> cfg.statsAirBarScale = (float) v, "%.2f", ModConfig.SCALE_GRID)
                                .visibleWhen(() -> cfg.statsModernStyle),
                        // Rarely needed, so it lives in a folded section rather than taking a row of
                        // its own on a page that is already long.
                        new Setting.GroupSetting("高级", List.of(
                                new Setting.ActionSetting(() -> Localization.tr("重置条位置"),
                                        () -> StatsBarHudElement.resetToDefaults(cfg)),
                                new Setting.ActionSetting(() -> Localization.tr("恢复默认颜色"),
                                        () -> {
                                            cfg.statsHealthColor = dev.skypocket.config.ColorDefaults.STATS_HEALTH;
                                            cfg.statsAbsorptionColor = dev.skypocket.config.ColorDefaults.STATS_ABSORPTION;
                                            cfg.statsManaColor = dev.skypocket.config.ColorDefaults.STATS_MANA;
                                            cfg.statsOverflowColor = dev.skypocket.config.ColorDefaults.STATS_OVERFLOW;
                                            cfg.statsAirColor = dev.skypocket.config.ColorDefaults.STATS_AIR;
                                            cfg.statsDefenseColor = dev.skypocket.config.ColorDefaults.STATS_DEFENSE;
                                            cfg.statsSpeedColor = dev.skypocket.config.ColorDefaults.STATS_SPEED;
                                            cfg.statsVitalityColor = dev.skypocket.config.ColorDefaults.STATS_VITALITY;
                                            rebuildPanelAfterStyleChange();
                                        })), false)
                                .visibleWhen(() -> cfg.statsModernStyle)
                )));
    }

    private static void registerAutoSprint(ModConfig cfg) {
        ModuleRegistry.register(new Module("auto_sprint", "自动疾跑", Category.MOVEMENT,
                () -> cfg.autoSprintEnabled, v -> cfg.autoSprintEnabled = v, List.of()));
    }
}
