package dev.skypocket.gui;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.gui.setting.Setting;
import dev.skypocket.i18n.Localization;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Default {@link Feature} implementation for the Click GUI. Its settings are a
 * declarative list of {@link Setting}s; the panel lays them out automatically and
 * derives the expanded height, so there are no hand-maintained heights.
 *
 * <p>Prefer this class over implementing {@link Feature} directly for concrete
 * features: the constructor wires the enabled getter/setter and the settings list in
 * one place, which is the standard feature template used by {@link ModuleDefinitions}.
 */
public final class Module implements Feature {
    private final String id;
    private final String name;
    private final Category category;
    private final BooleanSupplier enabled;
    private final Consumer<Boolean> setEnabled;
    private final List<Setting> settings;

    public Module(String id, String name, Category category, BooleanSupplier enabled, Consumer<Boolean> setEnabled, List<Setting> settings) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.enabled = enabled;
        // Bump the config revision so the Click GUI panel cache re-bakes: the definitions write a
        // plain config field without saving, and the cache key contains ConfigManager.revision().
        this.setEnabled = value -> {
            setEnabled.accept(value);
            ConfigManager.touch();
        };
        this.settings = settings;
    }

    @Override public String id() {
        return id;
    }

    @Override public String name() {
        return Localization.tr(name);
    }

    @Override public String rawName() {
        return name;
    }

    @Override public Category category() {
        return category;
    }

    @Override public boolean isEnabled() {
        return enabled.getAsBoolean();
    }

    @Override public void setEnabled(boolean value) {
        setEnabled.accept(value);
    }

    @Override public List<Setting> settings() {
        return settings;
    }
}
