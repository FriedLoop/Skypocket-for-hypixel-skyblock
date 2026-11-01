package dev.skypocket.gui.setting;

import dev.skypocket.gui.widget.ClickGuiStyle;
import dev.skypocket.gui.widget.DropdownWidget;
import dev.skypocket.gui.widget.GuiMetrics;
import dev.skypocket.gui.widget.ToggleButton;
import dev.skypocket.i18n.Localization;
import dev.skypocket.render.ClickGuiFonts;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws and hit-tests a list of settings rows, without knowing where the list lives.
 *
 * <p>It exists so the settings page and the HUD position editor's settings card can share one
 * implementation: the page draws the rows inside its cached panel, the card draws them inside a floating
 * box. Duplicating the row code instead would have been the fastest way to make the two disagree about
 * heights, colours or how a dropdown behaves - the exact class of bug this project keeps having to undo.
 *
 * <p>Stateless apart from the slider drag, which the host owns (see {@link State}) so the card and the page
 * cannot steal each other's drag.
 */
public final class SettingsView {
    /** Per-host interaction state: the dragged slider and one picker per open colour row. */
    public static final class State {
        Setting.SliderSetting dragSlider;
        /** Set by hosts that render the rows as a picture: no pickers, no editing. */
        public boolean suppressPickers;

        /** Created on demand when a colour row opens, dropped when it closes. */
        final java.util.Map<Setting.ColorSetting, dev.skypocket.gui.widget.ImmediateColorPicker>
                pickers = new java.util.LinkedHashMap<>();
    }

    /** True when a colour row is open, i.e. the host may need a wider layout. */
    public static boolean hasPicker(State state) {
        return !state.pickers.isEmpty();
    }

    /**
     * A row together with its y in the host's coordinate space.
     *
     * <p>{@code inCard} marks rows that sit inside an open group: they draw no background of their own,
     * because the group paints one card behind the whole block.
     */
    public record RowAt(Setting setting, int y, boolean inCard) {
        public RowAt(Setting setting, int y) {
            this(setting, y, false);
        }
    }

    private SettingsView() {
    }

    // ---- layout ---------------------------------------------------------------------------

    /** Rows of a list with their y, open groups expanded into their children. */
    public static List<RowAt> layout(List<Setting> rows, int startY) {
        List<RowAt> out = new ArrayList<>();
        int y = startY;
        for (Setting s : rows) {
            if (!s.isVisible()) {
                continue;
            }
            collect(out, s, y, false);
            y += s.totalHeight();
        }
        return out;
    }

    private static void collect(List<RowAt> out, Setting s, int y, boolean inCard) {
        // The group's own header row is listed too: it is drawn by the page (fold arrow and, for
        // groups that own a switch, the switch), so skipping it left the header invisible.
        out.add(new RowAt(s, y, inCard));
        if (s instanceof Setting.GroupSetting group && group.isExpanded()) {
            int childY = y + Setting.rowHeight();
            for (Setting child : group.children()) {
                if (!child.isVisible()) {
                    continue;
                }
                // Children of an open group live inside its card, so they draw no background.
                collect(out, child, childY, true);
                childY += child.totalHeight();
            }
        }
    }

    // ---- drawing --------------------------------------------------------------------------

    /**
     * Draws the rows that have no widget, culling to {@code [clipTop, clipBottom)}.
     *
     * <p>Widget-backed rows (text fields, an expanded colour picker) are drawn by the screen itself, so
     * they are skipped here.
     */
    public static void draw(State state, GuiGraphicsExtractor graphics, List<RowAt> rows, int x,
                            int width, int mouseX, int mouseY, int clipTop, int clipBottom) {
        // One card behind each open group, drawn first so its rows paint over it.
        for (RowAt row : rows) {
            // Only top-level groups get a card: a nested one would paint a second rounded box inside the
            // parent card.
            if (!row.inCard() && row.setting() instanceof Setting.GroupSetting group
                    && group.isExpanded()) {
                int cardH = group.totalHeight();
                if (row.y() + cardH < clipTop || row.y() > clipBottom) {
                    continue;
                }
                drawGroupCard(graphics, x, row.y(), width, cardH);
            }
        }
        for (RowAt row : rows) {
            Setting s = row.setting();
            if (!s.immediate()) {
                continue;
            }
            int h = s.totalHeight();
            if (row.y() + h < clipTop || row.y() > clipBottom) {
                continue;
            }
            drawRow(graphics, s, x, width, row.y(), mouseX, mouseY, row.inCard());
        }
        // The filter box of an open dropdown, drawn where the widget used to sit.
        for (RowAt row : rows) {
            if (row.setting() instanceof Setting.DropdownSetting dropdown && dropdown.expanded()) {
                dropdown.searchField().draw(graphics, controlX(x, width),
                        row.y() + Setting.rowHeight(), controlWidth(width), Setting.controlHeight());
            }
        }
        // An open colour row owns the space its height reserved; the picker draws itself there.
        if (state.suppressPickers) {
            return;
        }
        for (RowAt row : rows) {
            if (!(row.setting() instanceof Setting.ColorSetting colour) || !colour.expanded()) {
                continue;
            }
            int pickerY = row.y() + Setting.rowHeight();
            if (pickerY + dev.skypocket.gui.widget.ImmediateColorPicker.CONTENT_H < clipTop
                    || pickerY > clipBottom) {
                continue;
            }
            // Centred in the row, the way the widget version was: the picker is narrower than the row.
            int pickerX = x + Math.max(0, (width - dev.skypocket.gui.widget.ImmediateColorPicker.CONTENT_W) / 2);
            picker(state, colour).draw(graphics, pickerX, pickerY, mouseX, mouseY);
        }
    }

    /** The picker for a colour row, created the first time that row is opened. */
    private static dev.skypocket.gui.widget.ImmediateColorPicker picker(State state,
                                                                        Setting.ColorSetting colour) {
        return state.pickers.computeIfAbsent(colour, key -> new dev.skypocket.gui.widget.ImmediateColorPicker(
                key::value, key::setValue, new dev.skypocket.gui.widget.PickerMemory()));
    }

    /** Drops pickers whose row closed, so a reopened row starts from the current colour. */
    private static void prunePickers(State state, List<RowAt> rows) {
        state.pickers.keySet().removeIf(colour -> !colour.expanded());
    }

    private static void drawRow(GuiGraphicsExtractor graphics, Setting s, int x, int width, int y,
                                int mx, int my, boolean inCard) {
        GuiMetrics metrics = GuiMetrics.current();
        int inset = metrics.labelInset();
        int rowH = Setting.controlHeight();
        int rowY = y + Math.max(0, (Setting.rowHeight() - rowH) / 2);
        int textY = rowY + (rowH - ClickGuiFonts.lineHeight()) / 2 - 1;
        int controlW = Math.max(40, Math.min(metrics.controlWidth(), width - inset * 2));
        int controlX = x + width - inset - controlW;

        if (s instanceof Setting.Header header) {
            drawHeader(graphics, header, x, width, y);
            return;
        }
        if (s instanceof Setting.ActionSetting action) {
            boolean hovered = inside(mx, my, x, y, width, Setting.rowHeight());
            rowBackground(graphics, x, rowY, width, rowH, hovered, inCard);
            String caption = action.text();
            ClickGuiFonts.drawText(graphics, caption,
                    x + (width - ClickGuiFonts.width(caption)) / 2,
                    rowY + (rowH - ClickGuiFonts.lineHeight()) / 2 - 1, ClickGuiStyle.text(), true);
            return;
        }
        if (s instanceof Setting.GroupSetting group) {
            boolean hovered = inside(mx, my, x, y, width, Setting.rowHeight());
            if (group.isExpanded()) {
                // The card (with its header strip) is drawn by draw(); adding a surface here would put a
                // second rounded box inside the card, which is exactly what "one card" is meant to stop.
                if (hovered) {
                    // Same shape as the strip the card draws, so the tint cannot spill past its corners.
                    dev.skypocket.render.GuiPixels.fillRoundedTopRect(graphics, x + 1, y + 1,
                            width - 2, Setting.rowHeight() - 1,
                            Math.max(2, GuiMetrics.current().cardRadius()),
                            ClickGuiStyle.fillFor(true, false));
                }
            } else {
                // Folded: it is just another row, so it gets the same bar and shadow as one.
                rowBackground(graphics, x, rowY, width, rowH, hovered, inCard);
            }
            ClickGuiFonts.drawText(graphics, group.isExpanded() ? "-" : "+", x + inset, textY,
                    ClickGuiStyle.textDim(), true);
            ClickGuiFonts.drawText(graphics, Localization.tr(group.label()), x + inset + 14,
                    textY, ClickGuiStyle.text(), true);
            if (group.hasSwitch()) {
                ToggleButton.drawSwitch(graphics, x, rowY, width, rowH, group.switchProgress(),
                        hovered);
            }
            return;
        }
        if (s instanceof Setting.BooleanSetting toggle) {
            boolean on = toggle.value();
            boolean hovered = inside(mx, my, x, y, width, Setting.rowHeight());
            // The row no longer takes the accent colour when it is on: the switch already says so, and a
            // whole row of accent made a group of five switches look like five selected buttons.
            rowBackground(graphics, x, rowY, width, rowH, hovered, inCard);
            ClickGuiFonts.drawText(graphics, Localization.tr(toggle.label()), x + inset, textY,
                    on ? ClickGuiStyle.text() : ClickGuiStyle.textDim(), true);
            ToggleButton.drawSwitch(graphics, x, rowY, width, rowH, toggle.progress(), hovered);
            return;
        }
        if (s instanceof Setting.SliderSetting slider) {
            boolean sliderHover = inside(mx, my, x, y, width, Setting.rowHeight());
            rowBackground(graphics, x, rowY, width, rowH, sliderHover, inCard);
            ClickGuiFonts.drawText(graphics, slider.display(), x + inset, textY,
                    ClickGuiStyle.textDim(), true);
            int trackH = Math.max(2, metrics.sliderTrackHeight());
            int trackY = rowY + (rowH - trackH) / 2;
            ClickGuiStyle.pill(graphics, controlX, trackY, controlW, trackH,
                    ClickGuiStyle.fieldInner());
            double t = (slider.value() - slider.min())
                    / Math.max(1e-6, slider.max() - slider.min());
            int fillEnd = controlX + (int) Math.round(controlW * t);
            if (fillEnd > controlX) {
                ClickGuiStyle.pill(graphics, controlX, trackY, fillEnd - controlX, trackH,
                        ClickGuiStyle.accent());
            }
            int knob = Math.max(6, Math.min(metrics.knobSize(), rowH - 4));
            int knobX = controlX + (int) Math.round((controlW - knob) * t);
            ClickGuiStyle.surface(graphics, knobX, rowY + (rowH - knob) / 2, knob, knob,
                    ClickGuiStyle.text(), 0);
            return;
        }
        if (s instanceof Setting.DropdownSetting dropdown) {
            rowBackground(graphics, x, rowY, width, rowH,
                    inside(mx, my, x, y, width, Setting.rowHeight()), inCard);
            drawDropdown(graphics, dropdown, x, rowY, width, inset, controlX, controlW, rowH,
                    textY, mx, my);
            return;
        }
        if (s instanceof Setting.ColorSetting colour) {
            boolean hovered = inside(mx, my, x, y, width, Setting.rowHeight());
            rowBackground(graphics, x, rowY, width, rowH, hovered, inCard);
            ClickGuiFonts.drawText(graphics, colour.expanded() ? "-" : "+", x + inset, textY,
                    ClickGuiStyle.textDim(), true);
            ClickGuiFonts.drawText(graphics, Localization.tr(colour.label()), x + inset + 14, textY,
                    ClickGuiStyle.text(), true);
            ClickGuiStyle.surface(graphics, x + width - inset - 34, rowY + 3, 34, rowH - 6,
                    colour.value(), 0);
        }
    }

    /**
     * One card behind an open group: the header strip plus every row of the group.
     *
     * <p>Before this, a group rendered as a header bar followed by a stack of individually rounded bars,
     * which read as five unrelated buttons. The body uses the row colour and the header keeps the darker
     * field tone it had, so the two tones survive but the whole block is one shape.
     */
    private static void drawGroupCard(GuiGraphicsExtractor graphics, int x, int y, int width,
                                      int height) {
        ClickGuiStyle.shadow(graphics, x, y, width, height,
                Math.max(2, GuiMetrics.current().cardRadius()));
        ClickGuiStyle.surface(graphics, x, y, width, height, ClickGuiStyle.button(), 0);
        // The header shares the row colour: the group is one surface, and a hairline separates the
        // title from its rows (a darker strip made the group blend into the panel instead).
        int lineY = y + Setting.rowHeight() - 1;
        graphics.fill(x + 6, lineY, x + width - 6, lineY + 1, 0x33FFFFFF);
    }

    /**
     * Background of one row.
     *
     * <p>A standalone row is its own rounded bar; a row inside a group card draws nothing (the card is
     * already behind it) except a hover tint, so the card does not look subdivided.
     */
    private static void rowBackground(GuiGraphicsExtractor graphics, int x, int rowY, int width,
                                      int rowH, boolean hovered, boolean inCard) {
        if (inCard) {
            if (hovered) {
                // Rounded and inset: a square fill spilled its corners past the card's rounded edge.
                int r = Math.max(2, GuiMetrics.current().cardRadius() - 1);
                dev.skypocket.render.GuiPixels.fillRoundedRect(graphics, x + 3, rowY - 2,
                        width - 6, rowH + 4, r, ClickGuiStyle.fillFor(true, false));
            }
            return;
        }
        // Every standalone row is its own card: fill plus a soft shadow, no stroke. One layer only -
        // the two-layer shadow is for floating surfaces, and on a list of rows it read as noise.
        ClickGuiStyle.shadowSoft(graphics, x, rowY, width, rowH, ClickGuiStyle.RADIUS);
        ClickGuiStyle.button(graphics, x, rowY, width, rowH, hovered, false);
    }

    /** Section heading: a gap above, then the centred title with a hairline on each side. */
    private static void drawHeader(GuiGraphicsExtractor graphics, Setting.Header header, int x,
                                   int width, int y) {
        GuiMetrics metrics = GuiMetrics.current();
        int gap = metrics.sectionGap();
        int h = metrics.sectionTitleHeight();
        int top = y + gap;
        String title = Localization.tr(header.label());
        float scale = 0.9f;
        int tw = Math.round(ClickGuiFonts.width(title) * scale);
        int textX = x + (width - tw) / 2;
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(textX, top + (h - ClickGuiFonts.lineHeight() * scale) / 2f);
        pose.scale(scale, scale);
        ClickGuiFonts.drawText(graphics, title, 0, 0, ClickGuiStyle.sectionTitle(), true);
        pose.popMatrix();
        int alpha = metrics.dividerAlpha() & 0xFF;
        if (alpha > 0) {
            int line = (alpha << 24) | 0x00FFFFFF;
            int lineY = top + h / 2;
            int lineGap = 8;
            graphics.fill(x, lineY, Math.max(x, textX - lineGap), lineY + 1, line);
            graphics.fill(Math.min(x + width, textX + tw + lineGap), lineY, x + width, lineY + 1,
                    line);
        }
    }

    /** The dropdown row: label on the left, a fixed-width field on the right. */
    private static void drawDropdown(GuiGraphicsExtractor graphics, Setting.DropdownSetting dropdown,
                                     int x, int rowY, int width, int inset, int controlX,
                                     int controlW, int rowH, int textY, int mx, int my) {
        // The row bar is the control now: a field inside it would be a box in a box.
        ClickGuiFonts.drawText(graphics, Localization.tr(dropdown.label()), x + inset, textY,
                ClickGuiStyle.textDim(), true);
        String arrow = "▼";
        int arrowX = controlX + controlW - 6 - ClickGuiFonts.width(arrow);
        String value = DropdownWidget.fit(dropdown.value(), arrowX - controlX - 12);
        ClickGuiFonts.drawText(graphics, value, controlX + 6, textY, ClickGuiStyle.text(), true);
        ClickGuiFonts.drawText(graphics, arrow, arrowX, textY, ClickGuiStyle.textDim(), true);
        if (dropdown.expanded()) {
            drawDropdownList(graphics, dropdown, controlX, controlW, rowY + Setting.rowHeight() * 2,
                    mx, my);
        }
    }

    /** Width of the control column on the right of a row. */
    private static int controlWidth(int width) {
        GuiMetrics m = GuiMetrics.current();
        return Math.max(40, Math.min(m.controlWidth(), width - m.labelInset() * 2));
    }

    /** Left edge of that column. */
    private static int controlX(int x, int width) {
        return x + width - GuiMetrics.current().labelInset() - controlWidth(width);
    }

    /** The expanded option list, drawn under the row's control column. */
    private static void drawDropdownList(GuiGraphicsExtractor graphics,
                                         Setting.DropdownSetting dropdown, int x, int width,
                                         int listY, int mx, int my) {
        int itemH = Setting.DropdownSetting.itemHeight();
        int listH = Setting.DropdownSetting.listHeight();
        ClickGuiStyle.shadow(graphics, x, listY, width, listH, 4);
        ClickGuiStyle.surface(graphics, x, listY, width, listH, ClickGuiStyle.field(), 0);
        int visible = listH / itemH;
        dropdown.clampScroll(visible);
        List<String> items = dropdown.filtered();
        String current = dropdown.value();
        boolean over = inside(mx, my, x, listY, width, listH);
        for (int row = 0; row < visible; row++) {
            int index = dropdown.scrollIndex() + row;
            if (index >= items.size()) {
                break;
            }
            String option = items.get(index);
            int ry = listY + row * itemH;
            if (option.equals(current)) {
                graphics.fill(x + 1, ry, x + width - 1, ry + itemH, ClickGuiStyle.listRowCurrent());
            } else if (over && my >= ry && my < ry + itemH) {
                graphics.fill(x + 1, ry, x + width - 1, ry + itemH, ClickGuiStyle.listRowHover());
            }
            ClickGuiFonts.drawText(graphics, DropdownWidget.fit(option, width - 12), x + 6,
                    ry + (itemH - ClickGuiFonts.lineHeight()) / 2, ClickGuiStyle.text(), true);
        }
    }
    // ---- interaction ----------------------------------------------------------------------

    /** The immediate row under a point, or null. */
    public static Setting immediateAt(List<RowAt> rows, int x, int width, double lx, double ly) {
        for (RowAt row : rows) {
            if (row.setting().immediate() && lx >= x && lx < x + width && ly >= row.y()
                    && ly < row.y() + Setting.controlHeight()) {
                return row.setting();
            }
        }
        return null;
    }

    /**
     * Handles a click on an immediate row.
     *
     * @return true when the click was consumed; {@code relayout} must be run when a list or a picker
     *         opened or closed (the host rebuilds its widgets then)
     */
    public static boolean click(State state, List<RowAt> rows, int x, int width, double lx, double ly,
                                Runnable relayout) {
        if (clickDropdownSearch(rows, x, width, lx, ly)) {
            return true;
        }
        if (clickPicker(state, rows, x, width, lx, ly)) {
            return true;
        }
        if (clickDropdownList(rows, x, width, lx, ly, relayout)) {
            return true;
        }
        Setting row = immediateAt(rows, x, width, lx, ly);
        if (row instanceof Setting.ColorSetting colour) {
            colour.toggleExpanded();
            if (!colour.expanded()) {
                // Drop the picker so a closed row cannot keep keyboard focus.
                state.pickers.remove(colour);
            }
            relayout.run();
            return true;
        }
        if (row instanceof Setting.DropdownSetting dropdown) {
            dropdown.toggleExpanded();
            relayout.run();
            return true;
        }
        if (row instanceof Setting.BooleanSetting toggle) {
            toggle.toggle();
            return true;
        }
        if (row instanceof Setting.ActionSetting action) {
            action.run();
            return true;
        }
        if (row instanceof Setting.GroupSetting group) {
            // The right end of the header is the group's own switch; anywhere else folds it.
            boolean onSwitch = group.hasSwitch() && lx > x + width - 60;
            if (onSwitch) {
                group.toggleSwitch();
            } else {
                group.toggleExpanded();
                relayout.run();
            }
            return true;
        }
        if (row instanceof Setting.SliderSetting slider) {
            // Only the control column is the slider: clicking the label used to jump the value to 0.
            int cx = controlX(x, width);
            int cw = controlWidth(width);
            if (lx >= cx && lx < cx + cw) {
                state.dragSlider = slider;
                setSliderFrom(slider, cx, cw, lx);
            }
            return true;
        }
        return row != null;
    }

    /** Continues a picker or slider drag; false when neither is in progress. */
    public static boolean drag(State state, List<RowAt> rows, int x, int width, double mx, double my) {
        if (dragPicker(state, rows, x, width, mx, my)) {
            return true;
        }
        if (state.dragSlider == null) {
            return false;
        }
        setSliderFrom(state.dragSlider, controlX(x, width), controlWidth(width), mx);
        return true;
    }

    public static boolean release(State state) {
        releasePickers(state);
        if (state.dragSlider == null) {
            return false;
        }
        state.dragSlider = null;
        return true;
    }

    // ---- pickers ---------------------------------------------------------------------------

    private static int pickerY(List<RowAt> rows, Setting.ColorSetting colour) {
        for (RowAt row : rows) {
            if (row.setting() == colour) {
                return row.y() + Setting.rowHeight();
            }
        }
        return -1;
    }

    /** A click inside an open dropdown's filter box. */
    public static boolean clickDropdownSearch(List<RowAt> rows, int x, int width, double lx,
                                              double ly) {
        for (RowAt row : rows) {
            if (row.setting() instanceof Setting.DropdownSetting dropdown && dropdown.expanded()
                    && dropdown.searchField().mouseClicked(lx, ly, x,
                    row.y() + Setting.rowHeight(), width, Setting.controlHeight(), false)) {
                return true;
            }
        }
        return false;
    }

    /** A click inside an open colour row's picker. */
    public static boolean clickPicker(State state, List<RowAt> rows, int x, int width, double mx, double my) {
        for (RowAt row : rows) {
            if (row.setting() instanceof Setting.ColorSetting colour && colour.expanded()) {
                int py = row.y() + Setting.rowHeight();
                int px = x + Math.max(0, (width - dev.skypocket.gui.widget.ImmediateColorPicker.CONTENT_W) / 2);
                if (my >= py && my <= py + dev.skypocket.gui.widget.ImmediateColorPicker.CONTENT_H
                        && picker(state, colour).mouseClicked(mx, my, px, py, false)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean dragPicker(State state, List<RowAt> rows, int x, int width, double mx, double my) {
        for (java.util.Map.Entry<Setting.ColorSetting,
                dev.skypocket.gui.widget.ImmediateColorPicker> entry : state.pickers.entrySet()) {
            int py = pickerY(rows, entry.getKey());
            if (py >= 0 && entry.getValue().mouseDragged(mx, my, x + Math.max(0, (width - dev.skypocket.gui.widget.ImmediateColorPicker.CONTENT_W) / 2), py)) {
                return true;
            }
        }
        return false;
    }

    private static void releasePickers(State state) {
        for (dev.skypocket.gui.widget.ImmediateColorPicker picker : state.pickers.values()) {
            picker.mouseReleased();
        }
    }

    /** A typed character goes to the focused field of an open picker. */
    public static boolean charTyped(State state, List<RowAt> rows, char c) {
        for (RowAt row : rows) {
            if (row.setting() instanceof Setting.DropdownSetting dropdown && dropdown.expanded()
                    && dropdown.searchField().charTyped(c)) {
                dropdown.setFilter(dropdown.searchField().text());
                return true;
            }
        }
        for (dev.skypocket.gui.widget.ImmediateColorPicker picker : state.pickers.values()) {
            if (picker.charTyped(c)) {
                return true;
            }
        }
        return false;
    }

    /** A key goes to the focused field first. */
    public static boolean keyPressed(State state, List<RowAt> rows, int key, boolean shift,
                                     boolean ctrl) {
        for (RowAt row : rows) {
            if (row.setting() instanceof Setting.DropdownSetting dropdown && dropdown.expanded()
                    && dropdown.searchField().keyPressed(key, shift, ctrl)) {
                dropdown.setFilter(dropdown.searchField().text());
                return true;
            }
        }
        for (dev.skypocket.gui.widget.ImmediateColorPicker picker : state.pickers.values()) {
            if (picker.keyPressed(key, shift, ctrl)) {
                return true;
            }
        }
        return false;
    }

    public static boolean clickDropdownList(List<RowAt> rows, int x, int width, double lx, double ly,
                                            Runnable relayout) {
        int itemH = Setting.DropdownSetting.itemHeight();
        for (RowAt row : rows) {
            if (!(row.setting() instanceof Setting.DropdownSetting dropdown) || !dropdown.expanded()) {
                continue;
            }
            int listY = row.y() + Setting.rowHeight() * 2;
            if (lx >= x && lx < x + width && ly >= listY
                    && ly < listY + Setting.DropdownSetting.listHeight()) {
                dropdown.clampScroll(Setting.DropdownSetting.listHeight() / itemH);
                int index = dropdown.scrollIndex() + (int) ((ly - listY) / itemH);
                List<String> items = dropdown.filtered();
                if (index >= 0 && index < items.size()) {
                    dropdown.select(items.get(index));
                    relayout.run();
                }
                return true;
            }
        }
        return false;
    }

    public static boolean scrollDropdownList(List<RowAt> rows, int x, int width, double lx, double ly,
                                             double amount) {
        for (RowAt row : rows) {
            if (!(row.setting() instanceof Setting.DropdownSetting dropdown) || !dropdown.expanded()) {
                continue;
            }
            int listY = row.y() + Setting.rowHeight() * 2;
            if (lx >= x && lx < x + width && ly >= listY
                    && ly < listY + Setting.DropdownSetting.listHeight()) {
                dropdown.scrollBy(amount > 0 ? -1 : 1);
                dropdown.clampScroll(Setting.DropdownSetting.listHeight()
                        / Setting.DropdownSetting.itemHeight());
                return true;
            }
        }
        return false;
    }

    /** The note attached to the row under a point, or null. */
    public static String tooltipAt(List<RowAt> rows, double lx, double ly) {
        for (RowAt row : rows) {
            Setting s = row.setting();
            if (ly >= row.y() && ly < row.y() + s.height()) {
                return s instanceof Setting.Header header ? header.subtitle() : s.hint();
            }
        }
        return null;
    }

    private static void setSliderFrom(Setting.SliderSetting slider, int x, int width, double lx) {
        double t = (lx - x) / Math.max(1.0, width);
        t = Math.max(0.0, Math.min(1.0, t));
        slider.setValue(slider.min() + t * (slider.max() - slider.min()));
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
