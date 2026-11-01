package dev.skypocket.mixin;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.skyblock.PlayerLookalike;
import dev.skypocket.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hides other players closer than the configured range (client-side render skip).
 * The local player is never hidden. No network state is touched.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void sky$hideNearbyPlayers(Entity entity, Frustum frustum, double x, double y, double z,
//#if MC>=260300
                                       float partialTick,
//#endif
                                       CallbackInfoReturnable<Boolean> cir) {
        ModConfig cfg = ConfigManager.get();
        if (!cfg.hideNearbyPlayersEnabled || cfg.hideNearbyPlayersRange <= 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || entity == mc.player) {
            return;
        }
        // Only hide genuine player entities (minecraft:player). Servers often spawn
        // custom enemies as Player subclasses (player-model NPCs, fake players, etc.)
        // registered under their own entity type; an instanceof Player check would
        // wrongly hide those too. Compare the entity type key instead.
        // Real players only: player-shaped NPCs and disguised mobs must stay visible. The test lives
        // in one place because the splash attribution needs exactly the same answer.
        if (!PlayerLookalike.isRealPlayer(entity)) {
            return;
        }
        double range = cfg.hideNearbyPlayersRange;
        if (entity.distanceToSqr(mc.player) < range * range) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void sky$hideLandedArrows(Entity entity, Frustum frustum, double x, double y, double z,
//#if MC>=260300
                                      float partialTick,
//#endif
                                      CallbackInfoReturnable<Boolean> cir) {
        ModConfig cfg = ConfigManager.get();
        if (!cfg.hideLandedArrows) {
            return;
        }
        // Only real arrows (regular/spectral/tipped), not tridents, and only once
        // they have landed and are stuck in the ground.
        if (entity instanceof AbstractArrow arrow && !(entity instanceof ThrownTrident)
                && ((AbstractArrowAccessor) arrow).sky$isInGround()) {
            cir.setReturnValue(false);
        }
    }
}
