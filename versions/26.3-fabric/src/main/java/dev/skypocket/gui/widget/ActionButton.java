package dev.skypocket.gui.widget;

import dev.skypocket.render.ClickGuiFonts;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.function.Supplier;

/** Simple text button that runs an action on click. Supports static or dynamic labels. */
public class ActionButton extends BaseWidget {
    private final Supplier<String> label;
    private final Runnable onPress;

    public ActionButton(int x, int y, int width, int height, String label, Runnable onPress) {
        this(x, y, width, height, () -> label, onPress);
    }

    public ActionButton(int x, int y, int width, int height, Supplier<String> label, Runnable onPress) {
        super(x, y, width, height);
        this.label = label;
        this.onPress = onPress;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubled) {
        if (event.button() == 0) {
            onPress.run();
        }
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        drawButtonBackground(graphics, hovered(mouseX, mouseY), false);
        String text = label.get();
        ClickGuiFonts.drawText(graphics, text, getX() + (width - ClickGuiFonts.width(text)) / 2,
                getY() + (height - ClickGuiFonts.lineHeight()) / 2 - 1, ClickGuiStyle.text(), true);
    }
}
