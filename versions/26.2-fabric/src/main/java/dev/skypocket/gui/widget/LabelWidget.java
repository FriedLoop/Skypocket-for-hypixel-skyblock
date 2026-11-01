package dev.skypocket.gui.widget;

import dev.skypocket.render.ClickGuiFonts;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import org.joml.Matrix3x2fStack;

/**
 * A row that is only text: a section heading, or the note under a setting.
 *
 * <p>It exists because every other row is a control that draws its own label (see
 * {@link ToggleButton}), so there was nowhere to put a line that is not a control at all. Two looks,
 * chosen by {@code heading}: a heading is drawn slightly smaller and indented so it reads as a label
 * for the rows below rather than as one of them, a note is drawn at the normal size in the dim colour.
 *
 * <p>It draws no background and answers no clicks, so it can sit in the gap a row leaves for it
 * without looking - or behaving - like a button.
 */
public class LabelWidget extends BaseWidget {
    /** Heading text is drawn at this fraction of the row text size. */
    private static final float HEADING_SCALE = 0.9f;
    /** Left inset of a heading's text. */
    private static final int HEADING_INSET = 4;
    private static final int TEXT_INSET = 8;

    private final String text;
    private final int color;
    private final boolean heading;

    public LabelWidget(int x, int y, int width, int height, String text, int color, boolean heading) {
        super(x, y, width, height);
        this.text = text;
        this.color = color;
        this.heading = heading;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubled) {
        // Nothing to do: a label is not a control.
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                            float delta) {
        if (heading) {
            Matrix3x2fStack pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(getX() + HEADING_INSET, getY() + (height - ClickGuiFonts.lineHeight()) / 2f);
            pose.scale(HEADING_SCALE, HEADING_SCALE);
            ClickGuiFonts.drawText(graphics, text, 0, 0, color, true);
            pose.popMatrix();
            return;
        }
        ClickGuiFonts.drawText(graphics, text, getX() + TEXT_INSET,
                getY() + (height - ClickGuiFonts.lineHeight()) / 2 - 1, color, true);
    }
}
