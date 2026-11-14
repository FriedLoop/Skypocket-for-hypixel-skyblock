package dev.skypocket.hud;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;
import dev.skypocket.i18n.Localization;
import dev.skypocket.net.SkyBlockDetector;
import dev.skypocket.render.HudText;
import dev.skypocket.sound.SoundManager;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

/** Centre-screen warning when a Legendary/Mythic sea creature spawn message arrives. */
public final class LegendarySeaCreatureWarningHudElement {
    private static final Identifier ID = Identifier.fromNamespaceAndPath("skypocket", "legendary_sea_creature");
    private static volatile long visibleUntilMs;

    private LegendarySeaCreatureWarningHudElement() {
    }

    public static void register() {
        HudElementRegistry.addLast(ID, LegendarySeaCreatureWarningHudElement::render);
        ClientReceiveMessageEvents.GAME.register(LegendarySeaCreatureWarningHudElement::onMessage);
    }

    private static void onMessage(Component message, boolean overlay) {
        if (overlay) {
            return;
        }
        ModConfig cfg = ConfigManager.get();
        if (!cfg.legendarySeaCreatureWarningBlock.legendarySeaCreatureWarning || !SkyBlockDetector.isActive()) {
            return;
        }
        // Hypixel's spawn messages usually contain neither the rarity nor the words
        // "sea creature"; match the exact message against the local table, like SBA.
        if (!dev.skypocket.skyblock.SeaCreatureMessages.isLegendaryOrAbove(message.getString())) {
            return;
        }
        visibleUntilMs = System.currentTimeMillis() + cfg.legendarySeaCreatureWarningBlock.legendaryWarningDurationMs;
        SoundManager.play("minecraft:entity.experience_orb.pickup", 0.5f, 1.0f);
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        ModConfig cfg = ConfigManager.get();
        if (!cfg.legendarySeaCreatureWarningBlock.legendarySeaCreatureWarning || System.currentTimeMillis() >= visibleUntilMs) {
            return;
        }
        String text = Localization.tr("传奇海怪出现!");
        float scale = 2.0f;
        int width = HudText.width(cfg, text, scale);
        int x = (graphics.guiWidth() - Math.round(width * scale)) / 2;
        int y = graphics.guiHeight() / 2 - 24;
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        HudText.draw(graphics, cfg, text, 0, 0, cfg.legendarySeaCreatureWarningBlock.legendaryWarningColor, true, scale);
        pose.popMatrix();
    }
}
