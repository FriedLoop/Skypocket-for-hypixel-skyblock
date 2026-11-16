package dev.skypocket.ui;

import dev.skypocket.ui.input.InputRouter;
import dev.skypocket.ui.mc.McUiPainter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The floating elements above a page: their registration and their painting.
 *
 * <p>{@link InputRouter} already routes input to layers before the page, and {@link PaintPass}
 * already knows how to draw a tree. What was missing is the bookkeeping between them - every host
 * kept its own list and ran its own paint loop, and {@code SettingsPage} ended up with three
 * near-identical loops (colour popups, dropdown popups, custom-row popups) because each feature added
 * one instead of sharing.
 *
 * <h2>Positioning is not this class's job</h2>
 *
 * <p>Deliberately. An overlay's position depends on what it is anchored to, and the two real cases
 * differ completely: a dropdown opens below or above the field that opened it and flips at the
 * viewport edge, while a modal is centred on the page. {@code ColorField.layoutPopup} already
 * computes that anchor - including snapping to design pixels so a live popup does not sit a pixel off
 * its cached row - and a generic host that re-derived positions would have to reproduce every one of
 * those rules.
 *
 * <p>So an overlay lays itself out and calls {@link UiElement#offset} with the result; this class
 * only makes sure it is registered for input, laid out, and painted in order.
 */
public final class OverlayHost {
    private final List<UiElement> overlays = new ArrayList<>();
    private InputRouter input;

    /** Binds the router whose layer list this host mirrors. */
    public void attach(InputRouter router) {
        this.input = router;
        // Re-register anything added before the router existed: a page builds its children before it
        // builds the router, and the first overlay can be added in between.
        for (UiElement overlay : overlays) {
            if (!router.layers().contains(overlay)) {
                router.addLayer(overlay);
            }
        }
    }

    /**
     * Registers an element that floats above the page.
     *
     * <p>Idempotent, because a host may re-register on every rebuild and a duplicate entry would
     * make the layer hit-test twice and paint twice (the second paint compositing the first).
     */
    public void add(UiElement overlay) {
        if (overlay == null || overlays.contains(overlay)) {
            return;
        }
        overlays.add(overlay);
        if (input != null && !input.layers().contains(overlay)) {
            input.addLayer(overlay);
        }
    }

    /** Removes an overlay from this host and from the router's layer list. */
    public void remove(UiElement overlay) {
        if (overlay == null) {
            return;
        }
        overlays.remove(overlay);
        if (input != null) {
            input.removeLayer(overlay);
        }
    }

    /** Whether anything floats; a host can skip the paint pass entirely when nothing does. */
    public boolean isEmpty() {
        return overlays.isEmpty();
    }

    /** Everything registered, bottom first. */
    public List<UiElement> overlays() {
        return Collections.unmodifiableList(overlays);
    }

    /**
     * Paints every visible overlay, bottom first.
     *
     * <p>Overlays are drawn after the page's own content and without the page cache: they are the
     * things the cache must not freeze, which is the whole reason they are registered here.
     */
    public void paint(McUiPainter painter) {
        if (overlays.isEmpty()) {
            return;
        }
        for (UiElement overlay : overlays) {
            if (overlay.visible()) {
                PaintPass.paintStatic(painter, overlay);
            }
        }
    }
}
