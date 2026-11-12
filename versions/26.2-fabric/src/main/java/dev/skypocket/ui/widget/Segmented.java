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

/** Mutually exclusive segmented choice with an animated selection indicator. */
public final class Segmented extends Control {
    private static final float GAP = 4f;
    private static final float INNER = 4f;
    private static final long ANIM_NANOS = 140_000_000L;

    private final List<String> options;
    private final IntSupplier selected;
    private final IntConsumer onChange;
    private int target;
    private int pendingIndex = -1;
    private float position;
    private float animFrom;
    private long animStart;
    private boolean animating;
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
        target = clamp(selected.getAsInt());
        position = target;
    }

    @Override
    protected void activate() {
        startTo((target + 1) % options.size());
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
                startTo(index);
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
            startTo(clamp(target + delta));
            return true;
        }
        return super.onKeyDown(event);
    }

    @Override
    public void paint(UiPainter painter) {
        int current = clamp(selected.getAsInt());
        if (!animating && current != target) {
            target = current;
            position = current;
        }
        advance();

        painter.surface(bounds(), palette.radius(), palette.field(), 0);
        paintFocus(painter);
        int count = options.size();
        float innerW = bounds().width() - INNER * 2f - GAP * (count - 1);
        float segW = Math.max(1f, innerW / count);
        float segH = bounds().height() - INNER * 2f;
        // The accent is one continuous pill, so it can slide through the gap between segments.
        float accentX = bounds().x() + INNER + position * (segW + GAP);
        painter.fillRounded(new Rect(accentX, bounds().y() + INNER, segW, segH),
                palette.radius(), palette.accent());

        for (int i = 0; i < count; i++) {
            float x = bounds().x() + INNER + i * (segW + GAP);
            Rect seg = new Rect(x, bounds().y() + INNER, segW, segH);
            String text = options.get(i);
            float tx = seg.x() + (seg.width() - painter.textWidth(text)) / 2f;
            float ty = seg.y() + (seg.height() - painter.lineHeight()) / 2f;
            painter.text(text, tx, ty, i == target ? palette.onAccent() : palette.muted());
        }
    }

    private void startTo(int index) {
        int next = clamp(index);
        if (next == target) {
            return;
        }
        target = next;
        pendingIndex = next;
        animFrom = position;
        animStart = System.nanoTime();
        animating = true;
    }

    private void advance() {
        if (!animating) {
            return;
        }
        float t = (System.nanoTime() - animStart) / (float) ANIM_NANOS;
        if (t >= 1f) {
            position = target;
            animating = false;
            int callback = pendingIndex;
            pendingIndex = -1;
            if (callback >= 0) {
                onChange.accept(callback);
            }
            return;
        }
        float eased = 1f - (1f - t) * (1f - t);
        position = animFrom + (target - animFrom) * eased;
    }

    private int clamp(int index) {
        return Math.max(0, Math.min(options.size() - 1, index));
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
        return clamp(index);
    }
}
