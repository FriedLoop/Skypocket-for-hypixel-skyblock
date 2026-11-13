package dev.skypocket.feature.modules;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.feature.FeatureModule;
import dev.skypocket.gui.Category;
import dev.skypocket.gui.setting.Setting;

import java.util.List;

/**
 * Hides arrows already stuck in a body.
 *
 * <p>Phase 4 migration: toggle only, no settings. Same shape as the Phase 1 trio, so the only new
 * thing it exercises is the {@code markSkyBlockOnly} chain through {@code ModuleAdapter}.
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

    @Override
    public boolean isEnabled() {
        return ConfigManager.get().hideStuckArrows;
    }

    @Override
    public void setEnabled(boolean value) {
        ConfigManager.get().hideStuckArrows = value;
    }
}
