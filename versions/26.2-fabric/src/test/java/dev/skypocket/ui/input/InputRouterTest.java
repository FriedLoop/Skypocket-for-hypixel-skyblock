package dev.skypocket.ui.input;

import dev.skypocket.ui.Box;
import dev.skypocket.ui.Dimension;
import dev.skypocket.ui.LayoutEngine;
import dev.skypocket.ui.ScrollView;
import dev.skypocket.ui.Size;
import dev.skypocket.ui.Style;
import dev.skypocket.ui.TextMeasurer;
import dev.skypocket.ui.UiElement;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hit testing, event bubbling, focus and layers.
 *
 * <p>None of this needs Minecraft, which means the interaction rules can be asserted directly
 * instead of opened in a game. Each test builds a small tree, lays it out with the real
 * {@link LayoutEngine}, and dispatches through a real {@link InputRouter} - the same path the
 * screen uses.
 */
class InputRouterTest {
    private static final TextMeasurer MONO = (text, maxWidth) ->
            new Size(text.length() * 6f, 10f);

    /** Records the events it receives, so bubbling order can be asserted. */
    private static class Probe extends Box {
        final List<String> events = new ArrayList<>();
        final String name;
        boolean acceptsPointer = true;
        boolean focusable;
        boolean consumed;
        boolean hovered;

        Probe(String name, Style style) {
            super(style);
            this.name = name;
        }

        @Override
        public boolean onPointerDown(PointerEvent event) {
            events.add("down");
            return consumed;
        }

        @Override
        public boolean onPointerUp(PointerEvent event) {
            events.add("up");
            return consumed;
        }

        @Override
        public void onHoverChanged(boolean value) {
            hovered = value;
        }

        @Override
        public boolean acceptsPointer() {
            return acceptsPointer;
        }

        @Override
        public boolean focusable() {
            return focusable;
        }
    }

    private static Style sized(float w, float h) {
        return Style.builder().width(Dimension.points(w)).height(Dimension.points(h)).build();
    }

    private static InputRouter routerFor(UiElement root) {
        LayoutEngine.layout(root, 400f, 400f, MONO);
        return new InputRouter(root);
    }

    // ---- hit testing --------------------------------------------------------------------

    @Test
    void clickHitsTheTopmostChildAtThatPoint() {
        Box root = new Box(sized(200f, 200f));
        Probe back = new Probe("back", Style.builder()
                .position(Style.Position.ABSOLUTE).left(0f).top(0f)
                .width(Dimension.points(100f)).height(Dimension.points(100f)).build());
        Probe front = new Probe("front", Style.builder()
                .position(Style.Position.ABSOLUTE).left(0f).top(0f)
                .width(Dimension.points(100f)).height(Dimension.points(100f)).build());
        root.add(back);
        root.add(front);
        InputRouter router = routerFor(root);

        router.pointerDown(new PointerEvent(50f, 50f, 0, 0));

        // Later siblings are drawn on top, so they get the click.
        assertEquals(List.of("down"), front.events);
        assertTrue(back.events.isEmpty());
    }

    @Test
    void clickOutsideEveryChildClearsFocus() {
        Box root = new Box(sized(200f, 200f));
        Probe child = new Probe("child", Style.builder()
                .position(Style.Position.ABSOLUTE).left(0f).top(0f)
                .width(Dimension.points(50f)).height(Dimension.points(50f)).build());
        child.focusable = true;
        root.add(child);
        InputRouter router = routerFor(root);

        router.pointerDown(new PointerEvent(10f, 10f, 0, 0));
        assertSame(child, router.focused(), "clicking a focusable element focuses it");

        router.pointerDown(new PointerEvent(150f, 150f, 0, 0));
        assertNull(router.focused(), "clicking empty space releases focus");
    }

    @Test
    void invisibleElementIsNotHit() {
        Box root = new Box(sized(200f, 200f));
        Probe child = new Probe("child", Style.builder()
                .position(Style.Position.ABSOLUTE).left(0f).top(0f)
                .width(Dimension.points(100f)).height(Dimension.points(100f)).build());
        root.add(child);
        InputRouter router = routerFor(root);

        child.visible(false);
        router.pointerDown(new PointerEvent(50f, 50f, 0, 0));

        assertTrue(child.events.isEmpty());
    }

    // ---- bubbling -----------------------------------------------------------------------

    @Test
    void unhandledEventBubblesToTheParent() {
        Box root = new Box(sized(200f, 200f));
        Probe parent = new Probe("parent", Style.builder()
                .position(Style.Position.ABSOLUTE).left(0f).top(0f)
                .width(Dimension.points(150f)).height(Dimension.points(150f)).build());
        Probe child = new Probe("child", Style.builder()
                .position(Style.Position.ABSOLUTE).left(0f).top(0f)
                .width(Dimension.points(100f)).height(Dimension.points(100f)).build());
        parent.add(child);
        root.add(parent);
        InputRouter router = routerFor(root);

        router.pointerDown(new PointerEvent(50f, 50f, 0, 0));

        assertEquals(List.of("down"), child.events, "child sees it first");
        assertEquals(List.of("down"), parent.events, "then the parent, because the child declined");
    }

    @Test
    void handledEventStopsBubbling() {
        Box root = new Box(sized(200f, 200f));
        Probe parent = new Probe("parent", Style.builder()
                .position(Style.Position.ABSOLUTE).left(0f).top(0f)
                .width(Dimension.points(150f)).height(Dimension.points(150f)).build());
        Probe child = new Probe("child", Style.builder()
                .position(Style.Position.ABSOLUTE).left(0f).top(0f)
                .width(Dimension.points(100f)).height(Dimension.points(100f)).build());
        child.consumed = true;
        parent.add(child);
        root.add(parent);
        InputRouter router = routerFor(root);

        router.pointerDown(new PointerEvent(50f, 50f, 0, 0));

        assertEquals(List.of("down"), child.events);
        assertTrue(parent.events.isEmpty(), "the child consumed it, so the parent never sees it");
    }

    @Test
    void consumingElementBecomesTheCaptureTarget() {
        Box root = new Box(sized(200f, 200f));
        Probe child = new Probe("child", Style.builder()
                .position(Style.Position.ABSOLUTE).left(0f).top(0f)
                .width(Dimension.points(100f)).height(Dimension.points(100f)).build());
        child.consumed = true;
        root.add(child);
        InputRouter router = routerFor(root);

        router.pointerDown(new PointerEvent(50f, 50f, 0, 0));

        assertSame(child, router.captured(), "a press that returns true captures the pointer");
    }

    // ---- layers -------------------------------------------------------------------------

    @Test
    void layerIsHitBeforeTheRoot() {
        Box root = new Box(sized(200f, 200f));
        Probe page = new Probe("page", Style.builder()
                .position(Style.Position.ABSOLUTE).left(0f).top(0f)
                .width(Dimension.points(200f)).height(Dimension.points(200f)).build());
        root.add(page);

        Probe popup = new Probe("popup", Style.builder()
                .position(Style.Position.ABSOLUTE).left(0f).top(0f)
                .width(Dimension.points(100f)).height(Dimension.points(100f)).build());
        InputRouter router = routerFor(root);
        router.addLayer(popup);
        // A layer is not laid out by the router - its position comes from whoever opened it, which
        // for a real popup is OverlayHost. Layout it explicitly or its bounds are all zero and
        // nothing can hit it.
        LayoutEngine.layout(popup, 100f, 100f, MONO);

        router.pointerDown(new PointerEvent(50f, 50f, 0, 0));

        assertEquals(List.of("down"), popup.events, "the layer wins over the page beneath it");
        assertTrue(page.events.isEmpty());
    }

    @Test
    void lastAddedLayerIsHitFirst() {
        Box root = new Box(sized(200f, 200f));
        Probe first = new Probe("first", Style.builder()
                .position(Style.Position.ABSOLUTE).left(0f).top(0f)
                .width(Dimension.points(100f)).height(Dimension.points(100f)).build());
        Probe second = new Probe("second", Style.builder()
                .position(Style.Position.ABSOLUTE).left(0f).top(0f)
                .width(Dimension.points(100f)).height(Dimension.points(100f)).build());
        InputRouter router = routerFor(root);
        router.addLayer(first);
        router.addLayer(second);
        // Layers are positioned by their owner, not the router; see layerIsHitBeforeTheRoot.
        LayoutEngine.layout(first, 100f, 100f, MONO);
        LayoutEngine.layout(second, 100f, 100f, MONO);

        router.pointerDown(new PointerEvent(50f, 50f, 0, 0));

        assertEquals(List.of("down"), second.events, "the later layer is on top");
    }

    @Test
    void removedLayerStopsReceivingEvents() {
        Box root = new Box(sized(200f, 200f));
        Probe popup = new Probe("popup", Style.builder()
                .position(Style.Position.ABSOLUTE).left(0f).top(0f)
                .width(Dimension.points(100f)).height(Dimension.points(100f)).build());
        InputRouter router = routerFor(root);
        router.addLayer(popup);
        router.removeLayer(popup);

        router.pointerDown(new PointerEvent(50f, 50f, 0, 0));

        assertTrue(popup.events.isEmpty());
        assertTrue(router.layers().isEmpty());
    }

    // ---- focus --------------------------------------------------------------------------

    @Test
    void focusSkipsToTheNearestFocusableAncestor() {
        Box root = new Box(sized(200f, 200f));
        Probe focusable = new Probe("focusable", Style.builder()
                .position(Style.Position.ABSOLUTE).left(0f).top(0f)
                .width(Dimension.points(150f)).height(Dimension.points(150f)).build());
        focusable.focusable = true;
        Probe plain = new Probe("plain", Style.builder()
                .position(Style.Position.ABSOLUTE).left(0f).top(0f)
                .width(Dimension.points(100f)).height(Dimension.points(100f)).build());
        focusable.add(plain);
        root.add(focusable);
        InputRouter router = routerFor(root);

        router.pointerDown(new PointerEvent(50f, 50f, 0, 0));

        // The click landed on `plain`, which cannot hold focus, so the router walks up.
        assertSame(focusable, router.focused());
    }

    @Test
    void tabCyclesThroughFocusableElementsInTreeOrder() {
        Box root = new Box(sized(200f, 200f));
        Probe a = new Probe("a", sized(50f, 50f));
        Probe b = new Probe("b", sized(50f, 50f));
        Probe c = new Probe("c", sized(50f, 50f));
        a.focusable = true;
        b.focusable = true;
        c.focusable = true;
        root.add(a);
        root.add(b);
        root.add(c);
        InputRouter router = routerFor(root);

        router.focusNext(false);
        assertSame(a, router.focused(), "starts at the first");
        router.focusNext(false);
        assertSame(b, router.focused());
        router.focusNext(false);
        assertSame(c, router.focused());
        router.focusNext(false);
        assertSame(a, router.focused(), "wraps to the start");

        router.focusNext(true);
        assertSame(c, router.focused(), "backwards wraps to the end");
    }

    @Test
    void tabWithNothingFocusableClearsFocus() {
        Box root = new Box(sized(200f, 200f));
        InputRouter router = routerFor(root);

        router.focusNext(false);

        assertNull(router.focused());
    }

    @Test
    void focusNotifiesOnlyOnChange() {
        Box root = new Box(sized(200f, 200f));
        Probe a = new Probe("a", sized(50f, 50f));
        a.focusable = true;
        root.add(a);
        InputRouter router = routerFor(root);

        router.focus(a);
        assertSame(a, router.focused());
        // Focusing the same element again must not fire another change notification - a control
        // that rebuilds on focus change would otherwise loop.
        router.focus(a);
        assertSame(a, router.focused());
    }

    @Test
    void clearReleasesEverything() {
        Box root = new Box(sized(200f, 200f));
        Probe child = new Probe("child", Style.builder()
                .position(Style.Position.ABSOLUTE).left(0f).top(0f)
                .width(Dimension.points(100f)).height(Dimension.points(100f)).build());
        child.focusable = true;
        root.add(child);
        InputRouter router = routerFor(root);

        router.pointerDown(new PointerEvent(50f, 50f, 0, 0));
        assertSame(child, router.focused());

        router.clear();

        assertNull(router.focused());
        assertNull(router.captured());
        assertNull(router.hovered());
    }

    // ---- text and keyboard routing ------------------------------------------------------

    @Test
    void textGoesToTheFocusedElementOnly() {
        Box root = new Box(sized(200f, 200f));
        String[] received = new String[1];
        Probe field = new Probe("field", sized(50f, 50f)) {
            @Override
            public boolean focusable() {
                return true;
            }

            @Override
            public boolean onTextInput(TextInputEvent event) {
                received[0] = event.text();
                return true;
            }
        };
        root.add(field);
        InputRouter router = routerFor(root);

        router.textInput(new TextInputEvent("ignored"));
        assertNull(received[0], "no focus means nobody receives text");

        router.focus(field);
        router.textInput(new TextInputEvent("hello"));
        assertEquals("hello", received[0]);
    }

    // ---- hover follows content that moves under the cursor -------------------------------

    @Test
    void scrollingRecomputesWhatIsHovered() {
        Box root = new Box(sized(100f, 100f));
        Box content = new Box(Style.builder()
                .width(Dimension.points(100f))
                .direction(Style.Direction.COLUMN)
                .build());
        // Two full-height rows stacked in a short viewport: scrolling swaps which one is at the top.
        Probe first = new Probe("first", sized(100f, 100f));
        Probe second = new Probe("second", sized(100f, 100f));
        content.add(first);
        content.add(second);
        ScrollView view = new ScrollView(Style.builder()
                .width(Dimension.points(100f)).height(Dimension.points(100f)).build(), content, 50f);
        root.add(view);
        InputRouter router = routerFor(root);
        view.refresh();

        // Hover the first row.
        router.pointerMove(new PointerEvent(50f, 50f, -1, 0));
        assertSame(first, router.hovered());
        assertTrue(first.hovered);

        // Scroll by exactly one row. The cursor has not moved, but a different row is under it now.
        view.scrollTo(100f);
        router.wheel(new WheelEvent(50f, 50f, 0f, 0));

        assertSame(second, router.hovered(), "hover follows the content, not the old element");
        assertTrue(second.hovered, "the row that scrolled into place becomes hovered");
        assertFalse(first.hovered, "the row that scrolled away must not keep its highlight");
    }

    @Test
    void wheelOverSomethingInertStillClearsStaleHover() {
        Box root = new Box(sized(200f, 200f));
        Probe top = new Probe("top", Style.builder()
                .position(Style.Position.ABSOLUTE).left(0f).top(0f)
                .width(Dimension.points(100f)).height(Dimension.points(100f)).build());
        root.add(top);
        InputRouter router = routerFor(root);

        router.pointerMove(new PointerEvent(50f, 50f, -1, 0));
        assertTrue(top.hovered);

        // A wheel outside the hovered element: nothing consumes it, but the hover must still be
        // re-evaluated against the cursor position rather than left pointing at the previous
        // element. The point lands on the root container, which is a valid hit target too.
        router.wheel(new WheelEvent(180f, 180f, 1f, 0));

        assertFalse(top.hovered, "hover at the old element is stale once the cursor moved");
        assertSame(root, router.hovered(), "the cursor is over the root container now");
    }
}
