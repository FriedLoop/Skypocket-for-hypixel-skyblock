package dev.skypocket.skyblock;

import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parses Hypixel's commission list out of the tab list.
 *
 * <p>In the three commission areas (Dwarven Mines, Crystal Hollows, Glacite Tunnels) the tab list
 * carries a block that starts with a {@code "Commissions:"} line followed by one line per active
 * commission: {@code "<name>: <n>%"} while in progress, {@code "<name>: DONE"} once finished. How
 * many lines there are follows the player's commission slots (2 at first, 3 after 100 completed,
 * 4 with the Peak of the Mountain perk), so the block is read until it stops looking like
 * commissions rather than for a fixed count.
 *
 * <p><b>Matching is deliberately position-free.</b> Hypixel renders the header and each commission
 * as its own fake-player entry, and {@code getOnlinePlayers()} hands those back in map order -
 * which is <em>not</em> the order they are displayed in. So "the lines following the header" is not
 * something that can be relied on: the {@code "Commissions:"} line is only a gate (are we in one of
 * the three areas?), and each commission is found by its own shape. The pickaxe-ability tracker
 * documents the same trap and matches its {@code "<ability>: <n>s"} line on its own for the same
 * reason.
 *
 * <p>What keeps a stat line from being mistaken for a commission is the value shape (a percentage or
 * {@code DONE}) plus the four-entry cap, since the commission slots only go up to 4. When the header
 * disappears - left the area, or the tab list has not arrived yet - {@link #entries()} goes empty,
 * which hides the HUD element on its own.
 *
 * <p>Read through {@link TabListText#allLines} rather than {@code all}: one tab entry may hold
 * several lines, and only the line-splitting reader keeps the header and the first commission apart.
 */
public final class CommissionTracker {
    /** Anchor above the entries; matched case-insensitively, with or without its colon. */
    private static final String HEADER = "commissions";
    /** Slots go up to 4; the cap also guards against a mis-anchored block. */
    private static final int MAX_ENTRIES = 4;
    /** The server rebuilds the tab list every few hundred ms - polling faster is wasted work. */
    private static final long POLL_INTERVAL_MS = 300;

    /** One commission line: its name plus the raw progress text it was read from. */
    public record Entry(String name, String value, boolean done, float progress) {
    }

    private static long lastPoll;
    private static List<Entry> entries = List.of();

    private CommissionTracker() {
    }

    /** Re-reads the tab list (throttled); call once per frame. */
    public static void update(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (now - lastPoll < POLL_INTERVAL_MS) {
            return;
        }
        lastPoll = now;
        entries = parse(TabListText.allLines(mc));
    }

    /** Active commissions in tab-list order; empty outside a commission area. */
    public static List<Entry> entries() {
        return entries;
    }

    private static List<Entry> parse(List<String> lines) {
        // The header is only a gate, so it is looked up on its own: it may sit anywhere in the
        // iteration order, and a cap applied while scanning must not stop us from seeing it.
        boolean inCommissionArea = false;
        for (String line : lines) {
            if (isHeader(line.trim())) {
                inCommissionArea = true;
                break;
            }
        }
        if (!inCommissionArea) {
            return List.of();
        }
        List<Entry> out = new ArrayList<>(MAX_ENTRIES);
        for (String line : lines) {
            if (out.size() >= MAX_ENTRIES) {
                break;
            }
            Entry entry = parseEntry(line.trim());
            if (entry != null) {
                out.add(entry);
            }
        }
        return out;
    }

    private static boolean isHeader(String line) {
        String text = line.trim();
        if (text.endsWith(":")) {
            text = text.substring(0, text.length() - 1);
        }
        return text.equalsIgnoreCase(HEADER);
    }

    /** Parses {@code "<name>: <value>"}, or returns null when the line is not a commission. */
    private static Entry parseEntry(String line) {
        int sep = line.lastIndexOf(": ");
        if (sep <= 0) {
            return null;
        }
        String name = line.substring(0, sep).trim();
        String value = line.substring(sep + 2).trim();
        boolean done = value.toUpperCase(Locale.ROOT).contains("DONE");
        float progress = done ? 1f : percent(value);
        if (!done && progress < 0f) {
            return null;
        }
        return new Entry(name, value, done, progress);
    }

    /** {@code "11.4%"} -&gt; 0.114, or -1 when the text is not a percentage. */
    private static float percent(String value) {
        if (!value.endsWith("%")) {
            return -1f;
        }
        try {
            String number = value.substring(0, value.length() - 1).trim().replace(",", "");
            return Math.max(0f, Math.min(1f, Float.parseFloat(number) / 100f));
        } catch (NumberFormatException e) {
            return -1f;
        }
    }
}
