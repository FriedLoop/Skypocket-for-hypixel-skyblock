package dev.skypocket.skyblock;

import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the current level of a skill from Hypixel's tab list.
 *
 * <p>The tab list is the only server-provided level source. It is parsed on demand
 * (only when an ActionBar line has a percentage instead of current/total), so the
 * normal XP path never scans the tab list.
 */
public final class SkillLevelTracker {
    private SkillLevelTracker() {
    }

    public static int level(SkillDisplayTracker.Skill skill) {
        if (skill == null) {
            return -1;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return -1;
        }
        Pattern pattern = Pattern.compile("(?i)\\b" + Pattern.quote(skill.displayName())
                + "\\s+(\\d+)\\s*:\\s*(?:MAX|[\\d.,]+%?)");
        List<String> lines = TabListText.allLines(mc);
        for (String line : lines) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                try {
                    return Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException ignored) {
                    return -1;
                }
            }
        }
        return -1;
    }
}
