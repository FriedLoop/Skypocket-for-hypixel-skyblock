package dev.skypocket.feature.modules;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.feature.FeatureModule;
import dev.skypocket.feature.config.AutoSprintConfig;
import dev.skypocket.gui.Category;

/**
 * Keeps the sprint key held, so the player does not have to hold it.
 *
 * <p>Not a SkyBlock feature: sprinting matters everywhere, so this is a plain movement toggle.
 *
 * <p>State lives in {@link AutoSprintConfig}. The toggle is read by {@code KeyboardInputMixin}, which
 * is why the block is reached through {@code ConfigManager.get()} there rather than injected.
 */
public final class AutoSprintFeature implements FeatureModule {

    @Override
    public String id() {
        return "auto_sprint";
    }

    @Override
    public String name() {
        return "自动疾跑";
    }

    @Override
    public Category category() {
        return Category.MOVEMENT;
    }

    @Override
    public Object config() {
        return ConfigManager.get().autoSprintBlock;
    }

    @Override
    public boolean isEnabled() {
        return ConfigManager.get().autoSprintBlock.autoSprintEnabled;
    }

    @Override
    public void setEnabled(boolean value) {
        ConfigManager.get().autoSprintBlock.autoSprintEnabled = value;
    }
}
