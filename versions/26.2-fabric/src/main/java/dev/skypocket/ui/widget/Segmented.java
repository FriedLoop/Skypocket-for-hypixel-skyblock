package dev.skypocket.ui.widget;

import dev.skypocket.ui.Rect;
import dev.skypocket.ui.Style;
import dev.skypocket.ui.UiPainter;
import dev.skypocket.ui.input.KeyEvent;
import dev.skypocket.ui.input.Keys;
import dev.skypocket.ui.input.PointerEvent;

import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** Mutually exclusive segmented choice; used for the stats display style. */
public final class Segmented extends Control {
    private static final float GAP = 2f;
    private static final float INNER = 2f;

    private final List<String> options;
    private final IntSupplier selected;
    private final IntConsumer onChange;
    private int pressedIndex = -1;

    public Segmented(List<String> options, IntSupplier selected, IntConsumer onChange,
                     Style style, WidgetPalette palette) {
        super(style, palette);
        this.options = List.copyOf(options);
        this.selected = selected;
        this.onChange = Objects.requireNonNull(onChange);
        if (this.options.isEmpty()) {
            throw new IllegalArgumentException("segmented control needs at least one option");
        }
    }

    @Override
    protected void activate() {
        // Pointer clicks choose an index; keyboard activation advances to the next option.
        int next = (selected.getAsInt() + 1) % options.size();
        onChange.accept(next);
    }

    @Override
    public boolean onPointerDown(PointerEvent event) {
        pressedIndex = indexAt(event.x() - ancestorOffsetX());
        pressed = pressedIndex >= 0;
        return pressed;
    }

    @Override
    public boolean onPointerUp(PointerEvent event) {
        if (pressed) {
            int index = indexAt(event.x() - ancestorOffsetX());
            if (index >= 0) {
                onChange.accept(index);
            }
        }
        pressed = false;
        pressedIndex = -1;
        return true;
    }

    @Override
    public boolean onKeyDown(KeyEvent event) {
        if (event.key() == Keys.LEFT || event.key() == Keys.RIGHT) {
            int delta = event.key() == Keys.LEFT ? -1 : 1;
            int next = Math.max(0, Math.min(options.size() - 1, selected.getAsInt() + delta));
            if (next != selected.getAsInt()) {
                onChange.accept(next);
            }
            return true;
        }
        return super.onKeyDown(event);
    }

    @Override
    public void paint(UiPainter painter) {
        painter.surface(bounds(), palette.radius(), palette.field(), 0);
        paintFocus(painter);
        int current = Math.max(0, Math.min(options.size() - 1, selected.getAsInt()));
        int count = options.size();
        float innerW = bounds().width() - INNER * 2f - GAP * (count - 1);
        float segW = Math.max(1f, innerW / count);
        for (int i = 0; i < count; i++) {
            float x = bounds().x() + INNER + i * (segW + GAP);
            Rect seg = new Rect(x, bounds().y() + INNER, segW,
                    bounds().height() - INNER * 2f);
            boolean active = i == current;
            if (active) {
                painter.fillRounded(seg, 3f, palette.accent());
            } else if (i == pressedIndex) {
                painter.fillRounded(seg, 3f, palette.hover());
            }
            String text = options.get(i);
            float tx = seg.x() + (seg.width() - painter.textWidth(text)) / 2f;
            float ty = seg.y() + (seg.height() - painter.lineHeight()) / 2f;
            painter.text(text, tx, ty, active ? 0xFFFFFFFF : palette.muted());
        }
    }

    private int indexAt(float localX) {
        int count = options.size();
        float innerW = bounds().width() - INNER * 2f - GAP * (count - 1);
        float segW = Math.max(1f, innerW / count);
        if (localX < bounds().x() + INNER || localX >= bounds().x() + INNER
                + count * segW + (count - 1) * GAP) {
            return -1;
        }
        int index = (int) ((localX - bounds().x() - INNER) / (segW + GAP));
        return Math.max(0, Math.min(count - 1, index));
    }
}
