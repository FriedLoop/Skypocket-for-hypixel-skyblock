package dev.skypocket.feature.modules;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.feature.FeatureModule;
import dev.skypocket.gui.Category;
import dev.skypocket.gui.setting.Setting;

import java.util.List;

/**
 * Disables the camera shake applied when the player takes damage.
 *
 * <p>Phase 1 migration, one of the three simplest features: no settings, one boolean. The toggle is
 * read by {@code GameRendererMixin#sky$disableHurtShake}, which is why the field stays on
 * {@code ModConfig} for this step (see {@link ThirdPersonFeature} for the reasoning).
 */
public final class NoHurtCameraFeature implements FeatureModule {

    @Override
    public String id() {
        return "no_hurt_camera";
    }

    @Override
    public String name() {
        return "受伤镜头不抖动";
    }

    @Override
    public Category category() {
        return Category.RENDER;
    }
    @Override
    public Object config() {
        return ConfigManager.get().noHurtCameraBlock;
    }

    @Override
    public boolean isEnabled() {
        return ConfigManager.get().noHurtCameraBlock.noHurtCameraEnabled;
    }

    @Override
    public void setEnabled(boolean value) {
        ConfigManager.get().noHurtCameraBlock.noHurtCameraEnabled = value;
    }

    @Override
    public List<Setting> settings() {
        return List.of();
    }
}
