package dev.skypocket.render;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;
import dev.skypocket.net.SkyBlockDetector;
import dev.skypocket.skyblock.PlayerStatsTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Gate and entry point for the SkyBlock health/mana display.
 *
 * <p>Only the classic style draws here, in the region the vanilla bars occupy. The modern style is a
 * set of six HUD elements of its own ({@code StatsBarHudElement}) so that each bar can be placed and
 * sized individually in the position editor - which also means the two styles cannot be drawn at the
 * same time by accident.
 *
 * <p>The gate is deliberately not just "the module is on". Out of SkyBlock the vanilla hearts have to
 * stay exactly as they were - they are fed by the client entity, which is only wrong on SkyBlock - and
 * the tracker has to be cleared, or leaving the island would leave the last captured numbers on screen
 * forever (there is no packet that says "stats over"; see {@link PlayerStatsTracker}).
 */
public final class StatsOverlayRenderer {
    private StatsOverlayRenderer() {
    }

    /**
     * Whether the SkyBlock display should take over the vanilla health area right now. Also resets the
     * captured numbers whenever the answer is "no, because we are not in SkyBlock".
     */
    public static boolean active() {
        if (!SkyBlockDetector.isActive()) {
            PlayerStatsTracker.reset();
            return false;
        }
        return ConfigManager.get().statsEnabled;
    }

    /** Draws the classic icons; called from the HUD mixin at the end of the vanilla health block. */
    public static void draw(GuiGraphicsExtractor graphics) {
        if (!active()) {
            return;
        }
        ModConfig cfg = ConfigManager.get();
        if (!cfg.statsModernStyle) {
            VanillaStatsStyle.draw(graphics, cfg);
        }
    }
}
