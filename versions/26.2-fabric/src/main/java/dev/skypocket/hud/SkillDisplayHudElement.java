package dev.skypocket.hud;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;
import dev.skypocket.render.HudText;
import dev.skypocket.skyblock.SkillDisplayTracker;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

import java.util.List;

/** Shows the skill that most recently gained XP, with its vanilla item icon. */
public final class SkillDisplayHudElement {
    private static final Identifier ID = Identifier.fromNamespaceAndPath("skypocket", "skill_display");
    public static final int ICON_SIZE = 16;
    public static final int TEXT_GAP = 20;

    private SkillDisplayHudElement() {
    }

    public static void register() {
        HudElementRegistry.addLast(ID, SkillDisplayHudElement::render);
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        ModConfig cfg = ConfigManager.get();
        HudPositionSync.sync(Minecraft.getInstance(), cfg);
        if (!cfg.skillDisplayBlock.skillDisplayEnabled || !SkillDisplayTracker.visible()) {
            return;
        }
        String line = line();
        SkillDisplayTracker.Skill skill = SkillDisplayTracker.skill();
        if (skill == null || line == null) {
            return;
        }
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(cfg.skillDisplayBlock.skillDisplayX, cfg.skillDisplayBlock.skillDisplayY);
        pose.scale(cfg.skillDisplayBlock.skillDisplayScale, cfg.skillDisplayBlock.skillDisplayScale);
        drawContents(graphics, cfg, cfg.skillDisplayBlock.skillDisplayScale, line, skill);
        pose.popMatrix();
    }

    private static String line() {
        return SkillDisplayTracker.displayLine();
    }

    public static List<String> previewLines() {
        String line = line();
        return List.of(line == null ? SkillDisplayTracker.previewLine() : line);
    }

    /** Draws at the current pose origin; the caller owns translation and scale. */
    public static void drawContents(GuiGraphicsExtractor graphics, ModConfig cfg, float scale,
                                    String line, SkillDisplayTracker.Skill skill) {
        graphics.item(skill.icon(), 0, 0);
        int y = (ICON_SIZE - HudText.lineHeight(cfg)) / 2;
        HudText.draw(graphics, cfg, line, TEXT_GAP, y, cfg.skillDisplayBlock.skillDisplayColor, true, scale);
    }

    /** Editor preview uses the same icon/text layout as the live HUD. */
    public static void preview(GuiGraphicsExtractor graphics, ModConfig cfg) {
        SkillDisplayTracker.Skill skill = SkillDisplayTracker.skill();
        String line = line();
        if (skill == null || line == null) {
            skill = SkillDisplayTracker.Skill.FARMING;
            line = SkillDisplayTracker.previewLine();
        }
        drawContents(graphics, cfg, cfg.skillDisplayBlock.skillDisplayScale, line, skill);
    }

}
