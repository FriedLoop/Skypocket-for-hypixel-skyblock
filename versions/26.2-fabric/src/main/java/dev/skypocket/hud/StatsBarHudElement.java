package dev.skypocket.hud;

import com.mojang.blaze3d.platform.NativeImage;
import dev.skypocket.SkyPocketMod;
import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;
import dev.skypocket.render.HudText;
import dev.skypocket.render.StatsBarTexture;
import dev.skypocket.render.StatsOverlayRenderer;
import dev.skypocket.skyblock.PlayerStatsTracker;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix3x2fStack;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.ObjDoubleConsumer;
import java.util.function.ObjIntConsumer;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;

/**
 * The modern style's bars, one HUD element per value.
 *
 * <p>They are elements rather than a block the style draws, because a bar is something a player wants
 * to place: each one is registered with the HUD, so it shows up in the position editor with its own
 * drag target, wheel/slider size and stored position - exactly like the clock or the FPS readout. The
 * only thing still shared is the bar's base geometry ({@code statsBarWidth/Height}), since a row of
 * bars that are each a different thickness looks broken rather than configured.
 *
 * <p>Every bar is built the same way as the classic style's icons: an outline, a track, the fill
 * clipped to its fraction, and - for health and mana - a second fill on top for absorption and
 * overflow. That second fill is what makes the two styles read the same way; it just has no icons
 * here.
 *
 * <p>Nothing is drawn unless the modern style is selected: the classic style draws its own icons where
 * the vanilla ones were, and both at once would be two displays of the same numbers.
 */
public final class StatsBarHudElement {
    /** One bar per value; the enum is what the editor, the settings and the config are keyed on. */
    public enum Kind {
        HEALTH, MANA, DEFENSE, SPEED, VITALITY, AIR
    }

    /**
     * Speed's bar fills at 400, the normal cap. The printed value is whatever the server reports, and
     * gear can raise the cap to 500 - so the bar saturates while the number keeps counting.
     */
    private static final float SPEED_FULL = 400f;
    /** Fully rounded ends up to this radius; a taller bar just keeps the same corner softness. */
    private static final int MAX_CORNER = 3;
    private static final int FRAME = 0xC0000000;
    private static final int TRACK = 0x70000000;

    /**
     * Where the bars start out: a column just right of centre, at the height of the vanilla food row
     * on a default GUI, stepping upwards. Deliberately on-screen at any GUI size - the editor is where
     * they get placed, and an element that starts off-screen cannot be grabbed.
     */
    private static final int DEFAULT_X = 171;
    private static final int DEFAULT_Y = 231;
    private static final int DEFAULT_STEP = 9;

    private StatsBarHudElement() {
    }

    /** Registers all six bars. Called once from the client initializer. */
    public static void register() {
        for (Kind kind : Kind.values()) {
            HudElementRegistry.addLast(id(kind), (graphics, delta) -> render(graphics, kind));
        }
    }

    private static boolean editorPreview;

    /** The position editor draws its own previews; suppress the real HUD copies while it is open. */
    public static void setEditorPreview(boolean active) {
        editorPreview = active;
    }

    private static void render(GuiGraphicsExtractor graphics, Kind kind) {
        ModConfig cfg = ConfigManager.get();
        if (editorPreview || !cfg.statsModernStyle || !enabled(cfg, kind)
                || !StatsOverlayRenderer.active()) {
            return;
        }
        if (!PlayerStatsTracker.hasData()) {
            return;
        }
        if (kind == Kind.VITALITY && !PlayerStatsTracker.vitalityShown()) {
            return; // the server is not publishing vitality at all
        }
        if (kind == Kind.AIR) {
            Player player = Minecraft.getInstance().player;
            if (player == null || player.getAirSupply() >= player.getMaxAirSupply()) {
                return; // the bar only appears while there is something to show
            }
        }
        draw(graphics, cfg, kind, x(cfg, kind), y(cfg, kind), scale(cfg, kind));
    }

    /** Draws one bar with its top-left corner at {@code (x, y)}. */
    public static void draw(GuiGraphicsExtractor graphics, ModConfig cfg, Kind kind, int x, int y,
                            float scale) {
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        int width = width(cfg, kind);
        int height = height(cfg, kind);
        float fraction;
        int color = color(cfg, kind);
        float overlay = 0f;
        int overlayColor = 0;
        String label = null;
        switch (kind) {
            case HEALTH -> {
                fraction = fraction(PlayerStatsTracker.health(), PlayerStatsTracker.maxHealth());
                overlay = fraction(PlayerStatsTracker.absorption(), PlayerStatsTracker.maxHealth());
                overlayColor = cfg.statsAbsorptionColor;
                label = PlayerStatsTracker.health() + "/" + PlayerStatsTracker.maxHealth();
            }
            case MANA -> {
                fraction = fraction(PlayerStatsTracker.mana(), PlayerStatsTracker.maxMana());
                overlay = PlayerStatsTracker.overflowFraction();
                overlayColor = cfg.statsOverflowColor;
                // Same convention as the classic readout and as health itself: the number is the total,
                // the overflow only shows in the number, the bar keeps it as the second layer.
                label = PlayerStatsTracker.mana() + PlayerStatsTracker.overflowMana() + "/"
                        + PlayerStatsTracker.maxMana();
            }
            case DEFENSE -> {
                // No maximum to be a fraction of: an indicator, drawn full.
                fraction = 1f;
                label = Integer.toString(PlayerStatsTracker.defense());
            }
            case SPEED -> {
                fraction = fraction(PlayerStatsTracker.speed(), SPEED_FULL);
                label = Integer.toString(PlayerStatsTracker.speed());
            }
            case VITALITY -> {
                // Vitality publishes its own maximum, exactly like health and mana do.
                fraction = fraction(PlayerStatsTracker.vitality(), PlayerStatsTracker.maxVitality());
                label = PlayerStatsTracker.vitality() + "/" + PlayerStatsTracker.maxVitality();
            }
            default -> {
                Player player = Minecraft.getInstance().player;
                int air = player == null ? 0 : player.getAirSupply();
                int maxAir = player == null ? 300 : player.getMaxAirSupply();
                fraction = fraction(air, maxAir);
            }
        }
        // Icon to the left of the bar, vertically centred. Drawn in the bar's own coordinate space, so it
        // follows the bar's position, height and scale automatically.
        drawIcon(graphics, cfg, kind, -iconWidth(cfg, kind), (height - iconSize(cfg, kind)) / 2);
        bar(graphics, cfg, width, height, fraction, color, overlay, overlayColor, label);
        pose.popMatrix();
    }

    /** The bar's own colour, from the settings - also used by the position editor's preview. */
    public static int color(ModConfig cfg, Kind kind) {
        return switch (kind) {
            case HEALTH -> cfg.statsHealthColor;
            case MANA -> cfg.statsManaColor;
            case DEFENSE -> cfg.statsDefenseColor;
            case SPEED -> cfg.statsSpeedColor;
            case VITALITY -> cfg.statsVitalityColor;
            case AIR -> cfg.statsAirColor;
        };
    }

    /**
     * A stand-in bar for the position editor: the real geometry, icon and colour, filled to a sample
     * fraction.
     *
     * <p>The editor used to draw a bar as an empty frame only - no fill, no icon - so while placing one
     * there was no way to see the colour it would have. Uses the same {@code bar(...)} the HUD does, so
     * the preview cannot drift from the real thing.
     */
    public static void preview(GuiGraphicsExtractor graphics, ModConfig cfg, Kind kind) {
        if (PlayerStatsTracker.hasData()) {
            // With data available this is literally the HUD's own draw path - same fractions, same
            // overlay layer, same value label - so the preview cannot differ from what will appear.
            draw(graphics, cfg, kind, 0, 0, 1f);
            return;
        }
        // Nothing published yet (outside SkyBlock, or before the first action bar line): a stand-in so
        // the element is still visible while it is being placed.
        int width = width(cfg, kind);
        int height = height(cfg, kind);
        drawIcon(graphics, cfg, kind, -iconWidth(cfg, kind), (height - iconSize(cfg, kind)) / 2);
        bar(graphics, cfg, width, height, 0.7f, color(cfg, kind), 0f, 0, null);
    }

    /**
     * The open-ended ones are the bars a player can place anywhere, which is why they are relative to
     * the screen only through the position: everything else comes from the shared geometry.
     */
    private static void bar(GuiGraphicsExtractor graphics, ModConfig cfg, int width, int height,
                            float fraction, int color, float overlay, int overlayColor, String label) {
        // The frame texture is the whole bar (border + track) at the bar's own rect - no 1 px outset:
        // the old code expanded it because it was drawing a 1 px outline, but the artwork already
        // contains its border. Without the file, fall back to the stepped rectangles that were here
        // before, so the bars never disappear because an asset is missing.
        if (!frame(graphics, 0, 0, width, height)) {
            rounded(graphics, -1, -1, width + 2, height + 2, FRAME);
            rounded(graphics, 0, 0, width, height, TRACK);
        }
        fill(graphics, 0, 0, width, height, fraction, color);
        if (overlay > 0f && overlayColor != 0) {
            fill(graphics, 0, 0, width, height, overlay, overlayColor);
        }
        if (cfg.statsBarShowValues && label != null) {
            value(graphics, cfg, label, 0, 0, width, height, color);
        }
    }

    /**
     * The icon for a bar, drawn to its left.
     *
     * <p>Its edge length is the bar's height, so it scales with the bar (and with the element's own
     * scale, because this runs inside the same pose). Coordinates are relative to the bar's top-left, so
     * the icon follows the bar wherever it is placed.
     */
    private static void drawIcon(GuiGraphicsExtractor graphics, ModConfig cfg, Kind kind, int x, int y) {
        StatsBarTexture.ensureRegistered();
        Identifier id = icon(kind);
        int size = iconSize(cfg, kind);
        if (id == null || size <= 0) {
            return;
        }
        graphics.blit(RenderPipelines.GUI_TEXTURED, id, x, y, 0f, 0f, size, size,
                ICON_SRC, ICON_SRC, ICON_SRC, ICON_SRC, 0xFFFFFFFF);
    }

    /** The icon texture id for a bar, or null when that bar has none (AIR) or the file is missing. */
    public static Identifier icon(Kind kind) {
        String name = switch (kind) {
            case HEALTH -> "health";
            case MANA -> "mana";
            case DEFENSE -> "defense";
            case SPEED -> "speed";
            case VITALITY -> "vitality";
            case AIR -> "air";
        };
        Identifier id = Identifier.fromNamespaceAndPath(SkyPocketMod.MOD_ID,
                "textures/gui/sprites/icons/" + name + ".png");
        // Missing file -> no icon, rather than a purple/black placeholder in the HUD. Cached because this
        // runs per bar per frame (and again from the position editor).
        int index = kind.ordinal();
        Boolean present = ICON_PRESENT[index];
        if (present == null) {
            present = Minecraft.getInstance().getResourceManager().getResource(id).isPresent();
            ICON_PRESENT[index] = present;
        }
        return present ? id : null;
    }

    /** The icon's edge length, in design units; 0 when the bar has no icon. */
    public static int iconSize(ModConfig cfg, Kind kind) {
        return icon(kind) == null ? 0 : height(cfg, kind);
    }

    /** How much room the icon takes to the left of the bar (gap included); 0 when there is none. */
    public static int iconWidth(ModConfig cfg, Kind kind) {
        int size = iconSize(cfg, kind);
        return size == 0 ? 0 : size + ICON_GAP;
    }

    /** The filled part of a bar, clipped to its fraction so the end stays crisply vertical. */
    private static void fill(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                             float fraction, int color) {
        int filled = Math.round(width * Math.max(0f, Math.min(1f, fraction)));
        if (filled <= 0) {
            return;
        }
        graphics.enableScissor(x, y, x + filled, y + height);
        if (!fillArt(graphics, x, y, width, height, color)) {
            rounded(graphics, x, y, width, height, color);
        }
        graphics.disableScissor();
    }

    // ---- Bar art (nine-sliced textures) -----------------------------------------------------

    /**
     * How much of each texture's left end is copied 1:1 before the middle starts stretching, in source
     * pixels - measured from the artwork, not guessed.
     *
     * <p>The number that matters is the width of the <em>curved part in texture coordinates</em>, not the
     * arc's radius: the frame's silhouette curves across x = 0..10 (a pill, radius ≈ half its 20 px
     * height), and the fill's curves across x = 4..10 because it is inset 4 px. Both therefore need a
     * 10 px cap. Using one too-small value (5 was an earlier guess) cut the arc in half and stretched the
     * rest, which is what made the ends thicker than the straight edges and the fill and frame disagree.
     */
    private static final int FRAME_CAP = 10;
    private static final int FILL_CAP = 10;
    private static final Identifier BAR_FRAME = Identifier.fromNamespaceAndPath(
            SkyPocketMod.MOD_ID, "textures/gui/sprites/bars/bar_frame.png");
    private static final Identifier BAR_FILL = Identifier.fromNamespaceAndPath(
            SkyPocketMod.MOD_ID, "textures/gui/sprites/bars/bar_fill.png");

    /** Source edge length of every icon (they are all authored at 20x20). */
    private static final int ICON_SRC = 20;
    /** Gap between an icon and its bar, in design units. */
    private static final int ICON_GAP = 2;
    /** Whether each bar's icon texture exists; null until looked up. */
    private static final Boolean[] ICON_PRESENT = new Boolean[Kind.values().length];

    private static final dev.skypocket.SkyPocketLog LOGGER =
            dev.skypocket.SkyPocketLog.of("statsBar");

    private static boolean artChecked;
    private static boolean artAvailable;
    private static int frameW;
    private static int frameH;
    private static int fillW;
    private static int fillH;

    /**
     * Reads the two textures' pixel sizes once.
     *
     * <p>Nine-slicing needs the source size to turn "5 px corners" into UVs, and the texture may not
     * have been uploaded yet on the frame this first runs - so the size comes from the PNG itself,
     * once, and is cached. A missing or unreadable file disables the art for the session, and the
     * callers fall back to the stepped rectangles rather than drawing nothing.
     */
    private static void ensureArt() {
        if (artChecked) {
            return;
        }
        artChecked = true;
        int[] f = size(BAR_FRAME);
        int[] g = size(BAR_FILL);
        if (f == null || g == null) {
            return;
        }
        frameW = f[0];
        frameH = f[1];
        fillW = g[0];
        fillH = g[1];
        artAvailable = true;
    }

    private static int[] size(Identifier id) {
        try (var stream = Minecraft.getInstance().getResourceManager().getResource(id)
                .orElseThrow().open();
             //#if MC>=260300
             NativeImage image = NativeImage.read(stream)) {
//#else
             NativeImage image = NativeImage.read(NativeImage.Format.RGBA, stream)) {
//#endif
            return new int[]{image.getWidth(), image.getHeight()};
        } catch (Throwable t) {
            // A missing or unreadable icon must not take the HUD down: the bar simply draws without one.
            // Logged at debug level, because "why is my icon missing" is exactly the question this
            // answer is for.
            LOGGER.debug("could not read the size of {}: {}", id, t.toString());
            return null;
        }
    }

    /** Draws the frame texture; false when the art is not available. */
    private static boolean frame(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
        ensureArt();
        if (!artAvailable) {
            return false;
        }
        StatsBarTexture.ensureRegistered();
        slice(graphics, BAR_FRAME, x, y, w, h, frameW, frameH, FRAME_CAP, 0xFFFFFFFF);
        return true;
    }

    /** Draws the fill texture in {@code color}; false when the art is missing. */
    private static boolean fillArt(GuiGraphicsExtractor graphics, int x, int y, int w, int h,
                                   int color) {
        ensureArt();
        if (!artAvailable) {
            return false;
        }
        StatsBarTexture.ensureRegistered();
        slice(graphics, BAR_FILL, x, y, w, h, fillW, fillH, FILL_CAP, color);
        return true;
    }

    /**
     * Draws one bar texture: the two ends keep their shape and the middle stretches.
     *
     * <p>Horizontal only, on purpose. The artwork is a complete bar at its natural size (728 x 20: a
     * border, a track, and an inner band), so cutting it in both axes - the usual nine-slice - pushed
     * 20 px of design into a much shorter bar: the border ate most of the height and the inner band was
     * squashed down to a line, which is exactly how it looked. Slicing sideways only, while scaling the
     * height uniformly, keeps the border thickness and the corner radius in proportion at any bar size.
     */
    private static void slice(GuiGraphicsExtractor graphics, Identifier id, int x, int y,
                              int w, int h, int srcW, int srcH, int radius, int color) {
        int cap = Math.max(1, Math.min(srcW / 2, Math.min(w / 2,
                Math.round(radius * (float) h / Math.max(1, srcH)))));
        int sc = Math.min(radius, Math.min(srcW / 2, Math.round(cap * (float) srcH / Math.max(1, h))));
        // Left cap, stretched middle, right cap. The source regions are (sc x srcH) and
        // (srcW-2*sc x srcH); only the destination width differs, so the height scales uniformly.
        part(graphics, id, x, y, 0, 0, cap, h, sc, srcH, srcW, srcH, color);
        part(graphics, id, x + cap, y, sc, 0, w - 2 * cap, h, srcW - 2 * sc, srcH, srcW, srcH, color);
        part(graphics, id, x + w - cap, y, srcW - sc, 0, cap, h, sc, srcH, srcW, srcH, color);
    }

    /**
     * One piece of the slice.
     *
     * <p>{@code dw/dh} are the destination size, {@code sw/sh} the source region - the overload that
     * takes both is what makes stretching possible. (The shorter overload uses the destination size as
     * the source size, i.e. it only copies, never scales.)
     */
    private static void part(GuiGraphicsExtractor graphics, Identifier id, int x, int y,
                             float u, float v, int dw, int dh, float sw, float sh,
                             int texW, int texH, int color) {
        if (dw <= 0 || dh <= 0 || sw <= 0f || sh <= 0f) {
            return;
        }
        graphics.blit(RenderPipelines.GUI_TEXTURED, id, x, y, u, v, dw, dh,
                Math.round(sw), Math.round(sh), texW, texH, color);
    }

    /** A rectangle with its corners stepped in, which is a rounded end at GUI resolutions. */
    private static void rounded(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                                int color) {
        int radius = Math.min(MAX_CORNER, Math.min(width / 2, height / 2));
        for (int row = 0; row < height; row++) {
            int inset = inset(row, height, radius);
            graphics.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
        }
    }

    private static int inset(int row, int height, int radius) {
        if (radius <= 0) {
            return 0;
        }
        int fromEdge = Math.min(row, height - 1 - row);
        return fromEdge >= radius ? 0 : radius - fromEdge;
    }

    /**
     * The bar's value, centred on it.
     *
     * <p>Sized a touch above the bar's height, so it reads over a 20 px bar without the top edge cutting
     * into it, and anchored on the bottom: the lowest pixel sits 1 px inside, so the value can never spill
     * below the bar. No outline: at this magnification an outline is ~3 device pixels (the text is drawn
     * in a scaled pose and {@code HudText} only takes integer coordinates), which reads as mush rather
     * than an edge - the brightened colour is what carries it instead.
     */
    private static void value(GuiGraphicsExtractor graphics, ModConfig cfg, String text, int x, int y,
                              int width, int height, int barColor) {
        float scale = Math.min(2.8f, Math.max(0.4f, height / 8f));
        float textWidth = HudText.width(cfg, text) * scale;
        float textHeight = 8f * scale;
        int x0 = Math.round(x + (width - textWidth) / 2f);
        int y0 = Math.round(y + height - 1f - textHeight);
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x0, y0);
        pose.scale(scale, scale);
        // Shadow on: against the bar's own fill the brightened colour alone was not enough to read at
        // small sizes, and a drop shadow is one extra draw rather than the four an outline needed.
        HudText.draw(graphics, cfg, text, 0, 0, brighter(barColor), true);
        pose.popMatrix();
    }

    /** The bar's colour, brightened, for the value drawn on top of it. */
    private static int brighter(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = Math.min(255, Math.round(((argb >> 16) & 0xFF) * 1.35f) + 22);
        int g = Math.min(255, Math.round(((argb >> 8) & 0xFF) * 1.35f) + 22);
        int b = Math.min(255, Math.round((argb & 0xFF) * 1.35f) + 22);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static float fraction(float value, float max) {
        return max <= 0f ? 0f : Math.max(0f, Math.min(1f, value / max));
    }

    // ---- Shared geometry and per-bar state, also used by the position editor ----

    public static int width(ModConfig cfg) {
        return Math.max(16, cfg.statsBarWidth);
    }

    public static int height(ModConfig cfg) {
        return Math.max(3, cfg.statsBarHeight);
    }

    /**
     * The bar's own width, or the shared default when it has never been resized.
     *
     * <p>Per-bar sizes are what makes the bars freely resizable: the editor writes a pair here when an
     * edge is dragged, and everything else (hit testing, the drawn box, rendering) reads it through this
     * one pair of methods, so a resized bar can never disagree with its own frame.
     */
    public static int width(ModConfig cfg, Kind kind) {
        int[] own = cfg.statsBarWidths;
        int value = own != null && kind.ordinal() < own.length ? own[kind.ordinal()] : 0;
        return value > 0 ? value : width(cfg);
    }

    public static int height(ModConfig cfg, Kind kind) {
        int[] own = cfg.statsBarHeights;
        int value = own != null && kind.ordinal() < own.length ? own[kind.ordinal()] : 0;
        return value > 0 ? value : height(cfg);
    }

    /** Stores a bar's own size; 0 falls back to the shared default (see {@link #width(ModConfig, Kind)}). */
    public static void setSize(ModConfig cfg, Kind kind, int width, int height) {
        if (cfg.statsBarWidths == null || cfg.statsBarHeights == null
                || kind.ordinal() >= cfg.statsBarWidths.length
                || kind.ordinal() >= cfg.statsBarHeights.length) {
            return;
        }
        cfg.statsBarWidths[kind.ordinal()] = Math.max(ModConfig.BAR_MIN_W,
                Math.min(ModConfig.BAR_MAX_W, width));
        cfg.statsBarHeights[kind.ordinal()] = Math.max(ModConfig.BAR_MIN_H,
                Math.min(ModConfig.BAR_MAX_H, height));
    }

    /**
     * The bar behind a position-editor target name, or null when the target is not one of these.
     *
     * <p>The editor's enum prefixes its bar targets ({@code BAR_HEALTH}) so they read differently from
     * the text rows it also owns, and this is the one place that prefix has to be understood: miss it
     * and every lookup returns null, which silently falls back to measuring the target as a line of
     * text - and a bar has no text, so its hit box collapses to nothing and it cannot be grabbed.
     */
    public static Kind kindOf(String name) {
        String bare = name.startsWith("BAR_") ? name.substring(4) : name;
        for (Kind kind : Kind.values()) {
            if (kind.name().equals(bare)) {
                return kind;
            }
        }
        return null;
    }

    /** Puts every bar back where it starts out - the settings page's reset button. */
    public static void resetToDefaults(ModConfig cfg) {
        for (Kind kind : Kind.values()) {
            reset(cfg, kind);
        }
    }

    /**
     * Puts one bar back to its default: its place in the column, scale 1, and no size override.
     *
     * <p>Used when a single bar has been dragged out of reach. The set used to be reset as a whole
     * ("the bars are a set"), which also wiped the other five bars' positions, scales and sizes because
     * of one mistake. Repairing the one bar keeps it in the column all the same - the default is the
     * same per-index position the column is laid out with.
     */
    public static void reset(ModConfig cfg, Kind kind) {
        setX(cfg, kind, DEFAULT_X);
        setY(cfg, kind, DEFAULT_Y - kind.ordinal() * DEFAULT_STEP);
        setScale(cfg, kind, 1f);
        // Drop any per-bar size too, so "reset" really returns to the shared width/height rather
        // than leaving a bar stretched from an earlier drag.
        if (cfg.statsBarWidths != null && kind.ordinal() < cfg.statsBarWidths.length) {
            cfg.statsBarWidths[kind.ordinal()] = 0;
        }
        if (cfg.statsBarHeights != null && kind.ordinal() < cfg.statsBarHeights.length) {
            cfg.statsBarHeights[kind.ordinal()] = 0;
        }
    }

    /** One bar's config fields - see {@link #FIELDS}. */
    private record Fields(ToIntFunction<ModConfig> x, ObjIntConsumer<ModConfig> setX,
                          ToIntFunction<ModConfig> y, ObjIntConsumer<ModConfig> setY,
                          ToDoubleFunction<ModConfig> scale, ObjDoubleConsumer<ModConfig> setScale,
                          Predicate<ModConfig> enabled) {
    }

    /**
     * Per-kind config access, in one place - the same structure the stat text rows use.
     *
     * <p>These were six switches (read and write for x, y and scale) plus one for the enabled flag.
     * Half of them were {@code switch} <em>statements</em>, where a missing constant silently does
     * nothing: adding a bar meant remembering seven places, and forgetting one stayed invisible until
     * someone tried to drag the new bar. With the fields in a table, a new kind is one row.
     */
    private static final Map<Kind, Fields> FIELDS = new EnumMap<>(Kind.class);

    static {
        put(Kind.HEALTH, c -> c.statsHealthBarX, (c, v) -> c.statsHealthBarX = v,
                c -> c.statsHealthBarY, (c, v) -> c.statsHealthBarY = v,
                c -> c.statsHealthBarScale, (c, v) -> c.statsHealthBarScale = (float) v,
                c -> c.statsHealthBarEnabled);
        put(Kind.MANA, c -> c.statsManaBarX, (c, v) -> c.statsManaBarX = v,
                c -> c.statsManaBarY, (c, v) -> c.statsManaBarY = v,
                c -> c.statsManaBarScale, (c, v) -> c.statsManaBarScale = (float) v,
                c -> c.statsManaBarEnabled);
        put(Kind.DEFENSE, c -> c.statsDefenseBarX, (c, v) -> c.statsDefenseBarX = v,
                c -> c.statsDefenseBarY, (c, v) -> c.statsDefenseBarY = v,
                c -> c.statsDefenseBarScale, (c, v) -> c.statsDefenseBarScale = (float) v,
                c -> c.statsDefenseBarEnabled);
        put(Kind.SPEED, c -> c.statsSpeedBarX, (c, v) -> c.statsSpeedBarX = v,
                c -> c.statsSpeedBarY, (c, v) -> c.statsSpeedBarY = v,
                c -> c.statsSpeedBarScale, (c, v) -> c.statsSpeedBarScale = (float) v,
                c -> c.statsSpeedBarEnabled);
        put(Kind.VITALITY, c -> c.statsVitalityBarX, (c, v) -> c.statsVitalityBarX = v,
                c -> c.statsVitalityBarY, (c, v) -> c.statsVitalityBarY = v,
                c -> c.statsVitalityBarScale, (c, v) -> c.statsVitalityBarScale = (float) v,
                c -> c.statsVitalityBarEnabled);
        put(Kind.AIR, c -> c.statsAirBarX, (c, v) -> c.statsAirBarX = v,
                c -> c.statsAirBarY, (c, v) -> c.statsAirBarY = v,
                c -> c.statsAirBarScale, (c, v) -> c.statsAirBarScale = (float) v,
                c -> c.statsAirBarEnabled);
    }

    private static void put(Kind kind, ToIntFunction<ModConfig> x, ObjIntConsumer<ModConfig> sx,
                            ToIntFunction<ModConfig> y, ObjIntConsumer<ModConfig> sy,
                            ToDoubleFunction<ModConfig> s, ObjDoubleConsumer<ModConfig> ss,
                            Predicate<ModConfig> enabled) {
        FIELDS.put(kind, new Fields(x, sx, y, sy, s, ss, enabled));
    }

    public static boolean enabled(ModConfig cfg, Kind kind) {
        return FIELDS.get(kind).enabled().test(cfg);
    }

    public static int x(ModConfig cfg, Kind kind) {
        return FIELDS.get(kind).x().applyAsInt(cfg);
    }

    public static void setX(ModConfig cfg, Kind kind, int value) {
        FIELDS.get(kind).setX().accept(cfg, value);
    }

    public static int y(ModConfig cfg, Kind kind) {
        return FIELDS.get(kind).y().applyAsInt(cfg);
    }

    public static void setY(ModConfig cfg, Kind kind, int value) {
        FIELDS.get(kind).setY().accept(cfg, value);
    }

    public static float scale(ModConfig cfg, Kind kind) {
        return (float) FIELDS.get(kind).scale().applyAsDouble(cfg);
    }

    public static void setScale(ModConfig cfg, Kind kind, float value) {
        FIELDS.get(kind).setScale().accept(cfg, value);
    }

    /** The bar's name, for the position editor's click targets and the settings rows. */
    public static String label(Kind kind) {
        return switch (kind) {
            case HEALTH -> dev.skypocket.i18n.Localization.tr("生命条");
            case MANA -> dev.skypocket.i18n.Localization.tr("法力条");
            case DEFENSE -> dev.skypocket.i18n.Localization.tr("防御条");
            case SPEED -> dev.skypocket.i18n.Localization.tr("速度条");
            case VITALITY -> dev.skypocket.i18n.Localization.tr("活力条");
            case AIR -> dev.skypocket.i18n.Localization.tr("空气条");
        };
    }

    private static Identifier id(Kind kind) {
        return Identifier.fromNamespaceAndPath("skypocket",
                "stats_bar_" + kind.name().toLowerCase(java.util.Locale.ROOT));
    }
}
