package dev.skypocket.ui.input;

import dev.skypocket.ui.UiElement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Platform-neutral pointer, keyboard and focus routing for a retained UI tree.
 *
 * <p>Hit testing uses design coordinates and walks children back-to-front. Pointer-down is bubbled from
 * the hit element to its ancestors; the first element that handles it becomes the drag capture. Focus
 * follows the nearest focusable ancestor. Wheel, key and text events bubble from the focused/hovered
 * element. Scroll containers and text fields are expected to override the corresponding hooks.</p>
 */
public final class InputRouter {
    private final UiElement root;
    private final List<UiElement> layers = new ArrayList<>();
    private UiElement hovered;
    private UiElement focused;
    private UiElement captured;

    public InputRouter(UiElement root) {
        this.root = root;
    }

    public UiElement root() {
        return root;
    }

    public UiElement focused() {
        return focused;
    }

    public UiElement captured() {
        return captured;
    }

    /**
     * Captures pointer events for a host that handled focus itself (the homepage search field
     * has a dedicated hit path). Without this, a drag released outside the field never reaches
     * its {@code onPointerUp}, leaving the field stuck in selecting/pressed state.
     */
    public void capture(UiElement element) {
        captured = element;
    }

    public UiElement hovered() {
        return hovered;
    }

    public void addLayer(UiElement layer) {
        layers.add(layer);
    }

    public void removeLayer(UiElement layer) {
        layers.remove(layer);
    }

    public List<UiElement> layers() {
        return Collections.unmodifiableList(layers);
    }

    public void pointerMove(PointerEvent event) {
        if (captured != null) {
            bubble(captured, node -> node.onPointerMove(event));
            return;
        }
        UiElement target = hitTest(event.x(), event.y());
        updateHover(target);
        if (target != null) {
            bubble(target, node -> node.onPointerMove(event));
        }
    }

    public void pointerDown(PointerEvent event) {
        UiElement target = hitTest(event.x(), event.y());
        if (target == null) {
            focus(null);
            return;
        }
        focus(focusableFrom(target));
        UiElement handled = bubble(target, node -> node.onPointerDown(event));
        captured = handled != null ? handled : (target.acceptsPointer() ? target : null);
        updateHover(target);
    }

    public void pointerUp(PointerEvent event) {
        UiElement target = captured != null ? captured : hitTest(event.x(), event.y());
        if (target != null) {
            bubble(target, node -> node.onPointerUp(event));
        }
        captured = null;
        updateHover(hitTest(event.x(), event.y()));
    }

    public void wheel(WheelEvent event) {
        UiElement target = hitTest(event.x(), event.y());
        if (target != null) {
            bubble(target, node -> node.onWheel(event));
        }
    }

    public void keyDown(KeyEvent event) {
        if (event.key() == Keys.TAB) {
            focusNext(Modifiers.shift(event.modifiers()));
            return;
        }
        if (focused != null) {
            bubble(focused, node -> node.onKeyDown(event));
        }
    }

    public void keyUp(KeyEvent event) {
        if (focused != null) {
            bubble(focused, node -> node.onKeyUp(event));
        }
    }

    public void textInput(TextInputEvent event) {
        if (focused != null) {
            bubble(focused, node -> node.onTextInput(event));
        }
    }

    public void preedit(PreeditEvent event) {
        if (focused != null) {
            bubble(focused, node -> node.onPreedit(event));
        }
    }

    public void focus(UiElement target) {
        UiElement next = focusableFrom(target);
        if (focused == next) {
            return;
        }
        if (focused != null) {
            focused.onFocusChanged(false);
        }
        focused = next;
        if (focused != null) {
            focused.onFocusChanged(true);
        }
    }

    public void focusNext(boolean backwards) {
        List<UiElement> focusable = new ArrayList<>();
        collectFocusable(root, focusable);
        for (UiElement layer : layers) {
            collectFocusable(layer, focusable);
        }
        if (focusable.isEmpty()) {
            focus(null);
            return;
        }
        int index = focused == null ? -1 : focusable.indexOf(focused);
        int step = backwards ? -1 : 1;
        int next = index < 0
                ? (backwards ? focusable.size() - 1 : 0)
                : Math.floorMod(index + step, focusable.size());
        focus(focusable.get(next));
    }

    public void clear() {
        if (focused != null) {
            focused.onFocusChanged(false);
        }
        if (hovered != null) {
            hovered.onHoverChanged(false);
        }
        focused = null;
        hovered = null;
        captured = null;
    }

    private void updateHover(UiElement target) {
        if (hovered == target) {
            return;
        }
        if (hovered != null) {
            hovered.onHoverChanged(false);
        }
        hovered = target;
        if (hovered != null) {
            hovered.onHoverChanged(true);
        }
    }

    private UiElement focusableFrom(UiElement target) {
        for (UiElement node = target; node != null; node = node.parent()) {
            if (node.focusable() && node.visible() && node.enabled()) {
                return node;
            }
        }
        return null;
    }

    private UiElement hitTest(float x, float y) {
        for (int i = layers.size() - 1; i >= 0; i--) {
            UiElement hit = hit(layers.get(i), x, y);
            if (hit != null) {
                return hit;
            }
        }
        return hit(root, x, y);
    }

    private UiElement hit(UiElement node, float x, float y) {
        if (!node.visible() || !node.enabled()) {
            return null;
        }
        float localX = x - node.offsetX();
        float localY = y - node.offsetY();
        if (node.clipsChildren() && !node.contains(localX, localY)) {
            return null;
        }
        List<UiElement> children = node.children();
        for (int i = children.size() - 1; i >= 0; i--) {
            UiElement hit = hit(children.get(i), localX, localY);
            if (hit != null) {
                return hit;
            }
        }
        return node.contains(localX, localY) ? node : null;
    }

    private void collectFocusable(UiElement node, List<UiElement> out) {
        if (!node.visible() || !node.enabled()) {
            return;
        }
        if (node.focusable()) {
            out.add(node);
        }
        for (UiElement child : node.children()) {
            collectFocusable(child, out);
        }
    }

    private UiElement bubble(UiElement from, Handler handler) {
        for (UiElement node = from; node != null; node = node.parent()) {
            if (handler.handle(node)) {
                return node;
            }
        }
        return null;
    }

    @FunctionalInterface
    private interface Handler {
        boolean handle(UiElement node);
    }
}
