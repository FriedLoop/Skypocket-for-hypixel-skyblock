package dev.skypocket.render;

import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.TrueTypeGlyphProvider;
import com.mojang.blaze3d.platform.TextureUtil;
import dev.skypocket.SkyPocketMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.font.FontOption;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.GlyphStitcher;
import net.minecraft.client.gui.font.glyphs.EffectGlyph;
import net.minecraft.client.gui.font.providers.FreeTypeUtil;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FreeType;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A "vector" font built at runtime: MC's own FreeType TTF provider
 * ({@link TrueTypeGlyphProvider}) rasterizes each glyph at an exact pixel size and
 * stitches it into a dedicated atlas via {@link FontSet}/{@link GlyphStitcher}.
 * Because glyphs are rasterized for the exact on-screen pixel size (instead of
 * resampling a pre-baked bitmap), Click GUI text stays sharp at any scale.
 *
 * <p>Instances are <em>not</em> closed (the set of sizes is small and fixed): the raw
 * font byte buffers are shared across all sizes so the (10&nbsp;MB CJK) font lives in
 * memory exactly once, rather than once per size.
 */
final class VectorFont implements AutoCloseable {
    private static final String NOTO = "assets/skypocket/font/notosanssc.ttf";

    /** Shared, never-freed font memory (FreeType only reads it), keyed by asset path. */
    private static final Map<String, ByteBuffer> MEMORY = new HashMap<>();

    private final FontSet fontSet;
    final Font font;

    private VectorFont(FontSet fontSet, Font font) {
        this.fontSet = fontSet;
        this.font = font;
    }

    @Override
    public void close() {
        fontSet.close();
    }

    /**
     * A font under construction. Building one is far too much for a single client
     * tick — each slice parses a FreeType face (the CJK family is a 10&nbsp;MB font)
     * plus the FontSet reload — so construction is sliced and driven by the tick,
     * keeping the work per tick small.
     */
    static final class Builder {
        private final int pixelSize;
        private final int generation;
        private final FontSet fontSet;
        private final List<GlyphProvider.Conditional> conditions = new ArrayList<>();
        private int slice;

        Builder(int pixelSize, int generation) {
            this.pixelSize = pixelSize;
            this.generation = generation;
            Minecraft mc = Minecraft.getInstance();
            // The lab can rebuild the same pixel size with a different hinting/gamma/weight, so the
            // texture id must also carry the generation; reusing it collides with the previous atlas.
            Identifier id = Identifier.fromNamespaceAndPath(SkyPocketMod.MOD_ID,
                    "vecfont_" + generation + "_" + pixelSize);
            this.fontSet = new FontSet(new GlyphStitcher(mc.getTextureManager(), id));
        }

        int pixelSize() {
            return pixelSize;
        }

        int generation() {
            return generation;
        }

        void close() {
            fontSet.close();
        }

        /** Runs one slice; returns the finished font, or null while more are needed. */
        VectorFont step() throws Exception {
            switch (slice++) {
                case 0 -> conditions.add(provider(memory(FontLab.weight().asset)));
                case 1 -> conditions.add(provider(memory(NOTO)));
                default -> {
                    fontSet.reload(conditions, Set.of());
                    return new VectorFont(fontSet, createFont(fontSet));
                }
            }
            return null;
        }

        private GlyphProvider.Conditional provider(ByteBuffer memory) {
            FT_Face face = createFace(memory);
            // The vertical shift has to be a WHOLE number of pixels.
            //
            // It is passed to FT_Set_Transform as a 26.6 fixed-point delta, i.e. a sub-pixel
            // translation of the outline before rasterisation. A fractional value (pixelSize / 9 is
            // 1.44 at 13 px, 2.33 at 21 px, ...) therefore resamples every glyph vertically and the
            // edges come out soft - which is exactly the "less crisp than it should be" look. Rounding
            // keeps the intent (nudge the ink down to sit like vanilla) without paying that cost.
            float shiftY = Math.round(pixelSize / 9.0f);
            GlyphProvider provider = new LightHintGlyphProvider(face, pixelSize, shiftY,
                    FontLab.hinting());
            return new GlyphProvider.Conditional(provider, FontOption.Filter.ALWAYS_PASS);
        }
    }

    /** The MC {@link Font} over a prepared font set. */
    private static Font createFont(FontSet fontSet) {
        // The old policy rounded advances to keep every glyph on the device grid. The lab can disable
        // that so the internal pen positions stay fractional and the atlas is sampled between pixels.
        GlyphSource source = FontLab.integerAdvance()
                ? new IntegerAdvanceGlyphSource(fontSet.source(false))
                : fontSet.source(false);
        EffectGlyph whiteGlyph = fontSet.whiteGlyph();
        return new Font(new Font.Provider() {
            @Override
            public GlyphSource glyphs(FontDescription description) {
                return source;
            }

            @Override
            public EffectGlyph effect() {
                return whiteGlyph;
            }
        });
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

    private static ByteBuffer memory(String path) throws Exception {
        synchronized (MEMORY) {
            ByteBuffer cached = MEMORY.get(path);
            if (cached != null) {
                return cached;
            }
            ByteBuffer loaded = load(path);
            MEMORY.put(path, loaded);
            return loaded;
        }
    }

    private static ByteBuffer load(String path) throws Exception {
        Path file = FabricLoader.getInstance().getModContainer(SkyPocketMod.MOD_ID)
                .orElseThrow().findPath(path).orElseThrow();
        try (InputStream in = Files.newInputStream(file)) {
            return TextureUtil.readResource(in);
        }
    }
}
