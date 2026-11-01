package dev.skypocket.gui.widget;

import dev.skypocket.render.ClickGuiFonts;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * The header row of a settings group: an expand indicator, the group's label, and - when the group is
 * a real sub-feature - the animated switch on the right.
 *
 * <p>Two hit areas in one row: the switch toggles the feature, anywhere else toggles the expansion. A
 * group that owns no enable flag (a cluster of parameters, such as a position or a set of colours)
 * simply has no switch: it still folds away, there is just nothing to turn off - and a switch that
 * does nothing would be a lie.
 *
 * <p>The header uses the same "+/-" convention as the colour rows' expand indicator, so expanding
 * anything in the panel reads the same way.
 */
public class GroupHeaderWidget extends BaseWidget {
    private final String label;
    /** Null for a group without a switch. */
    private final BooleanSupplier getter;
    private final Consumer<Boolean> setter;
    private final ToggleAnimation animation;
    private final BooleanSupplier expanded;
    private final Runnable onToggle;

    public GroupHeaderWidget(int x, int y, int width, int height, String label, BooleanSupplier getter,
                             Consumer<Boolean> setter, ToggleAnimation animation,
                             BooleanSupplier expanded, Runnable onToggle) {
        super(x, y, width, height);
        this.label = label;
        this.getter = getter;
        this.setter = setter;
        this.animation = animation;
        this.expanded = expanded;
        this.onToggle = onToggle;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubled) {
        if (event.button() != 0) {
            return;
        }
        if (getter != null && ToggleButton.inTrack(getX(), getY(), width, height, event.x(), event.y())) {
            setter.accept(!getter.getAsBoolean());
            return;
        }
        onToggle.run();
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        boolean open = expanded.getAsBoolean();
        boolean hovered = hovered(mouseX, mouseY);

        // A section rather than a setting: the darker field surface sets it apart from the rows below.
        ClickGuiStyle.surface(graphics, getX(), getY(), width, height,
                hovered ? ClickGuiStyle.fieldHover() : ClickGuiStyle.field(), 0);
        int textY = getY() + (height - ClickGuiFonts.lineHeight()) / 2 - 1;
        ClickGuiFonts.drawText(graphics, open ? "-" : "+", getX() + 8, textY, ClickGuiStyle.textDim(), true);
        ClickGuiFonts.drawText(graphics, label, getX() + 22, textY, ClickGuiStyle.text(), true);

        if (getter == null) {
            return;
        }
        boolean on = getter.getAsBoolean();
        animation.to(on);
        ToggleButton.drawSwitch(graphics, getX(), getY(), width, height, animation.progress(), hovered);
    }
}
