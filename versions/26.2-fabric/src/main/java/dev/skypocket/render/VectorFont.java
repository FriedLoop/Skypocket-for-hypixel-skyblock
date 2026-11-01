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
import java.util.List;
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
final class VectorFont {
    private static final String INTER = "assets/skypocket/font/inter.ttf";
    private static final String NOTO = "assets/skypocket/font/notosanssc.ttf";

    /** Rasterise with FreeType's light hinting; {@code false} falls back to the vanilla provider. */
    private static final boolean LIGHT_HINTING = true;

    /** Shared, never-freed font memory (FreeType only reads it). */
    private static ByteBuffer interMemory;
    private static ByteBuffer notoMemory;

    private final FontSet fontSet;
    final Font font;

    private VectorFont(FontSet fontSet, Font font) {
        this.fontSet = fontSet;
        this.font = font;
    }

    /**
     * A font under construction. Building one is far too much for a single client
     * tick — each slice parses a FreeType face (the CJK family is a 10&nbsp;MB font)
     * plus the FontSet reload — so construction is sliced and driven by the tick,
     * keeping the work per tick small.
     */
    static final class Builder {
        private final int pixelSize;
        private final FontSet fontSet;
        private final List<GlyphProvider.Conditional> conditions = new ArrayList<>();
        private int slice;

        Builder(int pixelSize) {
            this.pixelSize = pixelSize;
            Minecraft mc = Minecraft.getInstance();
            Identifier id = Identifier.fromNamespaceAndPath(SkyPocketMod.MOD_ID, "vecfont_" + pixelSize);
            this.fontSet = new FontSet(new GlyphStitcher(mc.getTextureManager(), id));
        }

        int pixelSize() {
            return pixelSize;
        }

        /** Runs one slice; returns the finished font, or null while more are needed. */
        VectorFont step() throws Exception {
            switch (slice++) {
                case 0 -> conditions.add(provider(interMemory()));
                case 1 -> conditions.add(provider(notoMemory()));
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
            // A/B switch: flip to false to go back to the vanilla rasteriser without touching anything
            // else. See the class comment on LightHintGlyphProvider for why light hinting is preferred.
            GlyphProvider provider = LIGHT_HINTING
                    ? new LightHintGlyphProvider(face, pixelSize, shiftY)
                    : new TrueTypeGlyphProvider(memory, face, pixelSize, 1.0f, 0f, shiftY, "");
            return new GlyphProvider.Conditional(provider, FontOption.Filter.ALWAYS_PASS);
        }
    }

    /** The MC {@link Font} over a prepared font set. */
    private static Font createFont(FontSet fontSet) {
        // One shared wrapper (not allocated per text): advances rounded to whole
        // pixels so the Click GUI's 1:1 mapping keeps glyphs on the pixel grid.
        GlyphSource source = new IntegerAdvanceGlyphSource(fontSet.source(false));
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

    private static ByteBuffer interMemory() throws Exception {
        if (interMemory == null) {
            interMemory = load(INTER);
        }
        return interMemory;
    }

    private static ByteBuffer notoMemory() throws Exception {
        if (notoMemory == null) {
            notoMemory = load(NOTO);
        }
        return notoMemory;
    }

    private static ByteBuffer load(String path) throws Exception {
        Path file = FabricLoader.getInstance().getModContainer(SkyPocketMod.MOD_ID)
                .orElseThrow().findPath(path).orElseThrow();
        try (InputStream in = Files.newInputStream(file)) {
            return TextureUtil.readResource(in);
        }
    }
}
