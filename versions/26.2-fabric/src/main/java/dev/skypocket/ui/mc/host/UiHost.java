package dev.skypocket.ui.mc.host;

import dev.skypocket.ui.Rect;
import dev.skypocket.ui.host.Layer;
import dev.skypocket.ui.input.KeyEvent;
import dev.skypocket.ui.input.Modifiers;
import dev.skypocket.ui.input.PreeditEvent;
import dev.skypocket.ui.mc.InputBridge;
import dev.skypocket.ui.mc.McUiPainter;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.List;

/**
 * A screen that shows {@link Layer}s and routes input to them.
 *
 * <p>Five screens used to do this themselves - the ClickGUI, the theme drawer's host, the HUD
 * position editor, its settings card, and the dev test screen - and no two of them routed the same
 * way. The differences were not stylistic: one never forwarded pointer moves (so its rows kept a
 * stale hover), two never converted key codes (so Backspace did nothing on 26.3), three never
 * registered for text input (so {@code charTyped} was never called at all). All of those are
 * consequences of the job being eight methods long with five independent implementers.
 *
 * <p>This class is that job, once. A subclass supplies its layers and the mapping from screen
 * coordinates into its design space; it does not write input handlers.
 *
 * <h2>What a subclass still owns</h2>
 *
 * <p>Which layer gets an event, when layers overlap, is business: the ClickGUI's drawer floats above
 * its pages but does not block them, while a modal would. That decision is
 * {@link #layers()} plus {@link #route} and stays in the subclass. This class guarantees the parts
 * that are mechanical - conversion, dispatch order, lifecycle.
 */
public abstract class UiHost extends Screen {

    protected UiHost(Component title) {
        super(title);
    }

    // ---- subclass contract ---------------------------------------------------------------

    /**
     * The layers this host shows, in painting order: index 0 is painted first and therefore sits
     * lowest.
     *
     * <p>Called on every event and every frame, so it may build its answer - but returning a fresh
     * list each time costs an allocation per event, so return a stable view where possible.
     */
    protected abstract List<Layer> layers();

    /**
     * Screen point to this host's design space.
     *
     * <p>The host owns the pose, so it is the only thing that can invert it. A host that scales
     * itself to fit the window divides here; a host that also animates must add the animation.
     */
    protected abstract float toDesignX(double screenX);

    protected abstract float toDesignY(double screenY);

    /** Paints the backdrop and the layers. Called with no transform applied. */
    protected abstract void paintHost(GuiGraphicsExtractor graphics, float partialTick);

    /**
     * Sizes and lays out the layers if that has not happened yet.
     *
     * <p>Input can arrive before the first frame is drawn, and dispatch needs each layer's
     * bounds to decide what the pointer is over. Doing the layout only while painting means the
     * first click of a session lands on layers that still report a zero-sized rect, and every
     * handler silently declines it.
     *
     * <p>Called before every event and before painting, so implementations must be idempotent.
     */
    protected abstract void ensureLaidOut();

    // ---- lifecycle -----------------------------------------------------------------------

    /**
     * Registers for text input.
     *
     * <p>Without this Minecraft never calls {@link #charTyped} - only key events arrive. The symptom
     * is specific and misleading: a text field can be focused, selected and pasted into, but typing
     * does nothing, which reads as a broken field rather than a screen that cannot type.
     *
     * <p>Idempotent, so subclasses can call it without checking whether init already ran.
     */
    protected final void registerTextInput() {
        if (minecraft != null) {
            minecraft.onTextInputFocusChange(this, true);
        }
    }

    @Override
    public void removed() {
        releaseTextInput();
        super.removed();
    }

    /** Releases the registration made by {@link #registerTextInput()}. */
    protected final void releaseTextInput() {
        if (minecraft != null) {
            minecraft.onTextInputFocusChange(this, false);
        }
    }

    // ---- drawing -------------------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        ensureLaidOut();
        paintHost(graphics, delta);
    }

    /**
     * Applies {@code scale} to the pose and pushes rasterisation to match, then runs {@code body}.
     *
     * <p>The two must agree: glyphs are rasterised at {@code DESIGN_SIZE × renderScale} and placed
     * by the pose, so drawing at a scale the rasteriser does not know about resamples every
     * character. That is a soft-text bug with no layout symptom, which is why it survived several
     * rounds of review before anyone connected "the test screen looks blurry" to
     * {@code setEffectiveScale}.
     */
    protected final void scaled(GuiGraphicsExtractor graphics, float scale, Runnable body) {
        dev.skypocket.render.ClickGuiMetrics.withScale(scale, body);
    }

    /** Paints one layer through a pose translated to {@code origin} and scaled by {@code scale}. */
    protected final void paintLayer(GuiGraphicsExtractor graphics, Layer layer, Rect origin,
                                    float scale) {
        if (!layer.isOpen()) {
            return;
        }
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(origin.x(), origin.y());
        if (scale != 1f) {
            pose.scale(scale, scale);
        }
        layer.paint(new McUiPainter(graphics));
        pose.popMatrix();
    }

    // ---- input ---------------------------------------------------------------------------
    //
    // Each handler walks the layers from top to bottom and stops at the first one that takes the
    // event. Top-to-bottom is the reverse of {@link #layers()}, because that is painting order.

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubled) {
        ensureLaidOut();
        float x = toDesignX(event.x());
        float y = toDesignY(event.y());
        int mods = InputBridge.modifiersOf(event.hasShiftDown(), event.hasControlDown(),
                event.hasAltDown());
        List<Layer> stack = layers();
        for (int i = stack.size() - 1; i >= 0; i--) {
            Layer layer = stack.get(i);
            if (!accepts(layer, x, y)) {
                continue;
            }
            if (layer.pointerDown(toLocalX(layer, x), toLocalY(layer, y), 0, mods)) {
                focusOnly(layer);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx,
                                double dy) {
        float x = toDesignX(event.x());
        float y = toDesignY(event.y());
        int mods = InputBridge.modifiersOf(event.hasShiftDown(), event.hasControlDown(),
                event.hasAltDown());
        List<Layer> stack = layers();
        for (int i = stack.size() - 1; i >= 0; i--) {
            Layer layer = stack.get(i);
            // A drag belongs to whoever captured the press, even if the pointer has left its bounds.
            if (!layer.isOpen()) {
                continue;
            }
            if (layer.hasPointerCapture()) {
                layer.pointerMove(toLocalX(layer, x), toLocalY(layer, y), mods);
                return true;
            }
        }
        for (int i = stack.size() - 1; i >= 0; i--) {
            Layer layer = stack.get(i);
            if (!accepts(layer, x, y)) {
                continue;
            }
            if (layer.pointerMove(toLocalX(layer, x), toLocalY(layer, y), mods)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        ensureLaidOut();
        float x = toDesignX(event.x());
        float y = toDesignY(event.y());
        int mods = InputBridge.modifiersOf(event.hasShiftDown(), event.hasControlDown(),
                event.hasAltDown());
        // Capture wins over position: the release belongs to the layer that took the press.
        List<Layer> stack = layers();
        for (int i = stack.size() - 1; i >= 0; i--) {
            Layer layer = stack.get(i);
            if (layer.isOpen() && layer.hasPointerCapture()) {
                layer.pointerUp(toLocalX(layer, x), toLocalY(layer, y), 0, mods);
                return true;
            }
        }
        for (int i = stack.size() - 1; i >= 0; i--) {
            Layer layer = stack.get(i);
            if (!accepts(layer, x, y)) {
                continue;
            }
            if (layer.pointerUp(toLocalX(layer, x), toLocalY(layer, y), 0, mods)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        ensureLaidOut();
        float x = toDesignX(mouseX);
        float y = toDesignY(mouseY);
        int mods = 0;
        boolean any = false;
        List<Layer> stack = layers();
        for (int i = stack.size() - 1; i >= 0; i--) {
            Layer layer = stack.get(i);
            if (!layer.isOpen()) {
                continue;
            }
            if (accepts(layer, x, y)) {
                layer.pointerMove(toLocalX(layer, x), toLocalY(layer, y), mods);
                any = true;
                break;
            }
        }
        if (!any) {
            // Nothing is under the pointer: clear hover everywhere, or a row keeps its highlight
            // after the cursor has left the window.
            for (Layer layer : layers()) {
                if (layer.isOpen()) {
                    layer.pointerMove(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, 0);
                }
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal,
                                 double vertical) {
        float x = toDesignX(mouseX);
        float y = toDesignY(mouseY);
        int mods = 0;
        List<Layer> stack = layers();
        for (int i = stack.size() - 1; i >= 0; i--) {
            Layer layer = stack.get(i);
            if (!accepts(layer, x, y)) {
                continue;
            }
            if (layer.wheel(toLocalX(layer, x), toLocalY(layer, y), (float) vertical, mods)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        ensureLaidOut();
        KeyEvent kernel = InputBridge.toKernel(event, true);
        List<Layer> stack = layers();
        for (int i = stack.size() - 1; i >= 0; i--) {
            Layer layer = stack.get(i);
            if (layer.isOpen() && layer.hasFocus() && layer.keyDown(kernel)) {
                return true;
            }
        }
        // ESC closes the screen - but only after the layers have had it. A focused layer uses ESC to
        // leave its own text field first, and swallowing it here would make the caret unreachable
        // without a mouse.
        if (event.isEscape()) {
            for (Layer layer : stack) {
                if (layer.isOpen() && layer.hasFocus()) {
                    layer.clearFocus();
                    return true;
                }
            }
            onClose();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyReleased(net.minecraft.client.input.KeyEvent event) {
        ensureLaidOut();
        KeyEvent kernel = InputBridge.toKernel(event, false);
        List<Layer> stack = layers();
        for (int i = stack.size() - 1; i >= 0; i--) {
            Layer layer = stack.get(i);
            if (layer.isOpen() && layer.hasFocus() && layer.keyUp(kernel)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        ensureLaidOut();
        String text = InputBridge.textOf(event);
        if (text == null) {
            return false;
        }
        List<Layer> stack = layers();
        for (int i = stack.size() - 1; i >= 0; i--) {
            Layer layer = stack.get(i);
            if (layer.isOpen() && layer.hasFocus() && layer.textInput(text)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean preeditUpdated(net.minecraft.client.input.PreeditEvent event) {
        ensureLaidOut();
        PreeditEvent kernel = new PreeditEvent(event == null ? "" : event.fullText(),
                event == null ? 0 : event.caretPosition());
        List<Layer> stack = layers();
        for (int i = stack.size() - 1; i >= 0; i--) {
            Layer layer = stack.get(i);
            if (layer.isOpen() && layer.hasFocus() && layer.preedit(kernel)) {
                return true;
            }
        }
        return false;
    }

    // ---- helpers -------------------------------------------------------------------------

    /** Whether {@code layer} is showing and takes a pointer at this point. */
    protected boolean accepts(Layer layer, float x, float y) {
        return layer.isOpen() && layer.contains(x, y);
    }

    /** Host space -> layer-local. Layers paint at their own origin, so the offset comes off here. */
    protected float toLocalX(Layer layer, float hostX) {
        return (hostX - layer.boundsOnScreen().x()) / layer.scale();
    }

    protected float toLocalY(Layer layer, float hostY) {
        return (hostY - layer.boundsOnScreen().y()) / layer.scale();
    }

    /**
     * Hands keyboard focus to {@code owner} and takes it from every other layer.
     *
     * <p>Layers cannot coordinate this: they do not share a router and most do not know the others
     * exist. Without it two carets stay live at once and typed characters go to whichever layer the
     * dispatch happened to reach first.
     */
    protected void focusOnly(Layer owner) {
        for (Layer layer : layers()) {
            if (layer != owner && layer.hasFocus()) {
                layer.clearFocus();
            }
        }
    }

    /** Adds a layer. Layers are painted in the order added. */


    /** The layer list this class dispatches over; subclasses may build it lazily in {@link #layers()}. */

    /** Keyboard focus of the topmost focused layer, or null. */
    protected final Layer focusedLayer() {
        List<Layer> stack = layers();
        for (int i = stack.size() - 1; i >= 0; i--) {
            Layer layer = stack.get(i);
            if (layer.isOpen() && layer.hasFocus()) {
                return layer;
            }
        }
        return null;
    }
}
