package dev.skypocket.skyblock;

import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the player's <em>total</em> Mining Speed from the tab list, where Hypixel
 * renders it as a fake-player line ({@code "Mining Speed: 1,234"}).
 *
 * <p>This is the live half of the break-time model: the duration comes from the server
 * formula {@code ticks = ceil(BlockStrength × 30 / speed)} (see {@link MiningBlocks}),
 * so the reading only has to be correct at the moment of the break — a value that
 * changes constantly (buffs, abilities, drills, potions) is not a problem as long as
 * {@link #settledSpeed()} is only trusted once the tab list has caught up.
 *
 * <p>The tab list only refreshes every few seconds, so this is polled at a modest
 * interval. When the line is not present the speed is unknown ({@code -1}) and the
 * caller shows nothing at all rather than guessing.
 */
public final class MiningSpeedTracker {
    private static final dev.skypocket.SkyPocketLog LOGGER =
            dev.skypocket.SkyPocketLog.of("mining");
    /** Tab poll interval; the tab list itself only refreshes every few seconds. */
    private static final long POLL_INTERVAL_MS = 1000;
    /** Drop the reading when no "Mining Speed" line has been seen for this long. */
    private static final long STALE_TTL_MS = 15000;
    /** The reading must stay unchanged this long before it is trusted for learning. */
    private static final long SETTLE_MS = 2500;
    /**
     * e.g. {@code "Mining Speed: ⛏8015"} — Hypixel puts a custom resource-pack glyph
     * between the colon and the number, so up to a few non-digit, non-letter
     * characters are allowed there (letters are excluded so the pattern can never
     * jump past an empty value into the next stat).
     */
    private static final Pattern SPEED = Pattern.compile(
            "Mining Speed:?[^0-9A-Za-z]{0,6}([0-9][0-9,]*(?:\\.[0-9]+)?)",
            Pattern.CASE_INSENSITIVE);

    private static double speed = -1;
    private static long seenMs;
    private static long lastPollMs;
    /** When the reported value last changed (used to tell "settled" from "just moved"). */
    private static long lastChangeMs;
    /** Discovery aid: log one tab line that mentions Mining Speed yet did not parse. */
    private static boolean reported;

    private MiningSpeedTracker() {
    }

    /** Per-frame entry point; polls the tab list at a fixed interval. */
    public static void update(Minecraft mc, long now) {
        if (now - lastPollMs < POLL_INTERVAL_MS) {
            return;
        }
        lastPollMs = now;
        parse(mc, now);
    }

    /** Total Mining Speed, or {@code -1} when it is not currently readable. */
    public static double speed() {
        if (speed <= 0 || System.currentTimeMillis() - seenMs > STALE_TTL_MS) {
            return -1;
        }
        return speed;
    }

    /** Latest raw reading (may be stale); for the debug HUD only. */
    public static double rawSpeed() {
        return speed;
    }

    /**
     * The speed only when the reported value has been unchanged for {@link #SETTLE_MS},
     * i.e. the tab list has caught up. Learning a break duration against a value that is
     * still moving mis-attributes it and scales the whole model, which is what makes a
     * prediction fire far too early. {@code -1} means "not trustworthy for learning".
     */
    public static double settledSpeed() {
        double value = speed();
        if (value <= 0 || lastChangeMs <= 0) {
            return -1;
        }
        return System.currentTimeMillis() - lastChangeMs >= SETTLE_MS ? value : -1;
    }

    public static void reset() {
        speed = -1;
        seenMs = 0;
        lastChangeMs = 0;
    }

    private static void parse(Minecraft mc, long now) {
        if (mc.getConnection() == null) {
            // Disconnected: the tab text would otherwise stay readable forever.
            reset();
            TabListText.clear();
            return;
        }
        for (String line : TabListText.all(mc)) {
            Matcher m = SPEED.matcher(line);
            if (m.find()) {
                try {
                    double value = Double.parseDouble(m.group(1).replace(",", ""));
                    if (value > 0) {
                        // A change has to be meaningful: the panel is repainted with
                        // slightly different values all the time, and treating that as a
                        // change would keep the value "unsettled" forever, which stops
                        // the timing fit from ever accepting a sample.
                        if (Math.abs(value - speed) > 0.005 * Math.max(1.0, speed)) {
                            lastChangeMs = now;
                        }
                        speed = value;
                        seenMs = now;
                        return;
                    }
                } catch (NumberFormatException ignored) {
                    // fall through to the discovery log below
                }
            }
            if (!reported && line.toLowerCase().contains("mining speed")) {
                reported = true;
                LOGGER.info("unrecognised Mining Speed tab line: '{}'", line);
            }
        }
    }
}
