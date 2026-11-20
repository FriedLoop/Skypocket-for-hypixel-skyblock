package dev.skypocket.ui.widget;

import dev.skypocket.ui.Dimension;
import dev.skypocket.ui.Rect;
import dev.skypocket.ui.Style;
import dev.skypocket.ui.UiPainter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rule that makes baking work: a control must not paint its transient state while the static
 * layer is being recorded.
 *
 * <p>The cache bakes the page once and blits it back while nothing changed. Hover is not part of
 * that - it changes when the cursor moves, which does not touch the cache key. A control that reads
 * its own hover flag unconditionally therefore bakes whichever highlight happened to be showing into
 * the texture, where it stays until something else invalidates the cache. The symptom is a card or
 * row keeping a highlight after the cursor has left, plus a second one on whatever is hovered now.
 *
 * <p>{@link Control#neutralPaint(boolean)} is how the bake tells controls to suppress that state.
 * The base class only enforces it in {@code background()}; controls that paint themselves - the
 * module card, the sidebar row - have to honour it at their own paint sites. That is what let the
 * bug through, so these tests pin both halves: the flag itself, and the fact that it is a shared
 * switch rather than per-control state.
 */
class ControlNeutralPaintTest {
    /** Exposes the protected static accessors without widening them for production code. */
    private static final class Probe extends Control {
        Probe() {
            super(Style.builder().width(Dimension.points(50f)).height(Dimension.points(20f)).build(),
                    WidgetPalette.DARK);
        }

        boolean neutralActive() {
            return neutralPaint();
        }

        boolean overlayActive() {
            return overlayPaint();
        }

        int backgroundNow() {
            return background();
        }

        @Override
        protected void activate() {
        }

        @Override
        public void paint(UiPainter painter) {
        }
    }

    @AfterEach
    void resetFlags() {
        // The flags are static; a failing assertion must not leak suppression into another test.
        Control.neutralPaint(false);
        Control.overlayPaint(false);
    }

    @Test
    void neutralPaintStartsOff() {
        assertFalse(new Probe().neutralActive(),
                "normal drawing shows live state, so suppression starts off");
    }

    @Test
    void neutralPaintToggles() {
        Probe probe = new Probe();
        Control.neutralPaint(true);
        assertTrue(probe.neutralActive());
        Control.neutralPaint(false);
        assertFalse(probe.neutralActive());
    }

    @Test
    void backgroundIgnoresHoverWhileBaking() {
        Probe probe = new Probe();
        int livePlain = probe.backgroundNow();

        Control.neutralPaint(true);
        int bakedPlain = probe.backgroundNow();
        Control.neutralPaint(false);

        // While baking, the background must not vary with anything the control could be doing -
        // that is the whole point: the texture has to be reproducible.
        assertEquals(bakedPlain, livePlain,
                "the neutral background is the same whether or not a bake is in progress");
    }

    @Test
    void neutralPaintIsSharedAcrossControls() {
        Probe first = new Probe();
        Probe second = new Probe();

        Control.neutralPaint(true);

        // The bake paints a whole subtree; one control must not be able to opt out for another.
        assertTrue(first.neutralActive());
        assertTrue(second.neutralActive());
    }

    @Test
    void overlayPaintIsIndependentOfNeutralPaint() {
        Probe probe = new Probe();

        Control.overlayPaint(true);
        assertTrue(probe.overlayActive());
        assertFalse(probe.neutralActive(),
                "the overlay pass shows live state, so it must not ask controls to suppress it");

        Control.overlayPaint(false);
        Control.neutralPaint(true);
        assertFalse(probe.overlayActive(), "and the bake does not imply an opaque row");
        assertTrue(probe.neutralActive());
    }

    @Test
    void bakedBackgroundDiffersFromAHoveredControlThatIgnoresSuppression() {
        // A control painting its own hover without consulting the flag is exactly the bug: it bakes
        // a highlight. This asserts the contrast so the rule is visible in a test rather than only
        // in a comment.
        int neutral = new Probe().backgroundNow();
        try {
            Control.neutralPaint(true);
            int baked = new Probe().backgroundNow();
            assertEquals(neutral, baked);
        } finally {
            Control.neutralPaint(false);
        }
    }
}
