package dev.skypocket.mixin;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;
import dev.skypocket.net.SkyBlockDetector;
import net.minecraft.client.renderer.entity.EnderDragonRenderer;
import net.minecraft.client.renderer.entity.state.EnderDragonRenderState;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Overrides the Ender Dragon's glow outline color with the configured one. The glow
 * itself is forced on in {@link EntityMixin#sky$glowDragon}; this mixin only recolors
 * the outline (the dragon renderer is not a {@code LivingEntityRenderer}, so it needs
 * its own mixin).
 */
@Mixin(EnderDragonRenderer.class)
public abstract class EnderDragonRendererMixin {

    @Inject(method = "extractRenderState", at = @At("TAIL"), require = 0)
    private void sky$setDragonGlowColor(EnderDragon dragon, EnderDragonRenderState state, float partialTick, CallbackInfo ci) {
        ModConfig cfg = ConfigManager.get();
        if (cfg.dragonGlowEnabled && SkyBlockDetector.isActive()) {
            state.outlineColor = cfg.dragonGlowColor;
        }
    }
}
