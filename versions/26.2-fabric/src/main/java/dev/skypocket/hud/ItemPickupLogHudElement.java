package dev.skypocket.hud;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;
import dev.skypocket.render.HudText;
import dev.skypocket.skyblock.ItemPickupTracker;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

import java.util.List;

/** Recent inventory gains and losses, rendered as green/red log lines. */
public final class ItemPickupLogHudElement {
    private static final Identifier ID = Identifier.fromNamespaceAndPath("skypocket", "item_pickup_log");
    private static final int TEXT_GAP = 4;
    private static final int LINE_GAP = 2;

    private ItemPickupLogHudElement() {
    }

    public static void register() {
        HudElementRegistry.addLast(ID, ItemPickupLogHudElement::render);
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        ModConfig cfg = ConfigManager.get();
        HudPositionSync.sync(Minecraft.getInstance(), cfg);
        if (!cfg.pickupLogEnabled) {
            return;
        }
        List<ItemPickupTracker.Entry> entries = ItemPickupTracker.entries();
        if (entries.isEmpty()) {
            return;
        }
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(cfg.pickupLogX, cfg.pickupLogY);
        pose.scale(cfg.pickupLogScale, cfg.pickupLogScale);
        int y = 0;
        int lineHeight = HudText.lineHeight(cfg) + LINE_GAP;
        for (ItemPickupTracker.Entry entry : entries) {
            String count = count(entry.amount);
            HudText.draw(graphics, cfg, count, 0, y, countColor(entry.amount), true,
                    cfg.pickupLogScale);
            int x = HudText.width(cfg, count, cfg.pickupLogScale) + TEXT_GAP;
            HudText.draw(graphics, cfg, entry.name, x, y, entry.rarityColor, true,
                    cfg.pickupLogScale);
            y += lineHeight;
        }
        pose.popMatrix();
    }

    public static List<String> previewLines() {
        return List.of("+ 1x Enchanted Diamond", "- 2x Rough Diamond");
    }

    private static String count(int amount) {
        return (amount > 0 ? "+ " : "- ") + Math.abs(amount) + "x";
    }

    private static int countColor(int amount) {
        return amount > 0 ? 0xFF55FF55 : 0xFFFF5555;
    }
}
