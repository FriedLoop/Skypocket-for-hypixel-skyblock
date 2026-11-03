package dev.skypocket.gui;

import dev.skypocket.gui.widget.MouseButtons;
import dev.skypocket.render.ClickGuiFonts;
import dev.skypocket.render.ScissorTracker;
import dev.skypocket.ui.Box;
import dev.skypocket.ui.Dimension;
import dev.skypocket.ui.LayoutEngine;
import dev.skypocket.ui.Painted;
import dev.skypocket.ui.Rect;
import dev.skypocket.ui.ScrollView;
import dev.skypocket.ui.Size;
import dev.skypocket.ui.Style;
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
import dev.skypocket.ui.widget.Button;
import dev.skypocket.ui.widget.Dropdown;
import dev.skypocket.ui.widget.Slider;
import dev.skypocket.ui.widget.Switch;
import dev.skypocket.ui.widget.TextField;
import dev.skypocket.ui.widget.WidgetPalette;
import dev.skypocket.ui.mc.McUiPainter;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;

import java.util.List;

/**
 * A small, intentionally separate page for validating the UI v2 layout and input core before any
 * existing ClickGUI screen is migrated. Open it with {@code /skypocket uitest}.
 */
public final class Ui2TestScreen extends Screen {
    private static final float DESIGN_W = 360f;
    private static final float DESIGN_H = 340f;
    private final WidgetPalette palette = Ui2Theme.palette();

    private final Box root;
    private final ScrollView scroll;
    private final InputRouter input;
    private final TextField textField;
    private final Dropdown dropdown;
    private final int[] rowClicks = new int[18];
    private int actionCount;
    private int choice;
    private boolean enabled;
    private double scaleValue = 50.0;
    private int originX;
    private int originY;
    private float fitScale = 1f;
    private int modifiers;

    public Ui2TestScreen() {
        super(Component.literal("UI v2 Test"));

        Box content = new Box(Style.builder()
                .direction(Style.Direction.COLUMN)
                .gap(4f)
                .build());
        for (int i = 0; i < rowClicks.length; i++) {
            int index = i;
            content.add(new Button(
                    () -> "Row " + (index + 1) + (rowClicks[index] == 0 ? ""
                            : "  x" + rowClicks[index]),
                    () -> rowClicks[index]++,
                    Style.builder().width(Dimension.percent(100f)).height(24f).build(), palette));
        }

        scroll = new ScrollView(Style.builder().height(150f).padding(4f).build(), content);
        Box controls = Box.of(Style.builder()
                        .direction(Style.Direction.ROW)
                        .height(20f)
                        .gap(6f)
                        .build(),
                new Switch("Enabled", () -> enabled, value -> enabled = value,
                        Style.builder().width(140f).height(20f).build(), palette),
                new Slider("Value", 0, 100, () -> scaleValue, value -> scaleValue = value, "%.0f",
                        Style.builder().width(180f).height(20f).build(), palette));
        textField = new TextField("Type here...", Style.builder()
                .width(Dimension.percent(100f)).height(22f).build(), palette);
        dropdown = new Dropdown("Choice", List.of("Alpha", "Beta", "Gamma"), () -> choice,
                value -> choice = value,
                Style.builder().width(160f).height(20f).build(), palette);
        Box footer = Box.of(Style.builder()
                        .direction(Style.Direction.ROW)
                        .height(24f)
                        .gap(6f)
                        .build(),
                new Button("Action A", () -> actionCount++,
                        Style.builder().width(100f).height(24f).build(), palette),
                new Button("Action B", () -> actionCount++,
                        Style.builder().width(100f).height(24f).build(), palette));
        root = Box.of(Style.builder()
                        .width(DESIGN_W)
                        .height(DESIGN_H)
                        .padding(12f)
                        .gap(8f)
                        .direction(Style.Direction.COLUMN)
                        .build(),
                new Text("UI v2: layout + input", Style.builder().height(18f).build()),
                scroll,
                controls,
                textField,
                dropdown,
                footer);
        input = new InputRouter(root);
    }

    @Override
    protected void init() {
        ClickGuiFonts.setRenderScale(1f);
        // Without this the screen never receives charTyped/text input from the game.
        minecraft.onTextInputFocusChange(this, true);
        layoutUi();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        ScissorTracker.reset(graphics);
        ClickGuiFonts.setRenderScale(1f);
        layoutUi();

        McUiPainter painter = new McUiPainter(graphics);
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(originX, originY);
        pose.scale(fitScale, fitScale);
        painter.surface(new Rect(0f, 0f, DESIGN_W, DESIGN_H), 8f, 0xF0181A1F, 0);
        drawElement(painter, root);
        if (dropdown.isOpen()) {
            drawElement(painter, dropdown.popup());
        }
        pose.popMatrix();
    }

    private void layoutUi() {
        float margin = 8f;
        fitScale = Math.max(0.1f, Math.min(1f, Math.min(
                (width - margin * 2f) / DESIGN_W,
                (height - margin * 2f) / DESIGN_H)));
        originX = Math.round((width - DESIGN_W * fitScale) / 2f);
        originY = Math.round((height - DESIGN_H * fitScale) / 2f);
        TextMeasurer measurer = (text, maxWidth) ->
                new Size(ClickGuiFonts.width(text), ClickGuiFonts.lineHeight());
        LayoutEngine.layout(root, DESIGN_W, DESIGN_H, measurer);
        scroll.refresh();
        dropdown.layoutPopup(measurer);
        if (dropdown.isOpen()) {
            if (!input.layers().contains(dropdown.popup())) {
                input.addLayer(dropdown.popup());
            }
        } else {
            input.removeLayer(dropdown.popup());
        }
    }

    private void drawElement(McUiPainter painter, UiElement element) {
        if (!element.visible()) {
            return;
        }
        Matrix3x2fStack pose = painter.graphics().pose();
        pose.pushMatrix();
        pose.translate(element.offsetX(), element.offsetY());

        boolean scissor = element instanceof ScrollView;
        if (scissor) {
            ScrollView view = (ScrollView) element;
            painter.shadow(view.bounds(), 4f);
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
        if (scissor) {
            painter.popClip();
        }
        pose.popMatrix();
    }

    private float localX(double screenX) {
        return ((float) screenX - originX) / fitScale;
    }

    private float localY(double screenY) {
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
        dropdown.closeIfFocusOutside(input.focused());
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
        dropdown.closeIfFocusOutside(input.focused());
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
        if (event.isLeft()) {
            return Keys.LEFT;
        }
        if (event.isRight()) {
            return Keys.RIGHT;
        }
        // 26.2 uses GLFW space (32); 26.3 uses SDL space (44). Normalise both for button activation.
        if (event.input() == 32 || event.input() == 44) {
            return 32;
        }
        // 26.3 exposes SDL scancodes for these editing keys; 26.2 already returns the GLFW values.
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
