package dev.skypocket.ui;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.render.GuiPanelCache;
import dev.skypocket.render.ScissorTracker;
import dev.skypocket.ui.input.InputRouter;
import dev.skypocket.ui.input.KeyEvent;
import dev.skypocket.ui.input.Modifiers;
import dev.skypocket.ui.input.PointerEvent;
import dev.skypocket.ui.input.PreeditEvent;
import dev.skypocket.ui.input.TextInputEvent;
import dev.skypocket.ui.input.WheelEvent;
import dev.skypocket.ui.mc.McUiPainter;
import dev.skypocket.ui.widget.Control;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix3x2fStack;

import java.util.Set;

/**
 * Base for a screen built on the retained tree: owns layout, painting and caching.
 *
 * <p>This is the piece that was missing. {@code SettingsPage} had worked out how to do all of it -
 * static bake, dynamic overlay pass, cache key, overlay painting - and because none of it lived in
 * the framework, the other pages did without (or did their own, differently). That is why no page in
 * the mod was pure v2 and why moving one onto it meant re-deriving the same ninety lines.
 *
 * <h2>What the subclass writes</h2>
 *
 * <p>{@link #build()} returns the tree. {@link #revision()} tells the cache when the content changed.
 * Everything else is handled here.
 *
 * <h2>The cache key</h2>
 *
 * <p>Four inputs, and leaving any out produces a stale panel: this page's identity (a rebuilt page
 * reuses revision 0), {@link #revision()}, the scroll position, and
 * {@link ConfigManager#revision()}. See UI-V2-CONTRACT.md §5.2.
 */
public abstract class UiPage {
    private final GuiPanelCache.Slot cache = new GuiPanelCache.Slot();
    private final OverlayHost overlays = new OverlayHost();

    /** Screen-to-design mapping supplied by the host; identity until one is set. */
    private PageTransform transform = PageTransform.NONE;

    /** Popup owners whose floating layers this page drives; see {@link #trackPopup}. */
    private final java.util.List<PopupOwner> popupOwners = new java.util.ArrayList<>();

    private UiElement root;
    private InputRouter input;
    private float width;
    private float height;
    private float renderScaleX = 1f;
    private float renderScaleY = 1f;
    private long renderRevision;
    private UiElement lastFocused;

    // ---- subclass contract ----------------------------------------------------------------

    /** Builds (or returns) the element tree. Called on the first layout and after a rebuild. */
    protected abstract UiElement build();

    /**
     * Counter of changes that affect appearance but not the config.
     *
     * <p>Default covers config writes. A page whose content depends on something else - a subsystem's
     * state, a data update - must override and fold that in, or the panel will keep showing the old
     * frame.
     */
    protected long revision() {
        return ConfigManager.revision();
    }

    // ---- lifecycle ------------------------------------------------------------------------

    /** Rebuilds the tree and invalidates the cache. */
    public final void rebuild() {
        // Popup owners belong to the tree that created them. A page that rebuilds creates new
        // ColorFields and Dropdowns, and the old ones would otherwise stay registered forever -
        // their popups still hit-tested, still painted, and their owners still referenced.
        clearPopups();
        root = build();
        // A rebuilt tree means new elements, so the router is replaced rather than reused: it holds
        // the hit-test root and the captured/focused elements, all of which belonged to the old tree.
        input = new InputRouter(root);
        overlays.attach(input);
        renderRevision++;
        lastFocused = null;
    }

    /**
     * Forgets every tracked popup owner and removes their layers.
     *
     * <p>Called by {@link #rebuild()} before {@code build()}, so a page never has to think about it.
     */
    protected final void clearPopups() {
        for (PopupOwner owner : popupOwners) {
            overlays.remove(owner.popup());
        }
        popupOwners.clear();
    }

    /** Sets the page's size in design units; call before {@link #layout}. */
    public final void bounds(float width, float height) {
        this.width = width;
        this.height = height;
    }

    public final float width() {
        return width;
    }

    public final float height() {
        return height;
    }

    /** Lays the tree and every overlay out. Call once per frame before drawing. */
    public void layout(TextMeasurer measurer) {
        if (root == null) {
            rebuild();
        }
        LayoutEngine.layout(root, width, height, measurer);
        // Refresh through the same search the cache key uses. `root instanceof ScrollView` was the
        // first version and it never matched - the root is a layout Box and the scroll view is one of
        // its children - so the content offset was never applied and the page could not scroll at
        // all. The last section of the test screen was unreachable because of it.
        ScrollView scroll = findScrollView(root);
        if (scroll != null) {
            scroll.refresh();
        }
        // Popups anchor to their owner's bounds, so they can only be placed after the page is.
        layoutPopups(measurer);
    }

    public final InputRouter input() {
        if (input == null) {
            rebuild();
        }
        return input;
    }

    public final OverlayHost overlays() {
        return overlays;
    }

    public final UiElement root() {
        if (root == null) {
            rebuild();
        }
        return root;
    }

    /**
     * Registers an overlay that floats above this page.
     *
     * <p>Convenience so a subclass does not have to reach through {@link #overlays()} for the common
     * case; a popup is added when it opens and removed when it closes.
     */
    protected final void addOverlay(UiElement overlay) {
        overlays.add(overlay);
    }

    protected final void removeOverlay(UiElement overlay) {
        overlays.remove(overlay);
    }

    /**
     * Registers a control whose popup this page should drive.
     *
     * <p>Without this, a page has to write the same four steps for every popup owner it contains -
     * lay it out against its anchor, register it while open, unregister it when closed, and sync its
     * contents before painting. {@code SettingsPage} had three copies of that loop, one per kind of
     * popup it grew; the test screen had two. Each copy is a chance to forget a step, and forgetting
     * one produces a popup that is invisible, unclickable, or shows stale values.
     */
    protected final void trackPopup(PopupOwner owner) {
        if (owner != null && !popupOwners.contains(owner)) {
            popupOwners.add(owner);
        }
    }

    /**
     * Lays out and registers every tracked popup.
     *
     * <p>Called from {@link #layout(TextMeasurer)} after the page itself has been laid out: a popup
     * anchors to its owner's bounds, which do not exist until then.
     */
    private void layoutPopups(TextMeasurer measurer) {
        for (PopupOwner owner : popupOwners) {
            if (!owner.isOpen()) {
                removeOverlay(owner.popup());
                continue;
            }
            owner.layoutPopup(measurer, width, height);
            addOverlay(owner.popup());
        }
    }

    /** Refreshes popup contents just before painting; see {@link PopupOwner#syncBeforePaint()}. */
    private void syncPopups() {
        for (PopupOwner owner : popupOwners) {
            if (owner.isOpen()) {
                owner.syncBeforePaint();
            }
        }
    }

    // ---- painting -------------------------------------------------------------------------

    /**
     * Draws the page: cached static layer, live dynamic layer, then overlays.
     *
     * <p>The pose is left as the caller set it - a host that wants the page scaled or offset wraps
     * this call rather than having the page do it, because the host owns the screen-space transform.
     */
    public void draw(GuiGraphicsExtractor graphics, float scaleX, float scaleY, boolean animating) {
        if (root == null) {
            rebuild();
        }
        InputRouter router = input();
        UiElement focused = router.focused();
        if (focused != lastFocused) {
            lastFocused = focused;
            renderRevision++;
        }
        // Hover is handled by the dynamic pass, not by invalidating the cache.
        //
        // The static bake deliberately suppresses highlights (Control.paint checks !neutralPaint()
        // before drawing one), so a hovered control has to be repainted live - which
        // PaintPass.collectDynamic now guarantees by including isHovered. Invalidating the cache here
        // as well would re-bake the whole page on every hover change, and the highlight would look
        // right for that frame and then be replaced by the cached texture on the next - the
        // "flickers once and disappears" symptom this page had.
        McUiPainter painter = new McUiPainter(graphics);
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        boolean cached = cache.draw(graphics, cacheKey(),
                Math.max(1, Math.round(width)), Math.max(1, Math.round(height)),
                scaleX, scaleY, animating,
                (pageGraphics, mouseX, mouseY, delta) -> drawStaticLayer(pageGraphics),
                0, 0, 0f);
        renderScaleX = cached ? cache.lastBlitScaleX() : 1f;
        renderScaleY = cached ? cache.lastBlitScaleY() : 1f;
        if (!cached) {
            PaintPass.paintStatic(painter, root);
        } else {
            // The cached texture is blitted 1:1 with texels; the live layer must use the same scale
            // about the same origin or it drifts by a pixel at the far edge.
            pose.scale(renderScaleX, renderScaleY);
            paintDynamicLayer(painter);
        }
        // Popup contents mirror a value that can also change from elsewhere (the colour square moves
        // the hex field), so they are refreshed immediately before they are painted.
        syncPopups();
        overlays.paint(painter);
        pose.popMatrix();
    }

    /** Bakes the static layer into the cache texture. */
    private void drawStaticLayer(GuiGraphicsExtractor graphics) {
        ScissorTracker.reset(graphics);
        // Hover and focus are transient; painting them into the texture would freeze one frame's
        // highlight into every subsequent frame.
        Control.neutralPaint(true);
        try {
            PaintPass.paintStatic(new McUiPainter(graphics), root);
        } finally {
            Control.neutralPaint(false);
        }
    }

    private void paintDynamicLayer(McUiPainter painter) {
        Set<UiElement> dynamic = PaintPass.collectDynamic(root);
        if (dynamic.isEmpty()) {
            return;
        }
        // An opaque row under the overlay keeps the cached text beneath from ghosting through.
        Control.overlayPaint(true);
        try {
            PaintPass.paintDynamic(painter, root, dynamic);
        } finally {
            Control.overlayPaint(false);
        }
    }

    private long cacheKey() {
        return System.identityHashCode(this) * 1_000_003L
                + renderRevision * 31_415_926_535L
                + scrollBits() * 4_294_967_291L
                + revision() * 2_654_435_761L;
    }

    /**
     * Bit pattern of the page's scroll offset, or 0 when it has no scroll view.
     *
     * <p>Searched rather than read from {@code root}: the root is usually a layout Box and the scroll
     * view is one of its children, so testing {@code root instanceof ScrollView} silently finds
     * nothing and the scroll offset never reaches the cache key. The symptom is specific and
     * confusing - dragging the wheel moves only the few controls that repaint dynamically, while the
     * baked static layer stays exactly where it was.
     */
    private long scrollBits() {
        ScrollView scroll = findScrollView(root);
        return scroll == null ? 0L : Float.floatToIntBits(scroll.scrollY());
    }

    private static ScrollView findScrollView(UiElement element) {
        if (element instanceof ScrollView scroll) {
            return scroll;
        }
        for (UiElement child : element.children()) {
            ScrollView found = findScrollView(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    public final float renderScaleX() {
        return renderScaleX;
    }

    public final float renderScaleY() {
        return renderScaleY;
    }

    // ---- input ---------------------------------------------------------------------------
    //
    // Every host used to write these bridges itself, and each one had three jobs: convert screen
    // pixels into design units, convert the platform key code into the kernel's own, and remember to
    // forward every event that matters. The test screen got all three wrong at least once - a
    // missing mouseMoved meant hover never updated at all, and Keys.ESCAPE never matched the
    // platform code so ESC and backspace silently did nothing.
    //
    // Taking screen coordinates and raw platform events makes each of those impossible: the
    // conversion is here, and there is one method per event with no optional pieces.

    /** Sets how screen coordinates map into this page's design space. */
    public final void transform(PageTransform value) {
        this.transform = value == null ? PageTransform.NONE : value;
    }

    public final void pointerDown(double screenX, double screenY, int button, int modifiers) {
        input().pointerDown(new PointerEvent(transform.toDesignX(screenX),
                transform.toDesignY(screenY), button, modifiers));
    }

    public final void pointerMove(double screenX, double screenY, int button, int modifiers) {
        input().pointerMove(new PointerEvent(transform.toDesignX(screenX),
                transform.toDesignY(screenY), button, modifiers));
    }

    public final void pointerUp(double screenX, double screenY, int button, int modifiers) {
        input().pointerUp(new PointerEvent(transform.toDesignX(screenX),
                transform.toDesignY(screenY), button, modifiers));
    }

    /**
     * Hover tracking.
     *
     * <p>Separate from {@link #pointerMove} on purpose: a drag must not change what is hovered, and
     * a plain move must not be treated as one. Hosts that only forward drags get no hover at all.
     */
    public final void mouseMoved(double screenX, double screenY) {
        input().pointerMove(new PointerEvent(transform.toDesignX(screenX),
                transform.toDesignY(screenY), -1, 0));
    }

    public final void wheel(double screenX, double screenY, double amount, int modifiers) {
        input().wheel(new WheelEvent(transform.toDesignX(screenX),
                transform.toDesignY(screenY), (float) amount, modifiers));
    }

    /**
     * Forwards a key press.
     *
     * <p>Runs the platform event through {@code InputKeys.canonical} first. That conversion is
     * version-specific - 26.2 uses GLFW key codes, 26.3 uses SDL3 ones - so passing
     * {@code event.key()} straight through works for letters (their codes coincide) and silently
     * breaks every named key.
     */
    public final void keyDown(net.minecraft.client.input.KeyEvent event) {
        if (event == null) {
            return;
        }
        input().keyDown(new KeyEvent(dev.skypocket.gui.InputKeys.canonical(event), 0,
                keyModifiers(event), true));
    }

    public final void keyUp(net.minecraft.client.input.KeyEvent event) {
        if (event == null) {
            return;
        }
        input().keyUp(new KeyEvent(dev.skypocket.gui.InputKeys.canonical(event), 0,
                keyModifiers(event), false));
    }

    /** Forwards typed text; ignores characters the game would not accept in chat. */
    public final void textInput(net.minecraft.client.input.CharacterEvent event) {
        if (event != null && event.isAllowedChatCharacter()) {
            input().textInput(new TextInputEvent(event.codepointAsString()));
        }
    }

    /** Forwards IME composition state, which text fields show inline. */
    public final void preedit(net.minecraft.client.input.PreeditEvent event) {
        input().preedit(new PreeditEvent(event == null ? "" : event.fullText(),
                event == null ? 0 : event.caretPosition()));
    }

    /** Whether this page wants ESC handled by itself rather than by the host. */
    public final boolean shouldCloseOnEscape(net.minecraft.client.input.KeyEvent event) {
        return dev.skypocket.gui.InputKeys.isEscape(event);
    }

    private static int keyModifiers(net.minecraft.client.input.KeyEvent event) {
        int bits = 0;
        if (event.hasShiftDown()) {
            bits |= Modifiers.SHIFT;
        }
        if (event.hasControlDown()) {
            bits |= Modifiers.CONTROL;
        }
        if (event.hasAltDown()) {
            bits |= Modifiers.ALT;
        }
        return bits;
    }
}
