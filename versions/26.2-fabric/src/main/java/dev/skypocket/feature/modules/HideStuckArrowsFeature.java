package dev.skypocket.feature.modules;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.feature.FeatureModule;
import dev.skypocket.gui.Category;
import dev.skypocket.gui.setting.Setting;

import java.util.List;

/**
 * Hides arrows already stuck in a body.
 *
 * <p>The first feature whose state lives in its own config block instead of a flat {@code ModConfig}
 * field. It is the smallest feature in the mod, which is why it goes first: the shape can be
 * reviewed on one boolean before the other 34 follow it.
 */
public final class HideStuckArrowsFeature implements FeatureModule {

    @Override
    public String id() {
        return "hide_stuck_arrows";
    }

    @Override
    public String name() {
        return "隐藏插体箭矢";
    }

    @Override
    public Category category() {
        return Category.ESP;
    }

    /**
     * The feature's own state.
     *
     * <p>Returns the instance held by {@code ModConfig} rather than a field of this class: the config
     * object is created by Gson during load, so the feature cannot own the instance and still have the
     * saved values land in it.
     */
    @Override
    public Object config() {
        return ConfigManager.get().hideStuckArrowsBlock;
    }

    @Override
    public boolean isEnabled() {
        return ConfigManager.get().hideStuckArrowsBlock.enabled;
    }

    @Override
    public void setEnabled(boolean value) {
        ConfigManager.get().hideStuckArrowsBlock.enabled = value;
    }
}
