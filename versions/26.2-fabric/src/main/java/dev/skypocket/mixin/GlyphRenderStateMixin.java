package dev.skypocket.mixin;

import com.mojang.blaze3d.textures.FilterMode;
import dev.skypocket.render.FontLab;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GlyphRenderState;
import com.mojang.blaze3d.systems.RenderSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Replaces the hard-coded nearest-neighbour font sampler while the font lab's linear sampling
 * experiment is enabled.
 *
 * <p>Vanilla builds every {@link GlyphRenderState} with
 * {@code TextureSetup.singleTextureWithLightmap(textureView, getClampToEdge(NEAREST))}. That is the
 * right choice for the bitmap UI fonts, but it makes fractional glyph positions invisible: moving a
 * glyph by less than half a texel still samples the same texel. The experiment swaps in a linear sampler
 * so fractional advances and half-pixel origins can actually be evaluated. This is a temporary diagnostic
 * switch and applies to all glyphs, not only the Click GUI font.</p>
 */
@Mixin(GlyphRenderState.class)
public abstract class GlyphRenderStateMixin {

    @Inject(method = "textureSetup", at = @At("RETURN"), cancellable = true, require = 0)
    private void sky$linearFontSampling(CallbackInfoReturnable<TextureSetup> cir) {
        if (!FontLab.linearFilter()) {
            return;
        }
        GlyphRenderState state = (GlyphRenderState) (Object) this;
        TextRenderable text = state.renderable();
        cir.setReturnValue(TextureSetup.singleTextureWithLightmap(text.textureView(),
                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)));
    }
}
