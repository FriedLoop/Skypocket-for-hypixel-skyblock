package dev.skypocket.skyblock;

import dev.skypocket.chat.ChatText;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Read-only view of a Hypixel SkyBlock item.
 *
 * <p>Hypixel stores its data in the vanilla {@code custom_data} component under
 * {@code ExtraAttributes}. This class is the single place that knows the component
 * shape; feature code should ask it for an id/value instead of reading NBT itself.
 */
public final class SkyBlockItem {
    private SkyBlockItem() {
    }

    /** The ExtraAttributes compound, or null for a non-SkyBlock/malformed stack. */
    public static CompoundTag extra(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || data.isEmpty()) {
            return null;
        }
        CompoundTag tag = data.copyTag();
        // On current SkyBlock, custom_data itself contains id/drill_fuel/thunder_charge.
        // Some serialized/legacy forms nest the same payload under ExtraAttributes.
        if (tag.contains("ExtraAttributes")) {
            CompoundTag nested = tag.getCompoundOrEmpty("ExtraAttributes");
            if (!nested.isEmpty()) {
                return nested;
            }
        }
        return tag;
    }

    /** The SkyBlock item id (e.g. GEMSTONE_DRILL_*), or null. */
    public static String id(ItemStack stack) {
        CompoundTag extra = extra(stack);
        return extra == null ? null : extra.getStringOr("id", null);
    }

    /** A numeric ExtraAttributes field, or {@code fallback}. */
    public static int intValue(ItemStack stack, String key, int fallback) {
        CompoundTag extra = extra(stack);
        return extra == null ? fallback : extra.getIntOr(key, fallback);
    }

    /** Drills carry drill_fuel; name/id fallbacks cover servers that move the field. */
    public static boolean isDrill(ItemStack stack) {
        CompoundTag extra = extra(stack);
        if (extra != null && extra.contains("drill_fuel")) {
            return true;
        }
        String id = id(stack);
        if (id != null && id.toUpperCase(Locale.ROOT).contains("DRILL")) {
            return true;
        }
        return stack != null && !stack.isEmpty()
                && stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains("drill");
    }

    /** Flattened lore lines with legacy colour codes, for rarity/data parsing. */
    public static List<String> lore(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return List.of();
        }
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>(lore.lines().size());
        for (var line : lore.lines()) {
            out.add(ChatText.formatted(line));
        }
        return List.copyOf(out);
    }
}
