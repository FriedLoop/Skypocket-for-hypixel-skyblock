package dev.skypocket.gui.widget;

import dev.skypocket.render.ClickGuiFonts;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/**
 * A clickable color row for the inline Click GUI color editor: shows an expand
 * indicator, the label and a live color swatch. Clicking toggles the editor's
 * expanded state instead of opening a separate screen.
 */
public class ColorToggleWidget extends BaseWidget {
    private final String label;
    private final IntSupplier color;
    private final BooleanSupplier expanded;
    private final Runnable onToggle;

    public ColorToggleWidget(int x, int y, int width, int height, String label,
                             IntSupplier color, BooleanSupplier expanded, Runnable onToggle) {
        super(x, y, width, height);
        this.label = label;
        this.color = color;
        this.expanded = expanded;
        this.onToggle = onToggle;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubled) {
        if (event.button() == 0) {
            onToggle.run();
        }
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        drawButtonBackground(graphics, hovered(mouseX, mouseY), false);
        String indicator = expanded.getAsBoolean() ? "-" : "+";
        ClickGuiFonts.drawText(graphics, indicator, getX() + 8, getY() + 6, ClickGuiStyle.textDim(), true);
        ClickGuiFonts.drawText(graphics, label, getX() + 22, getY() + 6, ClickGuiStyle.text(), true);
        int sx = getX() + width - 40;
        ClickGuiStyle.surface(graphics, sx, getY() + 3, 34, height - 6,
                color.getAsInt(), 0);
    }
}
