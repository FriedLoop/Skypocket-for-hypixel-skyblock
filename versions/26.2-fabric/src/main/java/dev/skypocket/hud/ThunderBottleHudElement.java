package dev.skypocket.hud;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;
import dev.skypocket.render.HudText;
import dev.skypocket.skyblock.ThunderBottleTracker;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix3x2fStack;

import java.util.List;

/** Thunder bottle icon + charge text. */
public final class ThunderBottleHudElement {
    private static final Identifier ID = Identifier.fromNamespaceAndPath("skypocket", "thunder_bottle");
    public static final int ICON_SIZE = 16;
    public static final int TEXT_GAP = 20;

    private ThunderBottleHudElement() {
    }

    public static void register() {
        HudElementRegistry.addLast(ID, ThunderBottleHudElement::render);
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        ModConfig cfg = ConfigManager.get();
        HudPositionSync.sync(Minecraft.getInstance(), cfg);
        if (!cfg.thunderBottleBlock.thunderBottleEnabled || !ThunderBottleTracker.visible()) {
            return;
        }
        String line = ThunderBottleTracker.line();
        if (line == null) {
            return;
        }
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(cfg.thunderBottleBlock.thunderBottleX, cfg.thunderBottleBlock.thunderBottleY);
        pose.scale(cfg.thunderBottleBlock.thunderBottleScale, cfg.thunderBottleBlock.thunderBottleScale);
        drawContents(graphics, cfg, cfg.thunderBottleBlock.thunderBottleScale, line,
                ThunderBottleTracker.icon());
        pose.popMatrix();
    }

    public static void preview(GuiGraphicsExtractor graphics, ModConfig cfg) {
        if (!cfg.thunderBottleBlock.thunderBottleEnabled) {
            return;
        }
        String line = ThunderBottleTracker.line();
        ItemStack icon = ThunderBottleTracker.icon();
        if (line == null || icon.isEmpty()) {
            line = ThunderBottleTracker.previewLine();
            icon = new ItemStack(Items.PLAYER_HEAD);
        }
        drawContents(graphics, cfg, cfg.thunderBottleBlock.thunderBottleScale, line, icon);
    }

    private static void drawContents(GuiGraphicsExtractor graphics, ModConfig cfg, float scale,
                                    String line, ItemStack icon) {
        graphics.item(icon, 0, 0);
        int y = (ICON_SIZE - HudText.lineHeight(cfg)) / 2;
        HudText.draw(graphics, cfg, line, TEXT_GAP, y, cfg.thunderBottleBlock.thunderBottleColor, true, scale);
    }

    public static List<String> previewLines() {
        String line = ThunderBottleTracker.line();
        return List.of(line == null ? ThunderBottleTracker.previewLine() : line);
    }
}
