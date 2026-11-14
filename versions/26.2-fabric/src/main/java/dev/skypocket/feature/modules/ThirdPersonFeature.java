package dev.skypocket.feature.modules;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;
import dev.skypocket.feature.FeatureModule;
import dev.skypocket.gui.Category;
import dev.skypocket.gui.setting.Setting;

import java.util.List;

/**
 * Third-person camera can see through blocks.
 *
 * <p>Phase 1 migration: this is one of the three simplest features (no settings, one boolean). It
 * exists to prove the registry can produce the same {@code Module} the hand-written
 * {@code ModuleDefinitions.registerThirdPerson} used to produce, before the harder ones move.
 *
 * <p>The config field stays on {@link ModConfig} for now rather than moving into a dedicated block.
 * {@code ThirdPersonCamera} reads it directly, and moving it would mean touching the render path in
 * the same step that changes how features are registered - two changes at once, with a test between
 * them impossible. Block extraction is Phase 3.
 */
public final class ThirdPersonFeature implements FeatureModule {

    @Override
    public String id() {
        return "third_person";
    }

    @Override
    public String name() {
        return "第三人称穿墙";
    }

    @Override
    public Category category() {
        return Category.RENDER;
    }
    @Override
    public Object config() {
        return ConfigManager.get().thirdPersonBlock;
    }

    @Override
    public boolean isEnabled() {
        return ConfigManager.get().thirdPersonBlock.thirdPersonThroughWalls;
    }

    @Override
    public void setEnabled(boolean value) {
        ConfigManager.get().thirdPersonBlock.thirdPersonThroughWalls = value;
    }

    @Override
    public List<Setting> settings() {
        return List.of();
    }
}
