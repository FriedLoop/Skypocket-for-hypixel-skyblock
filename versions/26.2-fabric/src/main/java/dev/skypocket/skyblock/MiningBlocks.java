package dev.skypocket.skyblock;

import java.util.HashMap;
import java.util.Map;

/**
 * SkyBlock mining data: the server's break time is
 * {@code ticks = ceil(BlockStrength × 30 / MiningSpeed)} (seconds = ticks / 20), and
 * most custom blocks are disguised as vanilla ones (Mithril as cyan terracotta /
 * prismarine / wool, Tungsten as clay or cobblestone, Umber as red sandstone or
 * terracotta, gemstones as dyed glass and glass panes, ...).
 *
 * <p>Disguises that can only ever mean one SkyBlock block carry their exact strength
 * here, so those need no calibration at all: ores (30) and their {@code Pure} variants,
 * which are the matching mineral blocks (600), Sulphur Ore as sponge (500), the Dwarven
 * metals (Mithril as prismarine/wool/cyan terracotta, Tungsten as clay, Umber as red
 * sandstone or terracotta), and every gemstone as its dyed glass and pane.
 *
 * <p>Disguises that are ambiguous get a candidate list instead — the same vanilla block
 * is a different SkyBlock block depending on the area (stone is 15 in the Hub but Hard
 * Stone, 50, in the Dwarven Mines; cobblestone may be Tungsten; cyan terracotta is both
 * Mithril's 500 and Hard Stone's 100 variant) — and {@link MiningStrengthTable} resolves
 * which candidate it is from the observed break time.
 */
public final class MiningBlocks {
    /** Every block strength in the mining tables (candidates when nothing else fits). */
    private static final int[] ALL = {
            8, 15, 20, 30, 50, 100, 500, 600, 800, 1500, 2000, 2300, 3000, 3800, 4800, 5200, 5600, 6000
    };

    /**
     * How the server decides "instant" for a block.
     *
     * <p>The break time never goes below {@link MiningStrengthTable#MIN_BREAK_TICKS}, so the formula
     * cannot produce a 0-tick break by itself: instant is a separate rule, and the speed it needs
     * depends on the block's class, not on its strength alone.
     */
    public enum Category {
        /** Plain blocks: instant from {@code 30·S + 1} (stone 15 → 451, end stone 30 → 901). */
        BLOCK,
        /** Ores and their Pure variants: {@code 60·S} (coal ore 30 → 1800). */
        ORE,
        /** Gemstones: {@code 60·S + 1}, as the wiki's threshold column lists them. */
        GEMSTONE,
        /** Dwarven metals: {@code 60·S + 1} (mithril 500 → 30001, titanium 2000 → 120001). */
        DWARVEN
    }

    private static final Map<String, Integer> EXACT = new HashMap<>();
    private static final Map<String, Category> CATEGORY = new HashMap<>();
    private static final Map<String, int[]> CANDIDATES = new HashMap<>();

    static {
        // ---- unambiguous disguises (no calibration needed) ----
        exact("netherrack", 8);
        exact("end_stone", 30);
        exact("obsidian", 500);
        exact("sponge", 500);                 // Sulphur Ore
        exact("wet_sponge", 500);

        // Ores; their "Pure" variants are the matching mineral blocks.
        ore("coal_ore", 30);
        ore("deepslate_coal_ore", 30);
        ore("iron_ore", 30);
        ore("deepslate_iron_ore", 30);
        ore("gold_ore", 30);
        ore("deepslate_gold_ore", 30);
        ore("lapis_ore", 30);
        ore("deepslate_lapis_ore", 30);
        ore("redstone_ore", 30);
        ore("deepslate_redstone_ore", 30);
        ore("emerald_ore", 30);
        ore("deepslate_emerald_ore", 30);
        ore("diamond_ore", 30);
        ore("deepslate_diamond_ore", 30);
        ore("nether_quartz_ore", 30);
        ore("coal_block", 600);
        ore("iron_block", 600);
        ore("gold_block", 600);
        ore("lapis_block", 600);
        ore("redstone_block", 600);
        ore("emerald_block", 600);
        ore("diamond_block", 600);
        ore("quartz_block", 600);

        // Dwarven metals
        metal("clay", 5600);                  // Tungsten
        metal("polished_diorite", 2000);      // Titanium
        // Mithril appears as all three prismarine variants in current SkyBlock, not just the plain one.
        // Measured 2026-10-30: two breaks of the blue variants imply ~2100 (7.2t at 8773 and 7.58t at
        // 8441 parses as 2000 within ~7%), while the 800/1500 values from the reference notes would need
        // the speed reading to be 60-190% high - and the two breaks disagree with each other under that
        // assumption. Every Mithril variant is therefore 2000 in the current version.
        // Evidence is one break of each variant and both samples were polluted by the duplicate/partial
        // cases (see MiningBreakTracker), so the strength is resolved from clean observations instead.
        candidates(new int[]{800, 1500, 2000}, "prismarine");
        // The other two prismarine variants are the same Mithril in the reference client, but that is
        // second-hand: a single-candidate list means the block is predicted only after two breaks agree,
        // so a wrong guess costs nothing instead of producing a confidently wrong bar.
        candidates(new int[]{800, 1500, 2000}, "prismarine_bricks", "dark_prismarine");
        // The grey variant was not measured; the reference notes say 500 while the two blue ones turned
        // out to be 2000, so let observation decide instead of trusting either number.
        candidates(new int[]{500, 2000}, "gray_wool");
        // The clean samples cluster at 6.0-6.5 ticks at speed ~8064, which is 1500 (45000/8064 = 5.58);
        // the 10-12 tick outliers were duplicate/partial breaks, not a higher strength.
        metal("light_blue_wool", 1500);       // Mithril
        metal("terracotta", 5600);            // Umber
        metal("brown_terracotta", 5600);      // Umber
        metal("smooth_red_sandstone", 5600);  // Umber
        metal("packed_ice", 6000);            // Glacite

        // Gemstones, disguised as dyed glass and glass panes
        gemstone("red", 2300);                // Ruby
        gemstone("orange", 3000);             // Amber
        gemstone("purple", 3000);             // Amethyst
        gemstone("lime", 3000);               // Jade
        gemstone("white", 3000);              // Opal
        gemstone("light_blue", 3000);         // Sapphire
        gemstone("yellow", 3800);             // Topaz
        gemstone("magenta", 4800);            // Jasper
        gemstone("blue", 5200);               // Aquamarine
        gemstone("brown", 5200);              // Citrine
        gemstone("black", 5200);              // Onyx
        gemstone("green", 5200);              // Peridot

        // ---- ambiguous disguises: resolved from the observed break time ----
        candidates(new int[]{15, 50}, "stone");                     // plain / Hard Stone
        candidates(new int[]{20, 50, 5600}, "cobblestone");         // plain / Hard Stone / Tungsten
        // Infested variants seen in the Dwarven Mines: same disguises, same ambiguity. Candidates
        // rather than exact values, so a wrong guess is corrected by observation instead of being
        // trusted forever.
        candidates(new int[]{15, 50}, "infested_stone");
        candidates(new int[]{20, 50, 5600}, "infested_cobblestone");
        candidates(new int[]{50}, "andesite", "diorite", "granite"); // Hard Stone
        // "Hard Stone (terracotta variant)" is cyan hardened clay, but the Mithril
        // tables also list cyan terracotta, so this colour has to be resolved.
        candidates(new int[]{100, 500}, "cyan_terracotta");
        candidates(new int[]{100, 500, 5600},
                "white_terracotta", "orange_terracotta", "magenta_terracotta", "light_blue_terracotta",
                "yellow_terracotta", "lime_terracotta", "pink_terracotta", "gray_terracotta",
                "light_gray_terracotta", "purple_terracotta", "blue_terracotta", "green_terracotta",
                "red_terracotta", "black_terracotta");
    }

    private MiningBlocks() {
    }

    /** Exact strength of this disguise, or {@code -1} when it is ambiguous / unknown. */
    public static int exact(String blockId) {
        Integer value = EXACT.get(shortId(blockId));
        return value == null ? -1 : value;
    }

    /** Which class of block this disguise is, for the per-category speed calibration. */
    public static Category categoryOf(String blockId) {
        return CATEGORY.getOrDefault(shortId(blockId), Category.BLOCK);
    }

    /** Candidate strengths for a vanilla block id (the whole table when unknown). */
    public static int[] candidates(String blockId) {
        int[] list = CANDIDATES.get(shortId(blockId));
        return list != null ? list : ALL;
    }

    private static void exact(String block, int strength) {
        exact(block, strength, Category.BLOCK);
    }

    private static void exact(String block, int strength, Category category) {
        EXACT.put(block, strength);
        CATEGORY.put(block, category);
    }

    private static void ore(String block, int strength) {
        exact(block, strength, Category.ORE);
    }

    private static void metal(String block, int strength) {
        exact(block, strength, Category.DWARVEN);
    }

    /** A gemstone appears as both the dyed glass block and its pane. */
    private static void gemstone(String color, int strength) {
        exact(color + "_stained_glass", strength, Category.GEMSTONE);
        exact(color + "_stained_glass_pane", strength, Category.GEMSTONE);
    }

    /**
     * Mining Speed at or above which this block breaks instantly, or 0 when the strength is unknown.
     *
     * <p>The break-time formula cannot produce a 0-tick break, so "instant" is its own rule and it
     * differs per class of block - ores need twice the speed a plain block of the same strength does.
     * The ±1 between {@link Category#ORE} and the others only matters when the speed lands exactly on
     * the boundary; the thresholds come from the wiki table the reference notes cite.
     */
    public static int instantSpeed(String blockId, int strength) {
        if (strength <= 0) {
            return 0;
        }
        Category category = CATEGORY.getOrDefault(shortId(blockId), Category.BLOCK);
        return switch (category) {
            case BLOCK -> 30 * strength + 1;
            case ORE -> 60 * strength;
            case GEMSTONE, DWARVEN -> 60 * strength + 1;
        };
    }

    private static void candidates(int[] strengths, String... blocks) {
        for (String block : blocks) {
            CANDIDATES.put(block, strengths);
        }
    }

    /** Strip the {@code minecraft:} prefix so the tables read like the wiki. */
    private static String shortId(String blockId) {
        if (blockId == null) {
            return "";
        }
        int colon = blockId.indexOf(':');
        return colon < 0 ? blockId : blockId.substring(colon + 1);
    }
}
