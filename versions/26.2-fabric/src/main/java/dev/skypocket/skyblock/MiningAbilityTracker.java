package dev.skypocket.skyblock;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;
import dev.skypocket.i18n.Localization;
import dev.skypocket.net.SkyBlockDetector;
import dev.skypocket.sound.SoundManager;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;

import java.util.EnumMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tracks the pickaxe ability state on Hypixel and fires the "available" reminder.
 *
 * <p>Hypixel renders the live ability state as a fake player's tab list display
 * name: {@code "Pickaxe Ability:"} / {@code "<Name>: <n>s"} while cooling and
 * {@code "<Name>: Available"} once ready. The cooldown starts the moment the ability
 * is activated, so there are only two states: cooling and available.
 *
 * <p>The tab list only updates every few seconds, so it is polled sparingly (every
 * {@link #POLL_INTERVAL_MS}); between polls the countdown is driven by a local clock
 * ({@link #cooldownEndsAtMs}) and re-synced from the tab value on each poll. The
 * countdown total is the first value read per ability, used only to derive the
 * color progress (red 0-50%, yellow 50-100%, green when available).
 *
 * <p>Only one ability can be active at a time, so a single (ability, state) pair is
 * tracked. The reminder fires only on the authoritative chat event
 * {@code "<Name> is now available!"}; the polled tab list never fires it. Once
 * available, the HUD hides after {@link #AVAILABLE_HIDE_MS} without a use.
 */
public final class MiningAbilityTracker {
    /** Tab poll interval; the tab list itself updates only every ~3s. */
    private static final long POLL_INTERVAL_MS = 12000;
    /** Poll interval while force-polling for the first cooldown value after a use. */
    private static final long FORCE_POLL_INTERVAL_MS = 1000;
    /** Give up force-polling if no countdown appears within this window. */
    private static final long FORCE_POLL_TIMEOUT_MS = 15000;
    /** How long a state survives without the tab entry (lobby change / disconnect). */
    private static final long STALE_TTL_MS = 30000;
    /** Hide the countdown HUD this long after becoming available without a use. */
    private static final long AVAILABLE_HIDE_MS = 30000;
    /** Chat triggers share this dedup window. */
    private static final long REMINDER_DEDUP_MS = 5000;
    /** How long the reminder text stays on screen (fading out at the end). */
    private static final long REMINDER_DURATION_MS = 3000;
    /** Ignore a stale tab "Available" for this long after the ability was used. */
    private static final long USED_GRACE_MS = 4000;
    /** Cooldown text in the tab line, e.g. "96s". */
    private static final Pattern COUNTDOWN = Pattern.compile("(\\d+)\\s*s");

    // Countdown HUD colors by progress (derived, never shown as a percentage).
    private static final int COLOR_RED = 0xFFFF5555;
    private static final int COLOR_YELLOW = 0xFFFFAA00;
    private static final int COLOR_GREEN = 0xFF55FF55;

    public enum State { NONE, COOLDOWN, AVAILABLE }

    private static State state = State.NONE;
    private static MiningAbility ability;
    /** Local-clock cooldown end; {@code <= 0} means unknown (awaiting a tab poll). */
    private static long cooldownEndsAtMs;
    /** First cooldown value read per ability, used as that ability's total duration. */
    private static final Map<MiningAbility, Integer> TOTAL_SECONDS = new EnumMap<>(MiningAbility.class);

    private static long lastPollMs;
    /** True while waiting for the first cooldown value after a use (fast polling). */
    private static boolean forcePolling;
    private static long forcePollingSinceMs;
    /** Last successful tab read (for stale-state detection). */
    private static long lastSeenMs;
    /** Entered AVAILABLE (30s hide) / COOLDOWN; see usage. */
    private static long stateChangedMs;
    private static long lastReminderMs;
    private static long reminderUntilMs;
    private static MiningAbility reminderAbility;
    private static long lastUsedMs;

    private MiningAbilityTracker() {
    }

    public static void init() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> onChat(message.getString()));
        ClientReceiveMessageEvents.CHAT.register((message, playerChatMessage, sender, params, timestamp) ->
                onChat(message.getString()));
    }

    /** Per-frame entry from the HUD element; polls the tab list at a fixed interval. */
    public static void update(Minecraft mc) {
        ModConfig cfg = ConfigManager.get();
        if (!cfg.miningAbilityReminderBlock.miningAbilityReminderEnabled) {
            return;
        }
        if (!SkyBlockDetector.isActive()) {
            reset();
            return;
        }
        long now = System.currentTimeMillis();
        long interval = forcePolling ? FORCE_POLL_INTERVAL_MS : POLL_INTERVAL_MS;
        if (now - lastPollMs < interval) {
            return;
        }
        lastPollMs = now;
        if (forcePolling && now - forcePollingSinceMs > FORCE_POLL_TIMEOUT_MS) {
            forcePolling = false;
        }
        parseTabList(mc, now);
    }

    private static void onChat(String text) {
        if (!ConfigManager.get().miningAbilityReminderBlock.miningAbilityReminderEnabled || text == null) {
            return;
        }
        for (MiningAbility a : MiningAbility.values()) {
            if (text.equals(a.matchName + " is now available!")) {
                // The authoritative "became available" event — the only thing that
                // fires the reminder (the polled tab list can be stale).
                long now = System.currentTimeMillis();
                boolean transition = state != State.AVAILABLE;
                markAvailable(a, now);
                if (transition && now - lastReminderMs > REMINDER_DEDUP_MS) {
                    lastReminderMs = now;
                    remind(a);
                }
                return;
            }
            if (text.equals("You used your " + a.matchName + " Pickaxe Ability!")) {
                long now = System.currentTimeMillis();
                state = State.COOLDOWN;
                ability = a;
                stateChangedMs = now;
                lastUsedMs = now;
                // Start the local countdown immediately when this ability's total is
                // already known; otherwise force-poll until the first countdown is read.
                Integer total = TOTAL_SECONDS.get(a);
                if (total != null) {
                    cooldownEndsAtMs = now + total * 1000L;
                } else {
                    cooldownEndsAtMs = 0;
                    forcePolling = true;
                    forcePollingSinceMs = now;
                    lastPollMs = 0; // poll on the next frame
                }
                return;
            }
        }
    }

    /**
     * Scans the tab list for the ability entry. Hypixel renders the info as fake
     * players — often one entry per line ({@code "Pickaxe Ability:"} and
     * {@code "<Name>: <n>s|Available"} separately) — so every entry is parsed
     * independently; the {@code "<Ability>: <n>s|Available"} tail pattern is
     * specific enough on its own.
     */
    private static void parseTabList(Minecraft mc, long now) {
        for (String text : TabListText.all(mc)) {
            if (parseEntry(text, now)) {
                return;
            }
        }
        // Entry not found (lobby switch / ability not unlocked): keep the old state
        // briefly, then clear so the HUD does not show stale data forever.
        if (state != State.NONE && now - lastSeenMs > STALE_TTL_MS) {
            reset();
        }
    }

    /** Parses one tab entry; returns true when the ability state was found. */
    private static boolean parseEntry(String flat, long now) {
        for (MiningAbility a : MiningAbility.values()) {
            int at = flat.indexOf(a.matchName);
            if (at < 0) {
                continue;
            }
            int colon = flat.indexOf(':', at + a.matchName.length());
            if (colon < 0) {
                return false;
            }
            String rest = flat.substring(colon + 1).trim();
            if (rest.regionMatches(true, 0, "Available", 0, "Available".length())) {
                // A stale "Available" lingers right after using (the server hasn't
                // updated the entry yet); ignore it within the grace window so the
                // HUD does not flash "available". Tab availability never fires sound.
                if (now - lastUsedMs >= USED_GRACE_MS) {
                    markAvailable(a, now);
                }
                lastSeenMs = now;
                return true;
            }
            Matcher m = COUNTDOWN.matcher(rest);
            if (m.find()) {
                int seconds = Integer.parseInt(m.group(1));
                TOTAL_SECONDS.putIfAbsent(a, seconds);
                state = State.COOLDOWN;
                ability = a;
                stateChangedMs = now;
                cooldownEndsAtMs = now + seconds * 1000L; // re-sync the local countdown
                lastSeenMs = now;
                forcePolling = false; // got the first value; back to slow polling
                return true;
            }
            return false;
        }
        return false;
    }

    /** Marks the ability available for HUD display; does NOT fire the reminder. */
    private static void markAvailable(MiningAbility a, long now) {
        if (state != State.AVAILABLE) {
            stateChangedMs = now;
        }
        state = State.AVAILABLE;
        ability = a;
        cooldownEndsAtMs = 0;
    }

    /** Opens the 3s reminder window (rendered by the HUD element) + plays the alert. */
    private static void remind(MiningAbility a) {
        ModConfig cfg = ConfigManager.get();
        reminderAbility = a;
        reminderUntilMs = System.currentTimeMillis() + REMINDER_DURATION_MS;
        SoundManager.play(cfg.miningAbilityReminderBlock.miningAbilityReminderSound,
                cfg.miningAbilityReminderBlock.miningAbilityReminderVolume, cfg.miningAbilityReminderBlock.miningAbilityReminderPitch);
    }

    /** Milliseconds left in the current reminder display window (<= 0 = inactive). */
    public static long reminderRemainingMs() {
        return reminderUntilMs - System.currentTimeMillis();
    }

    /** The ability the active reminder is about (null when no window is open). */
    public static MiningAbility reminderAbility() {
        return reminderRemainingMs() > 0 ? reminderAbility : null;
    }

    private static void reset() {
        state = State.NONE;
        ability = null;
        cooldownEndsAtMs = 0;
    }

    /** Locally derived remaining seconds; {@code -1} until a cooldown end is known. */
    private static int remainingNow() {
        if (cooldownEndsAtMs <= 0) {
            return -1;
        }
        long ms = cooldownEndsAtMs - System.currentTimeMillis();
        if (ms <= 0) {
            return 0;
        }
        return (int) Math.ceil(ms / 1000.0);
    }

    /** The HUD countdown line, or null while hidden (never seen / 30s grace over). */
    public static String hudLine() {
        if (state == State.NONE || ability == null) {
            return null;
        }
        if (state == State.AVAILABLE) {
            if (System.currentTimeMillis() - stateChangedMs > AVAILABLE_HIDE_MS) {
                return null;
            }
            return ability.matchName + ": " + Localization.tr("可用!");
        }
        int remaining = remainingNow();
        return ability.matchName + ": "
                + (remaining < 0 ? "..." : Math.max(1, remaining) + "s");
    }

    /** HUD text color: red < 50% progress, yellow ≥ 50%, green when available. */
    public static int hudColor() {
        if (state == State.AVAILABLE) {
            return COLOR_GREEN;
        }
        if (state == State.COOLDOWN && ability != null) {
            Integer total = TOTAL_SECONDS.get(ability);
            int remaining = remainingNow();
            if (total == null || total <= 0 || remaining < 0) {
                return COLOR_RED;
            }
            double progress = 1.0 - (double) remaining / total;
            return progress < 0.5 ? COLOR_RED : COLOR_YELLOW;
        }
        return 0xFFFFFFFF;
    }
}
