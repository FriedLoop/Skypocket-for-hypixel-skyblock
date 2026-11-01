package dev.skypocket.skyblock;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/**
 * Tells a real player apart from the player-shaped things a server spawns.
 *
 * <p>Hypixel dress mobs and NPCs up as players. Some are {@code Player} subclasses registered under
 * their own entity type, others keep the {@code minecraft:player} type outright - so neither
 * {@code instanceof Player} nor the type alone is enough.
 */
public final class PlayerLookalike {

    /** Plain Minecraft usernames; anything else is a scoreboard name the server made up. */
    private static final String USERNAME = "[A-Za-z0-9_]{3,16}";

    private PlayerLookalike() {
    }

    /**
     * True only for a genuine player: player entity type, no custom name, a plain username and an
     * entry in the tab list.
     *
     * <p>Every check errs towards "not a real player": a mob disguised as a player should still be
     * treated as a mob by the features that ask.
     */
    public static boolean isRealPlayer(Entity entity) {
        if (entity == null) {
            return false;
        }
        Identifier type = EntityType.getKey(entity.getType());
        if (type == null || !"minecraft".equals(type.getNamespace()) || !"player".equals(type.getPath())) {
            return false;
        }
        if (entity.hasCustomName()) {
            return false;
        }
        String name = entity.getScoreboardName();
        if (name == null || !name.matches(USERNAME)) {
            return false;
        }
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection != null && connection.getPlayerInfo(entity.getUUID()) != null;
    }


}
