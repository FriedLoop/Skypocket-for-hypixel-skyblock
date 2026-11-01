package dev.skypocket.mixin;

import dev.skypocket.config.ConfigManager;
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
        Identifier type = EntityType.getKey(entity.getType());
        if (type == null || !"minecraft".equals(type.getNamespace()) || !"player".equals(type.getPath())) {
            return;
        }
        // Exclude server-spawned fake players (NPCs, disguised mobs) that still use the
        // minecraft:player entity type. Real players always have a plain username
        // (3-16 [A-Za-z0-9_]); fake players are caught by any of: a custom nametag, a
        // non-username scoreboard name (e.g. "Crypt Ghoul", "[Lv30] ..."), or no
        // tab-list entry. When in doubt, do NOT hide.
        if (entity.hasCustomName()) {
            return;
        }
        String name = entity.getScoreboardName();
        if (name == null || !name.matches("[A-Za-z0-9_]{3,16}")) {
            return;
        }
        ClientPacketListener conn = mc.getConnection();
        if (conn == null || conn.getPlayerInfo(entity.getUUID()) == null) {
            return;
        }
        double range = cfg.hideNearbyPlayersRange;
        if (entity.distanceToSqr(mc.player) < range * range) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void sky$hideLandedArrows(Entity entity, Frustum frustum, double x, double y, double z,
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
