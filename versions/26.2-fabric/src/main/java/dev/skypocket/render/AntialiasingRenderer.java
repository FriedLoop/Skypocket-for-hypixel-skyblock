package dev.skypocket.render;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.UniformValue;

import java.util.List;
import java.util.Map;

/**
 * FXAA (plus an optional unsharp mask) over the finished world image, as a post-effect chain of two
 * passes: {@code post/fxaa} into an internal target, then {@code post/sharpen} straight into the main
 * target.
 *
 * <p><b>Why a post pass instead of a shader pack's AA.</b> Shader packs implement AA (usually TAA)
 * <em>during</em> rendering, so every pixel has to be classified - and anything they cannot classify
 * is dropped: other mods' block highlights and world-space text have no gbuffer entry and no motion
 * vector, which is why they vanish. This chain only reads the composited image and writes it back, so
 * nothing that was drawn can disappear; the price is that everything gets slightly softer, including
 * those highlights.
 *
 * <p>It runs after the level and before the HUD/GUI (see {@code GameRendererMixin}: {@code renderLevel}
 * TAIL), which also means the crosshair, the hotbar and the Click GUI are never touched by it - but
 * <em>world-space</em> text is (floating TextDisplay labels, entity nametags): it is part of the world
 * image. FXAA is at its worst on exactly that, which is what the parameters and the pass order here
 * are tuned for - see {@link #PRESETS} and the shaders' comments.
 *
 * <p>The chain is data ({@code assets/skypocket/post_effect/antialiasing.json}) and its four
 * parameters are live through {@link LivePostChain}: the three strength presets and the sharpening
 * slider all write into the same uniform block, so switching a preset or dragging the slider rebuilds
 * the chain (debounced) rather than swapping in a different file.
 */
public final class AntialiasingRenderer {
    /** Strength ids; the dropdown shows them as-is. */
    public static final String LIGHT = "light";
    public static final String STANDARD = "standard";
    public static final String STRONG = "strong";

    /** Parameter order, shared by the shaders' {@code FxaaConfig} block and the template JSON. */
    private static final int EDGE_THRESHOLD = 0;
    private static final int EDGE_THRESHOLD_MIN = 1;
    private static final int SUBPIX = 2;
    private static final int SHARPEN = 3;

    /**
     * One-click strengths: edge threshold, threshold floor, sub-pixel amount.
     *
     * <p>All three are noticeably more conservative than the stock FXAA quality presets, because of
     * what they do to thin, high-contrast features - and the most common one on screen is small text.
     * FXAA hits it twice over: the edge filter engages <em>because</em> a glyph stroke is high
     * contrast, and then the sub-pixel term replaces it with the 9-tap average <em>because</em> a
     * stroke is thinner than the filter can follow. The thresholds decide how much of the image is
     * treated at all (a stroke's own luma range is small in absolute terms even when its contrast
     * relative to its neighbourhood is large, which is what {@code EdgeThresholdMin} filters on), and
     * {@code Subpix} caps how much of the average is mixed in - the single knob that trades text
     * legibility against shimmer on thin geometry. The relative steps between the three presets are
     * kept, so "stronger AA" still means what it says.
     */
    private static final Map<String, float[]> PRESETS = Map.of(
            LIGHT, new float[]{0.250f, 0.100f, 0.30f},
            STANDARD, new float[]{0.200f, 0.080f, 0.45f},
            STRONG, new float[]{0.150f, 0.060f, 0.60f});

    private static final LivePostChain CHAIN =
            new LivePostChain("post_effect/antialiasing.json", "FxaaConfig");

    /** Whether a shader pack loader is present at all; it cannot change while the game runs. */
    private static final boolean SHADER_MOD_LOADED = FabricLoader.getInstance().isModLoaded("iris");

    private AntialiasingRenderer() {
    }

    /** Strength ids for the module's dropdown. */
    public static List<String> presets() {
        return List.of(LIGHT, STANDARD, STRONG);
    }

    /** Called once per frame, right after the level and before the HUD/GUI. */
    public static void process() {
        ModConfig cfg = ConfigManager.get();
        if (!cfg.postProcessBlock.postProcessEnabled || !cfg.postProcessBlock.antialiasingEnabled) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (cfg.postProcessBlock.antialiasingSkipScreens && mc.gui.screen() != null) {
            // The world sits behind a screen (and behind vanilla's menu blur), where the difference
            // is invisible - so the pass is skipped and the frames cost nothing.
            return;
        }
        if (!cfg.postProcessBlock.antialiasingWithShaders && SHADER_MOD_LOADED) {
            return;
        }
        CHAIN.apply(parameters(cfg));
        WorldPostChain.process(CHAIN.chain());
    }

    /** The current parameters, in the order the shaders and the template expect. */
    private static List<UniformValue> parameters(ModConfig cfg) {
        float[] preset = PRESETS.get(cfg.postProcessBlock.antialiasingStrength);
        if (preset == null) {
            preset = PRESETS.get(STANDARD);
        }
        return LivePostChain.floats(preset[EDGE_THRESHOLD], preset[EDGE_THRESHOLD_MIN],
                preset[SUBPIX], cfg.postProcessBlock.antialiasingSharpen);
    }
}
