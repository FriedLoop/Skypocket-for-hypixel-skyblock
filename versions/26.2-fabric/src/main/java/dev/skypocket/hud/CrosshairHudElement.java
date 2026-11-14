package dev.skypocket.hud;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;
import dev.skypocket.render.CrosshairRenderer;
import dev.skypocket.render.CrosshairTargetClassifier;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Replaces the vanilla crosshair with the custom solid-color 15x15 grid.
 *
 * <p>26.2 exposes the vanilla crosshair as a registered HUD element, so we simply
 * swap it via {@code HudElementRegistry.replaceElement} - no mixin needed, and the
 * element inherits the vanilla crosshair's render conditions.
 */
public final class CrosshairHudElement {

    private CrosshairHudElement() {
    }

    public static void register() {
        // Capture the vanilla crosshair element so it can be restored when the
        // custom crosshair is toggled off (replaceElement swaps it permanently).
        HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR,
                vanilla -> (graphics, delta) -> render(graphics, delta, vanilla));
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, HudElement vanilla) {
        ModConfig config = ConfigManager.get();
        if (!config.crosshairBlock.crosshairEnabled) {
            // Disabled: delegate to the vanilla crosshair (keeps its own render conditions).
            vanilla.extractRenderState(graphics, deltaTracker);
            return;
        }

        int centerX = graphics.guiWidth() / 2;
        int centerY = graphics.guiHeight() / 2;
        int pixel = Math.max(1, Math.round(config.crosshairBlock.crosshairPixelSize));

        int color = config.getCrosshairColorARGB();
        if (config.crosshairBlock.crosshairEntityColorEnabled) {
            CrosshairTargetClassifier.Kind kind = CrosshairTargetClassifier.classify(Minecraft.getInstance());
            color = switch (kind) {
                case FRIENDLY -> config.crosshairBlock.crosshairFriendlyColor;
                case NEUTRAL -> config.crosshairBlock.crosshairNeutralColor;
                case HOSTILE -> config.crosshairBlock.crosshairHostileColor;
                case CONTAINER -> config.crosshairBlock.crosshairContainerColor;
                default -> color;
            };
        }

        CrosshairRenderer.render(graphics, centerX, centerY, pixel, config.crosshairBlock.crosshairScale,
                config.crosshairBlock.crosshairGrid, color, config.crosshairBlock.crosshairStroke,
                config.crosshairBlock.crosshairStrokeWidth);
    }
}
