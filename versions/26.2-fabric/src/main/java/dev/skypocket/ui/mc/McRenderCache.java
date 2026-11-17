package dev.skypocket.ui.mc;

import dev.skypocket.render.GuiPanelCache;
import dev.skypocket.render.ScissorTracker;
import dev.skypocket.ui.RenderCache;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Binds {@link RenderCache} to the game's panel-cache implementation.
 *
 * <p>Lives in {@code ui.mc} because that is the package for platform bindings: the kernel declares
 * the contract and this class supplies the Minecraft-backed one. Keeping the adapter here is what
 * lets {@code UiPage} and the colour picker stay free of {@code dev.skypocket.render}.
 */
public final class McRenderCache implements RenderCache {
    private final GuiPanelCache.Slot slot = new GuiPanelCache.Slot();

    public static McRenderCache create() {
        return new McRenderCache();
    }

    @Override
    public boolean draw(GuiGraphicsExtractor graphics, long key,
                        int width, int height,
                        float scaleX, float scaleY,
                        boolean animating,
                        Content content,
                        int mouseX, int mouseY, float delta) {
        return slot.draw(graphics, key, width, height, scaleX, scaleY, animating,
                (bakeGraphics, mx, my, d) -> {
                    // A bake records into its own extractor, so the scissor tracker starts empty for
                    // it. Doing it here rather than in the caller keeps the whole bake sequence -
                    // start, record, restore - inside the platform layer.
                    ScissorTracker.reset(bakeGraphics);
                    content.drawContent(bakeGraphics, mx, my, d);
                }, mouseX, mouseY, delta);
    }

    @Override
    public float lastBlitScaleX() {
        return slot.lastBlitScaleX();
    }

    @Override
    public float lastBlitScaleY() {
        return slot.lastBlitScaleY();
    }

    @Override
    public void bakePending() {
        slot.bakePending();
    }

    @Override
    public void release() {
        slot.release();
    }
}
