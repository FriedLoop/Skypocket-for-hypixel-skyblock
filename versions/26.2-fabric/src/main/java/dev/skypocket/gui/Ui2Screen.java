package dev.skypocket.gui;

import dev.skypocket.gui.widget.MouseButtons;
import dev.skypocket.render.ClickGuiFonts;
import dev.skypocket.render.GuiPanelCache;
import dev.skypocket.render.ScissorTracker;
import dev.skypocket.ui.Box;
import dev.skypocket.ui.LayoutEngine;
import dev.skypocket.ui.Painted;
import dev.skypocket.ui.ScrollView;
import dev.skypocket.ui.Size;
import dev.skypocket.ui.Text;
import dev.skypocket.ui.TextMeasurer;
import dev.skypocket.ui.UiElement;
import dev.skypocket.ui.input.InputRouter;
import dev.skypocket.ui.input.KeyEvent;
import dev.skypocket.ui.input.Keys;
import dev.skypocket.ui.input.Modifiers;
import dev.skypocket.ui.input.PointerEvent;
import dev.skypocket.ui.input.TextInputEvent;
import dev.skypocket.ui.input.WheelEvent;
import dev.skypocket.ui.mc.McUiPainter;
import dev.skypocket.ui.widget.ColorField;
import dev.skypocket.ui.widget.Control;
import dev.skypocket.ui.widget.Dropdown;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;

import java.util.HashSet;
import java.util.Set;

/**
 * Shared host for UI v2 pages: fit-to-window scaling, layout, painting, input routing and MC key
 * normalisation live here. Page code only builds the element tree.
 */
public abstract class Ui2Screen extends Screen {
    protected final float designWidth;
    protected final float designHeight;
    protected Box root;
    protected InputRouter input;
    protected float fitScale = 1f;
    protected int originX;
    protected int originY;
    protected int modifiers;
    private boolean layoutDirty = true;
    private long renderRevision;
    private UiElement lastFocused;

    protected Ui2Screen(Component title, float designWidth, float designHeight) {
        super(title);
        this.designWidth = designWidth;
        this.designHeight = designHeight;
    }

    protected final void setRoot(Box root) {
        this.root = root;
        this.input = new InputRouter(root);
        invalidateLayout();
    }

    protected final void invalidateLayout() {
        layoutDirty = true;
        renderRevision++;
    }

    /** Layout-only refresh (popup layer/scroll anchor) without invalidating the page base. */
    protected final void invalidateLayoutOnly() {
        layoutDirty = true;
    }

    protected boolean cacheAllowed() {
        // The cache corrects for the target/pose size mismatch, so a fit scale below 1 is fine.
        // Keep a lower bound only to avoid baking a postage stamp on a tiny window.
        return fitScale >= 0.5f;
    }

    private long pageCacheKey() {
        return renderRevision * 31_415_926_535L + scrollSignature(root);
    }

    private static long scrollSignature(UiElement element) {
        long hash = 0L;
        if (element instanceof ScrollView scroll) {
            hash = Float.floatToIntBits(scroll.scrollY()) * 1_000_003L;
        }
        for (UiElement child : element.children()) {
            hash = hash * 31L + scrollSignature(child);
        }
        return hash;
    }

    private void drawPageContent(GuiGraphicsExtractor graphics) {
        ScissorTracker.reset(graphics);
        Control.neutralPaint(true);
        try {
            drawElement(new McUiPainter(graphics), root);
        } finally {
            Control.neutralPaint(false);
        }
    }

    private void drawDynamicControls(McUiPainter painter) {
        Set<UiElement> dynamic = new HashSet<>();
        collectDynamic(root, dynamic);
        if (!dynamic.isEmpty()) {
            Control.overlayPaint(true);
            try {
                drawDynamicElement(painter, root, dynamic);
            } finally {
                Control.overlayPaint(false);
            }
        }
    }

    private static void collectDynamic(UiElement element, Set<UiElement> dynamic) {
        if (element instanceof Control control
                && (control.isFocused() || control.isPressed() || control.flashActive()
                || (control instanceof ColorField color && color.isOpen())
                || (control instanceof Dropdown dropdown && dropdown.isOpen()))) {
            dynamic.add(control);
        }
        for (UiElement child : element.children()) {
            collectDynamic(child, dynamic);
        }
    }

    private boolean drawDynamicElement(McUiPainter painter, UiElement element,
                                       Set<UiElement> dynamic) {
        boolean any = dynamic.contains(element);
        for (UiElement child : element.children()) {
            any |= containsDynamic(child, dynamic);
        }
        if (!any || !element.visible()) {
            return false;
        }
        Matrix3x2fStack pose = painter.graphics().pose();
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
            drawDynamicElement(painter, child, dynamic);
        }
        if (scroll) {
            painter.popClip();
        }
        pose.popMatrix();
        return true;
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

    @Override
    protected void init() {
        ClickGuiFonts.setRenderScale(1f);
        minecraft.onTextInputFocusChange(this, true);
        layoutUi();
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        invalidateLayout();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (root == null) {
            return;
        }
        ScissorTracker.reset(graphics);
        ClickGuiFonts.setRenderScale(1f);
        layoutUi();

        UiElement focused = input.focused();
        if (focused != lastFocused) {
            // Values of the previously focused control may now only exist in the live overlay; bake
            // them into the base once focus moves away.
            lastFocused = focused;
            renderRevision++;
        }

        McUiPainter painter = new McUiPainter(graphics);
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(originX, originY);
        pose.scale(fitScale, fitScale);
        boolean cached = cacheAllowed() && GuiPanelCache.draw(graphics, pageCacheKey(),
                (int) designWidth, (int) designHeight, fitScale, fitScale, false,
                (pageGraphics, pageMouseX, pageMouseY, pageDelta) ->
                        drawPageContent(pageGraphics), 0, 0, delta);
        if (!cached) {
            drawElement(painter, root);
        } else {
            drawDynamicControls(painter);
        }
        drawOverlays(painter);
        pose.popMatrix();
    }

    protected void layoutUi() {
        if (root == null || !layoutDirty) {
            return;
        }
        layoutDirty = false;
        float margin = 8f;
        fitScale = Math.max(0.1f, Math.min(1f, Math.min(
                (width - margin * 2f) / designWidth,
                (height - margin * 2f) / designHeight)));
        originX = Math.round((width - designWidth * fitScale) / 2f);
        originY = Math.round((height - designHeight * fitScale) / 2f);
        TextMeasurer measurer = (text, maxWidth) ->
                new Size(ClickGuiFonts.width(
                        dev.skypocket.i18n.Localization.auto(text)), ClickGuiFonts.lineHeight());
        LayoutEngine.layout(root, designWidth, designHeight, measurer);
        onLayout(measurer);
    }

    protected void onLayout(TextMeasurer measurer) {
    }

    protected void drawOverlays(McUiPainter painter) {
    }

    protected void drawElement(McUiPainter painter, UiElement element) {
        if (!element.visible()) {
            return;
        }
        Matrix3x2fStack pose = painter.graphics().pose();
        pose.pushMatrix();
        pose.translate(element.offsetX(), element.offsetY());

        boolean scroll = element instanceof ScrollView;
        if (scroll) {
            ScrollView view = (ScrollView) element;
            painter.surface(view.bounds(), 4f, 0xFF20232A, 0);
            painter.pushClip(view.bounds());
        }

        if (element instanceof Painted painted) {
            painted.paint(painter);
        } else if (element instanceof Text text) {
            painter.text(text.value(), text.bounds().x(), text.bounds().y(), 0xFFE6EAF2);
        }

        for (UiElement child : element.children()) {
            drawElement(painter, child);
        }
        if (scroll) {
            painter.popClip();
        }
        pose.popMatrix();
    }

    protected float localX(double screenX) {
        return ((float) screenX - originX) / fitScale;
    }

    protected float localY(double screenY) {
        return ((float) screenY - originY) / fitScale;
    }

    private int canonicalButton(int button) {
        if (MouseButtons.isLeft(button)) {
            return 0;
        }
        if (MouseButtons.isRight(button)) {
            return 1;
        }
        return 2;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        layoutUi();
        input.pointerDown(new PointerEvent(localX(event.x()), localY(event.y()),
                canonicalButton(event.button()), modifiers));
        return true;
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        layoutUi();
        input.pointerMove(PointerEvent.move(localX(mouseX), localY(mouseY), modifiers));
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        layoutUi();
        input.pointerMove(PointerEvent.move(localX(event.x()), localY(event.y()), modifiers));
        // Drag itself does not invalidate the page base; a scrollbar changes the scroll signature,
        // while a slider stays a live focused overlay.
        invalidateLayoutOnly();
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        layoutUi();
        input.pointerUp(new PointerEvent(localX(event.x()), localY(event.y()),
                canonicalButton(event.button()), modifiers));
        // A control may have opened a dropdown/color popup on release.
        invalidateLayoutOnly();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount,
                                 double verticalAmount) {
        layoutUi();
        input.wheel(new WheelEvent(localX(mouseX), localY(mouseY), (float) verticalAmount,
                modifiers));
        invalidateLayoutOnly();
        return true;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (event.isEscape()) {
            onClose();
            return true;
        }
        modifiers = modifierBits(event);
        input.keyDown(new KeyEvent(canonicalKey(event), 0, modifiers, true));
        return true;
    }

    @Override
    public boolean keyReleased(net.minecraft.client.input.KeyEvent event) {
        modifiers = modifierBits(event);
        input.keyUp(new KeyEvent(canonicalKey(event), 0, modifiers, false));
        return true;
    }

    private int canonicalKey(net.minecraft.client.input.KeyEvent event) {
        if (event.isCycleFocus()) {
            return Keys.TAB;
        }
        if (event.isEscape()) {
            return Keys.ESCAPE;
        }
        if (event.isConfirmation()) {
            return Keys.ENTER;
        }
        if (event.isLeft()) {
            return Keys.LEFT;
        }
        if (event.isRight()) {
            return Keys.RIGHT;
        }
        if (event.input() == 32 || event.input() == 44) {
            return 32;
        }
        return switch (event.input()) {
            case 42 -> Keys.BACKSPACE;
            case 76 -> Keys.DELETE;
            case 74 -> Keys.HOME;
            case 77 -> Keys.END;
            default -> event.input();
        };
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (event.isAllowedChatCharacter()) {
            input.textInput(new TextInputEvent(event.codepointAsString()));
        }
        return true;
    }

    @Override
    public boolean preeditUpdated(net.minecraft.client.input.PreeditEvent event) {
        input.preedit(new dev.skypocket.ui.input.PreeditEvent(
                event == null ? "" : event.fullText(), event == null ? 0 : event.caretPosition()));
        return true;
    }

    private int modifierBits(net.minecraft.client.input.KeyEvent event) {
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
