package dev.skypocket.ui;

import dev.skypocket.ui.widget.Card;
import dev.skypocket.ui.widget.Control;
import dev.skypocket.ui.widget.WidgetPalette;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which controls the live (dynamic) pass has to repaint.
 *
 * <p>This is the selection logic behind the "static bake suppresses hover, so hover must be
 * repainted live" rule. Getting it wrong produces two opposite bugs, both of which shipped at some
 * point: a control left out of the set keeps a stale baked appearance (a highlight that never
 * appears), and a control included when it should not be forces a full re-bake every frame.
 */
class PaintPassTest {
    /** A control whose visual state can be driven directly, to isolate the selection rule. */
    private static final class FakeControl extends Control {
        boolean hovered;
        boolean dynamic;
        boolean flash;

        FakeControl() {
            super(Style.builder().width(Dimension.points(50f)).height(Dimension.points(20f)).build(),
                    WidgetPalette.DARK);
        }

        @Override
        public boolean isHovered() {
            return hovered;
        }

        @Override
        public boolean isPressed() {
            return false;
        }

        @Override
        public boolean isFocused() {
            return false;
        }

        @Override
        public boolean flashActive() {
            return flash;
        }

        @Override
        public boolean dynamicOverlay() {
            return dynamic;
        }

        @Override
        protected void activate() {
        }

        @Override
        public void paint(UiPainter painter) {
        }
    }

    @Test
    void idleControlIsNotRepaintedLive() {
        Box root = new Box(Style.builder()
                .width(Dimension.points(100f)).height(Dimension.points(100f)).build());
        FakeControl control = new FakeControl();
        root.add(control);

        Set<UiElement> dynamic = PaintPass.collectDynamic(root);

        assertTrue(dynamic.isEmpty(), "an idle control is baked once and left alone");
    }

    @Test
    void hoveredControlIsRepaintedLive() {
        Box root = new Box(Style.builder()
                .width(Dimension.points(100f)).height(Dimension.points(100f)).build());
        FakeControl control = new FakeControl();
        root.add(control);
        control.hovered = true;

        Set<UiElement> dynamic = PaintPass.collectDynamic(root);

        // The static bake runs with neutralPaint on, where every Button surface declines to draw
        // hover. Leaving this out means the highlight never appears at all.
        assertTrue(dynamic.contains(control), "hover only exists if it is repainted live");
    }

    @Test
    void flashIsRepaintedLive() {
        Box root = new Box(Style.builder()
                .width(Dimension.points(100f)).height(Dimension.points(100f)).build());
        FakeControl control = new FakeControl();
        root.add(control);
        control.flash = true;

        assertTrue(PaintPass.collectDynamic(root).contains(control),
                "the keyboard-activation flash is transient and cannot be baked");
    }

    @Test
    void dynamicOverlayAlwaysRepaints() {
        Box root = new Box(Style.builder()
                .width(Dimension.points(100f)).height(Dimension.points(100f)).build());
        FakeControl control = new FakeControl();
        root.add(control);
        control.dynamic = true;

        assertTrue(PaintPass.collectDynamic(root).contains(control),
                "opt-out controls repaint every frame by definition");
    }

    @Test
    void nestedControlsAreFound() {
        Box root = new Box(Style.builder()
                .width(Dimension.points(200f)).height(Dimension.points(200f))
                .direction(Style.Direction.COLUMN).build());
        Card card = new Card(Style.builder()
                .width(Dimension.percent(100f)).height(Dimension.points(80f)).build(),
                WidgetPalette.DARK);
        Box inner = new Box(Style.builder()
                .width(Dimension.percent(100f)).height(Dimension.points(40f)).build());
        FakeControl deep = new FakeControl();
        deep.hovered = true;
        inner.add(deep);
        card.add(inner);
        root.add(card);

        Set<UiElement> dynamic = PaintPass.collectDynamic(root);

        assertTrue(dynamic.contains(deep), "the walk reaches controls at any depth");
    }

    @Test
    void onlyLiveControlsAreCollected() {
        Box root = new Box(Style.builder()
                .width(Dimension.points(200f)).height(Dimension.points(200f))
                .direction(Style.Direction.COLUMN).build());
        FakeControl idle = new FakeControl();
        FakeControl hovered = new FakeControl();
        hovered.hovered = true;
        root.add(idle);
        root.add(hovered);

        Set<UiElement> dynamic = PaintPass.collectDynamic(root);

        assertFalse(dynamic.contains(idle));
        assertTrue(dynamic.contains(hovered));
        assertTrue(dynamic.size() == 1, "exactly the one that is live");
    }
}
