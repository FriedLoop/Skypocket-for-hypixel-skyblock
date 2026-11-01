package dev.skypocket.render;

import com.mojang.blaze3d.font.GlyphInfo;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.network.chat.Style;
import net.minecraft.util.RandomSource;

/**
 * Wraps a {@link GlyphSource} so every glyph reports an <em>integer</em> advance.
 *
 * <p>MC's FreeType provider takes the advance straight from the font's 26.6 fixed
 * point metrics (so it is fractional) while the glyph bitmap offsets are whole
 * pixels. Fractional advances therefore give every glyph a slightly different
 * sub-pixel phase: the text looks soft and vanilla's 1px shadow lands at a
 * different offset per letter (most visible on round glyphs such as {@code o}).
 * Rounding the advance makes pen positions whole pixels again; combined with the
 * Click GUI's 1:1 raster-to-device mapping, every glyph then lands exactly on the
 * device pixel grid and the shadow is a consistent single pixel.
 *
 * <p>Rendering itself is delegated unchanged, so nothing about the baked glyph
 * (bitmap, uv, bearings) is affected.
 */
final class IntegerAdvanceGlyphSource implements GlyphSource {
    private final GlyphSource delegate;

    IntegerAdvanceGlyphSource(GlyphSource delegate) {
        this.delegate = delegate;
    }

    @Override
    public BakedGlyph getGlyph(int codepoint) {
        return wrap(delegate.getGlyph(codepoint));
    }

    @Override
    public BakedGlyph getRandomGlyph(RandomSource random, int codepoint) {
        return wrap(delegate.getRandomGlyph(random, codepoint));
    }

    private static BakedGlyph wrap(BakedGlyph glyph) {
        return glyph == null ? null : new Rounded(glyph);
    }

    /** Delegates rendering, but reports a rounded advance. */
    private static final class Rounded implements BakedGlyph {
        private final BakedGlyph inner;
        private final GlyphInfo info;

        Rounded(BakedGlyph inner) {
            this.inner = inner;
            GlyphInfo base = inner.info();
            float advance = Math.round(base.getAdvance());
            this.info = new GlyphInfo() {
                @Override
                public float getAdvance() {
                    return advance;
                }

                @Override
                public float getBoldOffset() {
                    return base.getBoldOffset();
                }

                @Override
                public float getShadowOffset() {
                    return base.getShadowOffset();
                }
            };
        }

        @Override
        public GlyphInfo info() {
            return info;
        }

        @Override
        public TextRenderable.Styled createGlyph(float x, float y, int color, int backgroundColor,
                                                 Style style, float boldOffset, float shadowOffset) {
            return inner.createGlyph(x, y, color, backgroundColor, style, boldOffset, shadowOffset);
        }
    }
}
