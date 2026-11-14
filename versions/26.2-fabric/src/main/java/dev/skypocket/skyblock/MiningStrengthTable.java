package dev.skypocket.skyblock;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;

import java.util.HashMap;
import java.util.Map;

/**
 * Which SkyBlock block a disguised vanilla block stands for.
 *
 * <p>The break time itself is computed from the server formula (see
 * {@link MiningBlocks}), so the only thing that ever needs figuring out is the
 * disguise: {@code minecraft:stone} is strength 15 in the Hub but Hard Stone (50) in
 * the Dwarven Mines, {@code minecraft:coal_ore} may be 30 or the 600 "Pure" variant,
 * dyed glass may be any gemstone. That is resolved by running the formula backwards
 * over the observed break time: of the candidates for that block, the one whose
 * {@code ceil(strength × 30 / speed)} matches what actually happened wins, and it is
 * persisted.
 *
 * <p>Resolution needs two agreeing observations, and a value that stops matching is
 * dropped and re-resolved — so switching areas re-identifies the block on its own.
 * One number per disguise is enough; the duration is then exact for any Mining Speed.
 */
public final class MiningStrengthTable {
    /**
     * The server never reports a break shorter than this, whatever the formula says.
     *
     * <p>Without the floor, a strength-800 block at speed 30000 predicted 1 tick (50 ms) while the
     * server took 4 (200 ms) - the progress bar ran to the end and waited, then the block vanished.
     * Every non-instant break in the reference data is a multiple of 4 for this reason.
     */
    public static final int DEFAULT_MIN_BREAK_TICKS = 4;

    /** The floor in effect, from the config so 4 and 5 can be compared in game. */
    public static int minBreakTicks() {
        return Math.max(1, ConfigManager.get().miningBreakPredictBlock.miningMinBreakTicks);
    }

    /** Agreeing observations required before a resolved strength is trusted. */
    private static final int VOTES_NEEDED = 2;

    /**
     * Breaks shorter than this are not used to identify a block.
     *
     * <p>At or below the floor every candidate predicts the same 4 ticks, so a vote based on such a
     * break says nothing about the real strength - it just agrees with whatever came first and can
     * collapse a whole candidate list onto one wrong entry.
     */
    private static final double MIN_TICKS_FOR_VOTE = 4.0;
    /** Contradicting observations before a resolved strength is thrown away. */
    private static final int MISMATCHES_ALLOWED = 2;
    /** Config writes stay off the per-break path. */
    private static final long SAVE_INTERVAL_MS = 20000;

    /** Pending votes: block id -> candidate strength -> agreeing observations. */
    private static final Map<String, Map<Integer, Integer>> VOTES = new HashMap<>();
    /** Contradiction counters for already resolved blocks. */
    private static final Map<String, Integer> MISMATCHES = new HashMap<>();

    private static boolean dirty;
    private static long lastSaveMs;
    /** Observations dropped as too short to identify a block; debug only. */
    private static int droppedSamples;

    private MiningStrengthTable() {
    }

    /** Strength to predict with: exact disguise, else resolved, else -1 (unknown). */
    public static int strength(String blockId) {
        int exact = MiningBlocks.exact(blockId);
        if (exact > 0) {
            return exact;
        }
        Integer resolved = resolved().get(blockId);
        return resolved == null || resolved <= 0 ? -1 : resolved;
    }

    /** Server formula: {@code ticks = ceil(strength × 30 / speed)}; 0 = unknown. */
    public static int breakTicks(String blockId, double speed) {
        return ticks(strength(blockId), speed);
    }

    /** The same formula for an explicit strength (used by the timing fit). */
    public static int ticks(int strength, double speed) {
        int raw = rawTicks(strength, speed);
        if (raw <= 0) {
            return 0;
        }
        // The server clamps breaks to a 4-tick floor: the raw formula happily returns 1-3, which would
        // predict a break two or three ticks early on anything that is not already instant.
        return Math.max(minBreakTicks(), raw);
    }

    /**
     * The unclamped formula, i.e. what the server would do without its 4-tick floor.
     *
     * <p>The rounding is a switch, not a decision: {@code ceil} is the documented formula, but a
     * {@code floor} reading fits the wiki's tick table better for some blocks, and the only way to
     * settle it is to measure both. Never below 1 tick, so a very fast break still counts as a break.
     */
    public static int rawTicks(int strength, double speed) {
        if (strength <= 0 || speed <= 0) {
            return 0;
        }
        double exact = strength * 30.0 / speed;
        return switch (rounding()) {
            case "floor" -> Math.max(1, (int) Math.floor(exact));
            case "round" -> Math.max(1, (int) Math.round(exact));
            default -> (int) Math.ceil(exact);
        };
    }

    private static String rounding() {
        String mode = ConfigManager.get().miningBreakPredictBlock.miningTickRounding;
        return mode == null ? "ceil" : mode;
    }

    /**
     * True when this block breaks instantly at that speed, i.e. there is no progress to predict.
     *
     * <p>Not the same as {@code ticks <= 1}: after the 4-tick floor the formula never returns 1, and the
     * speed an ore needs to be instant is twice what a plain block of equal strength needs.
     */
    public static boolean isInstant(String blockId, double speed) {
        int threshold = MiningBlocks.instantSpeed(blockId, strength(blockId));
        return threshold > 0 && speed >= threshold;
    }

    /**
     * The tick count all candidates agree on, or 0 when the strength is known or they differ.
     *
     * <p>At high Mining Speed every Mithril tier is capped at the same floor, so the strength cannot be
     * told apart - but the prediction does not need to be told apart either: if all candidates give the
     * same number, that number is the answer.
     */
    public static int agreedTicks(String blockId, double speed) {
        if (strength(blockId) > 0 || speed <= 0) {
            return 0;
        }
        int[] list = MiningBlocks.candidates(blockId);
        int agreed = ticks(list[0], speed);
        for (int candidate : list) {
            if (ticks(candidate, speed) != agreed) {
                return 0;
            }
        }
        return agreed;
    }

    /** True when the disguise already has an exact or resolved strength. */
    public static boolean known(String blockId) {
        return strength(blockId) > 0;
    }

    /**
     * Feeds one confirmed break: the server-side duration in seconds and the Mining
     * Speed it happened at (only ever called with a settled speed).
     */
    public static void observe(String blockId, double seconds, double speed) {
        if (blockId == null || seconds <= 0 || speed <= 0 || MiningBlocks.exact(blockId) > 0) {
            return;
        }
        double observedTicks = seconds * 20.0;
        if (observedTicks < MIN_TICKS_FOR_VOTE) {
            // Inside the cap: nothing to learn, and voting here would be noise dressed as evidence.
            droppedSamples++;
            return;
        }

        Integer resolved = resolved().get(blockId);
        if (resolved != null) {
            // Same clamped formula as the prediction, or every capped break would look like a mismatch.
            double predictedTicks = ticks(resolved, speed);
            if (Math.abs(predictedTicks - observedTicks) <= 1.5) {
                MISMATCHES.remove(blockId);
                return;
            }
            // Same disguise, different block (another area): forget and re-resolve.
            if (MISMATCHES.merge(blockId, 1, Integer::sum) < MISMATCHES_ALLOWED) {
                return;
            }
            resolved().remove(blockId);
            VOTES.remove(blockId);
            MISMATCHES.remove(blockId);
            dirty = true;
            return;
        }

        int best = -1;
        double bestError = Double.MAX_VALUE;
        boolean tied = false;
        for (int candidate : MiningBlocks.candidates(blockId)) {
            // Clamped like the prediction: a capped candidate predicts the floor, not its raw ceil.
            double error = Math.abs(ticks(candidate, speed) - observedTicks);
            if (error < bestError - 0.4) {
                bestError = error;
                best = candidate;
                tied = false;
            } else if (error <= bestError + 0.4) {
                tied = true;
            }
        }
        if (best <= 0 || tied || bestError > 1.0) {
            return;
        }
        int votes = VOTES.computeIfAbsent(blockId, key -> new HashMap<>())
                .merge(best, 1, Integer::sum);
        if (votes >= VOTES_NEEDED) {
            resolved().put(blockId, best);
            VOTES.remove(blockId);
            dirty = true;
        }
    }

    /** Marks the config as needing a save (shared debounce, see {@link #tickPersistence()}). */
    public static void markDirty() {
        dirty = true;
    }

    /** Debounced persistence; call every frame. */
    public static void tickPersistence() {
        if (!dirty) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastSaveMs < SAVE_INTERVAL_MS) {
            return;
        }
        lastSaveMs = now;
        dirty = false;
        ConfigManager.save();
    }

    /** Forgets every resolved disguise (the "reset" setting button). */
    public static void clear() {
        resolved().clear();
        VOTES.clear();
        MISMATCHES.clear();
        dirty = false;
        // The fitted scales were learned against these strengths, so they are meaningless now: drop them
        // and the samples, otherwise the old corrections fight the corrected table.
        MiningBreakTracker.clearFit();
        ConfigManager.save();
    }

    /** Observations rejected for being inside the 4-tick cap (debug; the value only ever grows). */
    public static int droppedSamples() {
        return droppedSamples;
    }

    /** How many disguises have been resolved so far. */
    public static int size() {
        return resolved().size();
    }

    private static Map<String, Integer> resolved() {
        ModConfig cfg = ConfigManager.get();
        if (cfg.miningBreakPredictBlock.miningResolvedStrength == null) {
            cfg.miningBreakPredictBlock.miningResolvedStrength = new HashMap<>();
        }
        return cfg.miningBreakPredictBlock.miningResolvedStrength;
    }
}
