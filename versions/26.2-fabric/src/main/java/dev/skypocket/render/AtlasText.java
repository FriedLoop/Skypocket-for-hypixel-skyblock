package dev.skypocket.render;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.skypocket.SkyPocketLog;
import dev.skypocket.SkyPocketMod;
import dev.skypocket.config.ConfigManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.font.providers.FreeTypeUtil;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.freetype.FT_Bitmap;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FT_GlyphSlot;
import org.lwjgl.util.freetype.FreeType;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small, self-contained glyph atlas used by the UI v2 text experiment.
 *
 * <p>It deliberately does not use MC's {@code Font} / {@code FontTexture} path: that path hard-codes
 * nearest-neighbour sampling and a 0.01-normalized UV inset, so linear sampling either bleeds
 * neighbouring cells or clips the glyph. Here each glyph owns a padded RGBA cell, the UV rectangle is
 * exact, and the page texture uses a linear clamp sampler. Glyph positions remain fractional; the
 * current pose supplies the subpixel offset.</p>
 *
 * <p>A text run is batched into one custom {@link GuiElementRenderState} per atlas page. This bypasses
 * MC's {@code BlitRenderState} integer rectangle, while {@link ScissorTracker} gives the run the same
 * clip as the engine's own glyphs. The state is still recorded by {@code GuiPanelCache} and replayed
 * from its baked texture like any other GUI element.</p>
 */
public final class AtlasText {
    private static final int PAGE_SIZE = 1024;
    private static final int GLYPH_PAD = 3;
    private static final int CELL_GAP = 2;
    private static final int MAX_RUNS = 512;
    private static final SkyPocketLog LOGGER = SkyPocketLog.of("atlasText");

    private static final String NOTO = "assets/skypocket/font/notosanssc.ttf";
    private static final Map<String, ByteBuffer> MEMORY = new HashMap<>();
    private static final Map<String, FT_Face> FACES = new HashMap<>();
    private static final Map<String, Integer> FACE_PX = new HashMap<>();
    private static final Map<GlyphKey, Glyph> GLYPHS = new HashMap<>();
    private static final List<AtlasPage> PAGES = new ArrayList<>();
    private static final Map<RunKey, Run> RUNS = new LinkedHashMap<>(MAX_RUNS, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<RunKey, Run> eldest) {
            return size() > MAX_RUNS;
        }
    };
    private static final List<PendingUpload> UPLOADS = new ArrayList<>();
    private static int nextPage;
    private static boolean rebuildPending;

    private AtlasText() {
    }

    /** Uploads queued glyph cells at a safe point: client tick, not GUI extraction. */
    public static void tick() {
        if (rebuildPending) {
            rebuildPending = false;
            reset();
        }
        if (uploadAll()) {
            // The panel may already have baked frames with the old/blank atlas.
            dev.skypocket.config.ConfigManager.touch();
        }
    }

    /**
     * Flushes before a text run is submitted. Client ticks can be paused by a screen in some worlds;
     * the draw path must not depend on the tick queue having run.
     */
    private static void flushBeforeDraw() {
        uploadAll();
    }

    private static boolean uploadAll() {
        if (UPLOADS.isEmpty()) {
            return false;
        }
        for (PendingUpload upload : UPLOADS) {
            try {
                RenderSystem.getDevice().createCommandEncoder().writeToTexture(
                        upload.page.texture.getTexture(), upload.image, 0, 0, upload.x, upload.y);
            } finally {
                upload.image.close();
            }
        }
        UPLOADS.clear();
        return true;
    }

    /** Drops pages and layouts when the experimental font settings change. */
    public static void reset() {
        for (AtlasPage page : PAGES) {
            page.texture.close();
        }
        PAGES.clear();
        GLYPHS.clear();
        RUNS.clear();
        for (PendingUpload upload : UPLOADS) {
            upload.image.close();
        }
        UPLOADS.clear();
        FACE_PX.clear();
        for (FT_Face face : FACES.values()) {
            synchronized (FreeTypeUtil.LIBRARY_LOCK) {
                FreeType.FT_Done_Face(face);
            }
        }
        FACES.clear();
        ConfigManager.touch();
    }

    /** Width in panel design units. */
    public static int width(String text, float scale, float deviceFactor, int pixelSize,
                            int oversample) {
        return width(text, scale, deviceFactor, pixelSize, oversample, FontLab.weight());
    }

    public static int width(String text, float scale, float deviceFactor, int pixelSize,
                            int oversample, FontLab.Weight weight) {
        Run run = run(text, scale, deviceFactor, pixelSize, oversample, weight);
        return Math.round(run.advanceRaster / (deviceFactor * oversample));
    }

    /**
     * Draws text with the current pose already in panel design units. {@code y} keeps the existing
     * design baseline convention (the base line is {@code 7 * scale} design units below it).
     */
    public static void draw(GuiGraphicsExtractor graphics, String text, float x, float y, int color,
                            boolean shadow, float scale, float deviceFactor, int pixelSize,
                            int oversample) {
        draw(graphics, text, x, y, color, shadow, scale, deviceFactor, pixelSize, oversample,
                FontLab.weight());
    }

    public static void draw(GuiGraphicsExtractor graphics, String text, float x, float y, int color,
                            boolean shadow, float scale, float deviceFactor, int pixelSize,
                            int oversample, FontLab.Weight weight) {
        drawInternal(graphics, text, x, y, color, shadow, scale, deviceFactor, pixelSize,
                oversample, weight, false);
    }

    /** HUD text uses the same atlas but honours its own shadow flag, not the ClickGUI setting. */
    public static void drawHud(GuiGraphicsExtractor graphics, String text, int x, int y, int color,
                               boolean shadow, float deviceFactor, int pixelSize) {
        drawHud(graphics, text, x, y, color, shadow, deviceFactor, pixelSize,
                FontLab.Weight.REGULAR);
    }

    public static void drawHud(GuiGraphicsExtractor graphics, String text, int x, int y, int color,
                               boolean shadow, float deviceFactor, int pixelSize,
                               FontLab.Weight weight) {
        drawInternal(graphics, text, x, y, color, shadow, 1.0f, deviceFactor, pixelSize, 1,
                weight, true);
    }

    private static void drawInternal(GuiGraphicsExtractor graphics, String text, float x, float y,
                                     int color, boolean shadow, float scale, float deviceFactor,
                                     int pixelSize, int oversample, FontLab.Weight weight,
                                     boolean forceShadow) {
        Run run = run(text, scale, deviceFactor, pixelSize, oversample, weight);
        flushBeforeDraw();
        if (run.glyphs.isEmpty()) {
            return;
        }
        float originStep = FontLab.origin().step;
        if (originStep > 0f) {
            float grid = 1.0f / (deviceFactor * originStep);
            x = Math.round(x / grid) * grid;
            y = Math.round(y / grid) * grid;
        }
        float rasterToDesign = 1.0f / (deviceFactor * oversample);
        if (shadow && (forceShadow || dev.skypocket.config.ConfigManager.get().clickGuiTextShadow)) {
            // One device pixel, not one raster pixel: a 2x atlas would otherwise halve the shadow.
            float offset = 1.0f / deviceFactor;
            drawPass(graphics, run, x + offset, y + offset, shadowColor(color), rasterToDesign);
        }
        drawPass(graphics, run, x, y, color, rasterToDesign);
    }

    private static void drawPass(GuiGraphicsExtractor graphics, Run run, float x, float y, int color,
                                 float rasterToDesign) {
        Map<AtlasPage, List<Quad>> batches = new LinkedHashMap<>();
        for (PlacedGlyph placed : run.glyphs) {
            Glyph glyph = placed.glyph;
            float x0 = x + placed.x * rasterToDesign;
            float y0 = y + placed.y * rasterToDesign;
            float x1 = x0 + glyph.cellW * rasterToDesign;
            float y1 = y0 + glyph.cellH * rasterToDesign;
            batches.computeIfAbsent(glyph.page, ignored -> new ArrayList<>())
                    .add(new Quad(x0, y0, x1, y1, glyph.u0, glyph.v0, glyph.u1, glyph.v1, color));
        }
        dev.skypocket.mixin.GuiGraphicsExtractorAccessor accessor =
                (dev.skypocket.mixin.GuiGraphicsExtractorAccessor) graphics;
        ScreenRectangle scissor = ScissorTracker.current(graphics);
        if (scissor != null && (scissor.width() <= 0 || scissor.height() <= 0)) {
            // Fully clipped text must not become a 0x0 scissor render state; RenderPearl rejects it.
            return;
        }
        for (Map.Entry<AtlasPage, List<Quad>> entry : batches.entrySet()) {
            AtlasPage page = entry.getKey();
            if (!page.texture.usable()) {
                // A reset or a lost GPU texture invalidated this run. Defer the rebuild to the next
                // client tick: closing textures during GUI extraction leaves the current frame's
                // render states pointing at deleted GL texture names.
                if (!rebuildPending) {
                    LOGGER.warn("atlas page {} is not usable; scheduling atlas rebuild", page.id);
                }
                rebuildPending = true;
                ConfigManager.touch();
                return;
            }
            accessor.sky$getRenderState().addGuiElement(new AtlasRunRenderState(
                    new Matrix3x2f(graphics.pose()), page, entry.getValue(), scissor));
        }
    }

    private static int shadowColor(int color) {
        int a = color & 0xFF000000;
        int r = ((color >> 16) & 0xFF) >> 2;
        int g = ((color >> 8) & 0xFF) >> 2;
        int b = (color & 0xFF) >> 2;
        return a | (r << 16) | (g << 8) | b;
    }

    private static Run run(String text, float scale, float deviceFactor, int pixelSize,
                           int oversample, FontLab.Weight weight) {
        RunKey key = new RunKey(text, pixelSize, Float.floatToIntBits(scale),
                Float.floatToIntBits(deviceFactor), oversample, weight.asset);
        Run cached = RUNS.get(key);
        if (cached != null) {
            return cached;
        }
        // The old 7px baseline was calibrated for the original 9px design font. ClickGUI now
        // uses an 18px design font, so keep the same 7/9 ascent ratio instead of pinning the
        // baseline to 7 design units (which made larger text grow upwards out of its row).
        int baseline = Math.round(pixelSize * (7.0f / 9.0f));
        float pen = 0.0f;
        List<PlacedGlyph> placed = new ArrayList<>();
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == '\u00a7' && i < text.length()) {
                // Skip the legacy colour code and its following character.
                i += Character.charCount(text.codePointAt(i));
                continue;
            }
            Glyph glyph = glyph(cp, pixelSize, oversample, weight);
            if (glyph == null) {
                continue;
            }
            if (glyph.page != null) {
                placed.add(new PlacedGlyph(glyph, pen + glyph.bearingX - glyph.pad,
                        baseline - glyph.bearingY - glyph.pad));
            }
            pen += glyph.advance;
        }
        Run run = new Run(List.copyOf(placed), pen);
        RUNS.put(key, run);
        // Layout (width) can build the run before draw; upload now so the first draw cannot sample an
        // uninitialised page.
        uploadAll();
        return run;
    }

    private static Glyph glyph(int codepoint, int pixelSize, int oversample,
                               FontLab.Weight weight) {
        String asset = weight.asset;
        GlyphKey key = new GlyphKey(asset, pixelSize, codepoint, FontLab.hinting());
        Glyph cached = GLYPHS.get(key);
        if (cached != null) {
            return cached;
        }
        Glyph glyph;
        try {
            glyph = rasterize(asset, codepoint, pixelSize, oversample);
            if (glyph == null && !asset.equals(NOTO)) {
                // Latin font has no CJK coverage; the UI's Chinese glyphs come from Noto.
                glyph = rasterize(NOTO, codepoint, pixelSize, oversample);
                if (glyph != null) {
                    key = new GlyphKey(NOTO, pixelSize, codepoint, FontLab.hinting());
                }
            }
        } catch (Throwable t) {
            LOGGER.warn("failed to rasterize U+{} at {}px: {}", Integer.toHexString(codepoint),
                    pixelSize, t.toString());
            return null;
        }
        if (glyph != null) {
            GLYPHS.put(key, glyph);
            // A page may have been uploaded after the panel cache was baked.
            dev.skypocket.config.ConfigManager.touch();
        }
        return glyph;
    }

    private static Glyph rasterize(String asset, int codepoint, int pixelSize, int oversample) {
        FT_Face face = face(asset);
        if (face == null) {
            return null;
        }
        synchronized (FreeTypeUtil.LIBRARY_LOCK) {
            int current = FACE_PX.getOrDefault(asset, -1);
            if (current != pixelSize) {
                FreeTypeUtil.assertError(FreeType.FT_Set_Pixel_Sizes(face, 0, pixelSize),
                        "Setting glyph size");
                FACE_PX.put(asset, pixelSize);
            }
            int index = (int) FreeType.FT_Get_Char_Index(face, codepoint);
            if (index == 0) {
                return null;
            }
            int loadFlags = switch (FontLab.hinting()) {
                case LIGHT -> FreeType.FT_LOAD_DEFAULT | (FreeType.FT_RENDER_MODE_LIGHT << 16);
                case NORMAL -> FreeType.FT_LOAD_DEFAULT;
                case NONE -> FreeType.FT_LOAD_NO_HINTING;
            };
            if (FreeType.FT_Load_Glyph(face, index, loadFlags) != 0) {
                return null;
            }
            FT_GlyphSlot slot = face.glyph();
            int renderMode = FontLab.hinting() == FontLab.Hinting.LIGHT
                    ? FreeType.FT_RENDER_MODE_LIGHT : FreeType.FT_RENDER_MODE_NORMAL;
            if (FreeType.FT_Render_Glyph(slot, renderMode) != 0) {
                return null;
            }
            float advance = slot.advance().x() / 64.0f;
            if (FontLab.integerAdvance()) {
                advance = Math.round(advance);
            }
            FT_Bitmap bitmap = slot.bitmap();
            int inkW = bitmap.width();
            int inkH = bitmap.rows();
            if (inkW <= 0 || inkH <= 0) {
                // Spaces and zero-area glyphs still advance the pen.
                return new Glyph(null, 0, 0, 0, 0, 0, 0, 0, 0, advance, 0);
            }
            int cellW = inkW + GLYPH_PAD * 2;
            int cellH = inkH + GLYPH_PAD * 2;
            AtlasPage page = page(cellW, cellH);
            if (page == null) {
                return null;
            }
            int cellX = page.cellX;
            int cellY = page.cellY;
            upload(page, cellX, cellY, bitmap, inkW, inkH);
            float u0 = cellX / (float) PAGE_SIZE;
            float v0 = cellY / (float) PAGE_SIZE;
            float u1 = (cellX + cellW) / (float) PAGE_SIZE;
            float v1 = (cellY + cellH) / (float) PAGE_SIZE;
            return new Glyph(page, u0, v0, u1, v1, cellW, cellH, slot.bitmap_left(),
                    slot.bitmap_top(), advance, GLYPH_PAD);
        }
    }

    private static void upload(AtlasPage page, int cellX, int cellY, FT_Bitmap bitmap, int inkW, int inkH) {
        int cellW = inkW + GLYPH_PAD * 2;
        int cellH = inkH + GLYPH_PAD * 2;
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, cellW, cellH, false);
        try {
            // NativeImage(false) is uninitialised. Every pixel must be written: leaving the transparent
            // gutter untouched leaks whatever was in that GPU/heap memory into linear sampling.
            for (int y = 0; y < cellH; y++) {
                for (int x = 0; x < cellW; x++) {
                    image.setPixel(x, y, 0);
                }
            }
            int pitch = bitmap.pitch();
            int stride = Math.abs(pitch);
            ByteBuffer src = bitmap.buffer(stride * inkH);
            for (int y = 0; y < inkH; y++) {
                int row = pitch >= 0 ? y : inkH - 1 - y;
                int base = row * stride;
                for (int x = 0; x < inkW; x++) {
                    int coverage = FontLab.coverage(src.get(base + x) & 0xFF);
                    if (coverage != 0) {
                        image.setPixel(x + GLYPH_PAD, y + GLYPH_PAD,
                                (coverage << 24) | 0x00FFFFFF);
                    }
                }
            }
            UPLOADS.add(new PendingUpload(page, cellX, cellY, image));
            image = null; // ownership moved to the tick-driven upload queue
        } finally {
            if (image != null) {
                image.close();
            }
        }
    }

    private static AtlasPage page(int cellW, int cellH) {
        if (cellW > PAGE_SIZE || cellH > PAGE_SIZE) {
            return null;
        }
        if (PAGES.isEmpty()) {
            PAGES.add(new AtlasPage(PAGE_SIZE));
        }
        AtlasPage page = PAGES.get(PAGES.size() - 1);
        if (!page.allocate(cellW, cellH)) {
            page = new AtlasPage(PAGE_SIZE);
            PAGES.add(page);
            if (!page.allocate(cellW, cellH)) {
                return null;
            }
        }
        return page;
    }

    private static FT_Face face(String asset) {
        synchronized (FACES) {
            FT_Face cached = FACES.get(asset);
            if (cached != null) {
                return cached;
            }
            try {
                ByteBuffer memory;
                synchronized (MEMORY) {
                    memory = MEMORY.get(asset);
                    if (memory == null) {
                        memory = load(asset);
                        MEMORY.put(asset, memory);
                    }
                }
                FT_Face face = createFace(memory);
                FACES.put(asset, face);
                return face;
            } catch (Exception e) {
                return null;
            }
        }
    }

    private static FT_Face createFace(ByteBuffer buffer) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer pFace = stack.mallocPointer(1);
            FT_Face face;
            synchronized (FreeTypeUtil.LIBRARY_LOCK) {
                FreeTypeUtil.assertError(
                        FreeType.FT_New_Memory_Face(FreeTypeUtil.getLibrary(), buffer, 0L, pFace),
                        "Initializing font face");
                face = FT_Face.create(pFace.get());
            }
            FreeTypeUtil.assertError(FreeType.FT_Select_Charmap(face, FreeType.FT_ENCODING_UNICODE),
                    "Find unicode charmap");
            return face;
        }
    }

    private static ByteBuffer load(String path) throws Exception {
        Path file = FabricLoader.getInstance().getModContainer(SkyPocketMod.MOD_ID)
                .orElseThrow().findPath(path).orElseThrow();
        try (InputStream in = Files.newInputStream(file)) {
            return TextureUtil.readResource(in);
        }
    }

    private record GlyphKey(String asset, int pixelSize, int codepoint, FontLab.Hinting hinting) {
    }

    private record RunKey(String text, int pixelSize, int scaleBits, int deviceBits, int oversample,
                          String asset) {
    }

    private record Run(List<PlacedGlyph> glyphs, float advanceRaster) {
    }

    private record PlacedGlyph(Glyph glyph, float x, float y) {
    }

    private record PendingUpload(AtlasPage page, int x, int y, NativeImage image) {
    }

    private record Quad(float x0, float y0, float x1, float y1, float u0, float v0, float u1,
                        float v1, int color) {
    }

    /**
     * One draw call for a text run. The texture view and UVs are owned here, so this does not go
     * through {@code FontTexture}, {@code BlitRenderState}'s integer rectangle, or TextureManager.
     */
    private static final class AtlasRunRenderState implements GuiElementRenderState {
        private final Matrix3x2f pose;
        private final List<Quad> quads;
        private final TextureSetup texture;
        private final ScreenRectangle scissor;

        private AtlasRunRenderState(Matrix3x2f pose, AtlasPage page, List<Quad> quads,
                                    ScreenRectangle scissor) {
            this.pose = pose;
            this.quads = quads;
            this.texture = TextureSetup.singleTexture(page.texture.getTextureView(),
                    page.texture.getSampler());
            this.scissor = scissor;
        }

        @Override
        public void buildVertices(com.mojang.blaze3d.vertex.VertexConsumer consumer) {
            for (Quad quad : quads) {
                consumer.addVertexWith2DPose(pose, quad.x0, quad.y0)
                        .setUv(quad.u0, quad.v0).setColor(quad.color);
                consumer.addVertexWith2DPose(pose, quad.x0, quad.y1)
                        .setUv(quad.u0, quad.v1).setColor(quad.color);
                consumer.addVertexWith2DPose(pose, quad.x1, quad.y1)
                        .setUv(quad.u1, quad.v1).setColor(quad.color);
                consumer.addVertexWith2DPose(pose, quad.x1, quad.y0)
                        .setUv(quad.u1, quad.v0).setColor(quad.color);
            }
        }

        @Override
        public com.mojang.blaze3d.pipeline.RenderPipeline pipeline() {
            return RenderPipelines.GUI_TEXTURED;
        }

        @Override
        public TextureSetup textureSetup() {
            return texture;
        }

        @Override
        public ScreenRectangle scissorArea() {
            return scissor;
        }

        @Override
        public ScreenRectangle bounds() {
            float minX = Float.POSITIVE_INFINITY;
            float minY = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY;
            float maxY = Float.NEGATIVE_INFINITY;
            for (Quad quad : quads) {
                for (int corner = 0; corner < 4; corner++) {
                    float qx = (corner & 1) == 0 ? quad.x0 : quad.x1;
                    float qy = (corner & 2) == 0 ? quad.y0 : quad.y1;
                    org.joml.Vector2f p = pose.transformPosition(new org.joml.Vector2f(qx, qy));
                    minX = Math.min(minX, p.x);
                    minY = Math.min(minY, p.y);
                    maxX = Math.max(maxX, p.x);
                    maxY = Math.max(maxY, p.y);
                }
            }
            int x = (int) Math.floor(minX);
            int y = (int) Math.floor(minY);
            int w = Math.max(1, (int) Math.ceil(maxX) - x);
            int h = Math.max(1, (int) Math.ceil(maxY) - y);
            return new ScreenRectangle(x, y, w, h);
        }
    }

    private record Glyph(AtlasPage page, float u0, float v0, float u1, float v1, int cellW, int cellH,
                         int bearingX, int bearingY, float advance, int pad) {
    }

    private static final class AtlasPage {
        private final Identifier id;
        private final PageTexture texture;
        private int x;
        private int y;
        private int rowHeight;
        private int cellX;
        private int cellY;

        private AtlasPage(int size) {
            this.id = Identifier.fromNamespaceAndPath(SkyPocketMod.MOD_ID,
                    "font_atlas_" + nextPage++);
            this.texture = new PageTexture(size);
            // This texture is owned by AtlasText, not by TextureManager: registering it there let
            // unrelated texture lifecycle cleanup close the page while cached render states still
            // held its view, which made every atlas glyph sample as a solid quad.
            // Pages are not zeroed by the GPU. Clear the whole page before any glyph cell is sampled;
            // otherwise linear filtering at a cell edge can read unrelated memory.
            UPLOADS.add(new PendingUpload(this, 0, 0,
                    new NativeImage(NativeImage.Format.RGBA, size, size, true)));
            ConfigManager.touch();
        }

        private boolean allocate(int width, int height) {
            if (x + width > PAGE_SIZE) {
                x = 0;
                y += rowHeight + CELL_GAP;
                rowHeight = 0;
            }
            if (y + height > PAGE_SIZE) {
                return false;
            }
            cellX = x;
            cellY = y;
            rowHeight = Math.max(rowHeight, height);
            x += width + CELL_GAP;
            return true;
        }
    }

    private static final class PageTexture extends AbstractTexture {
        private PageTexture(int size) {
            texture = RenderSystem.getDevice().createTexture(
                    () -> "skypocket font atlas", 7, GpuFormat.RGBA8_UNORM, size, size, 1, 1);
            sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            textureView = RenderSystem.getDevice().createTextureView(texture);
        }

        private boolean usable() {
            return texture != null && !texture.isClosed() && textureView != null;
        }
    }
}
