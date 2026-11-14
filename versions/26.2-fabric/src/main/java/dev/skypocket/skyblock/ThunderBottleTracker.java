package dev.skypocket.skyblock;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.net.SkyBlockDetector;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Finds the first empty Thunder/Storm/Hurricane bottle in the player inventory. */
public final class ThunderBottleTracker {
    private static final DecimalFormat NUMBER = new DecimalFormat("#,##0",
            DecimalFormatSymbols.getInstance(Locale.US));

    private static final dev.skypocket.SkyPocketLog LOGGER =
            dev.skypocket.SkyPocketLog.of("thunderBottle");
    private static ItemStack icon = ItemStack.EMPTY;
    private static int charge;
    private static int capacity;
    private static long lastScanMs;
    private static long lastProbeLogMs;

    private ThunderBottleTracker() {
    }

    public static void tick(Minecraft mc) {
        if (!ConfigManager.get().thunderBottleBlock.thunderBottleEnabled || !SkyBlockDetector.isActive()
                || mc.player == null) {
            clear();
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastScanMs < 250) {
            return;
        }
        lastScanMs = now;
        ItemStack fallbackIcon = ItemStack.EMPTY;
        int fallbackCharge = 0;
        int fallbackCapacity = 0;
        for (ItemStack stack : mc.player.getInventory().getNonEquipmentItems()) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            int cap = capacity(SkyBlockItem.id(stack),
                    stack.getHoverName().getString());
            if (cap <= 0) {
                continue;
            }
            int current = Math.max(0, SkyBlockItem.intValue(stack, "thunder_charge", 0));
            // Prefer the first bottle that is not full. A full bottle is only a fallback when
            // every matching bottle in the inventory is full.
            if (current < cap) {
                icon = stack.copy();
                charge = current;
                capacity = cap;
                return;
            }
            if (fallbackIcon.isEmpty()) {
                fallbackIcon = stack.copy();
                fallbackCharge = current;
                fallbackCapacity = cap;
            }
        }
        if (!fallbackIcon.isEmpty()) {
            icon = fallbackIcon;
            charge = fallbackCharge;
            capacity = fallbackCapacity;
            return;
        }
        if (now - lastProbeLogMs > 3000) {
            lastProbeLogMs = now;
            StringBuilder names = new StringBuilder();
            for (ItemStack stack : mc.player.getInventory().getNonEquipmentItems()) {
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                if (!names.isEmpty()) {
                    names.append(" | ");
                }
                names.append(stack.getHoverName().getString());
            }
            LOGGER.info("thunder bottle scan: no match; inventory='{}'", names);
        }
        clear();
    }

    public static void clear() {
        icon = ItemStack.EMPTY;
        charge = 0;
        capacity = 0;
    }

    public static boolean visible() {
        return capacity > 0 && !icon.isEmpty();
    }

    public static ItemStack icon() {
        return icon;
    }

    public static int charge() {
        return charge;
    }

    public static int capacity() {
        return capacity;
    }

    public static float fraction() {
        return capacity <= 0 ? 0f : Math.max(0f, Math.min(1f, (float) charge / capacity));
    }

    public static String line() {
        return visible() ? NUMBER.format(charge) + "/" + NUMBER.format(capacity) : null;
    }

    public static String previewLine() {
        return "12,345/50,000";
    }

    private static int capacity(String id, String name) {
        if (id != null) {
            int byId = switch (id) {
                case "THUNDER_IN_A_BOTTLE_EMPTY" -> 50_000;
                case "STORM_IN_A_BOTTLE_EMPTY" -> 500_000;
                case "HURRICANE_IN_A_BOTTLE_EMPTY" -> 5_000_000;
                default -> 0;
            };
            if (byId > 0) {
                return byId;
            }
        }
        if (name == null) {
            return 0;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (!lower.contains("bottle")) {
            return 0;
        }
        if (lower.contains("hurricane")) {
            return 5_000_000;
        }
        if (lower.contains("storm")) {
            return 500_000;
        }
        return lower.contains("thunder") ? 50_000 : 0;
    }
}
