package dev.skypocket.feature.config;

/**
 * Persisted state for the <code>chat_compact</code> feature.
 *
 * <p>Fields keep the names they had on ModConfig so the rewrite of every read site is an
 * insertion rather than a rename. Defaults are the ones the flat fields carried.
 */
public final class ChatCompactConfig {
    public boolean chatCompactEnabled = true;
    public int chatCompactTimeoutSeconds = 30;
}
