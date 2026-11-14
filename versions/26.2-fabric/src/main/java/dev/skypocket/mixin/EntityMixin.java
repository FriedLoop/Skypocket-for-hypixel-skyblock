package dev.skypocket.mixin;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;
import dev.skypocket.net.SkyBlockDetector;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Optional client-side glow for the local player in third person. Purely local
 * rendering - no network state is touched.
 *
 * <p>All three injections are {@code require = 0}: each is an independent cosmetic toggle, and a
 * missing target method (a renamed/vanilla-changed {@code isCurrentlyGlowing}) must disable that one
 * glow rather than abort mixin application and stop the game from starting.
 */
@Mixin(Entity.class)
public abstract class EntityMixin {

    @Inject(method = "isCurrentlyGlowing", at = @At("HEAD"), cancellable = true, require = 0)
    private void sky$glow(CallbackInfoReturnable<Boolean> cir) {
        ModConfig cfg = ConfigManager.get();
        Minecraft mc = Minecraft.getInstance();
        if (cfg.playerGlowBlock.playerGlowEnabled
                && (Object) this == mc.player
                && mc.options != null
                && !mc.options.getCameraType().isFirstPerson()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "isCurrentlyGlowing", at = @At("HEAD"), cancellable = true, require = 0)
    private void sky$glowDragon(CallbackInfoReturnable<Boolean> cir) {
        ModConfig cfg = ConfigManager.get();
        if (cfg.dragonGlowBlock.dragonGlowEnabled && (Object) this instanceof EnderDragon && SkyBlockDetector.isActive()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "isCurrentlyGlowing", at = @At("HEAD"), cancellable = true, require = 0)
    private void sky$glowDroppedItem(CallbackInfoReturnable<Boolean> cir) {
        ModConfig cfg = ConfigManager.get();
        if (cfg.itemGlowBlock.itemGlowEnabled && (Object) this instanceof ItemEntity && SkyBlockDetector.isActive()) {
            cir.setReturnValue(true);
        }
    }

}
