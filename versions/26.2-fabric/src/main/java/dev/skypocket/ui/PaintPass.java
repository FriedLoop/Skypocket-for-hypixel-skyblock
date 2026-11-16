package dev.skypocket.ui;

import dev.skypocket.ui.mc.McUiPainter;
import dev.skypocket.ui.widget.Control;

import java.util.HashSet;
import java.util.Set;

/**
 * The one place the retained tree is drawn.
 *
 * <p>This is lifted from {@code SettingsPage}, which was the only page that had a correct
 * implementation. Keeping it there meant every other page had to either copy those ninety lines or
 * write its own - and the copies drifted: the drawer built during the last migration attempt had a
 * subtly different recursion that double-shifted elements and drew control text in the wrong place.
 *
 * <h2>Two layers</h2>
 *
 * <p>A page is drawn twice over: a static layer that can be cached as one texture, and a dynamic
 * layer for the handful of controls whose appearance changes without the config changing - the
 * focused field's caret, a pressed button, an open popup. The framework splits them because they have
 * different cache lifetimes, not because they are different code.
 *
 * <p>Static paint happens with {@link Control#neutralPaint} on: hover and focus are suppressed so
 * the baked texture does not contain one frame's transient state. The dynamic pass then paints only
 * the controls that need it, with {@link Control#overlayPaint} on so an opaque row covers the cached
 * text underneath instead of letting it ghost through.
 */
public final class PaintPass {
    private PaintPass() {
    }

    /**
     * Paints a subtree for the static (cacheable) layer.
     *
     * <p>Callers that bake a texture must wrap this in {@link Control#neutralPaint} - {@link UiPage}
     * does it, but a hand-rolled bake has to as well or the texture captures a hover highlight.
     */
    public static void paintStatic(UiPainter painter, UiElement element) {
        paintStatic(painter, element, null);
    }

    private static void paintStatic(UiPainter painter, UiElement element, ScrollView viewport) {
        if (!element.visible()) {
            return;
        }
        // Clip test only applies inside a scroll viewport: an element scrolled out of view is skipped
        // wholesale rather than being clipped per draw call.
        if (viewport != null && !intersects(element, viewport)) {
            return;
        }
        if (!(painter instanceof McUiPainter mc)) {
            // Without the MC painter there is no pose stack to translate, so subtree offsets cannot
            // be applied; painting anyway would place children wrongly.
            return;
        }
        var pose = mc.graphics().pose();
        pose.pushMatrix();
        // Safe for ordinary flow children: LayoutEngine never writes an offset, so their value is
        // (0,0) and this is a no-op. It matters for the two cases that do set one - a ScrollView's
        // content and a popup - which is exactly why it is applied here rather than at the root.
        pose.translate(element.offsetX(), element.offsetY());

        ScrollView nextViewport = element instanceof ScrollView view ? view : viewport;
        if (element instanceof ScrollView view) {
            painter.pushClip(view.bounds());
        }
        paintSelf(painter, element);
        for (UiElement child : element.children()) {
            paintStatic(painter, child, nextViewport);
        }
        if (element instanceof ScrollView) {
            painter.popClip();
        }
        pose.popMatrix();
    }

    /**
     * Paints only the controls whose appearance is live, on top of an already-painted static layer.
     *
     * <p>Descends only into branches that contain such a control, so a page with a thousand rows pays
     * for the two that are animating.
     */
    public static void paintDynamic(McUiPainter painter, UiElement root, Set<UiElement> dynamic) {
        if (dynamic.isEmpty()) {
            return;
        }
        paintDynamicNode(painter, root, dynamic);
    }

    private static boolean paintDynamicNode(McUiPainter painter, UiElement element,
                                            Set<UiElement> dynamic) {
        if (!element.visible()) {
            return false;
        }
        boolean any = dynamic.contains(element);
        for (UiElement child : element.children()) {
            any |= containsDynamic(child, dynamic);
        }
        if (!any) {
            return false;
        }
        var pose = painter.graphics().pose();
        pose.pushMatrix();
        pose.translate(element.offsetX(), element.offsetY());
        boolean scroll = element instanceof ScrollView;
        if (scroll) {
            painter.pushClip(element.bounds());
        }
        if (dynamic.contains(element) && element instanceof Control control) {
            control.paint(painter);
        }
        for (UiElement child : element.children()) {
            paintDynamicNode(painter, child, dynamic);
        }
        if (scroll) {
            painter.popClip();
        }
        pose.popMatrix();
        return true;
    }

    /**
     * Collects the controls that need the dynamic pass.
     *
     * <p>The conditions are all "the control looks different while nothing in the config changed":
     * focused, pressed, mid key-flash, or showing a popup. A control that opts out of the cache with
     * {@link Control#dynamicOverlay()} is included too - that flag exists for controls that cannot be
     * cached at all.
     */
    public static Set<UiElement> collectDynamic(UiElement root) {
        Set<UiElement> dynamic = new HashSet<>();
        collectDynamic(root, dynamic);
        return dynamic;
    }

    private static void collectDynamic(UiElement element, Set<UiElement> dynamic) {
        if (element instanceof Control control
                && (control.dynamicOverlay() || control.isFocused() || control.isPressed()
                || control.flashActive()
                || (control instanceof dev.skypocket.ui.widget.ColorField color && color.isOpen())
                || (control instanceof dev.skypocket.ui.widget.Dropdown dropdown
                    && dropdown.isOpen()))) {
            dynamic.add(control);
        }
        for (UiElement child : element.children()) {
            collectDynamic(child, dynamic);
        }
    }

    private static boolean containsDynamic(UiElement element, Set<UiElement> dynamic) {
        if (dynamic.contains(element)) {
            return true;
        }
        for (UiElement child : element.children()) {
            if (containsDynamic(child, dynamic)) {
                return true;
            }
        }
        return false;
    }

    private static void paintSelf(UiPainter painter, UiElement element) {
        if (element instanceof Painted painted) {
            painted.paint(painter);
        } else if (element instanceof Text text) {
            // Explicit weights matter: headers are SemiBold and descriptions Regular even though
            // ordinary control text defaults to Medium.
            painter.textWeighted(text.value(), text.bounds().x(), text.bounds().y(),
                    text.color(), text.fontScale(), text.weight());
        }
    }

    /** Whether an element's box overlaps the viewport's; used to skip scrolled-out rows. */
    private static boolean intersects(UiElement element, ScrollView viewport) {
        Rect view = viewport.bounds();
        Rect box = element.bounds();
        return box.right() > view.x() && box.x() < view.right()
                && box.bottom() > view.y() && box.y() < view.bottom();
    }
}
