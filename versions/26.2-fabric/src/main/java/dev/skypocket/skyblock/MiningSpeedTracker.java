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

    /** Possible Mining Speed Boost multipliers: +200%, +250%, +300%. */
    private static final double[] BOOST_MULTIPLIERS = {3.0, 3.5, 4.0};
    /** Safety fallback if the expiry chat line is missed. */
    private static final long BOOST_MAX_MS = 30000;
    private static boolean boostActive;
    private static boolean boostCalibrated;
    private static boolean boostSettle;
    private static int boostCalibrations;
    private static Double pendingMultiplier;
    private static double boostMultiplier = BOOST_MULTIPLIERS[0];
    /** Pre-boost speed; also the value used after the boost until the tab catches up. */
    private static double baseSpeed = -1;
    private static long boostStartedMs;
    /** Last tab reading seen while a non-whitelisted item was held. */
    private static double lastNonToolSpeed = -1;
    private static boolean wasHolding;
    private static long nonToolIgnoreUntilMs;

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

    /** True while the Mining Speed Boost window is active. */
    public static boolean boostActive() {
        return boostActive;
    }

    /** True while the boost window or the post-boost tab settle window is changing the value. */
    public static boolean transitioning() {
        return boostActive || boostSettle;
    }

    /** Current learned multiplier (starts at the conservative +200% value). */
    public static double boostMultiplier() {
        return boostMultiplier;
    }

    /**
     * The speed the original predictor consumes.
     *
     * <p>While the boost is active, the tab value wins as soon as it actually shows the
     * boosted speed. Only during the tab's 3-second lag does the base x multiplier fill in.
     * After expiry the pre-boost base is used until the tab drops the stale boosted value.
     */
    public static double effectiveSpeed(long now) {
        if (speed <= 0 && baseSpeed <= 0) {
            return -1;
        }
        if (boostActive) {
            double base = baseSpeed > 0 ? baseSpeed : speed;
            if (base <= 0) {
                return -1;
            }
            // The tab's boosted value is the authoritative final speed once it appears.
            if (speed > base * 1.5) {
                return speed;
            }
            return base * boostMultiplier;
        }
        if (boostSettle && baseSpeed > 0) {
            return baseSpeed;
        }
        return speed;
    }

    /** Called from the authoritative "You used your ..." chat line. */
    public static void onBoostStarted(long now) {
        boostActive = true;
        boostSettle = false;
        boostCalibrated = false;
        boostCalibrations = 0;
        pendingMultiplier = null;
        boostStartedMs = now;
        // Current pre-boost tab value is the base for this activation. This is what keeps
        // a debuff before the ability from looking like a different multiplier.
        if (speed > 0) {
            baseSpeed = speed;
        }
    }

    /** Called from the authoritative "... has expired!" chat line. */
    public static void onBoostEnded(long now) {
        if (!boostActive) {
            return;
        }
        boostActive = false;
        boostSettle = true;
    }

    /**
     * The speed only when the reported value has been unchanged for {@link #SETTLE_MS},
     * i.e. the tab list has caught up. Learning a break duration against a value that is
     * still moving mis-attributes it and scales the whole model, which is what makes a
     * prediction fire far too early. {@code -1} means "not trustworthy for learning".
     */
    public static double settledSpeed() {
        if (transitioning()) {
            return -1;
        }
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
        boostActive = false;
        boostCalibrated = false;
        boostSettle = false;
        boostCalibrations = 0;
        pendingMultiplier = null;
        baseSpeed = -1;
        boostStartedMs = 0;
        lastNonToolSpeed = -1;
        wasHolding = false;
        nonToolIgnoreUntilMs = 0;
    }

    /**
     * Reconciles a tab sample with the boost timeline. During the boost the tab may still
     * show the pre-boost value, or the boosted value; after the end it may keep the old
     * boosted value for a few seconds. Neither can overwrite the true pre-boost base.
     */
    private static void onTabSpeed(double value, long now) {
        if (boostActive && now - boostStartedMs > BOOST_MAX_MS) {
            onBoostEnded(now);
        }
        if (boostActive) {
            if (baseSpeed <= 0) {
                baseSpeed = value;
                return;
            }
            double ratio = value / baseSpeed;
            Double candidate = nearestBoostMultiplier(ratio);
            if (!boostCalibrated) {
                if (candidate != null) {
                    // First useful sample: use it immediately.
                    boostMultiplier = candidate;
                    boostCalibrated = true;
                    boostCalibrations = 1;
                    pendingMultiplier = null;
                } else if (Math.abs(ratio - 1.0) <= 0.15) {
                    // Tab has not published the boosted value yet.
                    baseSpeed = value;
                }
            } else if (candidate != null && boostCalibrations < 2) {
                if (Math.abs(candidate - boostMultiplier) <= 0.01) {
                    pendingMultiplier = null;
                } else if (pendingMultiplier != null
                        && Math.abs(candidate - pendingMultiplier) <= 0.01) {
                    // The first sample was noisy; a second matching sample is allowed to
                    // correct it once, then the multiplier is locked for this activation.
                    boostMultiplier = candidate;
                    boostCalibrations = 2;
                    pendingMultiplier = null;
                } else {
                    pendingMultiplier = candidate;
                }
            } else if (Math.abs(ratio - 1.0) <= 0.15) {
                // Base changed (debuff) before the tab showed the boosted value.
                baseSpeed = value;
            }
            return;
        }
        if (boostSettle && baseSpeed > 0) {
            double ratio = value / baseSpeed;
            if (nearestBoostMultiplier(ratio) != null) {
                return; // still the stale boosted reading
            }
            baseSpeed = value;
            boostSettle = false;
            return;
        }
        baseSpeed = value;
    }

    private static Double nearestBoostMultiplier(double ratio) {
        if (ratio < 2.5 || ratio > 4.5) {
            return null;
        }
        double best = BOOST_MULTIPLIERS[0];
        double bestDistance = Math.abs(ratio - best);
        for (int i = 1; i < BOOST_MULTIPLIERS.length; i++) {
            double distance = Math.abs(ratio - BOOST_MULTIPLIERS[i]);
            if (distance < bestDistance) {
                best = BOOST_MULTIPLIERS[i];
                bestDistance = distance;
            }
        }
        return bestDistance <= 0.4 ? best : null;
    }

    private static void recordNonToolSpeed(Minecraft mc) {
        for (String line : TabListText.all(mc)) {
            Matcher m = SPEED.matcher(line);
            if (m.find()) {
                try {
                    double value = Double.parseDouble(m.group(1).replace(",", ""));
                    if (value > 0) {
                        lastNonToolSpeed = value;
                        return;
                    }
                } catch (NumberFormatException ignored) {
                    // keep the last readable value
                }
            }
        }
    }

    private static void parse(Minecraft mc, long now) {
        if (mc.getConnection() == null) {
            // Disconnected: the tab text would otherwise stay readable forever.
            reset();
            TabListText.clear();
            return;
        }
        boolean holding = MiningTools.holding();
        if (!holding) {
            // Freeze the last mining-tool reading, but record what the tab shows while
            // another item is held so the first stale refresh after switching back can
            // be rejected.
            recordNonToolSpeed(mc);
            wasHolding = false;
            return;
        }
        if (!wasHolding) {
            wasHolding = true;
            // Tab refreshes every ~3s; keep the stale filter open a little longer.
            nonToolIgnoreUntilMs = now + 4000;
        }
        for (String line : TabListText.all(mc)) {
            Matcher m = SPEED.matcher(line);
            if (m.find()) {
                try {
                    double value = Double.parseDouble(m.group(1).replace(",", ""));
                    if (value > 0) {
                        if (now < nonToolIgnoreUntilMs && lastNonToolSpeed > 0
                                && Math.abs(value - lastNonToolSpeed)
                                <= 0.02 * Math.max(1.0, lastNonToolSpeed)) {
                            return; // stale reading from the non-tool item
                        }
                        lastNonToolSpeed = -1;
                        nonToolIgnoreUntilMs = 0;
                        // A change has to be meaningful: the panel is repainted with
                        // slightly different values all the time, and treating that as a
                        // change would keep the value "unsettled" forever, which stops
                        // the timing fit from ever accepting a sample.
                        if (Math.abs(value - speed) > 0.005 * Math.max(1.0, speed)) {
                            lastChangeMs = now;
                        }
                        speed = value;
                        seenMs = now;
                        onTabSpeed(value, now);
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
