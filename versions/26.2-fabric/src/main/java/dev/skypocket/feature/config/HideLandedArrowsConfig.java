package dev.skypocket.feature.config;

/**
 * Persisted state for the {@code hide_landed_arrows} feature.
 *
 * <p>Second per-feature block, added alongside {@code hide_stuck_arrows}'s so the two can be tested
 * as a pair: landed arrows are trivial to produce in game, which makes this the one that actually
 * verifies the block round-trips through the config file.
 */
public final class HideLandedArrowsConfig {
    /** Hide arrows that have landed and are stuck in the ground. */
    public boolean enabled = false;
}
