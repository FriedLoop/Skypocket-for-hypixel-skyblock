package dev.skypocket.gui;

import dev.skypocket.gui.setting.Setting;

import java.util.List;

/**
 * The standard contract every Click GUI feature follows. A feature is a toggleable
 * unit identified by a stable {@link #id()}, grouped under a {@link #category()}, and
 * optionally exposing a declarative list of {@link Setting}s.
 *
 * <p>Whether a feature {@link #hasSettings()} drives how the panel renders its card:
 * a feature without settings shows a disabled "no settings" button and is only
 * togglable, while a feature with settings opens the shared UI v2 settings page.
 *
 * <p>This interface is the feature template: a new feature is added by providing these
 * members (typically via {@link Module}) and registering an instance in
 * {@link ModuleRegistry}.
 */
public interface Feature {

    /** Stable identifier, used to look up the feature's icon texture. */
    String id();

    /** Localized display name (current UI language). */
    String name();

    /** Source (Chinese) name, so search works across languages. */
    String rawName();

    Category category();

    /** Whether the feature is meaningful only on Hypixel SkyBlock; used for the SB badge. */
    default boolean skyBlockOnly() {
        return false;
    }

    boolean isEnabled();

    void setEnabled(boolean value);

    List<Setting> settings();

    /** Convenience: whether the feature exposes any settings. */
    default boolean hasSettings() {
        return !settings().isEmpty();
    }

    /** Whether the name matches the query (both localized and source). */
    default boolean matchesName(String query) {
        String q = query.toLowerCase();
        return name().toLowerCase().contains(q) || rawName().toLowerCase().contains(q);
    }

    /** Whether any setting matches the query. */
    default boolean matchesAnySetting(String query) {
        String q = query.toLowerCase();
        for (Setting s : settings()) {
            if (s.matches(q)) {
                return true;
            }
        }
        return false;
    }

    /** Total expanded height in px, derived from the settings list. */
    default int settingsHeight() {
        int h = 0;
        for (Setting s : settings()) {
            if (s.isVisible()) {
                h += s.totalHeight();
            }
        }
        return h;
    }
}
