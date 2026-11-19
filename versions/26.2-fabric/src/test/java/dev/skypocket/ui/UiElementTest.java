package dev.skypocket.ui;

import dev.skypocket.ui.input.KeyEvent;
import dev.skypocket.ui.input.Keys;
import dev.skypocket.ui.input.PointerEvent;
import dev.skypocket.ui.input.PreeditEvent;
import dev.skypocket.ui.input.TextInputEvent;
import dev.skypocket.ui.input.WheelEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The three coordinate spaces and the tree operations.
 *
 * <p>These are the rules from UI-V2-CONTRACT.md §1, pinned as tests. Every one of them has caused a
 * real bug at some point: bounds being parent-relative rather than absolute, offset moving the
 * drawing without moving the layout, and visualX being the only correct base for comparing two
 * elements that live at different depths.
 */
class UiElementTest {
    private static final TextMeasurer MONO = (text, maxWidth) -> new Size(text.length() * 6f, 10f);

    private static Box sized(float w, float h) {
        return new Box(Style.builder().width(Dimension.points(w)).height(Dimension.points(h)).build());
    }

    // ---- bounds are parent-local --------------------------------------------------------

    @Test
    void boundsAreRelativeToTheParentNotThePage() {
        Box outer = new Box(Style.builder().width(Dimension.points(200f))
                .direction(Style.Direction.COLUMN).build());
        // A padding on the parent is what makes nested coordinates differ from page coordinates.
        Box inner = new Box(Style.builder().width(Dimension.points(100f))
                .direction(Style.Direction.COLUMN).padding(10f).build());
        Box leaf = sized(50f, 50f);
        inner.add(leaf);
        outer.add(inner);

        LayoutEngine.layout(outer, 200f, 200f, MONO);

        // The leaf sits at the inner box's content origin, which is its own padding, not the page's.
        assertEquals(10f, leaf.bounds().x(), 0.001f,
                "bounds().x() is relative to the inner parent's content box");
        assertEquals(10f, leaf.bounds().y(), 0.001f);
    }

    @Test
    void containsUsesLocalBounds() {
        Box element = sized(100f, 50f);
        LayoutEngine.layout(element, 400f, 400f, MONO);

        assertTrue(element.contains(0f, 0f), "the origin corner is inside");
        assertTrue(element.contains(99f, 49f));
        assertFalse(element.contains(100f, 49f), "the right edge is exclusive");
        assertFalse(element.contains(99f, 50f), "the bottom edge is exclusive");
        assertFalse(element.contains(-1f, 10f));
    }

    // ---- offset moves drawing, not layout ------------------------------------------------

    @Test
    void offsetDoesNotChangeBounds() {
        Box element = sized(100f, 50f);
        LayoutEngine.layout(element, 400f, 400f, MONO);
        float originalWidth = element.bounds().width();

        element.offset(30f, 40f);

        assertEquals(30f, element.offsetX(), 0.001f);
        assertEquals(40f, element.offsetY(), 0.001f);
        assertEquals(0f, element.bounds().x(), 0.001f, "bounds are untouched by the offset");
        assertEquals(originalWidth, element.bounds().width(), 0.001f);

        // visualX counts *ancestor* offsets only - an element's own offset is applied by the painter
        // while drawing it, not folded into the coordinate its ancestors are measured in. A root
        // with offset(30, 40) therefore has visualX 0; its children have visualX 30.
        assertEquals(0f, element.visualX(), 0.001f, "own offset is not part of visualX");

        Box child = sized(10f, 10f);
        element.add(child);
        LayoutEngine.layout(element, 400f, 400f, MONO);
        assertEquals(30f, child.visualX(), 0.001f, "the parent's offset reaches the child");
        assertEquals(40f, child.visualY(), 0.001f);
    }

    @Test
    void ancestorOffsetAccumulatesThroughTheChain() {
        Box outer = sized(200f, 200f);
        Box middle = sized(100f, 100f);
        Box leaf = sized(50f, 50f);
        middle.add(leaf);
        outer.add(middle);
        LayoutEngine.layout(outer, 200f, 200f, MONO);

        outer.offset(5f, 0f);
        middle.offset(10f, 0f);

        // The leaf's ancestors contribute 5 + 10; its own offset is not part of ancestorOffset.
        assertEquals(15f, leaf.ancestorOffsetX(), 0.001f);
        assertEquals(0f, leaf.offsetX(), 0.001f);

        leaf.offset(3f, 0f);
        assertEquals(15f, leaf.ancestorOffsetX(), 0.001f, "own offset is excluded");
        assertEquals(leaf.bounds().x() + 15f + 3f, leaf.bounds().x() + leaf.ancestorOffsetX() + leaf.offsetX(),
                0.001f);
    }

    @Test
    void visualIsBoundsShiftedByAncestorOffsets() {
        Box outer = sized(200f, 200f);
        Box leaf = sized(50f, 50f);
        Box spacer = sized(30f, 30f);
        outer.add(spacer);
        outer.add(leaf);
        LayoutEngine.layout(outer, 200f, 200f, MONO);

        outer.offset(0f, 7f);

        assertEquals(leaf.bounds().x() + 0f, leaf.visualX(), 0.001f);
        assertEquals(leaf.bounds().y() + 7f, leaf.visualY(), 0.001f);
    }

    // ---- tree operations ----------------------------------------------------------------

    @Test
    void addSetsTheParentAndRemoveClearsIt() {
        Box parent = sized(100f, 100f);
        Box child = sized(10f, 10f);

        parent.add(child);

        assertSame(parent, child.parent());
        assertTrue(parent.children().contains(child));

        parent.remove(child);

        assertNull(child.parent());
        assertFalse(parent.children().contains(child));
    }

    @Test
    void addingAnAlreadyAttachedChildMovesIt() {
        Box first = sized(100f, 100f);
        Box second = sized(100f, 100f);
        Box child = sized(10f, 10f);
        first.add(child);

        second.add(child);

        assertSame(second, child.parent(), "a node has one parent, so adding moves it");
        assertFalse(first.children().contains(child));
    }

    @Test
    void addAcceptsSeveralChildrenAtOnce() {
        Box parent = sized(100f, 100f);
        Box a = sized(10f, 10f);
        Box b = sized(10f, 10f);

        parent.add(a, b);

        assertEquals(2, parent.children().size());
        assertSame(parent, a.parent());
        assertSame(parent, b.parent());
    }

    @Test
    void childrenListIsReadOnlyFromOutside() {
        Box parent = sized(100f, 100f);
        parent.add(sized(10f, 10f));

        // The list must not be a way around add/remove, which maintain the parent pointer.
        try {
            parent.children().clear();
        } catch (UnsupportedOperationException expected) {
            // fine
        }
        assertEquals(1, parent.children().size(), "the child is still attached");
    }

    @Test
    void visibilityAndEnabledDefaultToTrue() {
        Box element = sized(10f, 10f);
        assertTrue(element.visible());
        assertTrue(element.enabled());
        assertFalse(element.clipsChildren());

        element.visible(false).enabled(false).clipsChildren(true);

        assertFalse(element.visible());
        assertFalse(element.enabled());
        assertTrue(element.clipsChildren());
    }

    @Test
    void inputHooksDeclineByDefault() {
        Box element = sized(10f, 10f);
        PointerEvent pointer = new PointerEvent(0f, 0f, 0, 0);

        // A plain element must not silently consume input; controls opt in.
        assertFalse(element.onPointerDown(pointer));
        assertFalse(element.onPointerUp(pointer));
        assertFalse(element.onPointerMove(pointer));
        assertFalse(element.onWheel(new WheelEvent(0f, 0f, 1f, 0)));
        assertFalse(element.onKeyDown(new KeyEvent(Keys.ENTER, 0, 0, true)));
        assertFalse(element.onKeyUp(new KeyEvent(Keys.ENTER, 0, 0, false)));
        assertFalse(element.onTextInput(new TextInputEvent("a")));
        assertFalse(element.onPreedit(new PreeditEvent("a", 1)));
        assertFalse(element.focusable());
        // Pointer capture is opt-in too. A plain container that captured the pointer would keep
        // receiving moves after a click landed on it, which is not what a layout box should do.
        assertFalse(element.acceptsPointer(), "pointer capture is opt-in, not the default");
    }
}
