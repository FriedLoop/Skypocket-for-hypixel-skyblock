package dev.skypocket.feature.modules;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.feature.FeatureModule;
import dev.skypocket.gui.Category;

/**
 * Hides arrows that have landed and are stuck in the ground. Toggle only.
 *
 * <p>State lives in its own config block, matching {@code hide_stuck_arrows}. Landed arrows are the
 * easy one to produce in game, so this is the feature that actually exercises the block round-trip.
 */
public final class HideLandedArrowsFeature implements FeatureModule {

    @Override
    public String id() {
        return "hide_landed_arrows";
    }

    @Override
    public String name() {
        return "隐藏落地箭矢";
    }

    @Override
    public Category category() {
        return Category.ESP;
    }

    @Override
    public Object config() {
        return ConfigManager.get().hideLandedArrowsBlock;
    }

    @Override
    public boolean isEnabled() {
        return ConfigManager.get().hideLandedArrowsBlock.enabled;
    }

    @Override
    public void setEnabled(boolean value) {
        ConfigManager.get().hideLandedArrowsBlock.enabled = value;
    }
}
