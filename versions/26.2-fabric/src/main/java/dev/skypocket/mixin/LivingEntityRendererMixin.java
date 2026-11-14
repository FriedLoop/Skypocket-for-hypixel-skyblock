package dev.skypocket.mixin;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Overrides the local player's glow outline color with the configured one. */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {

    @Inject(method = "extractRenderState", at = @At("TAIL"), require = 0)
    private void sky$setGlowColor(LivingEntity entity, LivingEntityRenderState state, float partialTick, CallbackInfo ci) {
        ModConfig cfg = ConfigManager.get();
        if (cfg.playerGlowBlock.playerGlowEnabled && entity == Minecraft.getInstance().player) {
            state.outlineColor = cfg.playerGlowBlock.playerGlowColor;
        }
    }
}
