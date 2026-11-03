package dev.skypocket.mixin;

import dev.skypocket.render.ScissorTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mirrors the GUI scissor stack for custom render states. */
@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiScreenScissorMixin {

    @Inject(method = "enableScissor", at = @At("HEAD"))
    private void sky$pushScissor(int x1, int y1, int x2, int y2, CallbackInfo ci) {
        GuiGraphicsExtractor extractor = (GuiGraphicsExtractor) (Object) this;
        ScreenRectangle local = new ScreenRectangle(x1, y1, x2 - x1, y2 - y1);
        ScissorTracker.push(local.transformAxisAligned(extractor.pose()));
    }

    @Inject(method = "disableScissor", at = @At("HEAD"))
    private void sky$popScissor(CallbackInfo ci) {
        ScissorTracker.pop();
    }
}
