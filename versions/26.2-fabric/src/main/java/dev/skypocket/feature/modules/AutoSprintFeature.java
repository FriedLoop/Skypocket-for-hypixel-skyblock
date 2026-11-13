package dev.skypocket.feature.modules;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.feature.FeatureModule;
import dev.skypocket.gui.Category;
import dev.skypocket.gui.setting.Setting;

import java.util.List;

/**
 * Keeps the sprint key held, so the player does not have to hold it.
 *
 * <p>Phase 1 migration, one of the three simplest features: no settings, one boolean. The toggle is
 * read by {@code KeyboardInputMixin}, which is why the field stays on {@code ModConfig} for this
 * step (see {@link ThirdPersonFeature} for the reasoning).
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
    public boolean isEnabled() {
        return ConfigManager.get().autoSprintEnabled;
    }

    @Override
    public void setEnabled(boolean value) {
        ConfigManager.get().autoSprintEnabled = value;
    }

    @Override
    public List<Setting> settings() {
        return List.of();
    }
}
