package dev.skypocket.skyblock;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Set;

/**
 * Yqloss's held-item whitelist for SkyBlock mining tools.
 *
 * <p>Tab Mining Speed is only meaningful while a mining tool is held. When the player switches
 * to a weapon or another item, a tab refresh can belong to the new held item; accepting it
 * would poison both the frozen base and the ability window. The same list also gates mining
 * prediction, matching Yqloss's Mining event check.
 */
public final class MiningTools {
    private static final Set<Item> TOOLS = Set.of(
            Items.WOODEN_PICKAXE,
            Items.STONE_PICKAXE,
            Items.GOLDEN_PICKAXE,
            Items.IRON_PICKAXE,
            Items.DIAMOND_PICKAXE,
            Items.PLAYER_HEAD,      // 1.8 Items.skull; SkyBlock drills use player heads
            Items.PRISMARINE_SHARD
    );

    private MiningTools() {
    }

    /** Whether the local player's main hand is one of the whitelisted mining tools. */
    public static boolean holding() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && isTool(mc.player.getMainHandItem());
    }

    public static boolean isTool(ItemStack stack) {
        return stack != null && !stack.isEmpty() && TOOLS.contains(stack.getItem());
    }
}
