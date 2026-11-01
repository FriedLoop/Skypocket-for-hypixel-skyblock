package dev.skypocket.render;

import com.mojang.blaze3d.font.GlyphBitmap;
import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.UnbakedGlyph;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.gui.font.providers.FreeTypeUtil;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.freetype.FT_Bitmap;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FT_GlyphSlot;
import org.lwjgl.util.freetype.FreeType;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/**
 * A glyph provider that rasterises with FreeType's <b>light</b> hinting instead of the default target.
 *
 * <p>Why this exists: the Click GUI rasterises each glyph at the exact size it will be drawn at, so the
 * rasteriser's hinting is the whole of the text's perceived quality. The vanilla provider uses FreeType's
 * default target, which snaps stems onto the pixel grid - crisp, but at small sizes it distorts letter
 * shapes and leaves some strokes a pixel lighter than their neighbours, which reads as "less smooth"
 * next to a UI that uses light hinting.
 *
 * <p>The pixels are copied out of FreeType ourselves and written into a single-channel image. The
 * obvious shortcut - {@link NativeImage#copyFromFont} - cannot be used: it re-loads the glyph with its
 * own flags (and throws when that produces a differently sized bitmap than the one measured here).
 *
 * <p>Everything else is delegated to Minecraft: {@code stitcher.stitch(...)} builds the
 * {@link BakedGlyph}, packs it into the shared glyph atlas and uploads it, so UVs, padding and filtering
 * stay exactly what the rest of the game uses.
 */
final class LightHintGlyphProvider implements GlyphProvider {

    /** Render target for the outline: light hinting, grayscale coverage. */
    private static final int LOAD_FLAGS =
            FreeType.FT_LOAD_DEFAULT | (FreeType.FT_RENDER_MODE_LIGHT << 16);

    private final FT_Face face;
    private final IntSet codepoints;
    private boolean closed;

    LightHintGlyphProvider(FT_Face face, int pixelSize, float shiftY) {
        this.face = face;
        synchronized (FreeTypeUtil.LIBRARY_LOCK) {
            FreeTypeUtil.assertError(FreeType.FT_Set_Pixel_Sizes(face, 0, pixelSize),
                    "Setting glyph size");
            // Same vertical nudge the vanilla provider is given, but integral: FT_Set_Transform takes
            // 26.6 fixed point, so a fractional value resamples every glyph vertically.
            //
            // The delta is NEGATED: FreeType's coordinate system has y pointing up, while the shift this
            // parameter expresses is "down is positive" (the vanilla provider does the same conversion).
            // Passing it unchanged moves every glyph that many pixels up - which is exactly what it did.
            if (shiftY != 0f) {
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    FreeType.FT_Set_Transform(face, null,
                            FreeTypeUtil.setVector(
                                    org.lwjgl.util.freetype.FT_Vector.malloc(stack), 0f, -shiftY));
                }
            }
        }
        this.codepoints = enumerate(face);
    }

    @Override
    public UnbakedGlyph getGlyph(int codepoint) {
        synchronized (FreeTypeUtil.LIBRARY_LOCK) {
            if (closed) {
                return null;
            }
            int index = (int) FreeType.FT_Get_Char_Index(face, codepoint);
            if (index == 0) {
                return null;
            }
            if (FreeType.FT_Load_Glyph(face, index, LOAD_FLAGS) != 0) {
                return null;
            }
            FT_GlyphSlot slot = face.glyph();
            if (FreeType.FT_Render_Glyph(slot, FreeType.FT_RENDER_MODE_LIGHT) != 0) {
                return null;
            }
            float advance = slot.advance().x() / 64.0f;
            FT_Bitmap bitmap = slot.bitmap();
            int width = bitmap.width();
            int height = bitmap.rows();
            byte[] coverage = width > 0 && height > 0 ? copy(bitmap) : null;
            return new Glyph(GlyphInfo.simple(advance), width, height, slot.bitmap_left(),
                    slot.bitmap_top(), coverage);
        }
    }

    /** Copies the rendered coverage out of FreeType, row by row (pitch may be negative). */
    private static byte[] copy(FT_Bitmap bitmap) {
        int width = bitmap.width();
        int height = bitmap.rows();
        int pitch = bitmap.pitch();
        // FreeType may hand back a bottom-up buffer (negative pitch). The length passed to buffer()
        // must be positive either way, and the rows are flipped below.
        ByteBuffer src = bitmap.buffer(Math.abs(pitch) * height);
        byte[] out = new byte[width * height];
        int stride = Math.abs(pitch);
        for (int y = 0; y < height; y++) {
            int row = pitch >= 0 ? y : height - 1 - y;
            int base = row * stride;
            for (int x = 0; x < width; x++) {
                out[y * width + x] = src.get(base + x);
            }
        }
        return out;
    }

    @Override
    public IntSet getSupportedGlyphs() {
        return codepoints;
    }

    @Override
    public void close() {
        synchronized (FreeTypeUtil.LIBRARY_LOCK) {
            if (!closed) {
                closed = true;
                FreeType.FT_Done_Face(face);
            }
        }
    }

    /** Every codepoint in the face's Unicode charmap. */
    private static IntSet enumerate(FT_Face face) {
        IntSet out = new IntOpenHashSet();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer index = stack.mallocInt(1);
            synchronized (FreeTypeUtil.LIBRARY_LOCK) {
                long code = FreeType.FT_Get_First_Char(face, index);
                while (code != 0 && index.get(0) != 0) {
                    out.add((int) code);
                    code = FreeType.FT_Get_Next_Char(face, code, index);
                }
            }
        }
        return out;
    }

    /** One glyph: metrics and coverage up front, no second rasterisation later. */
    private static final class Glyph implements UnbakedGlyph {
        private final GlyphInfo info;
        private final int width;
        private final int height;
        private final float bearingX;
        private final float bearingY;
        private final byte[] coverage;

        private Glyph(GlyphInfo info, int width, int height, float bearingX, float bearingY,
                      byte[] coverage) {
            this.info = info;
            this.width = width;
            this.height = height;
            this.bearingX = bearingX;
            this.bearingY = bearingY;
            this.coverage = coverage;
        }

        @Override
        public GlyphInfo info() {
            return info;
        }

        @Override
        public BakedGlyph bake(Stitcher stitcher) {
            // Zero-area glyphs (space and friends) still need their advance; there is just nothing to
            // upload, and the stitcher places nothing for a zero-sized bitmap.
            if (width <= 0 || height <= 0 || coverage == null) {
                return stitcher.stitch(info, new EmptyBitmap());
            }
            return stitcher.stitch(info, new Bitmap());
        }

        private final class Bitmap implements GlyphBitmap {
            @Override
            public int getPixelWidth() {
                return width;
            }

            @Override
            public int getPixelHeight() {
                return height;
            }

            @Override
            public float getOversample() {
                // One raster pixel per pixel: the font is built for the exact size it is drawn at.
                return 1.0f;
            }

            @Override
            public float getBearingLeft() {
                return bearingX;
            }

            @Override
            public float getBearingTop() {
                return bearingY;
            }

            @Override
            public boolean isColored() {
                return false;
            }

            @Override
            public void upload(int x, int y, GpuTexture texture) {
                // LUMINANCE is what the glyph atlas stores, so the coverage goes in as-is: one byte per
                // pixel, no channel juggling, and the size always matches the bitmap handed to the
                // stitcher because both come from the same measurement.
                NativeImage image = new NativeImage(NativeImage.Format.LUMINANCE, width, height, false);
                try {
                    ByteBuffer dst = image.getPixelBytes();
                    for (int i = 0; i < coverage.length; i++) {
                        dst.put(i, coverage[i]);
                    }
                    RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture, image,
                            0, 0, x, y);
                } finally {
                    image.close();
                }
            }
        }

        /** A zero-area glyph: metrics only. */
        private static final class EmptyBitmap implements GlyphBitmap {
            @Override
            public int getPixelWidth() {
                return 0;
            }

            @Override
            public int getPixelHeight() {
                return 0;
            }

            @Override
            public float getOversample() {
                return 1.0f;
            }

            @Override
            public float getBearingLeft() {
                return 0f;
            }

            @Override
            public float getBearingTop() {
                return 0f;
            }

            @Override
            public boolean isColored() {
                return false;
            }

            @Override
            public void upload(int x, int y, GpuTexture texture) {
                // Nothing to draw.
            }
        }
    }
}
