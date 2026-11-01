package dev.skypocket.net;

import dev.skypocket.config.ConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;

import java.net.SocketAddress;

/**
 * Detects whether the client is currently connected to Hypixel SkyBlock, and gates
 * SkyBlock-category modules accordingly.
 *
 * <p>Detection is lightweight and self-contained: Hypixel is recognized by the
 * current server address, and SkyBlock by the sidebar objective's "SKYBLOCK" title.
 * SkyBlock-category modules should wrap their effect in {@link #isActive()} so they
 * only run in SkyBlock when the "SkyBlock modules only in SkyBlock" setting is on.
 */
public final class SkyBlockDetector {
    private SkyBlockDetector() {
    }

    /** Whether a SkyBlock-category module should run right now: always true unless the
     *  "SkyBlock modules only in SkyBlock" setting is on and we're not on SkyBlock. */
    public static boolean isActive() {
        return !ConfigManager.get().skyBlockModulesOnly || isOnSkyBlock();
    }

    /** True when connected to any Hypixel server. */
    public static boolean isOnHypixel() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) {
            return false;
        }
        // Prefer the server-list entry (human-readable ip), then fall back to the
        // live socket address (covers direct connections).
        ServerData server = mc.getConnection().getServerData();
        if (server != null && isHypixelAddress(server.ip)) {
            return true;
        }
        SocketAddress addr = mc.getConnection().getConnection().getRemoteAddress();
        return addr != null && isHypixelAddress(addr.toString());
    }

    /** True when on Hypixel and the sidebar identifies the SkyBlock game. */
    public static boolean isOnSkyBlock() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !isOnHypixel()) {
            return false;
        }
        Scoreboard board = mc.level.getScoreboard();
        Objective sidebar = board.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (sidebar == null) {
            return false;
        }
        String title = sidebar.getDisplayName().getString();
        return title != null && title.toUpperCase().contains("SKYBLOCK");
    }

    private static boolean isHypixelAddress(String address) {
        return address != null && address.toLowerCase().contains("hypixel.net");
    }
}
