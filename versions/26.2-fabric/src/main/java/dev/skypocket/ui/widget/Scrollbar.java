package dev.skypocket.ui.widget;

import dev.skypocket.ui.Rect;
import dev.skypocket.ui.ScrollView;
import dev.skypocket.ui.Style;
import dev.skypocket.ui.UiPainter;
import dev.skypocket.ui.input.PointerEvent;

/** Vertical scrollbar that drags the owning ScrollView. */
public final class Scrollbar extends Control {
    private final ScrollView owner;
    private boolean dragging;
    private float dragOffset;

    public Scrollbar(ScrollView owner) {
        this(owner, WidgetPalette.DARK);
    }

    public Scrollbar(ScrollView owner, WidgetPalette palette) {
        super(Style.builder()
                        .position(Style.Position.ABSOLUTE)
                        .right(0f)
                        .top(4f)
                        .bottom(4f)
                        .width(6f)
                        .build(),
                palette);
        this.owner = owner;
    }

    @Override
    public boolean focusable() {
        return false;
    }

    @Override
    protected void activate() {
        // No keyboard activation.
    }

    @Override
    public boolean onPointerDown(PointerEvent event) {
        if (owner.maxScroll() <= 0f) {
            return false;
        }
        float thumbY = thumbY();
        float thumbHeight = thumbHeight();
        dragging = true;
        if (event.y() >= thumbY && event.y() < thumbY + thumbHeight) {
            dragOffset = event.y() - thumbY;
        } else {
            dragOffset = thumbHeight / 2f;
            updateFromPointer(event.y());
        }
        return true;
    }

    @Override
    public boolean onPointerMove(PointerEvent event) {
        if (!dragging) {
            return false;
        }
        updateFromPointer(event.y());
        return true;
    }

    @Override
    public boolean onPointerUp(PointerEvent event) {
        dragging = false;
        return true;
    }

    private void updateFromPointer(float pointerY) {
        float max = owner.maxScroll();
        float travel = bounds().height() - thumbHeight();
        if (max <= 0f || travel <= 0f) {
            return;
        }
        float localY = pointerY - ancestorOffsetY();
        float y = localY - bounds().y() - dragOffset;
        float t = Math.max(0f, Math.min(1f, y / travel));
        owner.scrollTo(t * max);
    }

    private float thumbHeight() {
        float max = owner.maxScroll();
        float viewport = Math.max(1f, owner.bounds().height());
        return Math.min(bounds().height(), Math.max(16f,
                bounds().height() * viewport / (viewport + max)));
    }

    private float thumbY() {
        float max = owner.maxScroll();
        float travel = bounds().height() - thumbHeight();
        return bounds().y() + (max <= 0f ? 0f : travel * owner.scrollY() / max);
    }

    @Override
    public void paint(UiPainter painter) {
        if (owner.maxScroll() <= 0f) {
            return;
        }
        painter.fillRounded(bounds(), 2f, palette.scrollbarTrack());
        painter.fillRounded(new Rect(bounds().x() - 1f, thumbY(), bounds().width() + 2f, thumbHeight()),
                2f, palette.scrollbarThumb());
    }
}
