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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hover state when content moves under a stationary cursor.
 *
 * <p>Reproduces the reported sequence precisely: hover a card, scroll a little so the card shifts
 * but is still under the cursor, then move the cursor away. The old highlight must clear.
 */
class HoverScrollTest {
    private static final TextMeasurer MONO = (text, maxWidth) -> new Size(text.length() * 6f, 10f);

    private static final class HoverProbe extends Box {
        final String name;
        boolean hovered;

        HoverProbe(String name, Style style) {
            super(style);
            this.name = name;
        }

        @Override
        public void onHoverChanged(boolean value) {
            hovered = value;
        }

        @Override
        public boolean acceptsPointer() {
            return true;
        }
    }

    private static Style sized(float w, float h) {
        return Style.builder().width(Dimension.points(w)).height(Dimension.points(h)).build();
    }

    /** A viewport showing a column of rows, with a real ScrollView so offsets behave as in game. */
    private record Scene(Box root, ScrollView view, Box content, HoverProbe first, HoverProbe second) {
    }

    private static Scene scene() {
        Box root = new Box(sized(100f, 100f));
        Box content = new Box(Style.builder()
                .width(Dimension.points(100f))
                .direction(Style.Direction.COLUMN)
                .build());
        HoverProbe first = new HoverProbe("first", sized(100f, 60f));
        HoverProbe second = new HoverProbe("second", sized(100f, 60f));
        HoverProbe third = new HoverProbe("third", sized(100f, 60f));
        content.add(first);
        content.add(second);
        content.add(third);
        ScrollView view = new ScrollView(sized(100f, 100f), content, 20f);
        root.add(view);
        LayoutEngine.layout(root, 100f, 100f, MONO);
        view.refresh();
        return new Scene(root, view, content, first, second);
    }

    @Test
    void hoverClearsAfterSmallScrollThatKeepsTheCardUnderTheCursor() {
        Scene s = scene();
        InputRouter router = new InputRouter(s.root());

        // Hover the first row, near its top so it stays under the cursor after a small scroll.
        router.pointerMove(new PointerEvent(50f, 10f, -1, 0));
        assertSame(s.first(), router.hovered(), "cursor starts over the first row");
        assertTrue(s.first().hovered);

        // Scroll by less than a row: the cursor is still inside the first row, which has merely
        // shifted upward. The hovered element is unchanged.
        s.view().scrollTo(15f);
        router.wheel(new WheelEvent(50f, 10f, 0f, 0));
        assertSame(s.first(), router.hovered(), "still the same element under the cursor");
        assertTrue(s.first().hovered);

        // Now move the cursor down, past the first row into the second.
        router.pointerMove(new PointerEvent(50f, 80f, -1, 0));

        assertFalse(s.first().hovered, "the first row must lose its highlight");
        assertTrue(s.second().hovered, "the second row gains one");
        assertSame(s.second(), router.hovered());
    }

    @Test
    void hoverClearsAfterScrollThenMoveOutOfTheListEntirely() {
        Scene s = scene();
        InputRouter router = new InputRouter(s.root());

        router.pointerMove(new PointerEvent(50f, 10f, -1, 0));
        assertTrue(s.first().hovered);

        s.view().scrollTo(15f);
        router.wheel(new WheelEvent(50f, 10f, 0f, 0));

        // Move to a point outside the scroll viewport, onto the empty root.
        router.pointerMove(new PointerEvent(95f, 95f, -1, 0));

        assertFalse(s.first().hovered, "leaving the list must clear the row highlight");
    }

    @Test
    void repeatedSmallScrollsDoNotAccumulateHighlights() {
        Scene s = scene();
        InputRouter router = new InputRouter(s.root());

        router.pointerMove(new PointerEvent(50f, 10f, -1, 0));
        for (int i = 0; i < 5; i++) {
            s.view().scrollTo(5f * (i + 1));
            router.wheel(new WheelEvent(50f, 10f, 0f, 0));
        }

        int highlighted = (s.first().hovered ? 1 : 0) + (s.second().hovered ? 1 : 0)
                ;
        assertTrue(highlighted <= 1, "at most one element may be highlighted, saw " + highlighted);
    }
}
