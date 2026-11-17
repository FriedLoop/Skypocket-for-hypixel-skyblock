package dev.skypocket.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * A texture cache for a rendered element tree.
 *
 * <p>The kernel needs to bake a page into a texture and blit it back while nothing changed, but it
 * must not know how that texture is produced - the implementation is deep in the render package and
 * depends on the game's own render targets. This interface is the seam: {@code UiPage} and the
 * colour picker work against it, and the render package supplies the implementation.
 *
 * <p>Deliberately narrow. Everything here is what a caller needs to draw and to convert input: the
 * exact scale the texture was blitted at has to be reused for live overlays, or a popup sits a pixel
 * off the cached row it belongs to.
 */
public interface RenderCache {
    /**
     * Draws the cached texture, or re-bakes it through {@code content} when the key changed.
     *
     * @return true when the cached texture was reused, false when {@code content} ran
     */
    boolean draw(GuiGraphicsExtractor graphics, long key,
                 int width, int height,
                 float scaleX, float scaleY,
                 boolean animating,
                 Content content,
                 int mouseX, int mouseY, float delta);

    /** Scale the last blit used on each axis. */
    float lastBlitScaleX();

    float lastBlitScaleY();

    /** Re-bakes whatever is pending; call once per frame after the tree has been recorded. */
    void bakePending();

    /** Drops the cached texture. */
    void release();

    /** What to draw when the cache misses. */
    @FunctionalInterface
    interface Content {
        void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta);
    }
}
