package dev.skypocket.skyblock;

import dev.skypocket.chat.ChatText;
import dev.skypocket.net.SkyBlockDetector;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The player's SkyBlock numbers, scraped out of the action bar.
 *
 * <p><b>Why not the entity.</b> In SkyBlock the real health, defence and mana are settled on the
 * server; the client entity does not carry them (shields, damage reduction and hidden modifiers are
 * all absent from {@code getHealth()}), so the action bar text is the only honest source. The one
 * exception is {@link #speed()}, which is the client's own movement speed and is labelled as such.
 *
 * <p><b>Colour is data.</b> Hypixel writes the same "1234/1234" with different colours depending on
 * what it means - {@code §c} normal health against {@code §6} overflow, {@code §b} mana against
 * {@code §3} overflow mana - so the text is kept as its {@code §}-coded form (see {@link ChatText})
 * rather than flattened.
 *
 * <p><b>Missing means unchanged, not zero.</b> Hypixel only includes a field when its value changed,
 * so a pattern that does not match leaves the previous value alone. The two exceptions are the ones
 * whose absence <em>is</em> the information: overflow mana drops to zero and vitality is marked
 * hidden. Both are what the reference implementation does, down to which way round that asymmetry
 * goes.
 */
public final class PlayerStatsTracker {
    private static final dev.skypocket.SkyPocketLog LOGGER =
            dev.skypocket.SkyPocketLog.of("stats");

    /** Health as {@code current/max}, in either of the two colours (see the class comment). */
    private static final Pattern HEALTH = Pattern.compile("§([c6])([\\d,]+)/([\\d,]+)[\uE010❤]");
    /** A lone gold heart number, i.e. absorption hearts (never followed by {@code /}). */
    private static final Pattern ABSORPTION = Pattern.compile("§6([\\d,]+)[\uE010❤](?!/)");
    private static final Pattern MANA = Pattern.compile("§b([\\d,]+)/([\\d,]+)[\uE003✎]");
    private static final Pattern OVERFLOW_MANA = Pattern.compile("§3([\\d,]+)[\uE017ʬ]");
    /** A cast that was paid for: subtract, it is not a field of its own. */
    private static final Pattern MANA_SPENT = Pattern.compile("§b-([\\d,]+) Mana");
    /**
     * Defence. The anchor is the colour, not the glyph: the symbol after the number is resource-pack
     * art, and keying on a specific codepoint fails the moment the pack draws a different one. A green
     * number that is not a percentage, a decimal, a fraction or a timer is defence.
     *
     * <p>The number is possessive and must be followed by a non-number/non-separator. Without that,
     * the dark-green {@code §2} prefix of a drill line (for example {@code §246,921/50,000 Drill})
     * backtracked into {@code 46} and overwrote the defence stat.
     */
    private static final Pattern DEFENSE = Pattern.compile(
            "§[a2](\\d[\\d,]*+)(?![\\d,./%s])");
    /** Vitality as a pair; the trailing symbol is pack art and therefore optional. */
    private static final Pattern VITALITY = Pattern.compile("§4([\\d.,]+)/([\\d.,]+)");

    /** How many captured lines to report while the format is being confirmed. */
    private static final int REPORT_LINES = 3;

    private static int health;
    private static int maxHealth;
    private static int absorption;
    private static int mana;
    private static int maxMana;
    private static int overflowMana;
    private static int defense;
    /** Vitality is published with integer precision - its maximum comes from the same action bar pair. */
    private static int vitality;
    private static int maxVitality;
    private static boolean vitalityShown;
    private static boolean hasData;
    private static int reported;

    private PlayerStatsTracker() {
    }

    /** Registers the action bar hook. Called once from the client initializer. */
    public static void init() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) {
                return; // the action bar is the overlay game message; chat is not a stats line
            }
            if (!SkyBlockDetector.isActive()) {
                reset();
                return;
            }
            accept(ChatText.formatted(message));
        });
    }

    /** Forgets everything: called when leaving SkyBlock, so no stale value can be displayed. */
    public static void reset() {
        health = 0;
        maxHealth = 0;
        absorption = 0;
        mana = 0;
        maxMana = 0;
        overflowMana = 0;
        defense = 0;
        vitality = 0;
        maxVitality = 0;
        vitalityShown = false;
        hasData = false;
    }

    /** True once an action bar line carrying numbers has been seen in this session. */
    public static boolean hasData() {
        return hasData;
    }

    /**
     * Parses one action bar line. Package-private and free of game state (beyond the fields it fills)
     * so the patterns can be exercised without a client.
     */
    static void accept(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        boolean seen = false;

        Matcher healthMatch = HEALTH.matcher(text);
        if (healthMatch.find()) {
            health = number(healthMatch.group(2), health);
            maxHealth = number(healthMatch.group(3), maxHealth);
            seen = true;
            // Absorption. In the real action bar the "current health" already includes it - that is
            // what makes the number exceed the maximum, and the gold colour marks exactly that state
            // (the reference implementation reads the same two fields) - so it is the part above the
            // maximum, and a line carrying the pair is what makes that authoritative.
            absorption = Math.max(0, health - maxHealth);
        }
        // A lone gold number overrides it; a line with neither keeps whatever was last seen, because
        // SkyBlock drops fields it is not currently changing and "not mentioned" is not "zero".
        Matcher absorptionMatch = ABSORPTION.matcher(text);
        if (absorptionMatch.find()) {
            absorption = number(absorptionMatch.group(1), absorption);
        }

        Matcher manaMatch = MANA.matcher(text);
        if (manaMatch.find()) {
            mana = number(manaMatch.group(1), mana);
            maxMana = number(manaMatch.group(2), maxMana);
            seen = true;
        }
        // Kept, not zeroed, when the line does not mention it: the server only prints a stat while it
        // is being consumed, so its absence means "unchanged", exactly like every other field here.
        // (The reference implementation zeroes this one; that is the behaviour this deliberately
        // drops, because it makes the display flicker to empty between casts.)
        Matcher overflowMatch = OVERFLOW_MANA.matcher(text);
        if (overflowMatch.find()) {
            overflowMana = number(overflowMatch.group(1), overflowMana);
        }

        // A cast that was paid for is a subtraction, not a field of its own.
        Matcher spentMatch = MANA_SPENT.matcher(text);
        if (spentMatch.find()) {
            mana = Math.max(0, mana - number(spentMatch.group(1), 0));
        }
        if (text.contains("NOT ENOUGH MANA")) {
            mana = 0;
        }

        Matcher defenseMatch = DEFENSE.matcher(text);
        if (defenseMatch.find()) {
            defense = number(defenseMatch.group(1), defense);
            seen = true;
        }

        // Once vitality has been seen it stays on screen with its last value, for the same reason as
        // overflow mana: the server stops printing it between uses, and a row that blinks out is worse
        // than one showing a slightly old number.
        Matcher vitalityMatch = VITALITY.matcher(text);
        if (vitalityMatch.find()) {
            vitality = number(vitalityMatch.group(1), vitality);
            maxVitality = number(vitalityMatch.group(2), maxVitality);
            vitalityShown = true;
        }

        if (seen) {
            hasData = true;
            report(text);
        }
    }

    /**
     * Digits with the thousands separators removed. A stray decimal is rounded rather than rejected:
     * every value here is an integer, and tolerating one keeps a change of published precision from
     * freezing the field at its previous value. An unparsable value does keep the previous one.
     */
    private static int number(String raw, int previous) {
        String cleaned = raw.replace(",", "");
        try {
            return Integer.parseInt(cleaned);
        } catch (NumberFormatException e) {
            try {
                return (int) Math.round(Double.parseDouble(cleaned));
            } catch (NumberFormatException ignored) {
                return previous;
            }
        }
    }

    /**
     * Reports the first few captures, so the action bar format can be confirmed against the real
     * server rather than only against its documentation - the values below are exactly what the
     * colours were understood to mean.
     */
    private static void report(String text) {
        if (reported >= REPORT_LINES) {
            return;
        }
        reported++;
        LOGGER.info("action bar {}: hp={}/{} absorption={} mana={}/{}+{} defense={} vitality={} raw={}",
                reported, health, maxHealth, absorption, mana, maxMana, overflowMana, defense,
                vitalityShown ? vitality + "/" + maxVitality : "hidden", text);
    }

    // ---- Values. Every one of them is server-settled except speed. ----

    public static int health() {
        return health;
    }

    public static int maxHealth() {
        return maxHealth;
    }

    /** Absorption hearts, in health points (drawn over the health icons). */
    public static int absorption() {
        return absorption;
    }

    public static int mana() {
        return mana;
    }

    public static int maxMana() {
        return maxMana;
    }

    /** Overflow mana: a real, separate value that can be spent like mana. */
    public static int overflowMana() {
        return overflowMana;
    }

    public static int defense() {
        return defense;
    }

    public static int vitality() {
        return vitality;
    }

    /** The maximum vitality, as published by the action bar - it is a pair, like health and mana. */
    public static int maxVitality() {
        return maxVitality;
    }

    public static boolean vitalityShown() {
        return vitalityShown;
    }

    /**
     * Movement speed, in the same units SkyBlock's action bar uses. The only value taken from the
     * client entity, because SkyBlock does not publish it - treat it as an estimate.
     */
    public static int speed() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return 0;
        }
        return Math.round(mc.player.getAbilities().getWalkingSpeed() * 1000f);
    }

    /** Convenience for the display code: the fraction of the mana bar that overflow would cover. */
    public static float overflowFraction() {
        return maxMana <= 0 ? 0f : (float) overflowMana / (float) maxMana;
    }
}
