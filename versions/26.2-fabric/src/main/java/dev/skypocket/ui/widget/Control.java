package dev.skypocket.ui.widget;

import dev.skypocket.ui.Box;
import dev.skypocket.ui.Style;
import dev.skypocket.ui.UiPainter;
import dev.skypocket.ui.input.KeyEvent;
import dev.skypocket.ui.input.Keys;
import dev.skypocket.ui.input.PointerEvent;

/** Shared focus, hover and press state for interactive widgets. */
public abstract class Control extends Box {
    private static final long KEY_FLASH_NANOS = 120_000_000L;

    protected final WidgetPalette palette;
    protected boolean hovered;
    protected boolean focused;
    protected boolean pressed;
    private long keyFlashUntil;

    protected Control(Style style, WidgetPalette palette) {
        super(style);
        this.palette = palette;
    }

    @Override
    public boolean acceptsPointer() {
        return true;
    }

    @Override
    public boolean focusable() {
        return true;
    }

    @Override
    public boolean onPointerDown(PointerEvent event) {
        pressed = true;
        return true;
    }

    @Override
    public boolean onPointerUp(PointerEvent event) {
        if (pressed) {
            activate();
        }
        pressed = false;
        return true;
    }

    @Override
    public boolean onKeyDown(KeyEvent event) {
        if (event.key() == Keys.ENTER || event.key() == 32) {
            flashActivation();
            activate();
            return true;
        }
        return false;
    }

    /** Makes a keyboard activation visible for a few frames, just like a mouse press. */
    protected void flashActivation() {
        keyFlashUntil = System.nanoTime() + KEY_FLASH_NANOS;
    }

    @Override
    public void onHoverChanged(boolean hovered) {
        this.hovered = hovered;
    }

    @Override
    public void onFocusChanged(boolean focused) {
        this.focused = focused;
    }

    protected abstract void activate();

    public abstract void paint(UiPainter painter);

    protected int background() {
        // Hover deliberately does not paint a fill: after activation the only persistent state is the
        // focus outline, so mouse and keyboard activation end in the same visual state.
        boolean active = pressed || System.nanoTime() < keyFlashUntil;
        return active ? palette.pressed() : palette.background();
    }

    protected void paintFocus(UiPainter painter) {
        if (focused) {
            // Keep the button's own surface inside the ring; drawing only the stroke on a
            // transparent rect can make some painters fill the whole rounded area.
            painter.surface(bounds(), palette.radius(), background(), palette.focus());
        }
    }
}
