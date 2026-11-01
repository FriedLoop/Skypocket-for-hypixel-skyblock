package dev.skypocket.mixin;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.tooltip.TooltipScroller;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Feeds mouse-wheel scrolling into {@link TooltipScroller}. {@code MouseHandler#onScroll}
 * is the single global scroll entry point (unlike {@code Screen#mouseScrolled}, which
 * is a default method on {@code GuiEventListener} and not injectable on {@code Screen}).
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {

    @Inject(method = "onScroll", at = @At("HEAD"))
    private void sky$scrollableTooltip(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (ConfigManager.get().scrollableTooltipsEnabled && Minecraft.getInstance().gui.screen() != null) {
            TooltipScroller.scroll(vertical);
        }
    }
}
