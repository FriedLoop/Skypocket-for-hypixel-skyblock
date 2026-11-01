package dev.skypocket.skyblock;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;
import dev.skypocket.net.PingMonitor;
import dev.skypocket.net.SkyBlockDetector;
import dev.skypocket.sound.SoundManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/**
 * Predicts when the block the player is mining has actually been broken on the
 * server, so a high-ping player knows when to move on instead of waiting for the
 * block to visually disappear (which happens a full round trip late).
 *
 * <p>The model is two pieces that keep the data stable no matter how often the
 * player's Mining Speed changes:
 *
 * <pre>
 *   ticks    = ceil(BlockStrength x 30 / miningSpeed)   (server formula)
 *   assertAt = T0 + rtt/2 + ticks/20 + biasTicks
 * </pre>
 *
 * {@code T0} is the moment the client started destroying the block (the destroy
 * hooks). Reaching the assertion closes the ring and the outline and plays the alert
 * — the "move on" cue, {@code RTT - bias} earlier than the visual confirmation.
 *
 * <p>Correctness never depends on the prediction: the ring caps at
 * {@link #UNCONFIRMED_CAP} until the server's block update arrives, so it cannot claim
 * a block is gone while it is still there. Each confirmation also re-fits the model
 * ({@code observed = arrival - T0 - RTT}), so a bad estimate costs the early cue for
 * one block at worst. No packet is ever sent by this feature.
 */
public final class MiningBreakTracker {
    private static final double TICK_MS = 50.0;
    /** Progress is capped here until the server confirms the break. */
    private static final float UNCONFIRMED_CAP = 0.99f;
    /** How long the finished cue stays on screen after the confirmation arrived. */
    private static final long HOLD_AFTER_CONFIRM_MS = 150;
    /** Stop waiting for a confirmation this long after the assertion (bad estimate). */
    private static final long PREDICTION_TIMEOUT_MS = 1500;
    private static final long FADE_MS = 250;

    private enum Phase { IDLE, RUNNING, ASSERTED, FADING }

    private static Phase phase = Phase.IDLE;
    private static BlockPos targetPos;
    private static String targetBlockId;
    private static long startMs;
    /** Model output at cycle start in seconds, before the bias; &lt;= 0 = unknown. */
    private static double predictedSeconds = -1;
    /** Frozen assertion delay for this cycle (ms), model combined with the anchor. */
    private static double assertionMs = -1;
    /**
     * Last measured arrival offset ({@code arrival − T0}, ms) per block type.
     *
     * <p>This is the safety anchor: the assertion is never earlier than the most recent
     * real break of the same block. The tab list's Mining Speed lags the real value by a
     * few seconds, so right after a speed <em>drop</em> the model would use a too-high
     * speed and fire early; the measured arrival is immune to that.
     */
    private static final Map<String, Long> LAST_ARRIVAL_MS = new HashMap<>();
    /** Mining Speed used for this cycle (parsed value with the learned factor applied). */
    private static double speedAtStart = -1;
    /** The raw tab reading for this cycle, before the factor. */
    private static double parsedSpeedAtStart = -1;
    /**
     * Timing fit samples: x = the raw formula duration at the speed the tab reported
     * (ms), y = how long the break took to arrive (ms, from T0). The ratio
     * {@code (y - delivery)/x} is the scale the server really mined at, relative to the
     * speed the panel reports.
     */
    private static final int FIT_WINDOW = 16;
    /**
     * Samples per block class. One window for everything mixed blocks whose real speed differs, which is
     * why the single fitted scale wandered between 0.63 and 0.92 within a minute.
     */
    private static final int CATEGORY_COUNT = MiningBlocks.Category.values().length;
    private static final double[][] FIT_X = new double[CATEGORY_COUNT][FIT_WINDOW];
    private static final double[][] FIT_Y = new double[CATEGORY_COUNT][FIT_WINDOW];
    private static final int[] FIT_COUNT = new int[CATEGORY_COUNT];
    private static final int[] FIT_INDEX = new int[CATEGORY_COUNT];
    /** Implied effective speed of the last confirmed break (debug). */
    private static double lastImpliedSpeed = -1;
    /** Round trip used by the last observation (debug). */
    private static double lastRttMs = -1;
    /** Below this many ticks the ceil quantisation makes a sample too coarse. */
    private static long assertedMs;
    private static boolean confirmed;
    private static long confirmedMs;
    private static long fadeStartMs;

    /**
     * What the last finished cycle used, kept after the reset.
     *
     * <p>The debug HUD is read after the block is gone, which is exactly when the live fields have been
     * cleared - so "which block was it, what strength did we assume, what did it actually take" was
     * unanswerable from a screenshot.
     */
    private static String lastBlockId;
    private static int lastStrength;
    private static int lastTicks;
    private static double lastImpliedStrength = -1;

    /**
     * Tick-grid phase, in ms, estimated from the arrival times of confirmed breaks.
     *
     * <p>The server finishes a break on a tick boundary, so a block-change packet is sent on the grid and
     * lands here one one-way delay later. Taking {@code arrival - rtt/2} modulo 50 over enough samples
     * gives where the boundaries sit; the assertion can then start from the *next boundary* instead of
     * pretending the server starts the moment it receives the start packet (0-50ms early).
     */
    private static final int PHASE_SAMPLES = 32;
    private static final double[] PHASE = new double[PHASE_SAMPLES];
    private static int phaseCount;
    private static int phaseNext;

    /**
     * True when the button was released at some point in the current cycle.
     *
     * <p>A release lets the server keep (or reset) progress, so the next break can finish in fewer ticks
     * than the formula says. Those samples are not about the strength at all and used to be mixed in.
     */
    private static boolean releasedDuringCycle;

    /** Per-break log line, so the spread can be read instead of one HUD value. */
    private static final dev.skypocket.SkyPocketLog LOGGER =
            dev.skypocket.SkyPocketLog.of("mining");

    /** Predictions raised to the 4-tick floor; debug only. */
    private static int cappedPredictions;

    /**
     * {@code observed / model} per duration bucket.
     *
     * <p>This is the one measurement that separates the two possible errors: an additive offset (a
     * missing tick, the server's own tick, the 4-tick floor) makes the ratio fall towards 1 as the
     * break gets longer, while a wrong speed reading keeps it flat across every bucket. Buckets are
     * by model duration, {@code <=4, <=8, <=16, <=32, >32} ticks.
     */
    private static final int BUCKET_COUNT = 5;
    private static final int RATIO_SIZE = 24;
    private static final double[][] RATIOS = new double[BUCKET_COUNT][RATIO_SIZE];
    private static final int[] RATIO_COUNT = new int[BUCKET_COUNT];
    private static final int[] RATIO_NEXT = new int[BUCKET_COUNT];

    /** Debug HUD values. */
    private static double lastObservedSeconds = -1;
    private static double lastErrorSeconds;
    private static int observations;

    private MiningBreakTracker() {
    }

    /** True when the module is on (checked before every hook does any work). */
    public static boolean enabled() {
        return ConfigManager.get().miningBreakPredictEnabled;
    }

    /** Destroy hook: the block the client is destroying (start or continue). */
    public static void onTarget(BlockPos pos) {
        if (pos == null || !enabled() || pos.equals(targetPos)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !SkyBlockDetector.isActive()) {
            return;
        }
        targetPos = pos.immutable();
        BlockState state = mc.level.getBlockState(targetPos);
        targetBlockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        startMs = System.currentTimeMillis();
        releasedDuringCycle = false;
        parsedSpeedAtStart = MiningSpeedTracker.speed();
        speedAtStart = effectiveSpeed(parsedSpeedAtStart, targetBlockId);
        // Instant is its own rule (per block class), not "the formula returned one tick": after the
        // 4-tick floor the formula never returns 1, and an ore needs twice the speed of a plain block.
        int strength = MiningStrengthTable.strength(targetBlockId);
        int raw = MiningStrengthTable.rawTicks(strength, speedAtStart);
        if (raw > 0 && raw < MiningStrengthTable.minBreakTicks()) {
            // Diagnostic: frequent capping means the strength or the speed is wrong, not the blocks.
            cappedPredictions++;
        }
        int ticks = MiningStrengthTable.breakTicks(targetBlockId, speedAtStart);
        if (ticks <= 0) {
            // Unknown strength, but every candidate agrees: the cap is reachable without knowing which
            // tier it is.
            ticks = MiningStrengthTable.agreedTicks(targetBlockId, speedAtStart);
        }
        lastBlockId = targetBlockId;
        lastStrength = strength;
        lastTicks = ticks;
        predictedSeconds = MiningStrengthTable.isInstant(targetBlockId, speedAtStart) || ticks <= 0
                ? -1
                : ticks / 20.0;
        assertionMs = assertionDelayMs(targetBlockId, predictedSeconds);
        confirmed = false;
        fadeStartMs = 0;
        // Always tracked, even without a known strength: the break is learned from it.
        phase = Phase.RUNNING;
    }

    /** Destroy hook: the client stopped destroying (button released / target lost). */
    public static void onStopDestroy() {
        if (phase == Phase.RUNNING) {
            releasedDuringCycle = true;
            reset();
        }
    }

    /**
     * The server changed the block we are mining — the authoritative break. An
     * identical block id is a re-send (chunk updates do that), not a break.
     */
    public static void onServerBlockChange(BlockPos pos, String blockId) {
        if (phase == Phase.IDLE || targetPos == null || !pos.equals(targetPos)) {
            return;
        }
        if (blockId == null || blockId.equals(targetBlockId)) {
            return;
        }
        if (confirmed) {
            // The same break arrives more than once (block update + chunk update); recording each copy
            // added a second, longer observation for one break.
            return;
        }
        long now = System.currentTimeMillis();
        recordObservation(now);
        if (phase == Phase.RUNNING) {
            assertNow(now);
        }
        confirmed = true;
        confirmedMs = now;
    }

    /** Per-frame state machine, driven by the HUD element. */
    public static void update(Minecraft mc) {
        if (!enabled() || mc.player == null || mc.level == null || !SkyBlockDetector.isActive()) {
            reset();
            return;
        }
        long now = System.currentTimeMillis();
        switch (phase) {
            case RUNNING -> {
                if (!mc.options.keyAttack.isDown()) {
                    reset();
                    return;
                }
                if (assertionMs > 0 && now - startMs >= assertionMs) {
                    assertNow(now);
                }
            }
            case ASSERTED -> {
                long end = confirmed
                        ? confirmedMs + HOLD_AFTER_CONFIRM_MS
                        : assertedMs + PREDICTION_TIMEOUT_MS;
                if (now >= end) {
                    phase = Phase.FADING;
                    fadeStartMs = now;
                }
            }
            case FADING -> {
                if (now - fadeStartMs >= FADE_MS) {
                    reset();
                }
            }
            case IDLE -> {
            }
        }
        MiningStrengthTable.tickPersistence();
    }

    /** "Broken" is asserted: ring closes, outline encloses the block, alert sounds. */
    private static void assertNow(long now) {
        phase = Phase.ASSERTED;
        assertedMs = now;
        ModConfig cfg = ConfigManager.get();
        if (cfg.miningBreakSoundVolume > 0f) {
            SoundManager.play(cfg.miningBreakSound, cfg.miningBreakSoundVolume, cfg.miningBreakSoundPitch);
        }
    }

    /**
     * Fits the model from the confirmed break: server duration = arrival − T0 − delivery.
     *
     * <p>A sample is only kept when the Mining Speed reading was <em>settled</em> and did
     * not move for the whole cycle. The tab list lags the real speed by up to a few
     * seconds, so learning against a value that moved scales the stored strength by the
     * wrong factor — the single biggest cause of a prediction firing far too early.
     */
    private static void recordObservation(long now) {
        if (targetBlockId == null || speedAtStart <= 0 || startMs <= 0) {
            return;
        }
        double settled = MiningSpeedTracker.settledSpeed();
        if (settled <= 0 || Math.abs(settled - parsedSpeedAtStart) > 0.02 * parsedSpeedAtStart) {
            // Logged because these breaks stay out of the buckets, which makes the sample count look
            // complete when it is not.
            LOGGER.debug("skip {}: speed moved {} -> {}", targetBlockId, parsedSpeedAtStart, settled);
            return;
        }
        // Both legs lie between the server's break and the arrival here, so the whole
        // round trip comes off — not half of it.
        double rtt = rttMs();
        if (rtt <= 0) {
            return;
        }
        // The raw arrival is valid whatever the speed reading did, so it always feeds
        // the anchor used to keep the next assertion from firing early.
        LAST_ARRIVAL_MS.put(targetBlockId, now - startMs);
        if (releasedDuringCycle) {
            LOGGER.debug("skip {}: released mid-cycle", targetBlockId);
            LAST_ARRIVAL_MS.put(targetBlockId, now - startMs);
            return;
        }
        addPhase(now - rtt / 2.0);
        double observed = (now - startMs - rtt) / 1000.0;
        // The strength that would have produced this duration: the one number that says whether the
        // disguise table or the speed reading is wrong.
        lastImpliedStrength = observed * 20.0 * speedAtStart / 30.0;
        addRatio(observed * 20.0, lastTicks);
        // One line per confirmed break: the HUD can only show the latest and the medians, and those two
        // disagreed, so the raw samples need to be readable.
        LOGGER.debug("break {} t={} model={}s observed={}s rtt={}ms speed={} scale={}",
                targetBlockId, lastTicks, String.format("%.3f", predictedSeconds),
                String.format("%.3f", observed), Math.round(rttMs()),
                Math.round(parsedSpeedAtStart), String.format("%.2f", timingScale(targetBlockId)));
        if (observed <= 0.02) {
            return;
        }
        lastObservedSeconds = observed;
        lastErrorSeconds = predictedSeconds > 0 ? observed - predictedSeconds : 0;
        observations++;
        MiningStrengthTable.observe(targetBlockId, observed, speedAtStart);
        addFitSample(observed);
    }

    /**
     * Adds one timing sample. Only exact-strength blocks are used — a disguise resolved
     * from an observation was itself identified using this scale, so it would just
     * confirm its own assumption — and only when the break lasts long enough for the
     * {@code ceil} to stay a small fraction of it.
     */
    private static void addFitSample(double observed) {
        if (!ConfigManager.get().miningAutoScale) {
            // No point fitting a correction that is not applied: it only made the number wander.
            return;
        }
        int strength = MiningBlocks.exact(targetBlockId);
        if (strength <= 0 || parsedSpeedAtStart <= 0 || observed <= 0) {
            return;
        }
        lastImpliedSpeed = strength * 30.0 / (observed * 20.0);
        // Unclamped on purpose: this fit measures the speed scale, and a sample the server capped at
        // the floor carries no information about it.
        double rawTicks = Math.ceil(strength * 30.0 / parsedSpeedAtStart);
        if (rawTicks <= MiningStrengthTable.minBreakTicks()) {
            return;
        }
        int category = MiningBlocks.categoryOf(targetBlockId).ordinal();
        FIT_X[category][FIT_INDEX[category]] = rawTicks * TICK_MS;
        FIT_Y[category][FIT_INDEX[category]] = observed * 1000.0 + rttMs();
        FIT_INDEX[category] = (FIT_INDEX[category] + 1) % FIT_WINDOW;
        if (FIT_COUNT[category] < FIT_WINDOW) {
            FIT_COUNT[category]++;
        }
        fitTiming(category);
    }

    /** Drops the fitted samples, so the scale goes back to being re-learned from scratch. */
    public static void clearFit() {
        for (int i = 0; i < CATEGORY_COUNT; i++) {
            FIT_COUNT[i] = 0;
            FIT_INDEX[i] = 0;
        }
        ConfigManager.get().miningCategoryScale.clear();
        for (int b = 0; b < BUCKET_COUNT; b++) {
            RATIO_COUNT[b] = 0;
            RATIO_NEXT[b] = 0;
        }
    }

    private static int bucketFor(double modelTicks) {
        if (modelTicks <= 4) {
            return 0;
        }
        if (modelTicks <= 8) {
            return 1;
        }
        if (modelTicks <= 16) {
            return 2;
        }
        return modelTicks <= 32 ? 3 : 4;
    }

    private static void addRatio(double observedTicks, double modelTicks) {
        if (observedTicks <= 0 || modelTicks <= 0) {
            return;
        }
        int bucket = bucketFor(modelTicks);
        RATIOS[bucket][RATIO_NEXT[bucket]] = observedTicks / modelTicks;
        RATIO_NEXT[bucket] = (RATIO_NEXT[bucket] + 1) % RATIO_SIZE;
        if (RATIO_COUNT[bucket] < RATIO_SIZE) {
            RATIO_COUNT[bucket]++;
        }
    }

    /** Median observed/model per bucket, e.g. {@code <=4:1.02(9)  5-8:0.98(12)}. */
    public static String debugRatios() {
        String[] labels = {"<=4", "5-8", "9-16", "17-32", ">32"};
        StringBuilder out = new StringBuilder();
        for (int bucket = 0; bucket < BUCKET_COUNT; bucket++) {
            if (RATIO_COUNT[bucket] == 0) {
                continue;
            }
            double[] copy = java.util.Arrays.copyOf(RATIOS[bucket], RATIO_COUNT[bucket]);
            java.util.Arrays.sort(copy);
            if (out.length() > 0) {
                out.append("  ");
            }
            out.append(labels[bucket]).append(':').append(String.format("%.2f", copy[copy.length / 2]))
                    .append('(').append(RATIO_COUNT[bucket]).append(')');
        }
        return out.length() == 0 ? "-" : out.toString();
    }

    /**
     * Fits the speed scale only: {@code scale = median((arrival - delivery) / rawModel)}.
     *
     * <p>The arrival delay is deliberately <em>not</em> fitted. Fitting both at once needs
     * a wide spread of durations, and a player who mines one kind of block does not have
     * one — the two parameters are then collinear, and the least squares happily returns
     * a wild pair (e.g. scale 0.46 with a 360 ms delay) that matches the sample range
     * while stretching every assertion by the bogus delay. That error is a constant, so
     * it hurts short breaks most. The delay is a physical quantity (half a round trip
     * plus the server's tick), so it comes from the ping instead and is independent of
     * the scale.
     */
    private static void fitTiming(int category) {
        double rtt = rttMs();
        if (rtt <= 0) {
            return;
        }
        double[] ratios = new double[FIT_WINDOW];
        int n = 0;
        for (int i = 0; i < FIT_COUNT[category]; i++) {
            double x = FIT_X[category][i];
            double y = FIT_Y[category][i];
            if (x <= 0 || y - rtt <= 0) {
                continue;
            }
            // Half a tick added back: the model rounds up, so a true 5.2-tick break is modelled as 6 and
            // the plain ratio reads 0.87 even when nothing is wrong. The bias is 1/(2T), i.e. 8% at six
            // ticks and 2% at thirty - without this the fitted scale was dragged down most by short breaks.
            ratios[n++] = (y - rtt + 0.5 * TICK_MS) / x;
        }
        // Eight samples: with four the median jumped on every other break, which is what made the
        // applied correction wander while the player mined one kind of block.
        if (n < 8) {
            return;
        }
        java.util.Arrays.sort(ratios, 0, n);
        double scale = n % 2 == 1
                ? ratios[n / 2]
                : (ratios[n / 2 - 1] + ratios[n / 2]) / 2.0;
        if (scale < 0.4 || scale > 2.5) {
            return;
        }
        ModConfig cfg = ConfigManager.get();
        MiningBlocks.Category type = MiningBlocks.Category.values()[category];
        Double old = cfg.miningCategoryScale.get(type.name());
        if (old == null || Math.abs(old - scale) > 0.005) {
            cfg.miningCategoryScale.put(type.name(), scale);
            MiningStrengthTable.markDirty();
        }
    }

    /** Fitted speed scale for this block's class: the true duration is this many times the formula's. */
    private static double timingScale(String blockId) {
        if (blockId == null) {
            return 1.0;
        }
        Double scale = ConfigManager.get().miningCategoryScale
                .get(MiningBlocks.categoryOf(blockId).name());
        return scale == null || scale <= 0 ? 1.0 : scale;
    }

    /** Fitted scales, for the debug HUD. */
    public static String debugScales() {
        StringBuilder out = new StringBuilder();
        for (MiningBlocks.Category type : MiningBlocks.Category.values()) {
            Double scale = ConfigManager.get().miningCategoryScale.get(type.name());
            if (out.length() > 0) {
                out.append("  ");
            }
            out.append(type.name(), 0, Math.min(2, type.name().length())).append(':')
                    .append(String.format("%.2f", scale == null ? 1.0 : scale))
                    .append('(').append(FIT_COUNT[type.ordinal()]).append(')');
        }
        return out.toString();
    }

    /**
     * Round trip (ms) from the ping. Two different legs of it matter, and mixing them up
     * is what made every learned duration half a round trip too long:
     * <ul>
     *   <li>the <em>request</em> leg (half the round trip) is how much later than T0 the
     *       server starts counting, so the assertion <b>adds</b> it;</li>
     *   <li>the whole round trip lies between the server breaking the block and the
     *       client learning about it, so learning <b>subtracts</b> that.</li>
     * </ul>
     */
    private static double rttMs() {
        int rtt = PingMonitor.latency();
        lastRttMs = rtt > 0 ? rtt : 0.0;
        return lastRttMs;
    }

    /** The request leg only: how much later than T0 the server begins counting. */
    private static double oneWayMs() {
        return rttMs() / 2.0;
    }

    /** Parsed speed corrected by the fitted scale; the basis of every prediction. */
    private static double effectiveSpeed(double parsed, String blockId) {
        // With the auto-fit off the model uses the speed exactly as read, which is what makes the buckets
        // interpretable: no moving correction in the way.
        if (parsed <= 0) {
            return -1;
        }
        return ConfigManager.get().miningAutoScale ? parsed / timingScale(blockId) : parsed;
    }

    /**
     * Assertion delay in ms: the (corrected) duration plus the request leg of the round
     * trip, plus the configured bias. Returns -1 when the block has no known strength.
     *
     * <p>The prediction is the model itself. It is <em>not</em> the maximum of the model
     * and the previous break: those are two noisy estimates of the same moment, and
     * taking the larger one is systematically late by roughly half the noise, which is
     * the opposite of what a prediction is for. The previous break of the same block is
     * used only as a guard against a clearly stale speed reading — a model more than a
     * tick and a half shorter than what this block actually took last time.
     */
    private static double assertionDelayMs(String blockId, double seconds) {
        if (seconds <= 0) {
            return -1;
        }
            // The server begins counting on the first tick boundary after it receives the start, not the
        // moment it receives it: start the model from that boundary when the grid is known.
        String target = ConfigManager.get().miningAlignTarget;
        double delay = switch (target) {
            case "server" -> oneWayMs();
            case "client" -> rttMs();
            default -> 0;
        };
        double model;
        if (delay <= 0) {
            // Zero-latency goal: no grid snapping either, because the press is not on the grid.
            model = seconds * 1000.0 + biasMs();
        } else {
            double start = ConfigManager.get().miningPhaseAlign && phaseMs() >= 0
                    ? toTickBoundary(startMs + delay)
                    : startMs + delay;
            model = (start - startMs) + seconds * 1000.0 + biasMs();
        }
        Long lastArrival = LAST_ARRIVAL_MS.get(blockId);
        if (lastArrival == null) {
            return model;
        }
        // When the server actually performed it last time (arrival minus both legs).
        double lastBreak = lastArrival - rttMs();
        return model < lastBreak - 1.5 * TICK_MS ? lastBreak + biasMs() : model;
    }

    /** Records one grid-boundary sample: arrival minus the return leg, folded into one tick. */
    private static void addPhase(double boundaryMs) {
        PHASE[phaseNext] = ((boundaryMs % TICK_MS) + TICK_MS) % TICK_MS;
        phaseNext = (phaseNext + 1) % PHASE_SAMPLES;
        if (phaseCount < PHASE_SAMPLES) {
            phaseCount++;
        }
    }

    /** Median grid phase in ms, or -1 while there are too few samples to trust it. */
    private static double phaseMs() {
        if (phaseCount < 8) {
            return -1;
        }
        double[] copy = java.util.Arrays.copyOf(PHASE, phaseCount);
        java.util.Arrays.sort(copy);
        return copy[copy.length / 2];
    }

    /** The first tick boundary at or after {@code t}. */
    private static double toTickBoundary(double t) {
        double phase = phaseMs();
        if (phase < 0) {
            return t;
        }
        return t + (((phase - t) % TICK_MS) + TICK_MS) % TICK_MS;
    }

    /** Grid phase estimate for the debug HUD, e.g. {@code 23ms(14)}. */
    public static String debugPhase() {
        double phase = phaseMs();
        return (phase < 0 ? "-" : String.format("%.0fms", phase)) + "(" + phaseCount + ")";
    }
    private static double biasMs() {
        return ConfigManager.get().miningBreakBiasTicks * TICK_MS;
    }

    /** Only display when the block's strength is known and it is not instant-break. */
    private static boolean showable() {
        return assertionMs > 0;
    }

    /** Ring progress [0..1], or {@code -1} when nothing should be drawn. */
    public static float progress() {
        if (!showable()) {
            return -1f;
        }
        return switch (phase) {
            case RUNNING -> {
                yield assertionMs <= 0 ? -1f
                        : (float) Math.min(UNCONFIRMED_CAP, (System.currentTimeMillis() - startMs) / assertionMs);
            }
            case ASSERTED, FADING -> 1f;
            case IDLE -> -1f;
        };
    }

    /** Fade-out alpha [0..1]. */
    public static float alpha() {
        if (phase == Phase.FADING) {
            return Math.max(0f, 1f - (System.currentTimeMillis() - fadeStartMs) / (float) FADE_MS);
        }
        return 1f;
    }

    /** Block to draw the growing outline on, or null when nothing should be drawn. */
    public static BlockPos outlinePos() {
        if (!showable() || phase == Phase.IDLE) {
            return null;
        }
        return targetPos;
    }

    public static void reset() {
        phase = Phase.IDLE;
        targetPos = null;
        targetBlockId = null;
        startMs = 0;
        predictedSeconds = -1;
        speedAtStart = -1;
        confirmed = false;
        fadeStartMs = 0;
    }

    // ---- debug HUD ----

    public static String debugBlock() {
        return targetBlockId;
    }

    public static double debugPredicted() {
        return predictedSeconds;
    }

    public static double debugObserved() {
        return lastObservedSeconds;
    }

    /** Block id, assumed strength, tick count and implied strength of the last finished cycle. */
    public static String debugLastBlock() {
        return lastBlockId;
    }

    public static int debugLastStrength() {
        return lastStrength;
    }

    public static int debugLastTicks() {
        return lastTicks;
    }

    public static double debugImpliedStrength() {
        return lastImpliedStrength;
    }

    /** How many predictions were lifted to the 4-tick floor (debug). */
    public static int debugCappedPredictions() {
        return cappedPredictions;
    }

    public static double debugError() {
        return lastErrorSeconds;
    }

    /** Frozen assertion delay of the current cycle in ms (model + bias + anchor). */
    public static double debugAssertion() {
        return assertionMs;
    }

    public static int debugObservations() {
        return observations;
    }

    /** Strength used for the block currently being mined (-1 = unknown). */
    public static int debugStrength() {
        return MiningStrengthTable.strength(targetBlockId);
    }

    /** Effective Mining Speed the last confirmed break implies (-1 = none yet). */
    public static double debugImpliedSpeed() {
        return lastImpliedSpeed;
    }

    /** Round trip (ms) used by the last observation. */
    public static double debugRttMs() {
        return lastRttMs;
    }

    /** Timing samples collected this session, summed over the block classes. */
    public static int debugFitSamples() {
        int total = 0;
        for (int count : FIT_COUNT) {
            total += count;
        }
        return total;
    }

    /** Raw tab reading and the corrected value actually used. */
    public static double debugParsedSpeed() {
        return parsedSpeedAtStart;
    }

    public static double debugEffectiveSpeed() {
        return speedAtStart;
    }

    /** Where that strength comes from: exact table, resolved by observation, or none. */
    public static String debugSource() {
        if (targetBlockId == null) {
            return "-";
        }
        if (MiningBlocks.exact(targetBlockId) > 0) {
            return "exact";
        }
        return MiningStrengthTable.strength(targetBlockId) > 0 ? "resolved" : "unresolved";
    }

    public static boolean debugConfirmed() {
        return confirmed;
    }
}
