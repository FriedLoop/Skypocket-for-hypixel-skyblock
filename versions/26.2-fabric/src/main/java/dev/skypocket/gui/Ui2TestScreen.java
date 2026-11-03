package dev.skypocket.gui;

import dev.skypocket.gui.widget.MouseButtons;
import dev.skypocket.render.ClickGuiFonts;
import dev.skypocket.render.ScissorTracker;
import dev.skypocket.ui.Box;
import dev.skypocket.ui.Dimension;
import dev.skypocket.ui.LayoutEngine;
import dev.skypocket.ui.ScrollView;
import dev.skypocket.ui.Size;
import dev.skypocket.ui.Style;
import dev.skypocket.ui.Text;
import dev.skypocket.ui.UiElement;
import dev.skypocket.ui.input.InputRouter;
import dev.skypocket.ui.input.KeyEvent;
import dev.skypocket.ui.input.Keys;
import dev.skypocket.ui.input.Modifiers;
import dev.skypocket.ui.input.PointerEvent;
import dev.skypocket.ui.input.TextInputEvent;
import dev.skypocket.ui.input.WheelEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;

/**
 * A small, intentionally separate page for validating the UI v2 layout and input core before any
 * existing ClickGUI screen is migrated. Open it with {@code /skypocket uitest}.
 */
public final class Ui2TestScreen extends Screen {
    private static final float DESIGN_W = 360f;
    private static final float DESIGN_H = 280f;

    private final Box root;
    private final ScrollView scroll;
    private final InputRouter input;
    private int originX;
    private int originY;
    private int modifiers;

    public Ui2TestScreen() {
        super(Component.literal("UI v2 Test"));

        Box content = new Box(Style.builder()
                .direction(Style.Direction.COLUMN)
                .gap(4f)
                .build());
        for (int i = 1; i <= 18; i++) {
            content.add(new DemoButton("Row " + i, Style.builder()
                    .width(Dimension.percent(100f)).height(24f).build()));
        }

        scroll = new ScrollView(Style.builder().height(150f).padding(4f).build(), content);
        Box footer = Box.of(Style.builder()
                        .direction(Style.Direction.ROW)
                        .height(24f)
                        .gap(6f)
                        .build(),
                new DemoButton("Action A", Style.builder().width(100f).height(24f).build()),
                new DemoButton("Action B", Style.builder().width(100f).height(24f).build()));
        root = Box.of(Style.builder()
                        .width(DESIGN_W)
                        .height(DESIGN_H)
                        .padding(12f)
                        .gap(8f)
                        .direction(Style.Direction.COLUMN)
                        .build(),
                new Text("UI v2: layout + input", Style.builder().height(18f).build()),
                scroll,
                footer);
        input = new InputRouter(root);
    }

    @Override
    protected void init() {
        ClickGuiFonts.setRenderScale(1f);
        layoutUi();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        ScissorTracker.reset();
        ClickGuiFonts.setRenderScale(1f);
        layoutUi();

        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(originX, originY);
        graphics.fill(0, 0, (int) DESIGN_W, (int) DESIGN_H, 0xF0181A1F);
        graphics.outline(0, 0, (int) DESIGN_W, (int) DESIGN_H, 0xFF3A3E48);
        drawElement(graphics, root);
        pose.popMatrix();
    }

    private void layoutUi() {
        originX = Math.round((width - DESIGN_W) / 2f);
        originY = Math.round((height - DESIGN_H) / 2f);
        LayoutEngine.layout(root, DESIGN_W, DESIGN_H,
                (text, maxWidth) -> new Size(ClickGuiFonts.width(text), ClickGuiFonts.lineHeight()));
        scroll.refresh();
    }

    private void drawElement(GuiGraphicsExtractor graphics, UiElement element) {
        if (!element.visible()) {
            return;
        }
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(element.offsetX(), element.offsetY());

        boolean scissor = element instanceof ScrollView;
        if (scissor) {
            ScrollView view = (ScrollView) element;
            graphics.fill((int) view.bounds().x(), (int) view.bounds().y(),
                    (int) view.bounds().right(), (int) view.bounds().bottom(), 0xFF20232A);
            graphics.enableScissor((int) Math.floor(view.bounds().x()),
                    (int) Math.floor(view.bounds().y()),
                    (int) Math.ceil(view.bounds().right()),
                    (int) Math.ceil(view.bounds().bottom()));
        }

        if (element instanceof DemoButton button) {
            drawButton(graphics, button);
        } else if (element instanceof Text text) {
            ClickGuiFonts.drawText(graphics, text.value(), text.bounds().x(), text.bounds().y(),
                    buttonTextColor(element), false);
        }

        for (UiElement child : element.children()) {
            drawElement(graphics, child);
        }
        if (scissor) {
            graphics.disableScissor();
        }
        pose.popMatrix();
    }

    private void drawButton(GuiGraphicsExtractor graphics, DemoButton button) {
        int color = button.pressed()
                ? 0xFF536B9C
                : button.hovered() ? 0xFF3A4356 : 0xFF2A2F39;
        graphics.fill((int) button.bounds().x(), (int) button.bounds().y(),
                (int) button.bounds().right(), (int) button.bounds().bottom(), color);
        if (button.focused()) {
            // outline takes x, y, width, height (unlike fill, which takes x1, y1, x2, y2).
            graphics.outline((int) button.bounds().x(), (int) button.bounds().y(),
                    (int) button.bounds().width(), (int) button.bounds().height(), 0xFF9FB7FF);
        }
        String label = button.label();
        int textWidth = ClickGuiFonts.width(label);
        ClickGuiFonts.drawText(graphics, label,
                button.bounds().x() + (button.bounds().width() - textWidth) / 2f,
                button.bounds().y() + (button.bounds().height() - ClickGuiFonts.lineHeight()) / 2f,
                0xFFFFFFFF, false);
    }

    private int buttonTextColor(UiElement element) {
        return 0xFFE6EAF2;
    }

    private float localX(double screenX) {
        return (float) screenX - originX;
    }

    private float localY(double screenY) {
        return (float) screenY - originY;
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
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        layoutUi();
        input.pointerMove(PointerEvent.move(localX(event.x()), localY(event.y()), modifiers));
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        layoutUi();
        input.pointerUp(new PointerEvent(localX(event.x()), localY(event.y()),
                canonicalButton(event.button()), modifiers));
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount,
                                 double verticalAmount) {
        layoutUi();
        input.wheel(new WheelEvent(localX(mouseX), localY(mouseY), (float) verticalAmount,
                modifiers));
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

    /**
     * 26.2 exposes GLFW key codes while 26.3 exposes SDL key codes. The semantic helpers are stable
     * across both, so translate them into the small canonical set used by InputRouter.
     */
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
        // 26.2 uses GLFW space (32); 26.3 uses SDL space (44). Normalise both for button activation.
        if (event.input() == 32 || event.input() == 44) {
            return 32;
        }
        return event.input();
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (event.isAllowedChatCharacter()) {
            input.textInput(new TextInputEvent(event.codepointAsString()));
        }
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

    private static final class DemoButton extends Box {
        private final String base;
        private boolean pressed;
        private boolean hovered;
        private boolean focused;
        private int clicks;

        private DemoButton(String base, Style style) {
            super(style);
            this.base = base;
        }

        private String label() {
            return clicks == 0 ? base : base + "  x" + clicks;
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
                clicks++;
            }
            pressed = false;
            return true;
        }

        @Override
        public boolean onKeyDown(KeyEvent event) {
            if (event.key() == Keys.ENTER || event.key() == 32) {
                clicks++;
                return true;
            }
            return false;
        }

        @Override
        public void onHoverChanged(boolean hovered) {
            this.hovered = hovered;
        }

        @Override
        public void onFocusChanged(boolean focused) {
            this.focused = focused;
        }

        private boolean pressed() {
            return pressed;
        }

        private boolean hovered() {
            return hovered;
        }

        private boolean focused() {
            return focused;
        }
    }
}
