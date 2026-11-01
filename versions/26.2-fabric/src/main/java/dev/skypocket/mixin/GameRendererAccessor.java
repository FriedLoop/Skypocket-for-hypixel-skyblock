package dev.skypocket.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes {@code GameRenderer.mainRenderTarget} so {@code GuiPanelCache} can point the
 * GUI render pass at its own offscreen texture while baking the cached panel.
 *
 * <p>The target is swapped for the duration of a single {@code GuiRenderer.render()}
 * call and restored immediately; the field itself is only ever reassigned, never
 * replaced by anything else, so MC's own use of it is unaffected.
 */
@Mixin(GameRenderer.class)
public interface GameRendererAccessor {

    @Mutable
    @Accessor("mainRenderTarget")
    void sky$setMainRenderTarget(RenderTarget target);

    /**
     * Vanilla's cross-frame resource pool. {@code PostChain.process} wants a
     * {@code GraphicsResourceAllocator} for its own temporaries, and vanilla passes exactly this one
     * for the menu blur, so the anti-aliasing pass allocates the same way it does.
     */
    @Accessor("resourcePool")
    CrossFrameResourcePool sky$resourcePool();
}
