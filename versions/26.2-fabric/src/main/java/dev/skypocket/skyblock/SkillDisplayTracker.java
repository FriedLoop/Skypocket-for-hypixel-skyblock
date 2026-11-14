package dev.skypocket.skyblock;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.net.SkyBlockDetector;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the skill-gain segment of SkyBlock's action bar.
 *
 * <p>The normal format is {@code +10.9 Combat (313,937.1/600,000)}. A complete
 * {@code total} is shown as-is; a zero total means max level and is rendered as
 * {@code (118,084)}. The percentage-only form is kept as a fallback instead of
 * inventing XP numbers when no level table is available.
 */
public final class SkillDisplayTracker {
    private static final Pattern SKILL = Pattern.compile(
            "\\+(?<gain>[0-9][0-9,.]*[kKmMbB]?)\\s+"
                    + "(?<skill>Farming|Mining|Combat|Foraging|Fishing|Enchanting)\\s+"
                    + "\\((?<progress>[^)]*)\\)",
            Pattern.CASE_INSENSITIVE);
    private static final DecimalFormat XP = new DecimalFormat("#,##0.##",
            DecimalFormatSymbols.getInstance(Locale.US));

    private static Skill skill;
    private static String gained;
    private static String current;
    private static String total;
    private static Double percent;
    private static boolean maxLevel;
    private static long visibleUntilMs;

    private SkillDisplayTracker() {
    }

    public static void init() {
        ClientReceiveMessageEvents.GAME.register(SkillDisplayTracker::onMessage);
    }

    private static void onMessage(Component message, boolean overlay) {
        if (!overlay) {
            return;
        }
        if (!SkyBlockDetector.isActive()) {
            reset();
            return;
        }
        if (!ConfigManager.get().skillDisplayBlock.skillDisplayEnabled) {
            reset();
            return;
        }
        accept(message.getString());
    }

    static void accept(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        Matcher matcher = SKILL.matcher(text);
        if (!matcher.find()) {
            return;
        }
        Skill next = Skill.byName(matcher.group("skill"));
        if (next == null) {
            return;
        }

        String progress = matcher.group("progress").trim();
        String nextCurrent = null;
        String nextTotal = null;
        Double nextPercent = null;
        boolean nextMax = false;
        if (progress.contains("/")) {
            String[] parts = progress.split("/", 2);
            Double currentValue = parse(parts[0]);
            Double totalValue = parse(parts[1]);
            if (currentValue == null || totalValue == null) {
                return;
            }
            nextCurrent = XP.format(currentValue);
            nextTotal = XP.format(totalValue);
            nextMax = totalValue == 0;
            nextPercent = totalValue <= 0 ? 100.0
                    : Math.min(100.0, Math.max(0.0, currentValue / totalValue * 100.0));
        } else if (progress.endsWith("%")) {
            nextPercent = parsePercent(progress);
            if (nextPercent == null) {
                return;
            }
            nextPercent = Math.min(100.0, Math.max(0.0, nextPercent));
            int level = SkillLevelTracker.level(next);
            int required = SkillXpTable.xpForNextLevel(next, level);
            if (required > 0) {
                nextCurrent = XP.format(required * nextPercent / 100.0);
                nextTotal = XP.format(required);
            }
            // Max level or unknown tab-list level: keep the server percentage,
            // rather than inventing a current total.
        } else {
            return;
        }

        String nextGained = formatXp(matcher.group("gain"));
        if (nextGained == null) {
            return;
        }
        skill = next;
        gained = nextGained;
        current = nextCurrent;
        total = nextTotal;
        percent = nextPercent;
        maxLevel = nextMax;
        visibleUntilMs = System.currentTimeMillis()
                + ConfigManager.get().skillDisplayBlock.skillDisplayDurationMs;
    }

    public static void reset() {
        skill = null;
        gained = null;
        current = null;
        total = null;
        percent = null;
        maxLevel = false;
        visibleUntilMs = 0;
    }

    public static Skill skill() {
        return skill;
    }

    public static String displayLine() {
        if (skill == null || gained == null) {
            return null;
        }
        String prefix = "+" + gained + " " + skill.displayName() + " ";
        if (ConfigManager.get().skillDisplayBlock.skillDisplayPercentageOnly && percent != null) {
            return prefix + "(" + formatPercent(percent) + "%)";
        }
        if (current != null && total != null) {
            return prefix + "(" + current + (maxLevel ? "" : "/" + total) + ")";
        }
        if (percent != null) {
            return prefix + "(" + formatPercent(percent) + "%)";
        }
        return null;
    }

    public static boolean visible() {
        return skill != null && displayLine() != null && System.currentTimeMillis() < visibleUntilMs;
    }

    /** A sample for the HUD position editor. */
    public static String previewLine() {
        if (ConfigManager.get().skillDisplayBlock.skillDisplayPercentageOnly) {
            return "+50 Farming (24.69%)";
        }
        return "+50 Farming (12,345/50,000)";
    }

    private static String formatPercent(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    /** Converts a SkyBlock magnitude (1.5k, 2m) and groups the result. */
    private static String formatXp(String raw) {
        Double value = parse(raw);
        return value == null ? null : XP.format(value);
    }

    private static Double parsePercent(String raw) {
        if (raw == null || !raw.endsWith("%")) {
            return null;
        }
        try {
            return Double.parseDouble(raw.substring(0, raw.length() - 1).replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim().replace(",", "");
        double multiplier = 1.0;
        char suffix = Character.toLowerCase(value.charAt(value.length() - 1));
        if (suffix == 'k' || suffix == 'm' || suffix == 'b') {
            multiplier = suffix == 'k' ? 1_000.0 : suffix == 'm' ? 1_000_000.0 : 1_000_000_000.0;
            value = value.substring(0, value.length() - 1);
        }
        try {
            return Double.parseDouble(value) * multiplier;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public enum Skill {
        FARMING("Farming", Items.DIAMOND_HOE),
        MINING("Mining", Items.DIAMOND_PICKAXE),
        COMBAT("Combat", Items.DIAMOND_SWORD),
        FORAGING("Foraging", Items.DIAMOND_AXE),
        FISHING("Fishing", Items.FISHING_ROD),
        ENCHANTING("Enchanting", Items.ENCHANTING_TABLE);

        private final String displayName;
        private final ItemStack icon;

        Skill(String displayName, net.minecraft.world.item.Item item) {
            this.displayName = displayName;
            this.icon = new ItemStack(item);
        }

        public String displayName() {
            return displayName;
        }

        public ItemStack icon() {
            return icon;
        }

        private static Skill byName(String name) {
            String needle = name.toLowerCase(Locale.ROOT);
            for (Skill value : values()) {
                if (value.displayName.toLowerCase(Locale.ROOT).equals(needle)) {
                    return value;
                }
            }
            return null;
        }
    }
}
