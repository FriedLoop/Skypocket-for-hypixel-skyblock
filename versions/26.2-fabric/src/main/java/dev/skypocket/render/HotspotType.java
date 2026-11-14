package dev.skypocket.render;

import dev.skypocket.config.ModConfig;

import java.util.function.ObjIntConsumer;
import java.util.function.ToIntFunction;

/**
 * The five fishing hotspot kinds, identified by the stat line the server renders
 * next to the "HOTSPOT" marker (e.g. "+15 Fishing Speed"). Each kind carries its
 * own configurable overlay colour, defaulting to the stat text's colour.
 */
public enum HotspotType {
    FISHING_SPEED("Fishing Speed", "钓鱼速度",
            cfg -> cfg.fishingHotspotOverlayBlock.hotspotColorFishingSpeed, (cfg, v) -> cfg.fishingHotspotOverlayBlock.hotspotColorFishingSpeed = v),
    SEA_CREATURE("Sea Creature Chance", "海洋生物几率",
            cfg -> cfg.fishingHotspotOverlayBlock.hotspotColorSeaCreature, (cfg, v) -> cfg.fishingHotspotOverlayBlock.hotspotColorSeaCreature = v),
    DOUBLE_HOOK("Double Hook Chance", "双重上钩几率",
            cfg -> cfg.fishingHotspotOverlayBlock.hotspotColorDoubleHook, (cfg, v) -> cfg.fishingHotspotOverlayBlock.hotspotColorDoubleHook = v),
    TROPHY("Trophy Chance", "奖杯几率",
            cfg -> cfg.fishingHotspotOverlayBlock.hotspotColorTrophy, (cfg, v) -> cfg.fishingHotspotOverlayBlock.hotspotColorTrophy = v),
    TREASURE("Treasure Chance", "宝藏几率",
            cfg -> cfg.fishingHotspotOverlayBlock.hotspotColorTreasure, (cfg, v) -> cfg.fishingHotspotOverlayBlock.hotspotColorTreasure = v);

    /** Substring of the stat line identifying this kind. */
    public final String matchKey;
    /** Settings label key (Chinese literal). */
    public final String label;
    private final ToIntFunction<ModConfig> getter;
    private final ObjIntConsumer<ModConfig> setter;

    HotspotType(String matchKey, String label, ToIntFunction<ModConfig> getter, ObjIntConsumer<ModConfig> setter) {
        this.matchKey = matchKey;
        this.label = label;
        this.getter = getter;
        this.setter = setter;
    }

    /** The kind whose stat line appears in {@code text}, or null. */
    public static HotspotType fromText(String text) {
        if (text == null) {
            return null;
        }
        String upper = text.toUpperCase();
        for (HotspotType t : values()) {
            if (upper.contains(t.matchKey.toUpperCase())) {
                return t;
            }
        }
        return null;
    }

    public int color(ModConfig cfg) {
        return getter.applyAsInt(cfg);
    }

    public void setColor(ModConfig cfg, int value) {
        setter.accept(cfg, value);
    }
}
