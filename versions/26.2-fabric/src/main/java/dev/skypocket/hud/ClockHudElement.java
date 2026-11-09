package dev.skypocket.hud;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;
import dev.skypocket.render.HudText;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * On-screen clock showing the real-world time.
 *
 * <p>Uses the same {@code HudElementRegistry} as the crosshair but registered with
 * {@code addLast} so it becomes a new HUD element. Text is rendered with the
 * default Minecraft font (the one resource packs can replace) and scaled via the
 * {@code GuiGraphicsExtractor}'s 2D pose stack.
 */
public final class ClockHudElement {
    private static final Identifier ID = Identifier.fromNamespaceAndPath("skypocket", "clock");
    private static final DateTimeFormatter H24 = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter H12 = DateTimeFormatter.ofPattern("hh:mm:ss a");

    private ClockHudElement() {
    }

    public static void register() {
        HudElementRegistry.addLast(ID, ClockHudElement::render);
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        ModConfig config = ConfigManager.get();
        HudPositionSync.sync(Minecraft.getInstance(), config);
        if (!config.clockEnabled) {
            return;
        }
        String text = formatTime(config);
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(config.clockX, config.clockY);
        pose.scale(config.clockScale, config.clockScale);
        HudText.draw(graphics, config, text, 0, 0, 0xFFFFFFFF, true, config.clockScale);
        pose.popMatrix();
    }

    /** Formats the current wall-clock time according to the 12/24h setting. */
    public static String formatTime(ModConfig config) {
        return LocalTime.now().format(config.clockFormat24h ? H24 : H12);
    }
}
