package dev.skypocket.gui.widget;

import dev.skypocket.render.ClickGuiFonts;
import dev.skypocket.render.GuiPixels;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * The settings page's on/off control: the label on the left, an animated pill switch on the right.
 *
 * <p>Every boolean row and every group header uses the same switch geometry (see
 * {@link #drawSwitch}), so "on/off" looks the same wherever it appears. The animation state lives in
 * {@link ToggleAnimation} - owned by the setting, because rows are rebuilt on every scroll - and the
 * panel cache keys on its signature, so a moving switch is never frozen into a cached frame.
 */
public class ToggleButton extends BaseWidget {
    /**
     * Switch geometry comes from the theme's measurements ({@code switchWidth/switchHeight/knobSize}),
     * so the studio can resize the control without touching code. The defaults are 38 x 14 with a 10 px
     * knob: three characters at a legible size need the half the knob does not occupy.
     */
    private static final int INSET = 2;
    /** Distance from the control's right edge to the track. */
    private static final int MARGIN = 9;

    private final String label;
    private final BooleanSupplier getter;
    private final Consumer<Boolean> setter;
    private final ToggleAnimation animation;

    public ToggleButton(int x, int y, int width, int height, String label, BooleanSupplier getter,
                        Consumer<Boolean> setter, ToggleAnimation animation) {
        super(x, y, width, height);
        this.label = label;
        this.getter = getter;
        this.setter = setter;
        this.animation = animation;
    }

    private static int trackWidth() {
        return ClickGuiStyle.metrics().switchWidth();
    }

    private static int trackHeight() {
        return ClickGuiStyle.metrics().switchHeight();
    }

    /** The switch's left edge inside a control at {@code x} with {@code width}. */
    public static int trackX(int x, int width) {
        return x + width - trackWidth() - MARGIN;
    }

    /** The switch's top edge inside a control at {@code y} with {@code height}. */
    public static int trackY(int y, int height) {
        return y + (height - trackHeight()) / 2;
    }

    /** Whether a point falls on the switch rather than on the rest of the row. */
    public static boolean inTrack(int x, int y, int width, int height, double px, double py) {
        int tx = trackX(x, width);
        int ty = trackY(y, height);
        return px >= tx && px < tx + trackWidth() && py >= ty && py < ty + trackHeight();
    }

    // The switch colours live in GuiTheme (read through ClickGuiStyle#switchOff / #switchBorder): the
    // "off" track is deliberately darker than a row's surface, or the switch looks like a hole in it.

    /** Label size inside the track: three characters have to fit in the half the knob leaves free. */
    private static final float LABEL_SCALE = 1.0f;
    /** Cap height of the design font, in design units - used to centre the label by its ink, not its box. */
    private static final float CAP_HEIGHT = 6.6f;
    /**
     * One-pixel correction on top of the cap-height model: measured in game as 7 px above the ink and
     * 5 below, i.e. the letters sit a pixel lower than the geometry predicts (the font's ink starts a
     * little below the cap line). Nudging the single draw origin up by one centres them.
     */
    private static final int LABEL_Y_NUDGE = -1;

    /**
     * Draws the track, its sliding knob and the ON/OFF label. Shared with the group headers so both
     * read as one control.
     *
     * <p>The track fades between a dark "off" fill and the accent, and the knob slides - but the colour
     * alone was not enough to read the state, so the label says it outright. It sits in whichever half
     * the knob is <em>not</em> occupying, which keeps it legible through the whole animation instead of
     * being covered in the middle.
     */
    public static void drawSwitch(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                                  float progress, boolean hovered) {
        int tx = trackX(x, width);
        int ty = trackY(y, height);
        int w = trackWidth();
        int h = trackHeight();
        int track = ClickGuiStyle.blend(ClickGuiStyle.switchOff(), ClickGuiStyle.switchOn(), progress);
        if (hovered) {
            track = ClickGuiStyle.lighten(track, 0.14f);
        }
        // The ring is built in device pixels by the shared helper; drawing it as an outer pill plus an
        // inset one lets each side snap independently and the border comes out uneven.
        ClickGuiStyle.pillBordered(graphics, tx, ty, w, h, track, 0);

        int knob = Math.max(4, Math.min(ClickGuiStyle.metrics().knobSize(), h - INSET * 2));
        int travel = w - knob - INSET * 2;
        int knobX = tx + INSET + Math.round(progress * travel);
        GuiPixels.fillRoundedRect(graphics, knobX, ty + INSET, knob, knob, knob / 2,
                ClickGuiStyle.blend(ClickGuiStyle.textDim(), 0xFFFFFFFF, progress));

        boolean on = progress >= 0.5f;
        String text = on ? "ON" : "OFF";
        int textW = Math.round(ClickGuiFonts.width(text) * LABEL_SCALE);
        // Centre of the half the knob leaves free.
        int centre = on ? knobX - (knobX - tx) / 2 : knobX + knob + (tx + w - knobX - knob) / 2;
        int textX = centre - textW / 2;
        // Vertically the label is centred by its INK, not by the font's line box: the draw origin is the
        // top of the box and the baseline sits a CONSTANT 7 design units below it (the earlier attempt
        // scaled that 7 with the label, which pushed the ink down instead of centring it). Ink height is
        // the cap height at the label's scale.
        float inkHeight = CAP_HEIGHT * LABEL_SCALE;
        int textY = Math.round(ty + h / 2f - 7f + inkHeight / 2f) + LABEL_Y_NUDGE;
        int textColor = on ? 0xFFFFFFFF : 0xFFCFCFCF;
        ClickGuiFonts.drawTextScaled(graphics, text, textX, textY, textColor, false, LABEL_SCALE);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubled) {
        if (event.button() == 0) {
            setter.accept(!getter.getAsBoolean());
        }
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        boolean on = getter.getAsBoolean();
        // Catches a value changed elsewhere (a preset, a group header): the switch animates then too.
        animation.to(on);
        boolean hovered = hovered(mouseX, mouseY);

        drawButtonBackground(graphics, hovered, on);
        ClickGuiFonts.drawText(graphics, label, getX() + 8,
                getY() + (height - ClickGuiFonts.lineHeight()) / 2 - 1,
                on ? ClickGuiStyle.text() : ClickGuiStyle.textDim(), true);
        drawSwitch(graphics, getX(), getY(), width, height, animation.progress(), hovered);
    }
}
