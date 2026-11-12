package dev.skypocket.skyblock;

import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;

/** SkyBlock rarity parsed from the last rarity line of item lore. */
public enum SkyBlockRarity {
    COMMON("COMMON", 0xFFFFFFFF),
    UNCOMMON("UNCOMMON", 0xFF55FF55),
    RARE("RARE", 0xFF5555FF),
    EPIC("EPIC", 0xFFAA00AA),
    LEGENDARY("LEGENDARY", 0xFFFFAA00),
    MYTHIC("MYTHIC", 0xFFFF55FF),
    DIVINE("DIVINE", 0xFF55FFFF),
    VERY_SPECIAL("VERY SPECIAL", 0xFFFF5555),
    SPECIAL("SPECIAL", 0xFFFF5555),
    ULTIMATE("ULTIMATE", 0xFFAA0000);

    private final String label;
    private final int color;

    SkyBlockRarity(String label, int color) {
        this.label = label;
        this.color = color;
    }

    public int color() {
        return color;
    }

    /** Rarity from lore; vanilla rarity is the fallback when the lore has no SkyBlock marker. */
    public static SkyBlockRarity of(ItemStack stack) {
        List<String> lore = SkyBlockItem.lore(stack);
        for (int i = lore.size() - 1; i >= 0; i--) {
            String plain = lore.get(i).replaceAll("§.", "").toUpperCase(Locale.ROOT).trim();
            if (plain.isEmpty()) {
                continue;
            }
            // VERY SPECIAL must win over the SPECIAL substring.
            for (SkyBlockRarity rarity : values()) {
                if (plain.contains(rarity.label)) {
                    return rarity;
                }
            }
        }
        return switch (stack.getRarity()) {
            case COMMON -> COMMON;
            case UNCOMMON -> UNCOMMON;
            case RARE -> RARE;
            case EPIC -> EPIC;
        };
    }
}
