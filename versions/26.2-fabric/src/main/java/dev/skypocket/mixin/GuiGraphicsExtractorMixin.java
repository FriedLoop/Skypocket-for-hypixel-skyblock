package dev.skypocket.mixin;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.tooltip.TooltipScroller;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import org.joml.Vector2ic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Makes over-tall tooltips scrollable by adjusting their final position: when the
 * tooltip is taller than the screen it is pinned to the top and shifted by the
 * {@link TooltipScroller} scroll offset instead of being cut off.
 */
@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsExtractorMixin {

    @Redirect(
            method = "tooltip",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;positionTooltip(IIIIII)Lorg/joml/Vector2ic;")
    )
    private Vector2ic sky$scrollableTooltipPosition(ClientTooltipPositioner positioner,
            int screenWidth, int screenHeight, int mouseX, int mouseY, int tooltipWidth, int tooltipHeight) {
        Vector2ic pos = positioner.positionTooltip(screenWidth, screenHeight, mouseX, mouseY, tooltipWidth, tooltipHeight);
        if (!ConfigManager.get().scrollableTooltipsBlock.scrollableTooltipsEnabled) {
            return pos;
        }
        return TooltipScroller.adjust(pos.x(), pos.y(), tooltipHeight, screenHeight, mouseX, mouseY);
    }
}
