package dev.skypocket.hud;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;
import dev.skypocket.render.HudText;
import dev.skypocket.skyblock.DrillFuelTracker;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

import java.util.List;

/** Drill fuel bar; the bar artwork is the shared StatsBar texture. */
public final class DrillFuelBarHudElement {
    private static final Identifier ID = Identifier.fromNamespaceAndPath("skypocket", "drill_fuel_bar");

    private DrillFuelBarHudElement() {
    }

    public static void register() {
        HudElementRegistry.addLast(ID, DrillFuelBarHudElement::render);
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        ModConfig cfg = ConfigManager.get();
        HudPositionSync.sync(Minecraft.getInstance(), cfg);
        if (!cfg.drillFuelBarBlock.drillFuelBarEnabled || !DrillFuelTracker.visible()) {
            return;
        }
        draw(graphics, cfg, cfg.drillFuelBarBlock.drillFuelBarX, cfg.drillFuelBarBlock.drillFuelBarY, cfg.drillFuelBarBlock.drillFuelBarScale,
                DrillFuelTracker.fraction(), DrillFuelTracker.line());
    }

    /** Preview at the current pose origin; the position editor owns translation and scale. */
    public static void preview(GuiGraphicsExtractor graphics, ModConfig cfg) {
        if (!cfg.drillFuelBarBlock.drillFuelBarEnabled) {
            return;
        }
        StatsBarHudElement.drawPlainBar(graphics, cfg, 0, 0,
                cfg.drillFuelBarBlock.drillFuelBarWidth, cfg.drillFuelBarBlock.drillFuelBarHeight, 0.7f, cfg.drillFuelBarBlock.drillFuelBarColor,
                DrillFuelTracker.previewLine(), cfg.drillFuelBarBlock.drillFuelShowNumber);
    }

    private static void draw(GuiGraphicsExtractor graphics, ModConfig cfg, int x, int y,
                             float scale, float fraction, String label) {
        if (!cfg.drillFuelBarBlock.drillFuelBarEnabled) {
            return;
        }
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        StatsBarHudElement.drawPlainBar(graphics, cfg, 0, 0,
                cfg.drillFuelBarBlock.drillFuelBarWidth, cfg.drillFuelBarBlock.drillFuelBarHeight, fraction, cfg.drillFuelBarBlock.drillFuelBarColor,
                label, cfg.drillFuelBarBlock.drillFuelShowNumber);
        pose.popMatrix();
    }

    public static int width(ModConfig cfg, float scale) {
        return Math.round(cfg.drillFuelBarBlock.drillFuelBarWidth * scale);
    }

    public static int height(ModConfig cfg, float scale) {
        return Math.round(cfg.drillFuelBarBlock.drillFuelBarHeight * scale);
    }

    /** The editor's stand-in when no drill data has been seen yet. */
    public static List<String> previewLines() {
        return List.of("1,798/3,000");
    }
}
