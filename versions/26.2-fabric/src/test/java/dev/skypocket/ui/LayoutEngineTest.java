package dev.skypocket.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Layout arithmetic.
 *
 * <p>These run without Minecraft, which is only possible because {@code LayoutEngine} has no
 * platform imports - the whole point of keeping the kernel free of them. Every case here is one that
 * took a manual in-game check before; a layout rule that is off by a padding is hard to spot on
 * screen but trivial to assert.
 */
class LayoutEngineTest {
    /** Fixed-width measurer so text contributes a predictable size. */
    private static final TextMeasurer MONO = (text, maxWidth) ->
            new Size(text.length() * 6f, 10f);

    private static Box box(Style.Builder style) {
        return new Box(style.build());
    }

    @Test
    void columnStacksChildrenWithGap() {
        Box column = box(Style.builder()
                .width(Dimension.points(100f))
                .direction(Style.Direction.COLUMN)
                .gap(4f));
        Box first = box(Style.builder().width(Dimension.percent(100f)).height(Dimension.points(20f)));
        Box second = box(Style.builder().width(Dimension.percent(100f)).height(Dimension.points(30f)));
        column.add(first);
        column.add(second);

        LayoutEngine.layout(column, 100f, 200f, MONO);

        assertEquals(0f, first.bounds().y(), 0.001f, "first child at top");
        assertEquals(24f, second.bounds().y(), 0.001f, "second child = first height + gap");
        // The root has an explicit width but an automatic height, and layout gives the root the full
        // available height rather than its content height - that is how a page fills its window. The
        // children still stack from the top, which is what the assertions above check.
        assertEquals(200f, column.bounds().height(), 0.001f, "root fills the available height");
    }

    @Test
    void rowGrowsChildrenByFlexFactor() {
        Box row = box(Style.builder()
                .width(Dimension.points(300f))
                .height(Dimension.points(20f))
                .direction(Style.Direction.ROW));
        Box one = box(Style.builder().width(Dimension.points(0f)).flexGrow(1f));
        Box two = box(Style.builder().width(Dimension.points(0f)).flexGrow(2f));
        row.add(one);
        row.add(two);

        LayoutEngine.layout(row, 300f, 20f, MONO);

        // 300px split 1:2.
        assertEquals(100f, one.bounds().width(), 0.5f);
        assertEquals(200f, two.bounds().width(), 0.5f);
        assertEquals(100f, two.bounds().x(), 0.5f, "second starts where the first ends");
    }

    @Test
    void paddingShrinksContentBox() {
        Box outer = box(Style.builder()
                .width(Dimension.points(100f))
                .height(Dimension.points(100f))
                .padding(Insets.all(10f)));
        Box child = box(Style.builder()
                .width(Dimension.percent(100f))
                .height(Dimension.percent(100f)));
        outer.add(child);

        LayoutEngine.layout(outer, 100f, 100f, MONO);

        assertEquals(10f, child.bounds().x(), 0.001f, "child starts inside the padding");
        assertEquals(80f, child.bounds().width(), 0.001f, "child fills the content box, not the border box");
    }

    @Test
    void absoluteChildIgnoresFlowAndAnchorsRight() {
        Box container = box(Style.builder()
                .width(Dimension.points(200f))
                .height(Dimension.points(100f)));
        Box flow = box(Style.builder().width(Dimension.points(50f)).height(Dimension.points(10f)));
        Box pinned = box(Style.builder()
                .position(Style.Position.ABSOLUTE)
                .right(0f)
                .top(0f)
                .width(Dimension.points(40f))
                .height(Dimension.points(10f)));
        container.add(flow);
        container.add(pinned);

        LayoutEngine.layout(container, 200f, 100f, MONO);

        assertEquals(0f, flow.bounds().x(), 0.001f, "flow child keeps the origin");
        // Absolute children do not consume flow space, so right:0 resolves against the content box.
        assertEquals(160f, pinned.bounds().x(), 0.001f, "right-anchored at contentWidth - width");
    }

    @Test
    void absoluteChildWithBothEdgesStretches() {
        Box container = box(Style.builder()
                .width(Dimension.points(200f))
                .height(Dimension.points(100f)));
        Box pinned = box(Style.builder()
                .position(Style.Position.ABSOLUTE)
                .left(20f)
                .right(30f)
                .top(0f)
                .height(Dimension.points(10f)));
        container.add(pinned);

        LayoutEngine.layout(container, 200f, 100f, MONO);

        assertEquals(20f, pinned.bounds().x(), 0.001f);
        assertEquals(150f, pinned.bounds().width(), 0.001f, "width comes from the gap between the edges");
    }

    @Test
    void minWidthWinsOverSmallerResolvedWidth() {
        Box b = box(Style.builder()
                .width(Dimension.points(10f))
                .minWidth(50f)
                .height(Dimension.points(10f)));

        LayoutEngine.layout(b, 200f, 200f, MONO);

        assertEquals(50f, b.bounds().width(), 0.001f);
    }

    @Test
    void textNodeMeasuresThroughTheGivenMeasurer() {
        Text label = new Text("abcd", Style.builder().build(), 1f, TextWeight.REGULAR);

        // Wrapped in a container so the text node is laid out as a child rather than as the root: a
        // root with automatic size is stretched to the available space, which would hide the
        // measurer's contribution.
        Box host = box(Style.builder()
                .width(Dimension.points(200f))
                .height(Dimension.points(100f))
                .direction(Style.Direction.COLUMN));
        host.add(label);

        LayoutEngine.layout(host, 200f, 100f, MONO);

        assertEquals(24f, label.bounds().width(), 0.001f, "4 chars * 6px");
        assertEquals(10f, label.bounds().height(), 0.001f);
    }

    @Test
    void percentResolvesAgainstTheParentContentBox() {
        Box row = box(Style.builder()
                .width(Dimension.points(200f))
                .height(Dimension.points(50f))
                .padding(Insets.all(20f))
                .direction(Style.Direction.ROW));
        Box half = box(Style.builder().width(Dimension.percent(50f)).height(Dimension.percent(100f)));
        row.add(half);

        LayoutEngine.layout(row, 200f, 50f, MONO);

        assertEquals(80f, half.bounds().width(), 0.001f, "50% of the 160px content box");
        assertEquals(20f, half.bounds().x(), 0.001f);
    }

    @Test
    void scrollViewOffsetsItsContentWithoutChangingBounds() {
        Box content = box(Style.builder().direction(Style.Direction.COLUMN));
        for (int i = 0; i < 10; i++) {
            content.add(box(Style.builder()
                    .width(Dimension.points(100f)).height(Dimension.points(50f))));
        }
        ScrollView view = new ScrollView(Style.builder()
                .width(Dimension.points(100f)).height(Dimension.points(100f)).build(), content, 20f);

        LayoutEngine.layout(view, 100f, 100f, MONO);
        view.refresh();

        float contentHeight = content.bounds().height();
        assertTrue(contentHeight > 100f, "content is taller than the viewport");
        assertEquals(0f, view.scrollY(), 0.001f, "nothing scrolled yet");

        view.scrollTo(30f);
        assertEquals(30f, view.scrollY(), 0.001f);
        assertEquals(-30f, content.offsetY(), 0.001f, "offset moves the content, not its bounds");
        assertEquals(contentHeight, content.bounds().height(), 0.001f, "bounds are untouched by scrolling");
    }

    @Test
    void scrollClampsToContentHeight() {
        Box content = box(Style.builder().direction(Style.Direction.COLUMN));
        content.add(box(Style.builder().width(Dimension.points(100f)).height(Dimension.points(300f))));
        ScrollView view = new ScrollView(Style.builder()
                .width(Dimension.points(100f)).height(Dimension.points(100f)).build(), content, 20f);

        LayoutEngine.layout(view, 100f, 100f, MONO);
        view.refresh();
        view.scrollTo(10_000f);

        assertEquals(view.maxScroll(), view.scrollY(), 0.001f, "cannot scroll past the end");
        assertTrue(view.maxScroll() > 0f);
    }
}
