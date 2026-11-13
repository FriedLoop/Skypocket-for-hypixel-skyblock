package dev.skypocket.mixin;

import dev.skypocket.render.ScissorTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mirrors the GUI scissor stack for custom render states.
 *
 * <p><b>These three injections are deliberately left at {@code require = 1} (the default) and must
 * stay that way.</b> They are the only ones in this package that are not cosmetic: {@code push} and
 * {@code pop} drive a stack, so applying one without the others would leave it unbalanced and the
 * Click GUI would clip the wrong rectangle for the rest of the session - a silent visual corruption
 * that is much harder to diagnose than a startup failure. A loud failure here is the correct
 * outcome if the {@code GuiGraphicsExtractor} scissor API ever changes shape.
 */
@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiScreenScissorMixin {

    /** A GuiGraphicsExtractor is created per screen/render pass; never inherit a prior screen's clip. */
    @Inject(method = "<init>", at = @At("TAIL"), require = 1)
    private void sky$resetScissor(CallbackInfo ci) {
        ScissorTracker.reset((GuiGraphicsExtractor) (Object) this);
    }

    @Inject(method = "enableScissor", at = @At("HEAD"), require = 1)
    private void sky$pushScissor(int x1, int y1, int x2, int y2, CallbackInfo ci) {
        GuiGraphicsExtractor extractor = (GuiGraphicsExtractor) (Object) this;
        ScreenRectangle local = new ScreenRectangle(x1, y1, x2 - x1, y2 - y1);
        ScissorTracker.push(extractor, local.transformAxisAligned(extractor.pose()));
    }

    @Inject(method = "disableScissor", at = @At("HEAD"), require = 1)
    private void sky$popScissor(CallbackInfo ci) {
        ScissorTracker.pop((GuiGraphicsExtractor) (Object) this);
    }
}
