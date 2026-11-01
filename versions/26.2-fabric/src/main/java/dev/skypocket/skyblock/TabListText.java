package dev.skypocket.skyblock;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.PlayerTeam;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Reads Hypixel's tab list, which carries server info either as fake players (often
 * one line per entry) or as the tab header/footer block (the {@code "Stats:"} panel).
 * Shared by every tracker that parses server state out of the tab.
 */
public final class TabListText {
    /** Flattened tab header/footer, kept from the tab customisation packet. */
    private static String header = "";
    private static String footer = "";

    private TabListText() {
    }

    /** Tab customisation packet: the block shown above / below the player list. */
    public static void onTabList(Component headerText, Component footerText) {
        header = flatten(headerText);
        footer = flatten(footerText);
    }

    public static void clear() {
        header = "";
        footer = "";
    }

    /** Flat text of every tab source: header, footer, then each fake-player entry. */
    public static List<String> all(Minecraft mc) {
        List<String> out = new ArrayList<>(4);
        if (!header.isEmpty()) {
            out.add(header);
        }
        if (!footer.isEmpty()) {
            out.add(footer);
        }
        for (PlayerInfo info : orderedPlayers(mc)) {
            String text = entry(mc, info);
            if (text != null && !text.isEmpty()) {
                out.add(text);
            }
        }
        return out;
    }

    /**
     * Every tab line on its own: the header, the footer, then each fake-player entry, with the
     * newlines Hypixel uses to stack several values inside one entry split into separate lines.
     *
     * <p>{@link #all} deliberately flattens those newlines into spaces (its consumers each want a
     * single value), which makes a stacked <em>block</em> - the {@code "Commissions:"} list, say -
     * impossible to take apart again. Anything parsing such a list wants this method instead.
     */
    public static List<String> allLines(Minecraft mc) {
        List<String> out = new ArrayList<>(16);
        addLines(out, header);
        addLines(out, footer);
        for (PlayerInfo info : orderedPlayers(mc)) {
            addLines(out, rawEntry(mc, info));
        }
        return out;
    }

    /**
     * The tab entries in the order they are displayed in.
     *
     * <p>{@code getOnlinePlayers()} hands back a {@code HashMap}'s values - an order with nothing to
     * do with the tab list - so a block that spans several entries (the {@code "Commissions:"}
     * list) comes out shuffled without this. Vanilla sorts the same way for its own tab overlay
     * ({@link PlayerInfo#getTabListOrder()}, see {@code PlayerTabOverlay}), which is also what makes
     * "keep the tab order" meaningful for anything stacking several tab lines.
     */
    private static List<PlayerInfo> orderedPlayers(Minecraft mc) {
        ClientPacketListener conn = mc.getConnection();
        if (conn == null) {
            return List.of();
        }
        List<PlayerInfo> players = new ArrayList<>(conn.getOnlinePlayers());
        players.sort(Comparator.comparingInt(PlayerInfo::getTabListOrder));
        return players;
    }

    private static void addLines(List<String> out, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        for (String line : text.split("\n")) {
            if (!line.isEmpty()) {
                out.add(line);
            }
        }
    }

    /** Tab text for one entry, newlines flattened to spaces (see {@link #all}). */
    static String entry(Minecraft mc, PlayerInfo info) {
        String raw = rawEntry(mc, info);
        return raw == null ? null : raw.replace('\n', ' ');
    }

    /** Tab text for one entry with its newlines intact (see {@link #allLines}). */
    static String rawEntry(Minecraft mc, PlayerInfo info) {
        Component name = info.getTabListDisplayName();
        if (name != null) {
            return name.getString();
        }
        // Some servers render tab info as team prefix/suffix around the profile name.
        PlayerTeam team = mc.level == null
                ? null
                : mc.level.getScoreboard().getPlayersTeam(info.getProfile().name());
        if (team == null) {
            return null;
        }
        Component prefix = team.getPlayerPrefix();
        Component suffix = team.getPlayerSuffix();
        return (prefix == null ? "" : prefix.getString())
                + info.getProfile().name()
                + (suffix == null ? "" : suffix.getString());
    }

    /** One component as a single line (newlines become spaces). */
    private static String flatten(Component text) {
        return text == null ? "" : text.getString().replace('\n', ' ');
    }
}
