package dev.skypocket.feature.config;



import dev.skypocket.config.ConfigManager;
import dev.skypocket.render.ColorUtil;
import java.util.HashMap;
import java.util.Map;

/**
 * Persisted state for the <code>mining_break_predict</code> feature.
 *
 * <p>Fields keep the names they had on ModConfig so the rewrite of every read site is an
 * insertion rather than a rename. Defaults are the ones the flat fields carried.
 */
public final class MiningBreakPredictConfig {
    public String miningAlignTarget = "zero";
    public boolean miningAutoScale = true;
    public int miningBreakBiasTicks = 1;
    public boolean miningBreakDebugHud = false;
    public int miningBreakOutlineColor = ColorUtil.argb(210, 255, 255, 255);
    public boolean miningBreakOutlineEnabled = true;
    public boolean miningBreakPredictEnabled = false;
    public String miningBreakSound = "minecraft:entity.experience_orb.pickup";
    public float miningBreakSoundPitch = 1.0f;
    public float miningBreakSoundVolume = 1.0f;
    public Map<String, Double> miningCategoryScale = new HashMap<>();
    public int miningMinBreakTicks = 4;
    public boolean miningPhaseAlign = true;
    public Map<String, Integer> miningResolvedStrength = new HashMap<>();
    public String miningTickRounding = "ceil";
    /**

     * Clamps and repairs this block's fields after load.
     *
     * <p>Moved out of ModConfig.sanitize() when the fields moved here: a range belongs with the
     * field it constrains, and this class is the only one that knows it. Called from
     * ModConfig.sanitize(), which stays the single entry point.
     */
    public void sanitize() {
        miningBreakBiasTicks = ColorUtil.clamp(miningBreakBiasTicks, -6, 3);
        miningMinBreakTicks = ColorUtil.clamp(miningMinBreakTicks, 1, 20);
        if (miningCategoryScale == null) {
            miningCategoryScale = new HashMap<>();
        }
        for (Map.Entry<String, Double> entry : miningCategoryScale.entrySet()) {
            if (entry.getValue() == null || !Double.isFinite(entry.getValue())) {
                entry.setValue(1.0);
            } else {
                entry.setValue(Math.max(0.4, Math.min(2.5, entry.getValue())));
            }
        }
        if (miningAlignTarget == null || !(miningAlignTarget.equals("zero")
                || miningAlignTarget.equals("server") || miningAlignTarget.equals("client"))) {
            miningAlignTarget = "zero";
        }
        if (miningTickRounding == null || !(miningTickRounding.equals("ceil")
                || miningTickRounding.equals("floor") || miningTickRounding.equals("round"))) {
            miningTickRounding = "ceil";
        }
        if (ConfigManager.get().miningTimingVersion < 4) {
            // v0 fitted the arrival delay and the scale together (collinear for a single
            // block type, produced nonsense); v1 subtracted only half the round trip when
            // learning, which inflated the scale and left every prediction a tick long.
            // Start clean; the scale is re-fitted from real breaks.
            // v2: the Mithril strengths were wrong, so the scale that had been fitted against them is
            // meaningless too - start clean and re-fit.
            ConfigManager.get().miningTimingVersion = 4;
            // Per-category scales replaced the single number; start them empty so each class fits its own.
            if (miningCategoryScale != null) {
                miningCategoryScale.clear();
            }
        }
        miningBreakSoundVolume = ColorUtil.clamp(miningBreakSoundVolume, 0f, 2f);
        miningBreakSoundPitch = ColorUtil.clamp(miningBreakSoundPitch, 0.5f, 2f);
        if (miningBreakSound == null || miningBreakSound.isEmpty()) {
            miningBreakSound = "minecraft:entity.experience_orb.pickup";
        }
        if (miningResolvedStrength == null) {
            miningResolvedStrength = new HashMap<>();
        }
    }
}
