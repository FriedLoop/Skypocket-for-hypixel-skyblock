package dev.skypocket.skyblock;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;
import dev.skypocket.net.SkyBlockDetector;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Caches the positions of Hypixel's player-shaped NPCs.
 *
 * <p>Hypixel does not use a separate NPC entity type: an NPC is a {@link Player} with a
 * version-2 UUID, full health and no sleeping state. The entity must stay visible; this
 * tracker is only used to decide whether a real player is standing near one.
 */
public final class SkyBlockNpcTracker {
    private static volatile List<Vec3> positions = List.of();

    private SkyBlockNpcTracker() {
    }

    public static void tick(Minecraft mc) {
        ModConfig cfg = ConfigManager.get();
        if (!cfg.hidePlayersNearNpcsBlock.hidePlayersNearNpcs || !SkyBlockDetector.isActive() || mc.level == null) {
            positions = List.of();
            return;
        }
        List<Vec3> next = new ArrayList<>();
        for (Player player : mc.level.players()) {
            if (isNpc(player)) {
                next.add(player.position());
            }
        }
        positions = List.copyOf(next);
    }

    /** True when {@code entity} is within the configured range of a cached NPC. */
    public static boolean isNearNpc(Entity entity) {
        if (entity == null || positions.isEmpty()) {
            return false;
        }
        double range = Math.max(0.5, Math.min(8.0, ConfigManager.get().hidePlayersNearNpcsBlock.hidePlayersNearNpcsRange));
        double rangeSq = range * range;
        for (Vec3 npc : positions) {
            if (entity.position().distanceToSqr(npc) <= rangeSq) {
                return true;
            }
        }
        return false;
    }

    /** NPCs use a version-2 UUID and keep the vanilla player health of 20. */
    public static boolean isNpc(Player player) {
        return player != null
                && player.getUUID().version() == 2
                && player.getHealth() == 20.0f
                && !player.isSleeping();
    }
}
