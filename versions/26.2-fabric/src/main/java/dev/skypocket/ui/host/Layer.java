package dev.skypocket.ui.host;

import dev.skypocket.ui.Rect;
import dev.skypocket.ui.UiPainter;
import dev.skypocket.ui.input.KeyEvent;
import dev.skypocket.ui.input.PreeditEvent;

/**
 * One thing a host can show: a page, a floating overlay, a bar of controls.
 *
 * <p>Before this existed, the only way for a screen to talk to such a thing was to hold a field of
 * that thing's concrete type and call its own methods. {@code OneClickScreen} held five of them and
 * ended up with sixty-odd calls spread across nine input methods - and no two of those methods routed
 * the same way, which is how its hover forwarding got missed for a long time.
 *
 * <p>An interface rather than a base class, because the things a host shows are not one kind of
 * thing. A v2 page owns a tree and a cache; a floating card owns a tree and its own router; the top
 * bar owns a tree, a router and a rasterisation scale; the HUD position editor's elements are not a
 * tree at all. A base class would have had to be {@code UiPage}, and only one of those fits.
 *
 * <p>Coordinates arrive in <em>layer-local</em> design units. The host is responsible for turning a
 * screen point into that space - it is the only thing that knows the layer's origin and scale - so an
 * implementation never subtracts an offset of its own.
 */
public interface Layer {
    /**
     * Where this layer sits on screen, in the host's coordinate space.
     *
     * <p>Used both for hit testing and for the caller to decide whether a point is in the layer at
     * all. Returning a zero-sized rect means "not visible right now".
     */
    Rect boundsOnScreen();

    /** Whether this layer is showing and should receive input. */
    boolean isOpen();

    /** Design units to screen units inside this layer. */
    default float scale() {
        return 1f;
    }

    /**
     * Paints. The host has already applied its own transform, so the layer paints at its origin.
     *
     * <p>Takes the kernel painter: a layer does not need to know that Minecraft exists. The one
     * thing that does need the MC type - a host applying a pose - is already on the other side of
     * this boundary.
     */
    void paint(UiPainter painter);

    // ---- input, in layer-local coordinates --------------------------------------------

    /** @return whether the event was consumed. */
    boolean pointerDown(float x, float y, int button, int modifiers);

    /** @return whether the event was consumed. */
    boolean pointerMove(float x, float y, int modifiers);

    /** @return whether the event was consumed. */
    boolean pointerUp(float x, float y, int button, int modifiers);

    /** @return whether the event was consumed. */
    boolean wheel(float x, float y, float delta, int modifiers);

    /** @return whether the event was consumed. */
    boolean keyDown(KeyEvent event);

    /** @return whether the event was consumed. */
    boolean keyUp(KeyEvent event);

    /** @return whether the event was consumed. */
    boolean textInput(String text);

    /** @return whether the event was consumed. */
    boolean preedit(PreeditEvent event);

    /** True when a control inside this layer owns the keyboard. */
    boolean hasFocus();

    /** Releases keyboard focus held by this layer. */
    void clearFocus();

    /** True while a press that started inside this layer is still held. */
    boolean hasPointerCapture();
}
