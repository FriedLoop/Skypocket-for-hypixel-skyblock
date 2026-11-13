package dev.skypocket.mixin;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;
import dev.skypocket.net.SkyBlockDetector;
import dev.skypocket.skyblock.SkyBlockRarity;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies the SkyBlock lore rarity colour to dropped-item outlines. */
@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererMixin {

    @Inject(method = "extractRenderState", at = @At("TAIL"), require = 0)
    private void sky$rarityOutline(ItemEntity entity, ItemEntityRenderState state, float partialTick,
                                   CallbackInfo ci) {
        ModConfig cfg = ConfigManager.get();
        if (cfg.itemGlowEnabled && SkyBlockDetector.isActive()) {
            state.outlineColor = SkyBlockRarity.of(entity.getItem()).color();
        }
    }
}
