package dev.skypocket.feature.modules;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;
import dev.skypocket.feature.FeatureModule;
import dev.skypocket.gui.Category;
import dev.skypocket.gui.OneClickScreen;
import dev.skypocket.gui.setting.Setting;
import dev.skypocket.render.AntialiasingRenderer;
import dev.skypocket.render.ColorGradeRenderer;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/**
 * All full-screen post-processing passes over the finished world image, as one feature with a group
 * per effect, in the order they actually run (see {@code GameRendererMixin}).
 *
 * <p>The module's own switch is the master: off means the whole stack is off. Each group's switch is
 * that effect's existing enable flag, so a config written before the merge keeps meaning exactly what
 * it did, and the per-effect renderers still read the same fields.
 *
 * <p>The four effect helpers live here rather than in {@code ModuleDefinitions}: they belong to this
 * feature, and keeping them private means the next feature cannot borrow one by accident.
 */
public final class PostProcessFeature implements FeatureModule {

    @Override
    public String id() {
        return "post_process";
    }

    @Override
    public String name() {
        return "后处理";
    }

    @Override
    public Category category() {
        return Category.RENDER;
    }

    @Override
    public boolean isEnabled() {
        return ConfigManager.get().postProcessEnabled;
    }

    @Override
    public void setEnabled(boolean value) {
        ConfigManager.get().postProcessEnabled = value;
    }

    @Override
    public List<Setting> settings() {
        ModConfig cfg = ConfigManager.get();
        List<Setting> settings = new ArrayList<>();
        settings.add(new Setting.GroupSetting("抗锯齿", () -> cfg.antialiasingEnabled,
                v -> cfg.antialiasingEnabled = v, antialiasingSettings(cfg))
                .subtitle("平滑边缘，并按强度调整锐化"));
        settings.add(new Setting.GroupSetting("动态模糊", () -> cfg.motionBlurEnabled,
                v -> cfg.motionBlurEnabled = v, motionBlurSettings(cfg))
                .subtitle("移动视角时加入方向性模糊"));
        settings.add(new Setting.GroupSetting("画面调色", () -> cfg.colorGradeEnabled,
                v -> cfg.colorGradeEnabled = v, colorGradeSettings(cfg))
                .subtitle("调整画面亮度、对比度、饱和度与色调"));
        settings.add(new Setting.GroupSetting("泛光", () -> cfg.bloomEnabled,
                v -> cfg.bloomEnabled = v, bloomSettings(cfg))
                .subtitle("让高亮区域产生柔和光晕"));
        return settings;
    }

    /**
     * Anti-aliasing as a post pass (see {@code AntialiasingRenderer}): no SkyBlock gate, it is a plain
     * render feature. The strength picks a parameter set, and the sharpening slider rides along in the
     * same chain as its last pass - after FXAA, which is what lets it put back the contrast the filter
     * takes out of small text.
     */
    private static List<Setting> antialiasingSettings(ModConfig cfg) {
        List<Setting> settings = new ArrayList<>();
        settings.add(new Setting.DropdownSetting("强度", AntialiasingRenderer::presets,
                () -> cfg.antialiasingStrength, v -> cfg.antialiasingStrength = v));
        settings.add(effectParameter("锐化", 0.00, 1.50, 0.05, "%.2f",
                () -> cfg.antialiasingSharpen, v -> cfg.antialiasingSharpen = (float) v));
        settings.add(new Setting.BooleanSetting("光影下也生效",
                () -> cfg.antialiasingWithShaders, v -> cfg.antialiasingWithShaders = v));
        settings.add(new Setting.BooleanSetting("开界面时跳过",
                () -> cfg.antialiasingSkipScreens, v -> cfg.antialiasingSkipScreens = v));
        return settings;
    }

    /**
     * Colour grading (see {@code ColorGradeRenderer}): the cheap half of a shader pack's look as one
     * post pass. The parameters are live - the chain is rebuilt (debounced, see {@code LivePostChain})
     * whenever one changes - so the dropdown writes a whole preset at once and the sliders fine-tune it.
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
        settings.add(new Setting.BooleanSetting("开界面时跳过",
                () -> cfg.colorGradeSkipScreens, v -> cfg.colorGradeSkipScreens = v));
        return settings;
    }

    /**
     * Motion blur (see {@code MotionBlurRenderer}): camera reprojection against the previous frame. The
     * amount is an exposure time in milliseconds rather than a per-frame factor, which is what keeps the
     * look the same at any frame rate. Off by default - it is a look, not a correction.
     */
    private static List<Setting> motionBlurSettings(ModConfig cfg) {
        List<Setting> settings = new ArrayList<>();
        settings.add(effectParameter("强度", 0.00, 1.00, 0.05, "%.2f",
                () -> cfg.motionBlurStrength, v -> cfg.motionBlurStrength = (float) v));
        settings.add(effectParameter("曝光", 1, 50, 1, "%.0f",
                () -> cfg.motionBlurExposureMs, v -> cfg.motionBlurExposureMs = (float) v));
        settings.add(effectParameter("最大半径", 4, 64, 2, "%.0f",
                () -> cfg.motionBlurMaxRadius, v -> cfg.motionBlurMaxRadius = (float) v));
        settings.add(new Setting.BooleanSetting("开界面时跳过",
                () -> cfg.motionBlurSkipScreens, v -> cfg.motionBlurSkipScreens = v));
        settings.add(new Setting.BooleanSetting("调试：显示速度",
                () -> cfg.motionBlurDebug, v -> cfg.motionBlurDebug = v));
        return settings;
    }

    /**
     * Bloom (see {@code BloomRenderer}): a bright pass that already blurs, a composite and a blit. Its
     * own group so switching it off costs nothing - the passes only exist while it is on.
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
     * One of the seven colour parameters. Moving it also marks the state as "custom": those values no
     * longer match the preset the dropdown names, and saying so is cheaper than leaving the two out of
     * step.
     */
    private static Setting.SliderSetting colorParameter(ModConfig cfg, String label, double min,
                                                        double max, double step, String format,
                                                        DoubleSupplier getter, DoubleConsumer setter) {
        return new Setting.SliderSetting(label, min, max, getter, v -> {
            setter.accept(v);
            cfg.colorGradePreset = ColorGradeRenderer.CUSTOM;
        }, format, step);
    }

    /** One of the parameters no preset owns (sharpen, motion blur, bloom). */
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
}
