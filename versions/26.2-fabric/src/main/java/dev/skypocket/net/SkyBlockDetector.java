package dev.skypocket.net;

import dev.skypocket.config.ConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.Scoreboard;

import java.net.SocketAddress;
import java.util.Locale;

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

    /**
     * True when the sidebar identifies SkyBlock. The old version required a Hypixel server address
     * first, so a proxy address or a non-Hypixel SkyBlock server made the gate reject every
     * SkyBlock module even though the sidebar clearly said SKYBLOCK.
     */
    public static boolean isOnSkyBlock() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return false;
        }
        Scoreboard board = mc.level.getScoreboard();
        Objective sidebar = board.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (sidebar != null) {
            if (isSkyBlockLabel(sidebar.getName())
                    || isSkyBlockLabel(sidebar.getDisplayName().getString())) {
                return true;
            }
            // Some servers keep a generic title and put the game name in the first rows. Purse /
            // Bits / Gems are strong SkyBlock markers and avoid matching a generic "SKYBLOCK"
            // chat message or server name.
            for (PlayerScoreEntry entry : board.listPlayerScores(sidebar)) {
                // Hypixel can send entries whose formatted display component is null. Fall back to
                // the owner name instead of dereferencing it during the render pass.
                String line = entry.display() != null
                        ? entry.display().getString()
                        : entry.ownerName() != null ? entry.ownerName().getString() : null;
                if (isSkyBlockLabel(line) || containsAny(line, "PURSE", "BITS", "GEMS")) {
                    return true;
                }
            }
        }
        // Last resort for servers whose address itself names SkyBlock but whose sidebar is hidden.
        String address = serverAddress();
        return address != null && address.toLowerCase(Locale.ROOT).contains("skyblock");
    }

    private static boolean isSkyBlockLabel(String text) {
        return text != null && containsAny(text, "SKYBLOCK", "SKY BLOCK");
    }

    private static boolean containsAny(String text, String... needles) {
        if (text == null) {
            return false;
        }
        String upper = text.toUpperCase(Locale.ROOT);
        for (String needle : needles) {
            if (upper.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String serverAddress() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) {
            return null;
        }
        ServerData server = mc.getConnection().getServerData();
        if (server != null && server.ip != null) {
            return server.ip;
        }
        SocketAddress addr = mc.getConnection().getConnection().getRemoteAddress();
        return addr == null ? null : addr.toString();
    }

    private static boolean isHypixelAddress(String address) {
        return address != null && address.toLowerCase(Locale.ROOT).contains("hypixel.net");
    }
}
