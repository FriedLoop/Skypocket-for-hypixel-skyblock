package dev.skypocket.gui.widget;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.skypocket.SkyPocketLog;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Named themes on disk: {@code config/skypocket/themes/<name>.json}, one file per theme, holding both
 * the colours and the measurements.
 *
 * <p>Themes are kept out of {@code skypocket.json} on purpose. They are design assets - dozens of values
 * that are edited in bulk and swapped often - while the main config is player data that has to migrate
 * carefully. Keeping them apart means a theme edit can never endanger someone's settings.
 *
 * <p>Serialisation is driven by the records' own components, so a new colour or measurement is written
 * and read without touching this class. Missing entries fall back to the built-in palette, which is what
 * makes hand-editing a file safe: delete a line and that value returns to its default.
 *
 * <p>Colours are stored as {@code "#AARRGGBB"} strings so a human can read and edit them; measurements are
 * plain numbers.
 */
public final class ThemeStore {
    private static final SkyPocketLog LOGGER = SkyPocketLog.of("theme");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DIR =
            FabricLoader.getInstance().getConfigDir().resolve("skypocket/themes");

    /** Themes that always exist, without a file. */
    private static final String DARK = "Dark";
    private static final String LIGHT = "Light";

    /**
     * Old theme keys, tried in order when a canonical key is absent. This keeps hand-edited
     * themes from before the 14-token consolidation readable; new files only get canonical keys.
     */
    private static final Map<String, String[]> LEGACY_KEYS = Map.of(
            "window", new String[]{"panelRgb"},
            "chrome", new String[]{"sidebarRgb", "tabBarRgb", "tab"},
            "surface", new String[]{"card", "cardFloat"},
            "control", new String[]{"cardSurface"},
            "primary", new String[]{"accent", "tabSelected", "sectionTitle"},
            "primaryHover", new String[]{"accentHover"},
            "onPrimary", new String[]{"onAccent"},
            "textPrimary", new String[]{"text"},
            "textSecondary", new String[]{"textDim", "description", "cardText"},
            "textMuted", new String[]{"textMuted", "disabledText"}
    );

    /** Studio explanations; the UI localises these strings. */
    private static final Map<String, String> DESCRIPTIONS = Map.ofEntries(
            Map.entry("window", "面板主体背景，透明度由“背景透明度”统一控制"),
            Map.entry("chrome", "顶部栏、侧栏、footer 与 tab 的底色"),
            Map.entry("surface", "卡片和页面主体表面"),
            Map.entry("control", "按钮、普通控件和列表行的底色"),
            Map.entry("field", "输入框、下拉框和搜索框"),
            Map.entry("fieldInner", "输入框内部的数值框"),
            Map.entry("primary", "主色填充：选中、开启、滑块和重点元素"),
            Map.entry("primaryHover", "主色填充的 hover 状态"),
            Map.entry("onPrimary", "主色背景上的文字和开关滑块"),
            Map.entry("textPrimary", "主要文字"),
            Map.entry("textSecondary", "说明文字和次级信息"),
            Map.entry("textMuted", "弱化文字、禁用文字和占位符"),
            Map.entry("switchOn", "开关开启时的轨道颜色"),
            Map.entry("switchOff", "开关关闭时的轨道颜色")
    );

    private static final Map<String, String> METRIC_DESCRIPTIONS = Map.of(
            "cardRadius", "卡片、面板和标签页的圆角半径",
            "controlRadius", "按钮、输入框、下拉框和行高亮的圆角半径"
    );

    private ThemeStore() {
    }

    /** Applies a theme by name; unknown names fall back to the built-in dark palette. */
    public static void apply(String name) {
        GuiTheme colors = readColors(name);
        GuiMetrics metrics = readMetrics(name);
        GuiTheme.use(colors);
        GuiMetrics.use(metrics);
        JsonObject root = read(name);
        LOGGER.info("theme {} ({}): {} colours, cardRadius={} controlRadius={}",
                name, root == null ? "built-in" : "file",
                GuiTheme.class.getRecordComponents().length,
                metrics.cardRadius(), metrics.controlRadius());
    }

    /** Built-in names followed by the files found on disk (sorted). */
    public static List<String> names() {
        List<String> names = new ArrayList<>();
        names.add(DARK);
        names.add(LIGHT);
        if (Files.isDirectory(DIR)) {
            try (var stream = Files.list(DIR)) {
                stream.filter(p -> p.getFileName().toString().endsWith(".json"))
                        .map(p -> p.getFileName().toString().replace(".json", ""))
                        .filter(n -> !n.equalsIgnoreCase(DARK) && !n.equalsIgnoreCase(LIGHT))
                        .sorted()
                        .forEach(names::add);
            } catch (IOException ignored) {
                // A theme list that cannot be read is not worth failing over: the built-ins still work.
            }
        }
        return names;
    }

    /** Writes the current palette and measurements as {@code <name>.json}. */
    public static void save(String name) {
        save(name, GuiTheme.current(), GuiMetrics.current());
    }

    /**
     * Deletes {@code <name>.json}.
     *
     * <p>The two built-ins are code, not files, so they cannot be deleted - returning false lets the
     * caller say why instead of failing silently.
     */
    public static boolean delete(String name) {
        if (name == null || name.equalsIgnoreCase(DARK) || name.equalsIgnoreCase(LIGHT)) {
            return false;
        }
        try {
            return Files.deleteIfExists(DIR.resolve(name + ".json"));
        } catch (IOException e) {
            LOGGER.warn("could not delete theme {}: {}", name, e.toString());
            return false;
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
            // A theme that cannot be written must not take the game down; the caller reports it.
            LOGGER.warn("could not write theme {}: {}", name, e.toString());
            return false;
        }
    }

    /**
     * Writes {@code Dark.json} and {@code Light.json} once, the first time the mod runs with a theme
     * folder that does not exist yet, so they can be edited by hand instead of only living in code.
     */
    public static void seedDefaults() {
        if (Files.exists(DIR)) {
            return;
        }
        GuiTheme previousTheme = GuiTheme.current();
        GuiMetrics previousMetrics = GuiMetrics.current();
        try {
            GuiTheme.use(GuiTheme.DARK);
            GuiMetrics.use(GuiMetrics.DEFAULT);
            save(DARK);
            GuiTheme.use(GuiTheme.LIGHT);
            save(LIGHT);
        } finally {
            GuiTheme.use(previousTheme);
            GuiMetrics.use(previousMetrics);
        }
    }

    // ---- reading without applying (the studio edits a draft) ------------------------------

    /** The colours a theme resolves to, without touching the live palette. */
    public static GuiTheme readColors(String name) {
        JsonObject root = read(name);
        return colors(root, builtinColors(name));
    }

    /** The measurements a theme resolves to, without touching the live layout. */
    public static GuiMetrics readMetrics(String name) {
        return metrics(read(name), GuiMetrics.DEFAULT);
    }

    /** Writes the given values as {@code <name>.json}; false when the file could not be written. */
    public static boolean save(String name, GuiTheme colors, GuiMetrics metrics) {
        JsonObject root = new JsonObject();
        root.addProperty("name", name);
        root.add("colors", toJson(colors));
        root.add("metrics", toJson(metrics));
        return write(name, root);
    }

    /** Makes the given values the live palette (used when the studio commits a draft). */
    public static void apply(GuiTheme colors, GuiMetrics metrics) {
        GuiTheme.use(colors);
        GuiMetrics.use(metrics);
    }

    // ---- per-token access for the studio ---------------------------------------------------

    /** Colour component names, in declaration order. */
    public static List<String> colorNames() {
        return names(GuiTheme.class.getRecordComponents());
    }

    /** What a studio colour token controls, ready for the UI to localise. */
    public static String colorDescription(String name) {
        return DESCRIPTIONS.getOrDefault(name, name);
    }

    /** What a studio token controls, colour or metric. */
    public static String tokenDescription(String name) {
        String color = DESCRIPTIONS.get(name);
        if (color != null) {
            return color;
        }
        return METRIC_DESCRIPTIONS.getOrDefault(name, name);
    }

    /** Measurement component names, in declaration order. */
    public static List<String> metricNames() {
        return names(GuiMetrics.class.getRecordComponents());
    }

    public static int color(String name) {
        return color(GuiTheme.current(), name);
    }

    /** One colour of the given palette (used by the studio on its draft, not the live one). */
    public static int color(GuiTheme theme, String name) {
        for (RecordComponent component : GuiTheme.class.getRecordComponents()) {
            if (component.getName().equals(name)) {
                return (Integer) invoke(component.getAccessor(), theme);
            }
        }
        return 0;
    }

    public static double metric(String name) {
        return metric(GuiMetrics.current(), name);
    }

    public static double metric(GuiMetrics metrics, String name) {
        for (RecordComponent component : GuiMetrics.class.getRecordComponents()) {
            if (component.getName().equals(name)) {
                Object value = invoke(component.getAccessor(), metrics);
                return value instanceof Float f ? f : (Integer) value;
            }
        }
        return 0;
    }

    /**
     * A copy of {@code base} with one colour replaced.
     *
     * <p>Records are immutable, so editing means building a copy - the alternative would be a mutable
     * palette, which invites exactly the "who changed this?" bugs the record prevents.
     */
    public static GuiTheme withColor(GuiTheme base, String name, int value) {
        RecordComponent[] components = GuiTheme.class.getRecordComponents();
        Object[] args = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            args[i] = components[i].getName().equals(name) ? value
                    : invoke(components[i].getAccessor(), base);
        }
        return (GuiTheme) instantiate(GuiTheme.class, args);
    }

    /** A copy of {@code base} with one measurement replaced, keeping its own type (int or float). */
    public static GuiMetrics withMetric(GuiMetrics base, String name, double value) {
        RecordComponent[] components = GuiMetrics.class.getRecordComponents();
        Object[] args = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            RecordComponent component = components[i];
            if (!component.getName().equals(name)) {
                args[i] = invoke(component.getAccessor(), base);
            } else if (component.getType() == float.class) {
                args[i] = (float) value;
            } else {
                args[i] = (int) Math.round(value);
            }
        }
        return (GuiMetrics) instantiate(GuiMetrics.class, args);
    }

    /** Replaces one colour in the live palette. */
    public static void setColor(String name, int value) {
        GuiTheme.use(withColor(GuiTheme.current(), name, value));
    }

    /** Replaces one measurement in the live layout. */
    public static void setMetric(String name, double value) {
        GuiMetrics.use(withMetric(GuiMetrics.current(), name, value));
    }

    // The "export as JSON text" helper went with the studio screen: a theme file is the export, and the
    // drawer writes it directly.

    private static List<String> names(RecordComponent[] components) {
        List<String> names = new ArrayList<>(components.length);
        for (RecordComponent component : components) {
            names.add(component.getName());
        }
        return names;
    }

    // ---- reading --------------------------------------------------------------------------

    private static JsonObject read(String name) {
        Path file = DIR.resolve(name + ".json");
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            return GSON.fromJson(reader, JsonObject.class);
        } catch (IOException | RuntimeException e) {
            // A broken theme file falls back to the built-in palette rather than crashing on start.
            return null;
        }
    }

    private static GuiTheme builtinColors(String name) {
        return LIGHT.equalsIgnoreCase(name) ? GuiTheme.LIGHT : GuiTheme.DARK;
    }

    /** Reads the colour map, keeping the fallback for every entry the file does not mention. */
    private static GuiTheme colors(JsonObject root, GuiTheme fallback) {
        JsonObject colors = root == null ? null : root.getAsJsonObject("colors");
        if (colors == null) {
            return fallback;
        }
        RecordComponent[] components = GuiTheme.class.getRecordComponents();
        Object[] args = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            String name = components[i].getName();
            int def = (Integer) invoke(components[i].getAccessor(), fallback);
            JsonElement value = colors.get(name);
            if (value == null) {
                for (String legacy : LEGACY_KEYS.getOrDefault(name, new String[0])) {
                    value = colors.get(legacy);
                    if (value != null) {
                        break;
                    }
                }
            }
            args[i] = value == null ? def : parseColor(value.getAsString(), def);
        }
        return (GuiTheme) instantiate(GuiTheme.class, args);
    }

    private static GuiMetrics metrics(JsonObject root, GuiMetrics fallback) {
        JsonObject metrics = root == null ? null : root.getAsJsonObject("metrics");
        if (metrics == null) {
            return fallback;
        }
        RecordComponent[] components = GuiMetrics.class.getRecordComponents();
        Object[] args = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            Object def = invoke(components[i].getAccessor(), fallback);
            JsonElement value = metrics.get(components[i].getName());
            if (value == null) {
                args[i] = def;
            } else if (def instanceof Float) {
                args[i] = value.getAsFloat();
            } else {
                args[i] = value.getAsInt();
            }
        }
        return (GuiMetrics) instantiate(GuiMetrics.class, args);
    }

    // ---- writing --------------------------------------------------------------------------

    private static JsonObject toJson(GuiTheme theme) {
        JsonObject out = new JsonObject();
        for (RecordComponent component : GuiTheme.class.getRecordComponents()) {
            int value = (Integer) invoke(component.getAccessor(), theme);
            out.addProperty(component.getName(), String.format("#%08X", value));
        }
        return out;
    }

    private static JsonObject toJson(GuiMetrics metrics) {
        JsonObject out = new JsonObject();
        for (RecordComponent component : GuiMetrics.class.getRecordComponents()) {
            Object value = invoke(component.getAccessor(), metrics);
            if (value instanceof Float f) {
                out.addProperty(component.getName(), f);
            } else {
                out.addProperty(component.getName(), (Integer) value);
            }
        }
        return out;
    }

    // ---- small reflection helpers ---------------------------------------------------------

    private static Object invoke(java.lang.reflect.Method accessor, Object target) {
        try {
            return accessor.invoke(target);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("theme record accessor failed: " + accessor.getName(), e);
        }
    }

    private static Object instantiate(Class<?> type, Object[] args) {
        try {
            return type.getDeclaredConstructors()[0].newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("theme record construction failed: " + type.getSimpleName(), e);
        }
    }

    private static int parseColor(String text, int fallback) {
        String hex = text.trim();
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        try {
            return (int) Long.parseLong(hex, 16);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
