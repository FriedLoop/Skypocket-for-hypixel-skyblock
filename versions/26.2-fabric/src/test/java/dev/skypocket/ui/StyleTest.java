package dev.skypocket.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Value types and the style builder.
 *
 * <p>These look trivial but carry the rules the layout engine depends on: {@code resolve} signalling
 * "not definite" with NaN, negative lengths being clamped at construction, and {@code from()}
 * copying a style without sharing mutable state.
 */
class StyleTest {

    // ---- Dimension ----------------------------------------------------------------------

    @Test
    void autoResolvesToNotDefinite() {
        assertTrue(Float.isNaN(Dimension.AUTO.resolve(100f)),
                "auto must signal 'no definite size' with NaN, not zero");
        assertTrue(Dimension.AUTO.isAuto());
        assertFalse(Dimension.points(1f).isAuto());
    }

    @Test
    void pointsIgnoreTheParentSize() {
        assertEquals(42f, Dimension.points(42f).resolve(1000f), 0.001f);
        assertEquals(42f, Dimension.points(42f).resolve(Float.POSITIVE_INFINITY), 0.001f);
    }

    @Test
    void percentResolvesAgainstTheParent() {
        assertEquals(25f, Dimension.percent(25f).resolve(100f), 0.001f);
        assertEquals(200f, Dimension.percent(50f).resolve(400f), 0.001f);
    }

    @Test
    void percentAgainstAnInfiniteParentIsNotDefinite() {
        // A percentage of an unbounded parent has no answer; returning NaN lets the caller fall back
        // to content measurement instead of inventing a size.
        assertTrue(Float.isNaN(Dimension.percent(50f).resolve(Float.POSITIVE_INFINITY)));
    }

    @Test
    void negativeLengthsAreClampedAtConstruction() {
        assertEquals(0f, Dimension.points(-10f).value(), 0.001f);
        assertEquals(0f, Dimension.percent(-5f).value(), 0.001f);
    }

    // ---- Insets -------------------------------------------------------------------------

    @Test
    void insetsAllAndSymmetric() {
        Insets all = Insets.all(8f);
        assertEquals(16f, all.horizontal(), 0.001f);
        assertEquals(16f, all.vertical(), 0.001f);

        Insets symmetric = Insets.symmetric(4f, 10f);
        assertEquals(20f, symmetric.horizontal(), 0.001f);
        assertEquals(8f, symmetric.vertical(), 0.001f);
    }

    // ---- Size / Rect --------------------------------------------------------------------

    @Test
    void sizeClampsNegatives() {
        Size size = new Size(-5f, 10f);
        assertEquals(0f, size.width(), 0.001f);
        assertEquals(10f, size.height(), 0.001f);
        assertEquals(0f, Size.ZERO.width(), 0.001f);
    }

    @Test
    void rectEdgesDeriveFromOriginAndSize() {
        Rect rect = new Rect(10f, 20f, 30f, 40f);
        assertEquals(40f, rect.right(), 0.001f);
        assertEquals(60f, rect.bottom(), 0.001f);
    }

    // ---- Style builder ------------------------------------------------------------------

    @Test
    void builderDefaultsAreSensible() {
        Style style = Style.builder().build();
        assertTrue(style.width().isAuto(), "unsized elements measure to their content");
        assertTrue(style.height().isAuto());
        // ROW, not COLUMN. Worth pinning: a vertical stack is the common case in this project, so
        // every column container sets the direction explicitly and the default is easy to assume
        // wrong. It is ROW.
        assertEquals(Style.Direction.ROW, style.direction());
        assertEquals(0f, style.gap(), 0.001f);
        assertEquals(Insets.ZERO.horizontal(), style.padding().horizontal(), 0.001f);
    }

    @Test
    void floatSettersWrapAsPoints() {
        Style style = Style.builder().width(120f).height(30f).build();
        assertEquals(120f, style.width().value(), 0.001f);
        assertEquals(Dimension.Unit.POINT, style.width().unit());
        assertEquals(30f, style.height().resolve(1000f), 0.001f);
    }

    @Test
    void singlePaddingAndMarginApplyToAllSides() {
        Style style = Style.builder().padding(6f).margin(3f).build();
        assertEquals(12f, style.padding().horizontal(), 0.001f);
        assertEquals(12f, style.padding().vertical(), 0.001f);
        assertEquals(6f, style.margin().horizontal(), 0.001f);
    }

    @Test
    void fromCopiesEveryFieldIndependently() {
        Style original = Style.builder()
                .width(Dimension.percent(50f))
                .height(Dimension.points(40f))
                .padding(8f)
                .margin(4f)
                .gap(6f)
                .direction(Style.Direction.ROW)
                .justify(Style.Justify.CENTER)
                .alignItems(Style.Align.END)
                .flexGrow(2f)
                .flexShrink(0f)
                .build();

        Style copy = Style.builder().from(original).build();

        assertSame(original.width(), copy.width(), "value records are shared, which is safe");
        assertEquals(original.height(), copy.height());
        assertEquals(original.padding(), copy.padding());
        assertEquals(original.margin(), copy.margin());
        assertEquals(original.gap(), copy.gap());
        assertEquals(original.direction(), copy.direction());
        assertEquals(original.justify(), copy.justify());
        assertEquals(original.alignItems(), copy.alignItems());
        assertEquals(original.flexGrow(), copy.flexGrow(), 0.001f);
        assertEquals(original.flexShrink(), copy.flexShrink(), 0.001f);

        // Changing the copy must not touch the original.
        Style changed = Style.builder().from(original).gap(99f).build();
        assertEquals(6f, original.gap(), 0.001f);
        assertEquals(99f, changed.gap(), 0.001f);
    }

    @Test
    void fromThenOverridingOneFieldKeepsTheRest() {
        Style base = Style.builder()
                .width(Dimension.points(100f))
                .direction(Style.Direction.ROW)
                .gap(4f)
                .build();

        Style narrowed = Style.builder().from(base).width(Dimension.points(50f)).build();

        assertEquals(50f, narrowed.width().resolve(1000f), 0.001f, "the override wins");
        assertEquals(Style.Direction.ROW, narrowed.direction(), "the rest is preserved");
        assertEquals(4f, narrowed.gap(), 0.001f);
        assertEquals(100f, base.width().resolve(1000f), 0.001f, "the base is untouched");
    }

    @Test
    void positionDefaultsToFlowAndCanBecomeAbsolute() {
        assertEquals(Style.Position.FLOW, Style.builder().build().position());
        assertEquals(Style.Position.ABSOLUTE,
                Style.builder().position(Style.Position.ABSOLUTE).build().position());
    }
}
