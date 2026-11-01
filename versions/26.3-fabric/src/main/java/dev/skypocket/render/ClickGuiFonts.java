package dev.skypocket.render;

import dev.skypocket.SkyPocketMod;
import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;
import dev.skypocket.mixin.FontManagerAccessor;
import dev.skypocket.mixin.MinecraftAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.glyphs.EffectGlyph;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;

/**
 * The Click GUI's dedicated font. Text is rendered by a runtime {@link VectorFont}
 * (MC's FreeType TTF provider rasterized at the exact on-screen pixel size), so it
 * stays sharp at any Click GUI scale instead of resampling a pre-baked bitmap.
 *
 * <p>The old pre-baked bitmap bakes ({@code clickgui_1x.json} / {@code clickgui.json})
 * are kept as a fallback for the (unlikely) case the vector font can't be built.
 *
 * <p>Each needed pixel size costs a (slow) TTF parse, so every size reachable by the
 * scale slider — plus the larger title size — is <em>pre-built in the background</em>
 * from the client tick (see {@link #tick()}) rather than while the GUI is drawing.
 */
public final class ClickGuiFonts {
    /** Design text size in px; a text's raster size = DESIGN_SIZE × scale × pxPerUnit. */
    private static final int DESIGN_SIZE = 9;
    /** Panel scale baseline: the design size is scaled by scale × SCALE_BASE / guiScale. */
    public static final float SCALE_BASE = 1.75f;
    /** Scale slider range/step (the sizes reachable here are the ones we pre-build). */
    public static final float SCALE_MIN = 0.5f;
    public static final float SCALE_MAX = 1.5f;
    public static final float SCALE_STEP = 0.1f;
    /** Extra size factor for the panel heading (drawn at 1.3 × design size). */
    public static final float HEADING_SCALE = 1.3f;

    private static final int BAKE_1X = 9;
    private static final int BAKE_2X = 18;
    /** Click GUI scales above this use the 2x bake (fallback path only). */
    private static final float TWO_X_MIN_SCALE = 1.25f;

    private static final Identifier FONT_1X = Identifier.fromNamespaceAndPath(SkyPocketMod.MOD_ID, "clickgui_1x");
    private static final Identifier FONT_2X = Identifier.fromNamespaceAndPath(SkyPocketMod.MOD_ID, "clickgui");

    private static volatile Font font1x;
    private static volatile Font font2x;

    /** Built vector fonts by pixel size (never evicted; the set is small and fixed). */
    private static final Map<Integer, VectorFont> FONTS = new HashMap<>();
    /** Pixel sizes still waiting to be built in the background, a slice at a time. */
    private static final LinkedHashSet<Integer> PENDING = new LinkedHashSet<>();
    /** Ticks to idle between build slices (0 = one slice per tick). */
    private static final int SLICE_GAP_TICKS = 0;
    private static boolean vectorDisabled;
    private static boolean rangeQueued;
    private static int buildCooldown;
    /** Font currently being built slice by slice (null when the queue is idle). */
    private static VectorFont.Builder building;
    private static float renderScale = 1.0f;
    private static int currentPixelSize = -1;
    /** Cached font for the common scale = 1 case (avoids per-draw map lookups). */
    private static VectorFont currentVector;

    /** Memoized text widths (invalidated whenever the active pixel size changes). */
    private static final Map<String, Integer> WIDTH_CACHE = new HashMap<>();
    private static int widthCachePixel = -1;

    private ClickGuiFonts() {
    }

    /** Called by the panel each frame with its design -> GUI pixel factor. */
    public static void setRenderScale(float scale) {
        renderScale = scale;
        if (vectorDisabled) {
            return;
        }
        int size = pixelSize(scale);
        currentVector = ensureFont(size);
        currentPixelSize = currentVector != null ? size : -1;
    }

    /**
     * Background font builder, driven by the client tick: queues every reachable size
     * once, then advances <em>one slice</em> of one size per tick. A slice is a single
     * FreeType face (or the FontSet reload), so the per-tick cost stays small — a whole
     * font (two face parses, one of them a 10 MB CJK font) on one tick still stuttered.
     */
    public static void tick() {
        if (vectorDisabled) {
            return;
        }
        if (!rangeQueued) {
            rangeQueued = queueRange();
        }
        if (buildCooldown > 0) {
            buildCooldown--;
            return;
        }
        advanceBuild();
    }

    /** Builds one slice of the in-flight font, starting the next queued size when idle. */
    private static void advanceBuild() {
        if (building == null) {
            Integer next = pollPending();
            if (next == null) {
                return;
            }
            building = new VectorFont.Builder(next);
        }
        VectorFont finished;
        try {
            finished = building.step();
        } catch (Throwable t) {
            vectorDisabled = true;
            building = null;
            return;
        }
        if (finished != null) {
            int size = building.pixelSize();
            synchronized (FONTS) {
                FONTS.put(size, finished);
                PENDING.remove(size);
            }
            building = null;
        }
        buildCooldown = SLICE_GAP_TICKS;
    }

    /**
     * Queues the sizes the panel reaches <em>at the current settings</em>: the body size
     * and the heading size for the configured panel scale. Sizes for other slider
     * positions are built on demand if the slider is actually moved — pre-building all 22
     * of them made every launch stall for a few seconds of FreeType parsing that is almost
     * never used (the CJK face is 10 MB, one parse per size).
     */
    private static boolean queueRange() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) {
            return false;
        }
        float guiScale = mc.getWindow().getGuiScale();
        if (guiScale <= 0f) {
            return false;
        }
        float panelScale = ConfigManager.get().clickGuiScale * SCALE_BASE / guiScale;
        synchronized (FONTS) {
            for (float factor : new float[]{1.0f, HEADING_SCALE}) {
                int size = pixelSize(panelScale * factor);
                if (!FONTS.containsKey(size)) {
                    PENDING.add(size);
                }
            }
            // Screens that draw at 1:1 (the HUD position editor) need the size for renderScale = 1,
            // which the panel's own scale does not necessarily produce. Queueing it at launch means it
            // is built in the background instead of on the frame that first opens such a screen.
            int screenSize = pixelSize(1.0f);
            if (!FONTS.containsKey(screenSize)) {
                PENDING.add(screenSize);
            }

        }
        return true;
    }

    private static Integer pollPending() {
        synchronized (FONTS) {
            return PENDING.isEmpty() ? null : PENDING.iterator().next();
        }
    }

    /**
     * The raster pixel size for a scale. This is also the text's on-screen size in
     * device pixels: the glyphs are drawn 1:1 (see {@link #vectorGlyphScale()}), which
     * is what keeps them on the pixel grid. It must therefore be a whole number, so
     * the text size snaps to whole device pixels instead of being resampled.
     */
    private static int pixelSize(float renderScaleEquivalent) {
        int size = Math.round(DESIGN_SIZE * renderScaleEquivalent * guiScale());
        return Math.max(4, Math.min(128, size));
    }

    /**
     * GUI scale factor of the window (device pixels per GUI pixel). Public so the panel's
     * device-pixel helpers can share it: a scissor can only sit on whole gui-scaled units
     * (see {@code OneClickScreen#scissorBound}).
     */
    public static float guiScale() {
        var window = Minecraft.getInstance().getWindow();
        return window == null ? 1.0f : Math.max(1.0e-4f, window.getGuiScale());
    }

    /**
     * Design unit -> device pixel factor (MC applies the GUI scale on top of the pose).
     * Shared with the panel's drawing helpers so that pixel-level work (corner
     * anti-aliasing, text snapping) happens on the real device pixel grid.
     */
    public static float deviceFactor() {
        return renderScale * guiScale();
    }

    private static VectorFont ensureFont(int pixelSize) {
        synchronized (FONTS) {
            VectorFont existing = FONTS.get(pixelSize);
            if (existing != null) {
                return existing;
            }
            // Never build inside a frame.
            //
            // Constructing one font parses the TTF (the CJK face is 10 MB) and stitches a whole glyph
            // atlas - a few hundred milliseconds. Doing that on the frame that first needs a size is
            // exactly the stutter seen the first time the HUD position editor was opened after my
            // change made it ask for a raster the panel never used. Queue it for the tick-driven
            // builder and return null: every caller already falls back to the bundled bitmap font, so
            // the line simply appears in the vector font a moment later instead of freezing.
            PENDING.add(pixelSize);
            return null;
        }
    }

    /** The vector font serving {@code scale}, or null while its size is still being built. */
    private static VectorFont vectorFor(float scale) {
        if (vectorDisabled) {
            return null;
        }
        if (scale == 1.0f && currentPixelSize > 0) {
            return currentVector;
        }
        return ensureFont(pixelSize(renderScale * scale));
    }

    /** The font whose glyphs are rasterized for {@code scale} × the design size. */
    private static Font fontFor(float scale) {
        VectorFont vector = vectorFor(scale);
        if (vector != null) {
            return vector.font;
        }
        return ConfigManager.get().clickGuiScale > TWO_X_MIN_SCALE ? font2x() : font1x();
    }

    /**
     * Draw scale that maps the vector glyphs 1 raster px -> 1 device px. The raster
     * size already carries the requested text size (see {@link #pixelSize}), so this
     * must not depend on {@code scale}: any other factor would resample the atlas and
     * put glyphs on fractional pixels (soft text, uneven 1px shadows).
     */
    private static float vectorGlyphScale() {
        float factor = deviceFactor();
        return factor > 0f ? 1.0f / factor : 1.0f;
    }

    /** Draw scale that maps the {@link #fontFor} glyphs to {@code scale} × design size. */
    private static float glyphScaleFor(float scale) {
        if (vectorFor(scale) != null) {
            return vectorGlyphScale();
        }
        float base = ConfigManager.get().clickGuiScale > TWO_X_MIN_SCALE
                ? (float) DESIGN_SIZE / BAKE_2X
                : (float) DESIGN_SIZE / BAKE_1X;
        return base * scale;
    }

    /** Text width in design (panel) units, memoized per active pixel size. */
    public static int width(String text) {
        if (currentPixelSize != widthCachePixel) {
            WIDTH_CACHE.clear();
            widthCachePixel = currentPixelSize;
        }
        Integer cached = WIDTH_CACHE.get(text);
        if (cached != null) {
            return cached;
        }
        int width = Math.round(fontFor(1.0f).width(text) * glyphScaleFor(1.0f));
        WIDTH_CACHE.put(text, width);
        return width;
    }

    /** Line height in design units. */
    public static int lineHeight() {
        return fontFor(1.0f).lineHeight;
    }

    /**
     * Vanilla anchors every TTF glyph to a hardcoded 7px ascent below the draw
     * position, independent of the raster size. Compensate so the baseline lands at
     * the design position for any pixel size.
     */
    private static final float GLYPH_BASELINE = 7.0f;

    /** Draws text at the panel design size. */
    public static void drawText(GuiGraphicsExtractor graphics, String text, float x, float y, int color, boolean shadow) {
        drawTextScaled(graphics, text, x, y, color, shadow, 1.0f);
    }

    /** Draws text at {@code scale} × the panel design size (e.g. headings). */
    public static void drawTextScaled(GuiGraphicsExtractor graphics, String text, float x, float y,
                                      int color, boolean shadow, float scale) {
        // Panel text is drawn with the vector font: each size is rasterised by FreeType at exactly the
        // pixel size it is drawn at, so no resampling happens anywhere on this path.
        VectorFont vector = vectorFor(scale);
        float s = vector != null ? vectorGlyphScale() : glyphScaleFor(scale);
        // Snap the origin to whole DEVICE pixels. The vector font maps 1 raster px to
        // 1 device px and integer advances keep glyphs on that grid, so the text (and
        // its 1px shadow) stays crisp; the bitmap fallback snaps to GUI pixels.
        float step = vector != null ? deviceFactor() : renderScale;
        float grid = step > 0f ? 1.0f / step : 1.0f;
        float snappedX = Math.round(x / grid) * grid;
        float snappedY = Math.round((y + GLYPH_BASELINE * (1.0f - s)) / grid) * grid;
        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(snappedX, snappedY);
        pose.scale(s, s);
        // Shadows double the glyph geometry; the panel renders without them by default. Drawn inside the
        // pose pushed above, which already carries the panel scale and the device-pixel snap.
        graphics.text(vector != null ? vector.font : fontFor(scale), text, 0, 0, color,
                shadow && ConfigManager.get().clickGuiTextShadow);
        pose.popMatrix();
    }




    private static Font font1x() {
        Font f = font1x;
        if (f == null) {
            synchronized (ClickGuiFonts.class) {
                if (font1x == null) {
                    font1x = createFont(FONT_1X);
                }
                f = font1x;
            }
        }
        return f;
    }

    private static Font font2x() {
        Font f = font2x;
        if (f == null) {
            synchronized (ClickGuiFonts.class) {
                if (font2x == null) {
                    font2x = createFont(FONT_2X);
                }
                f = font2x;
            }
        }
        return f;
    }

    private static Font createFont(Identifier bakeId) {
        return new Font(new Font.Provider() {
            @Override
            public GlyphSource glyphs(FontDescription description) {
                return fontSet(bakeId).source(false);
            }

            @Override
            public EffectGlyph effect() {
                return fontSet(bakeId).whiteGlyph();
            }
        });
    }

    private static FontSet fontSet(Identifier bakeId) {
        FontManager manager = ((MinecraftAccessor) Minecraft.getInstance()).sky$getFontManager();
        return ((FontManagerAccessor) manager).sky$invokeGetFontSetRaw(bakeId);
    }
}
