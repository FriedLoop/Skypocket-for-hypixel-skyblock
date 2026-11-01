package dev.skypocket.render;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;
import net.minecraft.client.renderer.UniformValue;

import java.util.List;

/**
 * Bloom (a glow around bright areas) over the finished world image, as one chain of three passes:
 * a bright pass that already blurs (see {@code post/bloom_pre.fsh}), a composite that adds it back,
 * and a blit into the main target (a pass cannot read and write {@code minecraft:main} at once).
 *
 * <p>Kept in its own module rather than folded into the colour grade so that disabling it costs
 * nothing: the passes only exist while it is on, and its three sliders are live through
 * {@link LivePostChain} exactly like the grade's.
 *
 * <p>It runs <em>after</em> the grade on purpose: the bright pass then sees the image the player
 * actually looks at, so raising the grade's exposure makes things bloom.
 */
public final class BloomRenderer {
    /** Parameter order, shared by the shader's {@code BloomConfig} block and the template JSON. */
    private static final int STRENGTH = 0;
    private static final int THRESHOLD = 1;
    private static final int RADIUS = 2;

    private static final LivePostChain CHAIN =
            new LivePostChain("post_effect/bloom.json", "BloomConfig");

    private BloomRenderer() {
    }

    /** Called once per frame, after {@link ColorGradeRenderer}. */
    public static void process() {
        ModConfig cfg = ConfigManager.get();
        if (!cfg.postProcessEnabled || !cfg.bloomEnabled) {
            return;
        }
        CHAIN.apply(parameters(cfg));
        WorldPostChain.process(CHAIN.chain());
    }

    /** The current parameters, in the order the shaders and the template expect. */
    private static List<UniformValue> parameters(ModConfig cfg) {
        return LivePostChain.floats(cfg.bloomStrength, cfg.bloomThreshold, cfg.bloomRadius);
    }
}
