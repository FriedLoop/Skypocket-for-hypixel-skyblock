package dev.skypocket.hud;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;
import dev.skypocket.i18n.Localization;
import dev.skypocket.render.HudText;
import dev.skypocket.skyblock.MiningAbility;
import dev.skypocket.skyblock.MiningAbilityTracker;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

import java.util.List;

/**
 * Draggable HUD element showing the pickaxe ability state:
 * {@code "<ability name>: <n>s"} while cooling, {@code "<ability name>: available"}
 * once ready (auto-hidden after the 30s grace period without a use). Registered like
 * the clock; the position/scale are edited in the unified HUD position editor.
 */
public final class MiningAbilityHudElement {
    private static final Identifier ID = Identifier.fromNamespaceAndPath("skypocket", "mining_ability");

    private MiningAbilityHudElement() {
    }

    public static void register() {
        HudElementRegistry.addLast(ID, MiningAbilityHudElement::render);
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        // Poll first (throttled inside) so reminders fire even while the HUD is hidden.
        MiningAbilityTracker.update(mc);
        ModConfig cfg = ConfigManager.get();
        HudPositionSync.sync(mc, cfg);
        if (!cfg.miningAbilityReminderEnabled) {
            return;
        }
        String line = MiningAbilityTracker.hudLine();
        if (line != null) {
            Matrix3x2fStack pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(cfg.miningAbilityX, cfg.miningAbilityY);
            pose.scale(cfg.miningAbilityScale, cfg.miningAbilityScale);
            HudText.draw(graphics, cfg, line, 0, 0, MiningAbilityTracker.hudColor(), true,
                    cfg.miningAbilityScale);
            pose.popMatrix();
        }
        renderReminder(graphics, cfg);
    }

    /**
     * Draws the colored reminder ("<name> available!") at its own draggable
     * position, fading out over the last 500ms of the 3s window.
     */
    private static void renderReminder(GuiGraphicsExtractor graphics, ModConfig cfg) {
        long remaining = MiningAbilityTracker.reminderRemainingMs();
        MiningAbility ability = MiningAbilityTracker.reminderAbility();
        if (remaining <= 0 || ability == null) {
            return;
        }
        int fade = remaining < 500 ? (int) (255 * remaining / 500) : 255;
        int nameAlpha = (cfg.miningAbilityNameColor >>> 24) * fade / 255;
        int availAlpha = (cfg.miningAbilityAvailableColor >>> 24) * fade / 255;
        String name = ability.matchName + " ";
        String avail = Localization.tr("可用!");
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(cfg.miningAbilitySubtitleX, cfg.miningAbilitySubtitleY);
        pose.scale(cfg.miningAbilitySubtitleScale, cfg.miningAbilitySubtitleScale);
        HudText.draw(graphics, cfg, name, 0, 0,
                (nameAlpha << 24) | (cfg.miningAbilityNameColor & 0xFFFFFF), true,
                cfg.miningAbilitySubtitleScale);
        HudText.draw(graphics, cfg, avail,
                HudText.width(cfg, name, cfg.miningAbilitySubtitleScale), 0,
                (availAlpha << 24) | (cfg.miningAbilityAvailableColor & 0xFFFFFF), true,
                cfg.miningAbilitySubtitleScale);
        pose.popMatrix();
    }

    /** Current HUD text (or a sample) for the HUD position editor preview. */
    public static List<String> previewLines() {
        String line = MiningAbilityTracker.hudLine();
        if (line != null) {
            return List.of(line);
        }
        return List.of(MiningAbility.MINING_SPEED_BOOST.matchName + ": 60s");
    }

    /** Reminder text (or a sample) for the HUD position editor preview. */
    public static List<String> subtitlePreviewLines() {
        MiningAbility ability = MiningAbilityTracker.reminderAbility();
        String name = (ability != null ? ability : MiningAbility.MINING_SPEED_BOOST).matchName;
        return List.of(name + " " + Localization.tr("可用!"));
    }
}
