package dev.skypocket.chat;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;
import dev.skypocket.mixin.ChatComponentAccessor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Collapses repeated chat messages into one line with a counter.
 *
 * <p>Same rule as the reference mod: a message is compacted when the <em>same</em> message was seen
 * within the window, regardless of what else arrived in between - comparing against neighbouring lines
 * breaks as soon as a server sends a divider or an unrelated message in the middle. The previous line is
 * removed and the new one is the one that stays, so the counter always describes a contiguous run of
 * that message.
 *
 * <p>Dividers (a line of {@code -}, {@code =} or {@code ▬}) never compact: on Hypixel they bracket a
 * group of lines, and collapsing them would eat the block they delimit.
 */
public final class ChatCompactor {

    /** One run of a message: how many times it was seen, when, and the line currently showing it. */
    private record Entry(int count, long lastSeenMs, GuiMessage line) {
    }

    private static final Map<Component, Entry> SEEN = new HashMap<>();

    /** Keeps the map from growing without bound in a long session. */
    private static final int MAX_ENTRIES = 256;

    /**
     * The message being added right now.
     *
     * <p>{@code addMessage} builds the {@link GuiMessage} itself, so the handle can only be taken after
     * the call - the key and the counter are parked here in between.
     */
    private static Component pendingKey;
    private static int pendingCount;
    private static long pendingSeenMs;

    private ChatCompactor() {
    }

    /**
     * Called before the message is added: removes the previous line of the same message and decorates the
     * text with the counter.
     *
     * @return the text to add - the original when this is a first occurrence
     */
    public static Component incoming(ChatComponent hud, Component text) {
        ModConfig cfg = ConfigManager.get();
        // Cleared first: a message that is not compacted must not leave the previous run's key behind,
        // or `remember` would attach the new line to the wrong message.
        pendingKey = null;
        if (!cfg.chatCompactEnabled || text == null || isDivider(text)) {
            return text;
        }

        long now = System.currentTimeMillis();
        long windowMs = Math.max(1, cfg.chatCompactTimeoutSeconds) * 1000L;
        Entry previous = SEEN.get(text);

        int count = 1;
        if (previous != null && now - previous.lastSeenMs() <= windowMs) {
            count = previous.count() + 1;
            remove(hud, previous.line());
        }

        pendingKey = text;
        pendingCount = count;
        pendingSeenMs = now;
        prune(now, windowMs);

        if (count == 1) {
            return text;
        }
        // Gray so the counter never inherits the message's own colours (rank prefixes, rarity colours).
        return text.copy().append(Component.literal(" §7(x" + count + ")"));
    }

    /**
     * Called when the chat actually builds the message object, i.e. from {@code addMessageToQueue} /
     * {@code addMessageToDisplayQueue}.
     *
     * <p>Reading the tail of {@code allMessages} after {@code addMessage} returned picked up whatever
     * message happened to be last in the list - chat queues the entry first - so repeated lines removed
     * an unrelated message and piled up instead of collapsing.
     */
    public static void onCreated(GuiMessage message) {
        Component key = pendingKey;
        if (key == null || message == null) {
            return;
        }
        pendingKey = null;
        SEEN.put(key, new Entry(pendingCount, pendingSeenMs, message));
    }

    /** Removes a message from both the model and the wrapped-line list the renderer reads. */
    private static void remove(ChatComponent hud, GuiMessage line) {
        if (line == null) {
            return;
        }
        ChatComponentAccessor accessor = (ChatComponentAccessor) hud;
        accessor.skypocket$allMessages().remove(line);
        accessor.skypocket$trimmedMessages().removeIf(entry -> entry.parent() == line);
    }

    /**
     * Hypixel-style separator: after stripping colours, nothing but dashes and heavy lines.
     *
     * <p>{@code getString()} already drops the styling, so no chat-formatting stripping is needed.
     */
    private static boolean isDivider(Component text) {
        String plain = text.getString();
        if (plain.length() <= 5) {
            return false;
        }
        for (int i = 0; i < plain.length(); i++) {
            char c = plain.charAt(i);
            if (c != '-' && c != '=' && c != '\u25AC') {
                return false;
            }
        }
        return true;
    }

    /** Drops runs that fell out of the window, and hard-caps the map. */
    private static void prune(long now, long windowMs) {
        if (SEEN.size() < MAX_ENTRIES) {
            return;
        }
        SEEN.entrySet().removeIf(entry -> now - entry.getValue().lastSeenMs() > windowMs);
    }

    /** Drops all state, e.g. when leaving a world. */
    public static void clear() {
        SEEN.clear();
        pendingKey = null;
    }
}
