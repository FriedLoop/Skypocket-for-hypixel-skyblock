package dev.skypocket.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the extractor's own {@link GuiRenderState} so {@code GuiPanelCache} can add a
 * blit element itself (MC's extractor can only blit with the straight-alpha pipeline, but
 * a cached panel needs the premultiplied one — see {@code GuiPanelCache.draw}).
 */
@Mixin(GuiGraphicsExtractor.class)
public interface GuiGraphicsExtractorAccessor {

    @Accessor("guiRenderState")
    GuiRenderState sky$getRenderState();

    // The scissor stack is package-private and cannot be accessed by type; the current clip is tracked
    // instead, in GuiGraphicsExtractorMixin, by observing enableScissor/disableScissor.
}
