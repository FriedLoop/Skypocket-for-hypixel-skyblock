package dev.skypocket.chat;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;
import dev.skypocket.sound.SoundManager;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;

import java.util.ArrayDeque;
import java.util.Iterator;

/**
 * Plays the mention-alert sound when a chat message mentions the player. The search
 * token is the custom text if set, otherwise the first N characters of the game ID;
 * matching is case-insensitive.
 *
 * <p>Two delivery paths are handled differently:
 * <ul>
 *   <li>{@code CHAT} (signed player chat, e.g. singleplayer) — carries the sender, so
 *       the local player's own messages are skipped directly.</li>
 *   <li>{@code GAME} (system-formatted chat, how Hypixel delivers "Name: message") —
 *       has no sender; instead we skip our own echoed messages (tracked via
 *       {@code ClientSendMessageEvents.CHAT}) and require a ": " separator so join /
 *       welcome announcements don't trigger.</li>
 * </ul>
 */
public final class MentionAlert {
    /** How long a sent message stays eligible for echo matching. */
    private static final long ECHO_WINDOW_MS = 30_000;
    private static final int MAX_TRACKED = 8;

    private static final ArrayDeque<Sent> SENT = new ArrayDeque<>();

    private MentionAlert() {
    }

    public static void init() {
        ClientReceiveMessageEvents.CHAT.register((message, playerChatMessage, sender, params, timestamp) ->
                onMessage(message.getString(), sender.name(), true));
        ClientReceiveMessageEvents.GAME.register((message, overlay) ->
                onMessage(message.getString(), null, false));
        ClientSendMessageEvents.CHAT.register(MentionAlert::onSend);
    }

    private static void onSend(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        SENT.addLast(new Sent(message.trim(), System.currentTimeMillis()));
        while (SENT.size() > MAX_TRACKED) {
            SENT.removeFirst();
        }
    }

    private static void onMessage(String text, String senderName, boolean isPlayerChat) {
        ModConfig cfg = ConfigManager.get();
        if (!cfg.mentionAlertEnabled) {
            return;
        }
        if (text == null || text.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        String ownName = mc.getUser().getName();
        // Signed chat: the sender is known, so drop our own messages directly.
        if (senderName != null && ownName != null && senderName.equalsIgnoreCase(ownName)) {
            return;
        }
        if (!isPlayerChat) {
            // Hypixel system chat: skip our own echoes and non-chat announcements.
            if (isOwnEcho(text)) {
                return;
            }
            if (!text.contains(": ")) {
                return;
            }
        }
        String token = resolveToken(cfg, ownName);
        if (token.isEmpty()) {
            return;
        }
        if (text.toLowerCase().contains(token.toLowerCase())) {
            SoundManager.play(cfg.mentionAlertSound, cfg.mentionAlertVolume, cfg.mentionAlertPitch);
        }
    }

    /** True if {@code text} is our own message echoed back as "Name: <sent>". */
    private static boolean isOwnEcho(String text) {
        long now = System.currentTimeMillis();
        Iterator<Sent> it = SENT.iterator();
        while (it.hasNext()) {
            Sent s = it.next();
            if (now - s.time > ECHO_WINDOW_MS) {
                it.remove();
                continue;
            }
            if (text.endsWith(": " + s.text)) {
                return true;
            }
        }
        return false;
    }

    /** The custom text in full, or the first N characters of the game ID. */
    private static String resolveToken(ModConfig cfg, String gameId) {
        String custom = cfg.mentionAlertCustomText;
        if (custom != null && !custom.isBlank()) {
            return custom.trim();
        }
        if (gameId == null || gameId.isEmpty()) {
            return "";
        }
        int len = Math.min(Math.max(1, cfg.mentionAlertNameLength), gameId.length());
        return gameId.substring(0, len);
    }

    private static final class Sent {
        final String text;
        final long time;

        Sent(String text, long time) {
            this.text = text;
            this.time = time;
        }
    }
}
