package dev.skypocket.render;

import dev.skypocket.mixin.GameRendererAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;

/**
 * Runs one of our post-effect chains over the finished world image.
 *
 * <p>All three effects ({@link AntialiasingRenderer}, {@link ColorGradeRenderer},
 * {@link BloomRenderer}) process through here, so the "how" lives in one place: exactly the call
 * vanilla makes for its menu blur, with the same {@code GraphicsResourceAllocator}, on the main render
 * target. Where the chain comes from is {@link LivePostChain}'s business.
 */
final class WorldPostChain {
    private WorldPostChain() {
    }

    /**
     * Runs {@code chain} over the finished world image; a null chain (not built yet, or failed to
     * load - {@link LivePostChain} logs why) does nothing.
     */
    static void process(PostChain chain) {
        if (chain == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        GameRenderer renderer = mc.gameRenderer;
        chain.process(renderer.mainRenderTarget(), ((GameRendererAccessor) renderer).sky$resourcePool());
    }
}
