package dev.skypocket.render;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.UniformValue;

import java.util.List;
import java.util.Map;

/**
 * Colour grading over the finished world image - the cheap half of a "shader pack look": one
 * full-screen pass that shifts exposure, temperature, contrast, lift/gamma, saturation and adds a
 * vignette (see {@code shaders/post/grade.fsh}).
 *
 * <p>It costs a single pass (five taps at most) and, unlike a shader pack, adds no geometry pass, no
 * shadow map and no gbuffer targets, so its cost follows the resolution rather than the scene.
 *
 * <p>Deliberately no depth input here: a chain that fails to load draws nothing, and the depth-based
 * effects that were tried (distance fog, depth AO) have been dropped - see docs/DEVELOPMENT.md for why
 * and for what to know before attempting them again. Sharpening, which needs no depth, lives in the
 * antialiasing module's chain.
 *
 * <p>The parameters are live: the module's sliders write them into the config, and
 * {@link LivePostChain} rebuilds the chain (debounced) whenever they change. This class owns the
 * definition behind that - the parameter order the shader and the template agree on, and the presets
 * that fill all of them in at once.
 */
public final class ColorGradeRenderer {
    /** Parameter order, shared by the shader's {@code GradeConfig} block and the template JSON. */
    private static final int EXPOSURE = 0;
    private static final int CONTRAST = 1;
    private static final int SATURATION = 2;
    private static final int GAMMA = 3;
    private static final int LIFT = 4;
    private static final int TEMPERATURE = 5;
    private static final int VIGNETTE = 6;

    /** Preset ids; {@link #CUSTOM} is not a look, it is "the sliders have been touched since". */
    public static final String LIGHT = "light";
    public static final String STANDARD = "standard";
    public static final String CINEMATIC = "cinematic";
    public static final String WARM = "warm";
    public static final String CUSTOM = "custom";

    /** One-click looks: exposure, contrast, saturation, gamma, lift, temperature, vignette. */
    private static final Map<String, float[]> PRESETS = Map.of(
            LIGHT, new float[]{1.02f, 1.05f, 1.04f, 1.00f, 0.000f, 0.50f, 0.15f},
            STANDARD, new float[]{1.05f, 1.12f, 1.10f, 0.98f, 0.005f, 0.50f, 0.30f},
            CINEMATIC, new float[]{1.06f, 1.24f, 0.92f, 0.95f, 0.012f, 0.50f, 0.45f},
            WARM, new float[]{1.05f, 1.10f, 1.14f, 1.00f, 0.000f, 0.78f, 0.28f});

    private static final LivePostChain CHAIN =
            new LivePostChain("post_effect/grade.json", "GradeConfig");

    private ColorGradeRenderer() {
    }

    /** Preset ids for the module's dropdown, {@link #CUSTOM} last. */
    public static List<String> presets() {
        return List.of(LIGHT, STANDARD, CINEMATIC, WARM, CUSTOM);
    }

    /**
     * Writes a preset's parameters into {@code cfg}. {@link #CUSTOM} is recorded as-is without
     * touching any value - it is what the sliders set once they are moved away from a preset.
     */
    public static void applyPreset(ModConfig cfg, String id) {
        float[] preset = PRESETS.get(id);
        if (preset == null) {
            cfg.colorGradePreset = CUSTOM;
            return;
        }
        cfg.colorGradeExposure = preset[EXPOSURE];
        cfg.colorGradeContrast = preset[CONTRAST];
        cfg.colorGradeSaturation = preset[SATURATION];
        cfg.colorGradeGamma = preset[GAMMA];
        cfg.colorGradeLift = preset[LIFT];
        cfg.colorGradeTemperature = preset[TEMPERATURE];
        cfg.colorGradeVignette = preset[VIGNETTE];
        cfg.colorGradePreset = id;
    }

    /** Called once per frame, right after the level and before the HUD/GUI. */
    public static void process() {
        ModConfig cfg = ConfigManager.get();
        if (!cfg.postProcessEnabled || !cfg.colorGradeEnabled) {
            return;
        }
        // Track the parameters before the skip check: the sliders are dragged with a screen open, and
        // the chain has to be current by the time that screen closes even if the pass is skipped.
        CHAIN.apply(parameters(cfg));
        if (cfg.colorGradeSkipScreens && Minecraft.getInstance().gui.screen() != null) {
            return;
        }
        WorldPostChain.process(CHAIN.chain());
    }

    /** The current parameters, in the order the shader and the template expect. */
    private static List<UniformValue> parameters(ModConfig cfg) {
        return LivePostChain.floats(cfg.colorGradeExposure, cfg.colorGradeContrast,
                cfg.colorGradeSaturation, cfg.colorGradeGamma, cfg.colorGradeLift,
                cfg.colorGradeTemperature, cfg.colorGradeVignette);
    }
}
