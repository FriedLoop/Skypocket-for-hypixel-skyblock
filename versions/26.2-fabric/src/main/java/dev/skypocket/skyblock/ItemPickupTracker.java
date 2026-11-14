package dev.skypocket.skyblock;

import dev.skypocket.config.ConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Diffs the player inventory each tick and keeps recent pickup/loss lines. */
public final class ItemPickupTracker {
    private static final int SLOT_SKYBLOCK_MENU = 8;

    private static final List<Entry> ENTRIES = new ArrayList<>();
    private static Map<String, Snapshot> previous;
    /** Frozen when the player inventory opens, compared when it closes. */
    private static Map<String, Snapshot> inventorySnapshot;
    private static ClientLevel lastLevel;

    private ItemPickupTracker() {
    }

    public static void tick(Minecraft mc) {
        if (!ConfigManager.get().itemPickupLogBlock.pickupLogEnabled
                || !dev.skypocket.net.SkyBlockDetector.isActive()
                || mc.player == null || mc.level == null) {
            clear();
            return;
        }
        if (lastLevel != mc.level) {
            clear();
            lastLevel = mc.level;
        }
        long now = System.currentTimeMillis();
        long lifespan = ConfigManager.get().itemPickupLogBlock.pickupLogDurationMs;
        ENTRIES.removeIf(entry -> now - entry.timestamp > lifespan);

        // Rearranging items in the player inventory is not a pickup/loss. Freeze a baseline when
        // the screen opens and compare it only when the screen closes, so dropping an item while
        // the inventory is open still produces a negative entry.
        boolean inventoryOpen = mc.gui.screen() instanceof InventoryScreen;
        if (inventoryOpen) {
            if (inventorySnapshot == null) {
                inventorySnapshot = scan(mc);
            }
            return;
        }

        Map<String, Snapshot> current = scan(mc);
        if (inventorySnapshot != null) {
            emitDiff(inventorySnapshot, current, now);
            inventorySnapshot = null;
        } else if (previous != null) {
            emitDiff(previous, current, now);
        }
        previous = current;
    }

    private static void emitDiff(Map<String, Snapshot> old, Map<String, Snapshot> current,
                                 long now) {
        for (Map.Entry<String, Snapshot> e : current.entrySet()) {
            Snapshot before = old.get(e.getKey());
            int oldCount = before == null ? 0 : before.count;
            int diff = e.getValue().count - oldCount;
            if (diff != 0) {
                add(e.getValue().name, e.getValue().stack, diff, now);
            }
        }
        for (Map.Entry<String, Snapshot> e : old.entrySet()) {
            if (!current.containsKey(e.getKey())) {
                add(e.getValue().name, e.getValue().stack, -e.getValue().count, now);
            }
        }
    }

    private static Map<String, Snapshot> scan(Minecraft mc) {
        Map<String, Snapshot> out = new HashMap<>();
        var items = mc.player.getInventory().getNonEquipmentItems();
        for (int slot = 0; slot < items.size(); slot++) {
            if (slot == SLOT_SKYBLOCK_MENU) {
                continue;
            }
            ItemStack stack = items.get(slot);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            String name = stack.getHoverName().getString();
            String id = SkyBlockItem.id(stack);
            String key = (id == null ? stack.getItem().getDescriptionId() : id) + "\u0000" + name;
            Snapshot old = out.get(key);
            if (old == null) {
                out.put(key, new Snapshot(name, stack.copy(), stack.getCount()));
            } else {
                old.count += stack.getCount();
            }
        }
        return out;
    }

    private static void add(String name, ItemStack stack, int diff, long now) {
        for (int i = 0; i < ENTRIES.size(); i++) {
            Entry entry = ENTRIES.get(i);
            if (entry.name.equals(name) && Integer.signum(entry.amount) == Integer.signum(diff)) {
                entry.amount += diff;
                entry.timestamp = now;
                if (entry.amount == 0) {
                    ENTRIES.remove(i);
                }
                return;
            }
        }
        if (diff != 0) {
            ENTRIES.add(new Entry(name, diff, stack.copy(), now,
                    SkyBlockRarity.of(stack).color()));
        }
    }

    public static List<Entry> entries() {
        return List.copyOf(ENTRIES);
    }

    public static void clear() {
        ENTRIES.clear();
        previous = null;
        inventorySnapshot = null;
    }

    public static final class Entry {
        public final String name;
        public int amount;
        public final ItemStack stack;
        public long timestamp;
        public final int rarityColor;

        private Entry(String name, int amount, ItemStack stack, long timestamp, int rarityColor) {
            this.name = name;
            this.amount = amount;
            this.stack = stack;
            this.timestamp = timestamp;
            this.rarityColor = rarityColor;
        }
    }

    private static final class Snapshot {
        private final String name;
        private final ItemStack stack;
        private int count;

        private Snapshot(String name, ItemStack stack, int count) {
            this.name = name;
            this.stack = stack;
            this.count = count;
        }
    }
}
