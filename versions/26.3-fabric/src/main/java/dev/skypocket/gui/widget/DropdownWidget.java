package dev.skypocket.gui.widget;

import dev.skypocket.render.ClickGuiFonts;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.function.Supplier;

/**
 * The closed-state row of a dropdown setting: label on the left, the current value
 * and an arrow on the right. Clicking it toggles the expanded editor (the
 * {@link DropdownListWidget} + a search field below).
 */
public class DropdownWidget extends BaseWidget {
    private final String label;
    private final Supplier<String> valueSupplier;
    private final Runnable onToggle;

    public DropdownWidget(int x, int y, int width, int height, String label,
                          Supplier<String> valueSupplier, Runnable onToggle) {
        super(x, y, width, height);
        this.label = label;
        this.valueSupplier = valueSupplier;
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
        boolean hover = inBounds(mouseX, mouseY);
        ClickGuiStyle.field(graphics, getX(), getY(), width, height, hover, false);

        int textY = getY() + (height - ClickGuiFonts.lineHeight()) / 2 - 1;
        ClickGuiFonts.drawText(graphics, label, getX() + 6, textY, ClickGuiStyle.textDim(), true);

        String arrow = "▼";
        int arrowX = getX() + width - 8 - ClickGuiFonts.width(arrow);
        String value = valueSupplier.get();
        // Truncate long ids so they never overlap the label.
        int maxW = arrowX - getX() - 10 - ClickGuiFonts.width(label) - 12;
        value = fit(value, maxW);
        ClickGuiFonts.drawText(graphics, value, arrowX - 6 - ClickGuiFonts.width(value), textY,
                ClickGuiStyle.text(), true);
        ClickGuiFonts.drawText(graphics, arrow, arrowX, textY, ClickGuiStyle.textDim(), true);
    }

    /** Truncates {@code s} with an ellipsis so it fits {@code maxW} panel px. */
    public static String fit(String s, int maxW) {
        if (maxW <= 0 || ClickGuiFonts.width(s) <= maxW) {
            return s;
        }
        while (s.length() > 1 && ClickGuiFonts.width(s + "…") > maxW) {
            s = s.substring(0, s.length() - 1);
        }
        return s + "…";
    }
}
