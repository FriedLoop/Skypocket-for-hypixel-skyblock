package dev.skypocket.gui.widget;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * Small base for all custom widgets, pinned to the confirmed 26.2
 * {@link AbstractWidget} contract: {@code extractWidgetRenderState} +
 * {@code updateWidgetNarration}. All hit-testing is done with plain bounds
 * math (no extra framework API).
 */
public abstract class BaseWidget extends AbstractWidget {

    protected BaseWidget(int x, int y, int width, int height) {
        super(x, y, width, height, Component.empty());
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {
        // narration is out of scope for this utility mod
    }

    protected boolean inBounds(double mouseX, double mouseY) {
        return mouseX >= getX() && mouseX < getX() + width
                && mouseY >= getY() && mouseY < getY() + height;
    }

    /**
     * The button surface every widget shares: a rounded rect with a 1px border ring, drawn by
     * {@link ClickGuiStyle} so the settings widgets match the cards' buttons exactly. Never
     * draw a control with a plain {@code fill} + {@code outline} - that is what made the
     * settings pages look like a different UI.
     */
    protected void drawButtonBackground(GuiGraphicsExtractor graphics, boolean hovered, boolean accent) {
        ClickGuiStyle.button(graphics, getX(), getY(), width, height, hovered, accent);
    }

    /**
     * Hover test for our widgets: pass the {@code mouseX/mouseY} the widget was drawn with.
     *
     * <p><b>Never use {@code isHovered()} here.</b> MC sets that field in the final
     * {@code AbstractWidget.extractRenderState} as
     * {@code extractor.containsPointInScissor(mouseX, mouseY) && areCoordinatesInRectangle(...)},
     * i.e. it also requires the point to be inside the extractor's current scissor - and the panel
     * pushes its content scissor through {@code pushDeviceSpace()} (device pixels, plus the panel
     * origin in live frames). Comparing panel-local coordinates against it is meaningless, and the
     * live and cached frames use different numbers (the cache records without the origin), so a
     * widget hovered with {@code isHovered()} lit up and went dark again as the frames alternated.
     * {@code DropdownWidget}/{@code DropdownListWidget} always used local bounds, which is why they
     * were the only ones that did not flicker.
     */
    protected boolean hovered(int mouseX, int mouseY) {
        return inBounds(mouseX, mouseY);
    }
}
