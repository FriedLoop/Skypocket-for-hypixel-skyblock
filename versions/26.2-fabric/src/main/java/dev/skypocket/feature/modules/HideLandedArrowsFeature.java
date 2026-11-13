package dev.skypocket.feature.modules;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.feature.FeatureModule;
import dev.skypocket.gui.Category;
import dev.skypocket.gui.setting.Setting;

import java.util.List;

/** Hides arrows that have landed and are stuck in the ground. Toggle only. */
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
    public boolean isEnabled() {
        return ConfigManager.get().hideLandedArrows;
    }

    @Override
    public void setEnabled(boolean value) {
        ConfigManager.get().hideLandedArrows = value;
    }
}
