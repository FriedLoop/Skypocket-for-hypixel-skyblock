package dev.skypocket.gui;

import dev.skypocket.ui.Rect;
import dev.skypocket.ui.UiPainter;
import dev.skypocket.ui.host.Layer;
import dev.skypocket.ui.input.KeyEvent;
import dev.skypocket.ui.input.PreeditEvent;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Adapts one of the ClickGUI's panes to {@link Layer}.
 *
 * <p>The panes are not layers themselves and should not become layers: {@code ModulesPage} has two
 * instances with different content, {@code SettingsPage} is shared with other hosts, and
 * {@code DevDrawer} predates all of this. An adapter keeps the knowledge of "where is this pane, and
 * how do its coordinates work" in one place instead of in nine input handlers.
 *
 * <p>Everything is supplied through functions rather than captured values, because the panes are
 * created lazily and replaced when the theme or the layout changes. A captured reference would go
 * stale on the first theme edit.
 */
final class PaneLayer implements Layer {
    private final Supplier<Rect> bounds;
    private final BooleanSupplier open;
    private final Pane pane;
    /** Optional non-rectangular hit test; null means "use the bounding box". */
    private final java.util.function.BiPredicate<Float, Float> hitTest;

    /** The operations a pane has to offer. Implemented per pane kind, not per instance. */
    interface Pane {
        void paint(UiPainter painter);

        boolean pointerDown(float x, float y, int button, int modifiers);

        boolean pointerMove(float x, float y, int modifiers);

        boolean pointerUp(float x, float y, int button, int modifiers);

        boolean wheel(float x, float y, float delta, int modifiers);

        boolean keyDown(KeyEvent event);

        boolean keyUp(KeyEvent event);

        boolean textInput(String text);

        boolean preedit(PreeditEvent event);

        boolean hasFocus();

        void clearFocus();

        boolean hasPointerCapture();

        float scale();
    }

    /**
     * @param bounds where the pane sits, in the host's design space; called per event and per frame
     * @param open   whether the pane is showing; called per event and per frame
     * @param pane   the pane itself
     */
    PaneLayer(Supplier<Rect> bounds, BooleanSupplier open, Pane pane) {
        this(bounds, open, pane, null);
    }

    /**
     * @param hitTest overrides the rectangular hit test when the pane's interactive area is not a
     *                rectangle; null means "use {@code bounds}"
     */
    PaneLayer(Supplier<Rect> bounds, BooleanSupplier open, Pane pane,
              java.util.function.BiPredicate<Float, Float> hitTest) {
        this.bounds = bounds;
        this.open = open;
        this.pane = pane;
        this.hitTest = hitTest;
    }

    @Override
    public boolean contains(float x, float y) {
        if (hitTest != null) {
            return hitTest.test(x, y);
        }
        return Layer.super.contains(x, y);
    }

    @Override
    public Rect boundsOnScreen() {
        return bounds.get();
    }

    @Override
    public boolean isOpen() {
        return open.getAsBoolean();
    }

    @Override
    public float scale() {
        return pane.scale();
    }

    @Override
    public void paint(UiPainter painter) {
        pane.paint(painter);
    }

    @Override
    public boolean pointerDown(float x, float y, int button, int modifiers) {
        return pane.pointerDown(x, y, button, modifiers);
    }

    @Override
    public boolean pointerMove(float x, float y, int modifiers) {
        return pane.pointerMove(x, y, modifiers);
    }

    @Override
    public boolean pointerUp(float x, float y, int button, int modifiers) {
        return pane.pointerUp(x, y, button, modifiers);
    }

    @Override
    public boolean wheel(float x, float y, float delta, int modifiers) {
        return pane.wheel(x, y, delta, modifiers);
    }

    @Override
    public boolean keyDown(KeyEvent event) {
        return pane.keyDown(event);
    }

    @Override
    public boolean keyUp(KeyEvent event) {
        return pane.keyUp(event);
    }

    @Override
    public boolean textInput(String text) {
        return pane.textInput(text);
    }

    @Override
    public boolean preedit(PreeditEvent event) {
        return pane.preedit(event);
    }

    @Override
    public boolean hasFocus() {
        return pane.hasFocus();
    }

    @Override
    public void clearFocus() {
        pane.clearFocus();
    }

    @Override
    public boolean hasPointerCapture() {
        return pane.hasPointerCapture();
    }

    /** Convenience: a pane that fills a fixed rect and never moves. */
    static Supplier<Rect> fixed(float x, float y, float w, float h) {
        Rect rect = new Rect(x, y, w, h);
        return () -> rect;
    }

    /** Convenience: a pane whose rect is recomputed by a function. */
    static Supplier<Rect> of(Supplier<Rect> f) {
        return f;
    }

    /** Empty list helper for callers building their layer order. */
    static List<Layer> none() {
        return List.of();
    }
}
