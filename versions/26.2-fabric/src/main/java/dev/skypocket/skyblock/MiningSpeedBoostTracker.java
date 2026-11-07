package dev.skypocket.skyblock;

import dev.skypocket.net.SkyBlockDetector;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;

/**
 * Tracks the authoritative Mining Speed Boost window from Hypixel chat.
 *
 * <p>The start and end are server events, so they stay correct when the server lags; the tab
 * list is only used by {@link MiningSpeedTracker} to calibrate the multiplier and base speed.
 * This runs independently of {@code miningAbilityReminderEnabled}: the break predictor needs
 * the window even when the reminder HUD is switched off.
 */
public final class MiningSpeedBoostTracker {
    private static final String USED = "You used your Mining Speed Boost Pickaxe Ability!";
    private static final String EXPIRED = "Your Mining Speed Boost has expired!";
    /** GAME and CHAT can both deliver the same line; do not restart the window twice. */
    private static final long DEDUP_MS = 500;

    private static String lastText;
    private static long lastSeenMs;

    private MiningSpeedBoostTracker() {
    }

    public static void init() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> onChat(message.getString()));
        ClientReceiveMessageEvents.CHAT.register((message, playerChatMessage, sender, params, timestamp) ->
                onChat(message.getString()));
    }

    private static void onChat(String text) {
        if (text == null || !SkyBlockDetector.isActive()) {
            return;
        }
        if (!text.equals(USED) && !text.equals(EXPIRED)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (text.equals(lastText) && now - lastSeenMs < DEDUP_MS) {
            return;
        }
        lastText = text;
        lastSeenMs = now;
        if (text.equals(USED)) {
            MiningSpeedTracker.onBoostStarted(now);
        } else {
            MiningSpeedTracker.onBoostEnded(now);
        }
    }
}
