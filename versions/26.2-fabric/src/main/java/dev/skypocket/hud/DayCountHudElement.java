package dev.skypocket.hud;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;
import dev.skypocket.i18n.Localization;
import dev.skypocket.render.HudText;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

/**
 * On-screen world-day counter, positionable and scalable like the clock.
 */
public final class DayCountHudElement {
    private static final Identifier ID = Identifier.fromNamespaceAndPath("skypocket", "day_count");

    private DayCountHudElement() {
    }

    public static void register() {
        HudElementRegistry.addLast(ID, DayCountHudElement::render);
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        ModConfig cfg = ConfigManager.get();
        HudPositionSync.sync(Minecraft.getInstance(), cfg);
        if (!cfg.dayCountBlock.dayCountEnabled) {
            return;
        }
        String text = format(cfg);
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(cfg.dayCountBlock.dayCountX, cfg.dayCountBlock.dayCountY);
        pose.scale(cfg.dayCountBlock.dayCountScale, cfg.dayCountBlock.dayCountScale);
        HudText.draw(graphics, cfg, text, 0, 0, 0xFFFFFFFF, true, cfg.dayCountBlock.dayCountScale);
        pose.popMatrix();
    }

    /** Current world day number (1-based), e.g. "第 42 天". */
    public static String format(ModConfig cfg) {
        Minecraft mc = Minecraft.getInstance();
        // getGameTime() is the absolute tick count synced from the server (via
        // setTimeFromServer); getOverworldClockTime() only carries the in-day clock
        // phase on the client, so dividing it would always yield day 1.
        long day = mc.level == null ? 1 : mc.level.getLevelData().getGameTime() / 24000 + 1;
        return String.format(Localization.tr("第 %d 天"), day);
    }
}
