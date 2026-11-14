package dev.skypocket.crosshair;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.skypocket.SkyPocketLog;
import dev.skypocket.config.ModConfig;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Saved crosshairs on disk, plus the built-ins.
 *
 * <p>One JSON file per preset under {@code config/skypocket/crosshairs/}, following the pattern
 * {@code ThemeStore} established for themes: presets are design assets, they are edited outside the
 * game as often as inside it, and a broken one must not be able to corrupt the player's live config.
 * Keeping them out of {@code skypocket.json} is what makes that true.
 *
 * <p>Every operation is failure-tolerant by design. A preset that cannot be read is skipped with a
 * log line rather than aborting the list; a write that fails reports {@code false} so the caller can
 * tell the player. None of these paths is worth a crash.
 */
public final class CrosshairStore {
    private static final SkyPocketLog LOGGER = SkyPocketLog.of("crosshair");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DIR =
            FabricLoader.getInstance().getConfigDir().resolve("skypocket/crosshairs");

    /** File format version, so a future shape change can migrate rather than guess. */
    private static final int FORMAT_VERSION = 1;

    private CrosshairStore() {
    }

    /**
     * Every available name: built-ins first, then files on disk, sorted.
     *
     * <p>Built-ins come first because they are what a new player wants; user presets follow in a
     * stable order so the picker does not reshuffle between openings.
     */
    public static List<String> names() {
        List<String> names = new ArrayList<>(BuiltinCrosshairs.names());
        if (Files.isDirectory(DIR)) {
            try (var stream = Files.list(DIR)) {
                stream.filter(p -> p.getFileName().toString().endsWith(".json"))
                        .map(p -> p.getFileName().toString().replace(".json", ""))
                        .filter(n -> !BuiltinCrosshairs.isBuiltin(n))
                        .sorted()
                        .forEach(names::add);
            } catch (IOException e) {
                // A preset list that cannot be read is not worth failing over: the built-ins remain.
                LOGGER.warn("could not list crosshair presets: {}", e.toString());
            }
        }
        return names;
    }

    /**
     * Loads a preset by name, built-in or file.
     *
     * @return the preset, or {@code null} when the name is unknown or the file is unusable
     */
    public static CrosshairPreset load(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        CrosshairPreset builtin = BuiltinCrosshairs.byName(name);
        if (builtin != null) {
            return builtin;
        }
        Path file = DIR.resolve(name + ".json");
        if (!Files.exists(file)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            return fromJson(name, root);
        } catch (IOException | RuntimeException e) {
            // A malformed preset must not take the settings page down with it.
            LOGGER.warn("could not read crosshair preset {}: {}", name, e.toString());
            return null;
        }
    }

    /**
     * Saves a preset under its name.
     *
     * <p>Built-in names are refused: writing {@code 经典十字.json} would shadow a built-in and then
     * never be deletable, which is a state with no way out from the UI.
     *
     * @return whether the file was written
     */
    public static boolean save(CrosshairPreset preset) {
        if (preset == null || !preset.hasUsableName()) {
            return false;
        }
        if (BuiltinCrosshairs.isBuiltin(preset.name())) {
            LOGGER.warn("refusing to overwrite built-in crosshair preset {}", preset.name());
            return false;
        }
        return write(preset.name(), toJson(preset));
    }

    /**
     * Deletes a user preset.
     *
     * <p>Built-ins are code, not files, so there is nothing to delete; returning false lets the
     * caller explain why instead of appearing to succeed.
     */
    public static boolean delete(String name) {
        if (name == null || name.isBlank() || BuiltinCrosshairs.isBuiltin(name)) {
            return false;
        }
        try {
            return Files.deleteIfExists(DIR.resolve(name + ".json"));
        } catch (IOException e) {
            LOGGER.warn("could not delete crosshair preset {}: {}", name, e.toString());
            return false;
        }
    }

    /** Whether the name is a built-in, which the UI greys out in its delete button. */
    public static boolean isBuiltin(String name) {
        return BuiltinCrosshairs.isBuiltin(name);
    }

    // ---- serialisation --------------------------------------------------------------------

    private static JsonObject toJson(CrosshairPreset preset) {
        JsonObject root = new JsonObject();
        root.addProperty("version", FORMAT_VERSION);
        root.addProperty("name", preset.name());
        root.addProperty("pixelSize", preset.pixelSize());
        root.addProperty("scale", preset.scale());
        root.addProperty("stroke", preset.stroke());
        root.addProperty("strokeWidth", preset.strokeWidth());
        // Stored as a hex string rather than a number: the value is a packed ARGB int, and Gson
        // writes those as signed decimals, which is unreadable when hand-editing a preset.
        root.addProperty("color", String.format("#%08X", preset.color()));

        // The grid as a list of row strings ("#..#"), which is compact and editable by hand compared
        // with an array of 256 booleans.
        JsonArray rows = new JsonArray();
        int n = ModConfig.GRID;
        boolean[] grid = preset.grid();
        for (int y = 0; y < n; y++) {
            StringBuilder row = new StringBuilder(n);
            for (int x = 0; x < n; x++) {
                row.append(grid != null && y * n + x < grid.length && grid[y * n + x] ? '#' : '.');
            }
            rows.add(row.toString());
        }
        root.add("rows", rows);
        return root;
    }

    private static CrosshairPreset fromJson(String name, JsonObject root) {
        if (root == null) {
            return null;
        }
        int version = root.has("version") ? root.get("version").getAsInt() : 0;
        if (version > FORMAT_VERSION) {
            LOGGER.warn("crosshair preset {} is v{} but this build knows v{}", name, version,
                    FORMAT_VERSION);
            return null;
        }

        boolean[] grid = new boolean[ModConfig.GRID * ModConfig.GRID];
        JsonArray rows = root.has("rows") ? root.getAsJsonArray("rows") : null;
        if (rows != null) {
            int n = ModConfig.GRID;
            for (int y = 0; y < Math.min(n, rows.size()); y++) {
                String row = rows.get(y).getAsString();
                for (int x = 0; x < Math.min(n, row.length()); x++) {
                    grid[y * n + x] = row.charAt(x) == '#';
                }
            }
        }

        return new CrosshairPreset(name, grid,
                getFloat(root, "pixelSize", 1f),
                getFloat(root, "scale", 1f),
                root.has("stroke") && root.get("stroke").getAsBoolean(),
                getFloat(root, "strokeWidth", 1f),
                parseColor(root.has("color") ? root.get("color").getAsString() : "#FFFFFFFF"));
    }

    private static float getFloat(JsonObject root, String key, float fallback) {
        return root.has(key) ? root.get(key).getAsFloat() : fallback;
    }

    /** {@code #AARRGGBB} or {@code #RRGGBB}; anything else falls back to opaque white. */
    private static int parseColor(String text) {
        if (text == null) {
            return 0xFFFFFFFF;
        }
        String hex = text.trim();
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        try {
            long value = Long.parseLong(hex, 16);
            return hex.length() <= 6 ? (int) (0xFF000000L | value) : (int) value;
        } catch (NumberFormatException e) {
            return 0xFFFFFFFF;
        }
    }

    private static boolean write(String name, JsonObject root) {
        try {
            Files.createDirectories(DIR);
            try (Writer writer = Files.newBufferedWriter(DIR.resolve(name + ".json"))) {
                GSON.toJson(root, writer);
            }
            return true;
        } catch (IOException e) {
            // A preset that cannot be written must not take the game down; the caller reports it.
            LOGGER.warn("could not write crosshair preset {}: {}", name, e.toString());
            return false;
        }
    }
}
