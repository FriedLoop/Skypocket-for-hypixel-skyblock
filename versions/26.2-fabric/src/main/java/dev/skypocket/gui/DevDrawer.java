package dev.skypocket.gui;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.gui.widget.ClickGuiStyle;
import dev.skypocket.gui.widget.GuiMetrics;
import dev.skypocket.gui.widget.GuiTheme;
import dev.skypocket.gui.widget.ImmediateColorPicker;
import dev.skypocket.gui.widget.ImmediateTextField;
import dev.skypocket.gui.widget.PickerMemory;
import dev.skypocket.gui.widget.ThemeStore;
import dev.skypocket.i18n.Localization;
import dev.skypocket.render.ClickGuiFonts;
import dev.skypocket.render.GuiPixels;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/**
 * The theme editor, as a drawer beside the real panel.
 *
 * <p>This replaces the old studio screen, which rendered a miniature copy of the panel next to the
 * editor. That copy was a second implementation of the panel (a hand-composed chrome, a page of rows,
 * filters for everything it could not draw) and it kept drifting from the real one. Here there is
 * nothing to reproduce: edits go straight into the live palette, and the panel beside the drawer is the
 * result.
 *
 * <p>Because edits are live, closing the drawer without saving restores the snapshot taken when it
 * opened. Only «save» writes a theme file, so the old "draft" behaviour survives as "revert on exit".
 */
final class DevDrawer {
    /**
     * The picker is drawn at 75% here: at full size it forced the drawer wider than the gap beside the
     * panel. Only this host scales it - the settings page and the card keep it at 1:1.
     */
    private static final float PICKER_SCALE = 0.75f;
    private static final int WIDTH = 248;
    private static final int PAD = 8;
    private static final int ROW = 18;
    private static final int TITLE_H = 20;
    /** Height of the theme row under the title: «‹ name ›» plus the "new" button. */
    private static final int THEME_ROW = 18;

    private final List<String> tokens = new ArrayList<>();
    private final List<Boolean> isColor = new ArrayList<>();

    private boolean open;
    private int scroll;
    private String selected;
    private ImmediateColorPicker picker;
    private String pickerToken = "";

    /** Snapshot taken on open: closing without saving restores it. */
    private GuiTheme snapshotColors;
    private GuiMetrics snapshotMetrics;
    private String snapshotName = "";
    private String target = "";

    /** Themes on disk plus the two built-ins; refreshed whenever the drawer opens. */
    private final List<String> themes = new ArrayList<>();
    private int themeIndex;
    /** True while the "new theme" name field is open. */
    private boolean naming;
    private ImmediateTextField nameField;
    /** True while the delete confirmation is showing. */
    private boolean confirmingDelete;

    /** Absolute position once the user has dragged the title bar; -1 keeps the default anchor. */
    private int left = -1;
    private int top = -1;
    private boolean moving;
    private double grabX;
    private double grabY;

    private boolean dragging;
    private String status = "";
    private long statusUntil;

    DevDrawer() {
        for (String name : ThemeStore.colorNames()) {
            tokens.add(name);
            isColor.add(true);
        }
        for (String name : ThemeStore.metricNames()) {
            tokens.add(name);
            isColor.add(false);
        }
    }

    boolean isOpen() {
        return open;
    }

    /** Opens the drawer, remembering the palette so it can be restored. */
    void open() {
        if (!open) {
            snapshotColors = GuiTheme.current();
            snapshotMetrics = GuiMetrics.current();
            snapshotName = ConfigManager.get().clickGuiTheme;
            target = snapshotName;
            scroll = 0;
            selected = tokens.isEmpty() ? null : tokens.get(0);
            // Re-read the list: theme files may have been added or removed since last time.
            themes.clear();
            themes.addAll(ThemeStore.names());
            themeIndex = Math.max(0, themes.indexOf(target));
            naming = false;
            nameField = null;
        }
        open = true;
        picker = null;
        pickerToken = "";
    }

    /** Closes the drawer and discards unsaved edits - the snapshot is what the theme was. */
    void close() {
        if (!open) {
            return;
        }
        open = false;
        restore();
        hint(Localization.tr("已还原未保存的改动"));
    }

    private void restore() {
        if (snapshotColors != null && snapshotMetrics != null) {
            ThemeStore.apply(snapshotColors, snapshotMetrics);
            ConfigManager.get().clickGuiTheme = snapshotName;
            ConfigManager.touch();
        }
    }

    private void save() {
        boolean ok = ThemeStore.save(target, GuiTheme.current(), GuiMetrics.current());
        if (ok) {
            // Saving makes the current state the new baseline: closing must not undo it.
            snapshotColors = GuiTheme.current();
            snapshotMetrics = GuiMetrics.current();
            snapshotName = target;
            ConfigManager.get().clickGuiTheme = target;
            hint(Localization.tr("已保存到 ") + target + ".json");
        } else {
            hint(Localization.tr("保存失败，见日志"));
        }
    }

    /** Steps to the previous/next theme and loads it, discarding unsaved edits. */
    private void cycleTheme(int delta) {
        if (themes.isEmpty()) {
            return;
        }
        themeIndex = Math.floorMod(themeIndex + delta, themes.size());
        switchTo(themes.get(themeIndex));
    }

    private void switchTo(String name) {
        ThemeStore.apply(name);
        ConfigManager.get().clickGuiTheme = name;
        ConfigManager.touch();
        target = name;
        themeIndex = Math.max(0, themes.indexOf(name));
        naming = false;
        // Switching reads the theme from disk, so anything typed since the last save is gone. Same rule
        // as closing the drawer, which also restores the snapshot.
        hint(Localization.tr("已切换到 ") + name + Localization.tr("，未保存的改动已丢弃"));
    }

    /** Saves the current palette under a new name and keeps editing that. */
    private void createTheme() {
        String name = nameField == null ? "" : nameField.text().trim();
        if (name.isEmpty()) {
            hint(Localization.tr("请输入名字"));
            return;
        }
        for (String existing : themes) {
            if (existing.equalsIgnoreCase(name)) {
                hint(Localization.tr("名字已存在"));
                return;
            }
        }
        if (!ThemeStore.save(name, GuiTheme.current(), GuiMetrics.current())) {
            hint(Localization.tr("新建失败，见日志"));
            return;
        }
        themes.clear();
        themes.addAll(ThemeStore.names());
        target = name;
        themeIndex = Math.max(0, themes.indexOf(name));
        snapshotColors = GuiTheme.current();
        snapshotMetrics = GuiMetrics.current();
        snapshotName = name;
        ConfigManager.get().clickGuiTheme = name;
        naming = false;
        hint(Localization.tr("已新建 ") + name);
    }

    private void hint(String text) {
        status = text;
        statusUntil = System.currentTimeMillis() + 3000;
    }

    private void select(String token) {
        selected = token;
        if (isColorToken(token)) {
            pickerToken = token;
            picker = new ImmediateColorPicker(() -> ThemeStore.color(token),
                    value -> {
                        ThemeStore.setColor(token, value);
                        ConfigManager.touch();
                    }, new PickerMemory());
        } else {
            picker = null;
            pickerToken = "";
        }
    }

    private boolean isColorToken(String token) {
        int index = tokens.indexOf(token);
        return index >= 0 && isColor.get(index);
    }

    // ---- geometry -------------------------------------------------------------------------

    private int height(int screenH) {
        return Math.min(screenH - 24, 460);
    }

    private int x(int screenW) {
        int docked = screenW - WIDTH - 8;
        int value = left >= 0 ? left : docked;
        // Clamped on read as well as on write: a window resize can leave a dragged position outside.
        return Math.max(8, Math.min(docked, value));
    }

    private int y(int screenH) {
        int docked = Math.max(8, (screenH - height(screenH)) / 2);
        int value = top >= 0 ? top : docked;
        return Math.max(8, Math.min(screenH - height(screenH) - 8, value));
    }

    private int pickerWidth() {
        return Math.round(ImmediateColorPicker.CONTENT_W * PICKER_SCALE);
    }

    private int pickerHeight() {
        return Math.round(ImmediateColorPicker.CONTENT_H * PICKER_SCALE);
    }

    private int editorBottom(int screenW, int screenH) {
        int top = y(screenH) + TITLE_H + THEME_ROW + PAD;
        int description = selected == null ? 0 : descriptionHeight();
        if (selected != null && isColorToken(selected)) {
            return top + pickerHeight() + description + PAD;
        }
        if (selected != null) {
            return top + ROW + description + PAD;
        }
        return top;
    }

    /** Two lines are enough for every token explanation at this drawer width. */
    private int descriptionHeight() {
        return ClickGuiFonts.lineHeight() * 2 + 2;
    }

    private int listTop(int screenW, int screenH) {
        return editorBottom(screenW, screenH) + 4;
    }

    private int listHeight(int screenW, int screenH) {
        return y(screenH) + height(screenH) - PAD - listTop(screenW, screenH);
    }

    // ---- drawing --------------------------------------------------------------------------

    void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int screenW, int screenH) {
        if (!open) {
            return;
        }
        int x = x(screenW);
        int y = y(screenH);
        int h = height(screenH);
        int radius = Math.max(6, GuiMetrics.current().cardRadius());
        ClickGuiStyle.shadow(graphics, x, y, WIDTH, h, radius);
        GuiPixels.fillRoundedRect(graphics, x, y, WIDTH, h, radius,
                (0xF4 << 24) | (ClickGuiStyle.cardFloat() & 0xFFFFFF));
        GuiPixels.fillRoundedTopRect(graphics, x, y, WIDTH, TITLE_H, radius,
                (0xF4 << 24) | (ClickGuiStyle.cardFloatTitle() & 0xFFFFFF));

        // The drawer got narrower; keep the title from running under the buttons.
        String title = fit(Localization.tr("主题") + " · " + target, WIDTH - 140);
        ClickGuiFonts.drawText(graphics, title, x + PAD,
                y + (TITLE_H - ClickGuiFonts.lineHeight()) / 2, ClickGuiStyle.text(), true);
        drawButton(graphics, x + WIDTH - 130, y + 2, 38, Localization.tr("保存"), mouseX, mouseY);
        drawButton(graphics, x + WIDTH - 88, y + 2, 38, Localization.tr("还原"), mouseX, mouseY);
        drawButton(graphics, x + WIDTH - 46, y + 2, 38, "×", mouseX, mouseY);

        drawThemeRow(graphics, x, y + TITLE_H, mouseX, mouseY);

        // Editor for the selected token.
        int top = y + TITLE_H + THEME_ROW + PAD;
        if (selected != null && isColorToken(selected) && picker != null) {
            // Scaled draw: the picker keeps its own 1:1 geometry, the pose shrinks it. Its hit tests are
            // called with the inverse transform (see mouseClicked).
            graphics.enableScissor(x + PAD, top, x + PAD + pickerWidth(), top + pickerHeight());
            org.joml.Matrix3x2fStack pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(x + PAD, top);
            pose.scale(PICKER_SCALE, PICKER_SCALE);
            picker.draw(graphics, 0, 0,
                    Math.round((mouseX - (x + PAD)) / PICKER_SCALE),
                    Math.round((mouseY - top) / PICKER_SCALE));
            pose.popMatrix();
            graphics.disableScissor();
        } else if (selected != null) {
            drawMetricEditor(graphics, x + PAD, top, mouseX, mouseY);
        }
        if (selected != null) {
            int descY = top + (isColorToken(selected) ? pickerHeight() : ROW) + 2;
            drawWrappedText(graphics, Localization.tr(ThemeStore.tokenDescription(selected)),
                    x + PAD, descY, WIDTH - PAD * 2, 2, ClickGuiStyle.textMuted(), false);
        }

        // Token list.
        int listTop = listTop(screenW, screenH);
        int listH = listHeight(screenW, screenH);
        graphics.enableScissor(x + 1, listTop, x + WIDTH - 1, listTop + listH);
        int rowY = listTop - scroll;
        for (String token : tokens) {
            StringBuilder label = new StringBuilder(token);
            if (isColorToken(token)) {
                label.append("   ");
            }
            boolean rowSelected = token.equals(selected);
            boolean hovered = mouseY >= rowY && mouseY < rowY + ROW && mouseX >= x + PAD
                    && mouseX < x + WIDTH - PAD;
            if (rowSelected) {
                graphics.fill(x + 1, rowY, x + WIDTH - 1, rowY + ROW, ClickGuiStyle.listRowCurrent());
            } else if (hovered) {
                graphics.fill(x + 1, rowY, x + WIDTH - 1, rowY + ROW, ClickGuiStyle.listRowHover());
            }
            int textY = rowY + (ROW - ClickGuiFonts.lineHeight()) / 2;
            ClickGuiFonts.drawText(graphics, token, x + PAD, textY,
                    rowSelected ? ClickGuiStyle.text() : ClickGuiStyle.textDim(), false);
            if (isColorToken(token)) {
                ClickGuiStyle.surface(graphics, x + WIDTH - PAD - 16, rowY + 4, 14, 10,
                        ThemeStore.color(token), 0);
            } else {
                String value = format(ThemeStore.metric(token));
                ClickGuiFonts.drawText(graphics, value,
                        x + WIDTH - PAD - ClickGuiFonts.width(value), textY,
                        ClickGuiStyle.textMuted(), false);
            }
            rowY += ROW;
        }
        graphics.disableScissor();
        drawScrollbar(graphics, x, listTop, listH);

        if (System.currentTimeMillis() <= statusUntil && !status.isEmpty()) {
            ClickGuiFonts.drawText(graphics, status, x + PAD, y + h - PAD - ClickGuiFonts.lineHeight(),
                    ClickGuiStyle.textMuted(), false);
        }
        // The confirmation covers the drawer: it is the only thing that can be clicked while it shows.
        if (confirmingDelete) {
            drawDeleteConfirm(graphics, x, y, h, mouseX, mouseY);
        }
    }

    /** Draws a short token explanation, wrapping at Chinese/character boundaries. */
    private static void drawWrappedText(GuiGraphicsExtractor graphics, String text, int x, int y,
                                        int maxWidth, int maxLines, int color, boolean shadow) {
        String remaining = text == null ? "" : text;
        int lineY = y;
        for (int line = 0; line < maxLines && !remaining.isEmpty(); line++) {
            int cut = remaining.length();
            while (cut > 0 && ClickGuiFonts.width(remaining.substring(0, cut)) > maxWidth) {
                cut--;
            }
            if (cut <= 0) {
                cut = 1;
            }
            String part = remaining.substring(0, cut);
            ClickGuiFonts.drawText(graphics, part, x, lineY, color, shadow);
            remaining = remaining.substring(cut);
            lineY += ClickGuiFonts.lineHeight();
        }
    }

    private void drawButton(GuiGraphicsExtractor graphics, int x, int y, int w, String text,
                            int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 16;
        ClickGuiStyle.button(graphics, x, y, w, 16, hovered, false);
        ClickGuiFonts.drawText(graphics, text, x + (w - ClickGuiFonts.width(text)) / 2,
                y + (16 - ClickGuiFonts.lineHeight()) / 2, ClickGuiStyle.text(), false);
    }

    /** The theme row: «‹ name ›» on the left, "new" on the right. */
    private void drawThemeRow(GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
        boolean hoverLeft = inside(mouseX, mouseY, x + PAD, y + 2, 14, 14);
        boolean hoverRight = inside(mouseX, mouseY, x + PAD + 18, y + 2, 14, 14);
        boolean hoverNew = inside(mouseX, mouseY, x + WIDTH - PAD - 16, y + 2, 16, 14);
        boolean hoverDelete = inside(mouseX, mouseY, x + WIDTH - PAD - 34, y + 2, 16, 14)
                && deletable();
        ClickGuiStyle.button(graphics, x + PAD, y + 2, 14, 14, hoverLeft, false);
        ClickGuiFonts.drawText(graphics, "<", x + PAD + 5, y + 3, ClickGuiStyle.text(), false);
        ClickGuiStyle.button(graphics, x + PAD + 18, y + 2, 14, 14, hoverRight, false);
        ClickGuiFonts.drawText(graphics, ">", x + PAD + 23, y + 3, ClickGuiStyle.text(), false);
        int nameX = x + PAD + 36;
        int nameW = WIDTH - PAD * 2 - 36 - 40;
        if (naming && nameField != null) {
            nameField.draw(graphics, nameX, y + 2, nameW, 14);
        } else {
            ClickGuiFonts.drawText(graphics, fit(target, nameW), nameX, y + 3, ClickGuiStyle.text(),
                    true);
        }
        // Delete sits next to "new"; greyed out for the built-ins, which are code rather than files.
        ClickGuiStyle.button(graphics, x + WIDTH - PAD - 34, y + 2, 16, 14, hoverDelete, false);
        ClickGuiFonts.drawText(graphics, "-", x + WIDTH - PAD - 30, y + 3,
                deletable() ? ClickGuiStyle.text() : ClickGuiStyle.textMuted(), false);
        ClickGuiStyle.button(graphics, x + WIDTH - PAD - 16, y + 2, 16, 14, hoverNew, false);
        ClickGuiFonts.drawText(graphics, "+", x + WIDTH - PAD - 11, y + 3, ClickGuiStyle.text(),
                false);
    }

    /** Only themes stored as files can be deleted; Dark and Light are built in. */
    private boolean deletable() {
        return !target.equalsIgnoreCase("Dark") && !target.equalsIgnoreCase("Light");
    }

    /** The confirmation dialog, centred in the drawer. */
    private void drawDeleteConfirm(GuiGraphicsExtractor graphics, int x, int y, int h, int mouseX,
                                   int mouseY) {
        graphics.fill(x, y, x + WIDTH, y + h, 0x99000000);
        int mw = WIDTH - 40;
        int mh = 66;
        int mx0 = x + (WIDTH - mw) / 2;
        int my0 = y + (h - mh) / 2;
        GuiPixels.fillRoundedRect(graphics, mx0, my0, mw, mh, 6,
                (0xFF << 24) | (ClickGuiStyle.cardFloat() & 0xFFFFFF));
        ClickGuiFonts.drawText(graphics,
                fit(Localization.tr("删除主题 ") + target, mw - 20), mx0 + 10, my0 + 10,
                ClickGuiStyle.text(), true);
        drawButton(graphics, mx0 + mw - 96, my0 + mh - 24, 42, Localization.tr("删除"), mouseX,
                mouseY);
        drawButton(graphics, mx0 + mw - 50, my0 + mh - 24, 42, Localization.tr("取消"), mouseX,
                mouseY);
    }

    /** Deletes the current theme and falls back to the built-in dark palette. */
    private void deleteTheme() {
        String name = target;
        confirmingDelete = false;
        if (!ThemeStore.delete(name)) {
            hint(Localization.tr("删除失败，内置主题不可删除"));
            return;
        }
        ThemeStore.apply("Dark");
        ConfigManager.get().clickGuiTheme = "Dark";
        ConfigManager.touch();
        themes.clear();
        themes.addAll(ThemeStore.names());
        target = "Dark";
        themeIndex = 0;
        snapshotColors = GuiTheme.current();
        snapshotMetrics = GuiMetrics.current();
        snapshotName = "Dark";
        hint(Localization.tr("已删除 ") + name);
    }

    /** Whether a point is inside a rect. */
    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void drawMetricEditor(GuiGraphicsExtractor graphics, int x, int y, int mouseX,
                                  int mouseY) {
        double value = ThemeStore.metric(selected);
        double min = minMetric(selected);
        double max = maxMetric(selected);
        String caption = selected + " = " + format(value);
        ClickGuiFonts.drawText(graphics, caption, x, y + 2, ClickGuiStyle.text(), true);
        int barY = y + 16;
        int barW = WIDTH - PAD * 2;
        ClickGuiStyle.pill(graphics, x, barY, barW, 4, ClickGuiStyle.fieldInner());
        double t = (value - min) / Math.max(0.0001, max - min);
        int filled = (int) Math.round(barW * Math.max(0, Math.min(1, t)));
        ClickGuiStyle.pill(graphics, x, barY, Math.max(1, filled), 4, ClickGuiStyle.accent());
        GuiPixels.fillRoundedRect(graphics, x + filled - 3, barY - 4, 6, 12, 3,
                ClickGuiStyle.text());
    }

    private void drawScrollbar(GuiGraphicsExtractor graphics, int x, int top, int viewH) {
        int content = tokens.size() * ROW;
        if (content <= viewH) {
            return;
        }
        int barX = x + WIDTH - 4;
        graphics.fill(barX, top, barX + 2, top + viewH, 0x30FFFFFF);
        int thumb = Math.max(16, viewH * viewH / content);
        int maxScroll = Math.max(1, content - viewH);
        int thumbY = top + (viewH - thumb) * Math.min(scroll, maxScroll) / maxScroll;
        graphics.fill(barX, thumbY, barX + 2, thumbY + thumb, ClickGuiStyle.textDim());
    }

    // ---- input ----------------------------------------------------------------------------

    boolean mouseClicked(double mx, double my, int screenW, int screenH, boolean shift,
                         boolean ctrl) {
        if (!open) {
            return false;
        }
        int x = x(screenW);
        int y = y(screenH);
        if (mx < x || mx >= x + WIDTH || my < y || my >= y + height(screenH)) {
            return false;
        }
        if (confirmingDelete) {
            int mw = WIDTH - 40;
            int mh = 66;
            int m0x = x + (WIDTH - mw) / 2;
            int m0y = y + (height(screenH) - mh) / 2;
            if (my >= m0y + mh - 24 && my < m0y + mh - 8) {
                if (mx >= m0x + mw - 96 && mx < m0x + mw - 54) {
                    deleteTheme();
                    return true;
                }
                if (mx >= m0x + mw - 50 && mx < m0x + mw - 8) {
                    confirmingDelete = false;
                    return true;
                }
            }
            return true;
        }
        if (my < y + TITLE_H) {
            if (mx >= x + WIDTH - 130 && mx < x + WIDTH - 92) {
                save();
            } else if (mx >= x + WIDTH - 88 && mx < x + WIDTH - 50) {
                restore();
            } else if (mx >= x + WIDTH - 46) {
                close();
            } else {
                // Drag by the title bar: the drawer is wider than the space beside the panel on small
                // windows, so it has to be movable out of the way.
                moving = true;
                grabX = mx - x;
                grabY = my - y;
            }
            return true;
        }
        int themeY = y + TITLE_H;
        if (my >= themeY && my < themeY + THEME_ROW) {
            if (mx >= x + PAD && mx < x + PAD + 14) {
                cycleTheme(-1);
            } else if (mx >= x + PAD + 18 && mx < x + PAD + 32) {
                cycleTheme(1);
            } else if (mx >= x + WIDTH - PAD - 34 && mx < x + WIDTH - PAD - 18) {
                if (deletable()) {
                    confirmingDelete = true;
                } else {
                    hint(Localization.tr("内置主题不能删除"));
                }
            } else if (mx >= x + WIDTH - PAD - 16) {
                naming = true;
                nameField = new ImmediateTextField("theme.name", "", "", 16, c -> true);
                nameField.focus();
            } else if (naming && nameField != null) {
                nameField.mouseClicked(mx, my, x + PAD + 36, themeY + 2,
                        WIDTH - PAD * 2 - 56, 14, shift);
            }
            return true;
        }
        int editorTop = y + TITLE_H + THEME_ROW + PAD;
        if (selected != null && isColorToken(selected) && picker != null
                && my < editorTop + pickerHeight() + 4) {
            picker.mouseClicked((mx - (x + PAD)) / PICKER_SCALE,
                    (my - editorTop) / PICKER_SCALE, 0, 0, shift);
            return true;
        }
        if (selected != null && !isColorToken(selected) && my < editorTop + 28) {
            dragging = true;
            setMetricAt(mx - (x + PAD));
            return true;
        }
        int listTop = listTop(screenW, screenH);
        if (my >= listTop && my < listTop + listHeight(screenW, screenH)) {
            int index = (int) ((my - listTop + scroll) / ROW);
            if (index >= 0 && index < tokens.size()) {
                select(tokens.get(index));
            }
        }
        return true;
    }

    boolean mouseDragged(double mx, double my, int screenW, int screenH) {
        if (!open || confirmingDelete) {
            return false;
        }
        if (moving) {
            // Clamped while dragging (not only when drawn) so the drawer cannot be lost off-screen.
            left = Math.max(8, Math.min(screenW - WIDTH - 8, (int) Math.round(mx - grabX)));
            top = Math.max(8,
                    Math.min(screenH - height(screenH) - 8, (int) Math.round(my - grabY)));
            return true;
        }
        if (picker != null && picker.mouseDragged((mx - (x(screenW) + PAD)) / PICKER_SCALE,
                (my - (y(screenH) + TITLE_H + THEME_ROW + PAD)) / PICKER_SCALE, 0, 0)) {
            return true;
        }
        if (dragging) {
            setMetricAt(mx - (x(screenW) + PAD));
            return true;
        }
        return false;
    }

    void mouseReleased() {
        dragging = false;
        moving = false;
        if (picker != null) {
            picker.mouseReleased();
        }
    }

    boolean mouseScrolled(double mx, double my, double amount, int screenW, int screenH) {
        if (!open || confirmingDelete || mx < x(screenW)) {
            return false;
        }
        int content = tokens.size() * ROW;
        int viewH = listHeight(screenW, screenH);
        int max = Math.max(0, content - viewH);
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.round(amount * 18)));
        return true;
    }

    boolean keyPressed(int key, boolean shift, boolean ctrl) {
        if (!open) {
            return false;
        }
        if (confirmingDelete) {
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                confirmingDelete = false;
            }
            // While the confirmation shows, keys go to it and nowhere else.
            return true;
        }
        if (naming && nameField != null) {
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
                    || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
                createTheme();
                return true;
            }
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                naming = false;
                nameField.blur();
                return true;
            }
            if (nameField.keyPressed(key, shift, ctrl)) {
                return true;
            }
        }
        if (picker != null && picker.keyPressed(key, shift, ctrl)) {
            return true;
        }
        return false;
    }

    boolean charTyped(char c) {
        if (!open || confirmingDelete) {
            return false;
        }
        if (naming && nameField != null) {
            return nameField.charTyped(c);
        }
        return picker != null && picker.charTyped(c);
    }

    private void setMetricAt(double localX) {
        if (selected == null) {
            return;
        }
        double t = Math.max(0, Math.min(1, localX / (WIDTH - PAD * 2)));
        double value = minMetric(selected) + t * (maxMetric(selected) - minMetric(selected));
        ThemeStore.setMetric(selected, value);
        ConfigManager.touch();
    }

    /** Truncates with an ellipsis so a label fits {@code maxW}. */
    private static String fit(String text, int maxW) {
        if (ClickGuiFonts.width(text) <= maxW) {
            return text;
        }
        String cut = text;
        while (cut.length() > 1 && ClickGuiFonts.width(cut + "…") > maxW) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "…";
    }

    private static String format(double value) {
        return Math.abs(value - Math.rint(value)) < 0.0001 ? Long.toString(Math.round(value))
                : String.format("%.2f", value);
    }

    private static double minMetric(String name) {
        return name.equals("textScale") ? 0.6 : 0;
    }

    private static double maxMetric(String name) {
        return switch (name) {
            case "rowHeight" -> 40;
            case "controlHeight" -> 40;
            case "controlWidth" -> 260;
            case "switchWidth" -> 64;
            case "switchHeight" -> 24;
            case "knobSize" -> 24;
            case "cardRadius" -> 12;
            default -> 64;
        };
    }
}
