package dev.skypocket.feature.config;



import dev.skypocket.config.ModConfig;
import dev.skypocket.render.ColorUtil;

/**
 * Persisted state for the <code>stats_display</code> feature.
 *
 * <p>Fields keep the names they had on ModConfig so the rewrite of every read site is an
 * insertion rather than a rename. Defaults are the ones the flat fields carried.
 */
public final class StatsDisplayConfig {
    public int statsAbsorptionColor = dev.skypocket.config.ColorDefaults.STATS_ABSORPTION;
    public boolean statsAirBarEnabled = true;
    public float statsAirBarScale = 1.0f;
    public int statsAirBarX = 171;
    public int statsAirBarY = 186;
    public int statsAirColor = dev.skypocket.config.ColorDefaults.STATS_AIR;
    public int statsBarHeight = 20;
    public int[] statsBarHeights = new int[6];
    public boolean statsBarShowValues = true;
    public int statsBarWidth = 160;
    public int[] statsBarWidths = new int[6];
    public boolean statsDefenseBarEnabled = true;
    public float statsDefenseBarScale = 1.0f;
    public int statsDefenseBarX = 171;
    public int statsDefenseBarY = 213;
    public int statsDefenseColor = dev.skypocket.config.ColorDefaults.STATS_DEFENSE;
    public boolean statsDefenseEnabled = true;
    public float statsDefenseScale = 1.0f;
    public int statsDefenseX = 10;
    public int statsDefenseY = 70;
    public boolean statsEnabled = false;
    public boolean statsHealthBarEnabled = true;
    public float statsHealthBarScale = 1.0f;
    public int statsHealthBarX = 171;
    public int statsHealthBarY = 231;
    public int statsHealthColor = dev.skypocket.config.ColorDefaults.STATS_HEALTH;
    public boolean statsHealthTextEnabled = true;
    public float statsHealthTextScale = 1.0f;
    public int statsHealthTextX = 100;
    public int statsHealthTextY = 221;
    public boolean statsHideVanillaActionBar = true;
    public boolean statsHideVanillaAir = true;
    public boolean statsHideVanillaArmor = false;
    public boolean statsHideVanillaFood = true;
    public boolean statsHideVanillaHealth = true;
    public boolean statsManaBarEnabled = true;
    public float statsManaBarScale = 1.0f;
    public int statsManaBarX = 171;
    public int statsManaBarY = 222;
    public boolean statsManaBlink = true;
    public int statsManaColor = dev.skypocket.config.ColorDefaults.STATS_MANA;
    public boolean statsManaTextEnabled = true;
    public float statsManaTextScale = 1.0f;
    public int statsManaTextX = 335;
    public int statsManaTextY = 221;
    public boolean statsModernStyle = false;
    public int statsOverflowColor = dev.skypocket.config.ColorDefaults.STATS_OVERFLOW;
    public boolean statsSpeedBarEnabled = true;
    public float statsSpeedBarScale = 1.0f;
    public int statsSpeedBarX = 171;
    public int statsSpeedBarY = 204;
    public int statsSpeedColor = dev.skypocket.config.ColorDefaults.STATS_SPEED;
    public boolean statsSpeedEnabled = true;
    public float statsSpeedScale = 1.0f;
    public int statsSpeedX = 10;
    public int statsSpeedY = 90;
    public boolean statsVitalityBarEnabled = true;
    public float statsVitalityBarScale = 1.0f;
    public int statsVitalityBarX = 171;
    public int statsVitalityBarY = 195;
    public int statsVitalityColor = dev.skypocket.config.ColorDefaults.STATS_VITALITY;
    public boolean statsVitalityEnabled = true;
    public float statsVitalityScale = 1.0f;
    public int statsVitalityX = 10;
    public int statsVitalityY = 110;
    /**

     * Clamps and repairs this block's fields after load.
     *
     * <p>Moved out of ModConfig.sanitize() when the fields moved here: a range belongs with the
     * field it constrains, and this class is the only one that knows it. Called from
     * ModConfig.sanitize(), which stays the single entry point.
     */
    public void sanitize() {
        statsBarWidth = ColorUtil.clamp(statsBarWidth, 40, 400);
        statsBarHeight = ColorUtil.clamp(statsBarHeight, 8, 20);
        statsBarWidths = perBarSizes(statsBarWidths, ModConfig.BAR_MIN_W, ModConfig.BAR_MAX_W);
        statsBarHeights = perBarSizes(statsBarHeights, ModConfig.BAR_MIN_H, ModConfig.BAR_MAX_H);
        statsHealthTextX = ColorUtil.clamp(statsHealthTextX, -100000, 100000);
        statsHealthTextY = ColorUtil.clamp(statsHealthTextY, -100000, 100000);
        statsHealthTextScale = ColorUtil.clamp(statsHealthTextScale, 0.25f, 6f);
        statsManaTextX = ColorUtil.clamp(statsManaTextX, -100000, 100000);
        statsManaTextY = ColorUtil.clamp(statsManaTextY, -100000, 100000);
        statsManaTextScale = ColorUtil.clamp(statsManaTextScale, 0.25f, 6f);
        statsHealthBarX = ColorUtil.clamp(statsHealthBarX, -100000, 100000);
        statsHealthBarY = ColorUtil.clamp(statsHealthBarY, -100000, 100000);
        statsHealthBarScale = ColorUtil.clamp(statsHealthBarScale, 0.25f, 6f);
        statsManaBarX = ColorUtil.clamp(statsManaBarX, -100000, 100000);
        statsManaBarY = ColorUtil.clamp(statsManaBarY, -100000, 100000);
        statsManaBarScale = ColorUtil.clamp(statsManaBarScale, 0.25f, 6f);
        statsDefenseBarX = ColorUtil.clamp(statsDefenseBarX, -100000, 100000);
        statsDefenseBarY = ColorUtil.clamp(statsDefenseBarY, -100000, 100000);
        statsDefenseBarScale = ColorUtil.clamp(statsDefenseBarScale, 0.25f, 6f);
        statsSpeedBarX = ColorUtil.clamp(statsSpeedBarX, -100000, 100000);
        statsSpeedBarY = ColorUtil.clamp(statsSpeedBarY, -100000, 100000);
        statsSpeedBarScale = ColorUtil.clamp(statsSpeedBarScale, 0.25f, 6f);
        statsVitalityBarX = ColorUtil.clamp(statsVitalityBarX, -100000, 100000);
        statsVitalityBarY = ColorUtil.clamp(statsVitalityBarY, -100000, 100000);
        statsVitalityBarScale = ColorUtil.clamp(statsVitalityBarScale, 0.25f, 6f);
        statsAirBarX = ColorUtil.clamp(statsAirBarX, -100000, 100000);
        statsAirBarY = ColorUtil.clamp(statsAirBarY, -100000, 100000);
        statsAirBarScale = ColorUtil.clamp(statsAirBarScale, 0.25f, 6f);
        statsDefenseX = ColorUtil.clamp(statsDefenseX, -100000, 100000);
        statsDefenseY = ColorUtil.clamp(statsDefenseY, -100000, 100000);
        statsDefenseScale = ColorUtil.clamp(statsDefenseScale, 0.25f, 6f);
        statsSpeedX = ColorUtil.clamp(statsSpeedX, -100000, 100000);
        statsSpeedY = ColorUtil.clamp(statsSpeedY, -100000, 100000);
        statsSpeedScale = ColorUtil.clamp(statsSpeedScale, 0.25f, 6f);
        statsVitalityX = ColorUtil.clamp(statsVitalityX, -100000, 100000);
        statsVitalityY = ColorUtil.clamp(statsVitalityY, -100000, 100000);
        statsVitalityScale = ColorUtil.clamp(statsVitalityScale, 0.25f, 6f);
    }

    /** Clamps a per-bar size array, padding short arrays with zeros. */
    private static int[] perBarSizes(int[] values, int min, int max) {
        int[] out = new int[6];
        if (values == null) {
            return out;
        }
        for (int i = 0; i < Math.min(out.length, values.length); i++) {
            out[i] = values[i] <= 0 ? 0 : Math.max(min, Math.min(max, values[i]));
        }
        return out;
    }}
