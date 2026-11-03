package dev.skypocket.gui;

import dev.skypocket.gui.widget.MouseButtons;
import dev.skypocket.render.ClickGuiFonts;
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
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;

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

        McUiPainter painter = new McUiPainter(graphics);
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(originX, originY);
        pose.scale(fitScale, fitScale);
        drawElement(painter, root);
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
                new Size(ClickGuiFonts.width(text), ClickGuiFonts.lineHeight());
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
        // Dragging a scrollbar changes the popup anchor, even though the element tree did not change.
        invalidateLayout();
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        layoutUi();
        input.pointerUp(new PointerEvent(localX(event.x()), localY(event.y()),
                canonicalButton(event.button()), modifiers));
        // A control may have opened a dropdown/color popup on release.
        invalidateLayout();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount,
                                 double verticalAmount) {
        layoutUi();
        input.wheel(new WheelEvent(localX(mouseX), localY(mouseY), (float) verticalAmount,
                modifiers));
        invalidateLayout();
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
