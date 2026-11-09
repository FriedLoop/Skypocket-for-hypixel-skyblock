package dev.skypocket.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

public final class ConfigManager {
    private static final dev.skypocket.SkyPocketLog LOGGER =
            dev.skypocket.SkyPocketLog.of("config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("skypocket.json");

    /** Shape version this build writes. Bump it together with a new entry in {@link #MIGRATIONS}. */
    public static final int CURRENT_VERSION = 2;

    /**
     * Ordered upgrade steps: entry {@code i} turns a v{i} file into v{i+1}.
     *
     * <p>This replaces the old safety net of "if the file does not parse, use defaults": that silently
     * threw a player's settings away, and the next save wrote the defaults over the file for good. A
     * change in a field's shape now gets an explicit step here - it can read the old value and write the
     * new one, and a step that fails leaves the file at the version it was on instead of destroying it.
     */
    private static final List<Consumer<ModConfig>> MIGRATIONS = List.of(
            // v0 -> v1: the bars gained artwork authored for a 20 px height. 6 was the old default and
            // crushed the design into a dark line; a height chosen deliberately (8 or more) is left alone.
            cfg -> {
                if (cfg.statsBarHeight == 6) {
                    cfg.statsBarHeight = 20;
                }
            },
            // v1 -> v2: hudSkyPocketFont was added; the field default is off, so no conversion.
            cfg -> {
            });

    private static ModConfig config = new ModConfig();
    /** Bumped on every load/save so cached UI can tell that some setting changed. */
    private static volatile long revision;

    private ConfigManager() {
    }

    public static ModConfig get() {
        return config;
    }

    /** Monotonic counter of config writes; part of the Click GUI panel's cache key. */
    public static long revision() {
        return revision;
    }

    /**
     * Signals that a setting changed <em>without</em> a save.
     *
     * <p>Most settings are plain field writes ({@code cfg.someFlag = v}) - the module
     * definitions and the panel settings never call {@link #save()}. The Click GUI panel cache
     * keys on {@link #revision()}, so without this the cached texture stayed on screen after a
     * toggle and only refreshed when some other part of the key changed (the mouse moving, or the
     * 600-frame backstop) - "switching a module updates the button very slowly, it feels laggy".
     * {@code Setting} / {@code Module} wrap every setter they expose with this call.
     */
    public static void touch() {
        revision++;
    }

    public static void load() {
        boolean fresh = false;
        if (Files.exists(CONFIG_PATH)) {
            try (var reader = Files.newBufferedReader(CONFIG_PATH)) {
                config = GSON.fromJson(reader, ModConfig.class);
            } catch (IOException | RuntimeException e) {
                // A parse failure used to be almost invisible: defaults replaced the settings in memory,
                // and the first later save wrote them over the file - everything reset, with one WARN to
                // explain it. Keep the unreadable file and say so at ERROR level, so it can be recovered
                // and the cause (usually a field whose type changed) is visible.
                backUpUnreadable(e);
                config = new ModConfig();
                fresh = true;
            }
        } else {
            config = new ModConfig();
            fresh = true;
        }
        if (config == null) {
            config = new ModConfig();
            fresh = true;
        }
        // A brand-new config has nothing to migrate; skip the one-time scale rescale and mark it current.
        if (fresh) {
            config.clickGuiScaleVersion = 1;
            config.configVersion = CURRENT_VERSION;
        } else {
            migrate(config);
        }
        config.sanitize();
    }

    /**
     * Brings a file written by an older build up to {@link #CURRENT_VERSION}, then writes the result
     * back once so the work is not repeated on every start.
     */
    private static void migrate(ModConfig config) {
        int from = Math.max(0, config.configVersion);
        if (from > CURRENT_VERSION) {
            LOGGER.warn("config is v{} but this build only knows v{}; unknown settings will be dropped "
                    + "on the next save", from, CURRENT_VERSION);
            config.configVersion = CURRENT_VERSION;
            return;
        }
        if (from == CURRENT_VERSION) {
            return;
        }
        if (MIGRATIONS.size() < CURRENT_VERSION) {
            // The version was bumped without adding the step that goes with it. Refuse instead of
            // throwing IndexOutOfBounds in the middle of loading the config.
            LOGGER.error("config v{} has no migration step (only {} defined); leaving the file as is",
                    CURRENT_VERSION, MIGRATIONS.size());
            return;
        }
        for (int i = from; i < CURRENT_VERSION; i++) {
            try {
                MIGRATIONS.get(i).accept(config);
            } catch (RuntimeException e) {
                // Leave the file at the version it was on: a broken step must not look like a successful
                // upgrade, or the data it was meant to convert is skipped for good.
                LOGGER.warn("config migration v{} -> v{} failed; the file stays at v{}", i, i + 1, i, e);
                config.configVersion = i;
                return;
            }
        }
        config.configVersion = CURRENT_VERSION;
        save();
    }

    /**
     * Copies an unreadable config aside before defaults take over.
     *
     * <p>Without this the settings were simply lost: the defaults are in memory, and the next save
     * rewrites the file from them. The copy keeps the original text, so a broken field can be fixed by
     * hand instead of re-entering every setting.
     */
    private static void backUpUnreadable(Exception cause) {
        Path backup = CONFIG_PATH.resolveSibling(CONFIG_PATH.getFileName() + ".unreadable");
        try {
            Files.copy(CONFIG_PATH, backup, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            LOGGER.error("could not read the config; it was copied to {} and defaults are in use",
                    backup.getFileName(), cause);
        } catch (IOException copy) {
            LOGGER.error("could not read the config, and could not back it up", copy);
        }
    }

    public static void save() {
        revision++;
        config.sanitize();
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            // Keep the last good file next to it. Saves are infrequent (a setting change, closing a
            // screen), so the copy is cheap, and it turns "my settings were wiped" into "rename .bak".
            if (Files.exists(CONFIG_PATH)) {
                Files.copy(CONFIG_PATH, CONFIG_PATH.resolveSibling(CONFIG_PATH.getFileName() + ".bak"),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            // Write to a temporary file and swap it in. Writing straight into the real file means a crash
            // (or a killed game) leaves it truncated, and the next start cannot parse it - which is one
            // way every setting ends up back at its default.
            Path temp = CONFIG_PATH.resolveSibling(CONFIG_PATH.getFileName() + ".tmp");
            try (var writer = Files.newBufferedWriter(temp)) {
                GSON.toJson(config, writer);
            }
            try {
                Files.move(temp, CONFIG_PATH, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temp, CONFIG_PATH, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to write config", e);
        }
    }
}
