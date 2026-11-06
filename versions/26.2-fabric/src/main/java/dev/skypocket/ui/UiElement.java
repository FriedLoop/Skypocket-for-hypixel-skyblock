package dev.skypocket.ui;

import dev.skypocket.ui.input.KeyEvent;
import dev.skypocket.ui.input.PointerEvent;
import dev.skypocket.ui.input.PreeditEvent;
import dev.skypocket.ui.input.TextInputEvent;
import dev.skypocket.ui.input.WheelEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** A node in the retained UI tree. Bounds are written by the layout pass. */
public abstract class UiElement {
    private Style style;
    private final List<UiElement> children = new ArrayList<>();
    private UiElement parent;
    private Rect bounds = Rect.ZERO;
    private boolean visible = true;
    private boolean enabled = true;
    private boolean clipsChildren;
    private float offsetX;
    private float offsetY;

    protected UiElement(Style style) {
        this.style = Objects.requireNonNull(style);
    }

    public final Style style() {
        return style;
    }

    public final UiElement style(Style value) {
        style = Objects.requireNonNull(value);
        return this;
    }

    public final List<UiElement> children() {
        return Collections.unmodifiableList(children);
    }

    public final UiElement add(UiElement child) {
        Objects.requireNonNull(child);
        if (child.parent != null) {
            child.parent.children.remove(child);
        }
        child.parent = this;
        children.add(child);
        return this;
    }

    public final UiElement add(UiElement... nodes) {
        for (UiElement node : nodes) {
            add(node);
        }
        return this;
    }

    /** Detaches every child, for lists that are rebuilt in place. */
    public final void clearChildren() {
        for (UiElement child : children) {
            child.parent = null;
        }
        children.clear();
    }

    public final UiElement remove(UiElement child) {
        if (child != null && children.remove(child)) {
            child.parent = null;
        }
        return this;
    }

    public final UiElement parent() {
        return parent;
    }

    public final Rect bounds() {
        return bounds;
    }

    final void bounds(Rect value) {
        bounds = value;
    }

    public final boolean visible() {
        return visible;
    }

    public final UiElement visible(boolean value) {
        visible = value;
        return this;
    }

    public final boolean enabled() {
        return enabled;
    }

    public final UiElement enabled(boolean value) {
        enabled = value;
        return this;
    }

    public final boolean clipsChildren() {
        return clipsChildren;
    }

    public final UiElement clipsChildren(boolean value) {
        clipsChildren = value;
        return this;
    }

    public final float offsetX() {
        return offsetX;
    }

    public final float offsetY() {
        return offsetY;
    }

    /** Visual offset for this element and its descendants; layout bounds are not changed. */
    public final UiElement offset(float x, float y) {
        offsetX = x;
        offsetY = y;
        return this;
    }

    public final boolean contains(float x, float y) {
        return x >= bounds.x() && y >= bounds.y() && x < bounds.right() && y < bounds.bottom();
    }

    /** Sum of ancestor visual offsets; add to local bounds to get root/design coordinates. */
    public final float ancestorOffsetX() {
        float x = 0f;
        for (UiElement node = parent; node != null; node = node.parent) {
            x += node.offsetX();
        }
        return x;
    }

    public final float ancestorOffsetY() {
        float y = 0f;
        for (UiElement node = parent; node != null; node = node.parent) {
            y += node.offsetY();
        }
        return y;
    }

    public final float visualX() {
        return bounds.x() + ancestorOffsetX();
    }

    public final float visualY() {
        return bounds.y() + ancestorOffsetY();
    }

    /** True when this element is {@code ancestor} or one of its descendants. */
    public final boolean isDescendantOf(UiElement ancestor) {
        for (UiElement node = this; node != null; node = node.parent) {
            if (node == ancestor) {
                return true;
            }
        }
        return false;
    }

    // ---- input -----------------------------------------------------------------------------

    /** Whether a pointer may start an interaction on this element. */
    public boolean acceptsPointer() {
        return false;
    }

    /** Whether the element can take keyboard focus. */
    public boolean focusable() {
        return false;
    }

    public boolean onPointerDown(PointerEvent event) {
        return false;
    }

    public boolean onPointerMove(PointerEvent event) {
        return false;
    }

    public boolean onPointerUp(PointerEvent event) {
        return false;
    }

    public boolean onWheel(WheelEvent event) {
        return false;
    }

    public boolean onKeyDown(KeyEvent event) {
        return false;
    }

    public boolean onKeyUp(KeyEvent event) {
        return false;
    }

    public boolean onTextInput(TextInputEvent event) {
        return false;
    }

    public boolean onPreedit(PreeditEvent event) {
        return false;
    }

    public void onHoverChanged(boolean hovered) {
    }

    public void onFocusChanged(boolean focused) {
    }
}
