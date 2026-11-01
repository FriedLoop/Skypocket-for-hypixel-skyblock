package dev.skypocket.gui;

import dev.skypocket.gui.setting.Setting;
import dev.skypocket.gui.setting.SettingsView;
import dev.skypocket.gui.widget.ClickGuiStyle;
import dev.skypocket.gui.widget.GuiMetrics;
import dev.skypocket.gui.widget.ImmediateColorPicker;
import dev.skypocket.render.ClickGuiFonts;
import dev.skypocket.render.GuiPixels;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/**
 * The settings card of the HUD position editor: right-clicking an element opens the settings of the
 * module that element belongs to.
 *
 * <p>Everything inside is {@link SettingsView}: rows, the dropdown lists and the colour picker are drawn
 * by that one renderer, so a control cannot look or behave differently here than on the settings page.
 * The card itself only supplies a box, a title bar and scrolling.
 */
final class HudSettingsCard {
    static final int WIDTH = 280;
    /** Width while a picker is open: the picker's own content is 303 wide plus the card padding. */
    private static final int PICKER_WIDTH = ImmediateColorPicker.CONTENT_W + 16;
    private static final int TITLE_H = 18;
    private static final int PAD = 8;
    private static final int CONTENT_GAP = 6;

    private String title = "";
    private List<Setting> settings = List.of();
    private final SettingsView.State state = new SettingsView.State();

    private boolean open;
    private int x;
    private int y;
    private int scroll;
    private boolean dragging;
    private double grabX;
    private double grabY;

    boolean isOpen() {
        return open;
    }

    /** Element keywords, one per HUD element - see {@link #belongs}. */
    private static final String[][] ELEMENT_KEYS = {
            {"BAR_HEALTH", "生命"}, {"BAR_MANA", "法力"}, {"BAR_AIR", "空气"},
            {"BAR_DEFENSE", "防御"}, {"BAR_SPEED", "速度"}, {"BAR_VITALITY", "活力"},
            {"STAT_HEALTH", "生命"}, {"STAT_MANA", "法力"}, {"STAT_DEFENSE", "防御"},
            {"STAT_SPEED", "速度"}, {"STAT_VITALITY", "活力"},
    };

    private static String keyword(String element) {
        for (String[] pair : ELEMENT_KEYS) {
            if (pair[0].equals(element)) {
                return pair[1];
            }
        }
        return null;
    }

    private static boolean belongs(String label, String element) {
        String own = keyword(element);
        if (own == null || label == null) {
            return true;
        }
        for (String[] pair : ELEMENT_KEYS) {
            if (label.contains(pair[1]) && !pair[1].equals(own)) {
                return false;
            }
        }
        return true;
    }

    // ---- opening --------------------------------------------------------------------------

    void open(String title, List<Setting> settings, String element, int nearX, int nearY,
              int screenW, int screenH) {
        this.title = title;
        // Groups become section headings plus their children, flattened: a group's header row is a
        // widget, and the card only draws rows without widgets - keeping the group as-is left 26 px of
        // blank space where its header should have been.
        List<Setting> all = new ArrayList<>();
        for (Setting s : settings) {
            flatten(all, s);
        }
        List<Setting> rows = new ArrayList<>(all.size());
        for (Setting s : all) {
            if (s instanceof Setting.Header || belongs(s.label(), element)) {
                rows.add(s);
            }
        }
        this.settings = rows;
        this.scroll = 0;
        this.open = true;
        this.dragging = false;
        int preferX = nearX + 24;
        this.x = preferX + width() <= screenW - 8 ? preferX : Math.max(8, nearX - width() - 24);
        this.y = nearY;
        clampPosition();
    }

    void close() {
        open = false;
        dragging = false;
    }

    private static void flatten(List<Setting> out, Setting s) {
        if (s instanceof Setting.GroupSetting group) {
            out.add(new Setting.Header(group.label()));
            for (Setting child : group.children()) {
                flatten(out, child);
            }
            return;
        }
        out.add(s);
    }

    // ---- geometry -------------------------------------------------------------------------

    private int width() {
        return anyPickerOpen() ? PICKER_WIDTH : WIDTH;
    }

    /** Whether a colour row is open, which is what makes the card wider. */
    private boolean anyPickerOpen() {
        for (SettingsView.RowAt row : rows()) {
            if (row.setting() instanceof Setting.ColorSetting colour && colour.expanded()) {
                return true;
            }
        }
        return false;
    }

    private int contentTop() {
        return y + TITLE_H + CONTENT_GAP;
    }

    private int contentX() {
        return x + PAD;
    }

    private int contentWidth() {
        return width() - PAD * 2;
    }

    private List<SettingsView.RowAt> rows() {
        return SettingsView.layout(settings, contentTop() - scroll);
    }

    private int contentHeight() {
        int total = 0;
        for (SettingsView.RowAt row : rows()) {
            total = Math.max(total, row.y() - (contentTop() - scroll) + row.setting().totalHeight());
        }
        return total;
    }

    private int height() {
        return TITLE_H + CONTENT_GAP + PAD + Math.min(contentHeight(), maxContentHeight());
    }

    private static int maxContentHeight() {
        return Math.max(120, net.minecraft.client.Minecraft.getInstance().getWindow()
                .getGuiScaledHeight() - 120);
    }

    boolean inside(double mx, double my) {
        return open && mx >= x && mx <= x + width() && my >= y && my <= y + height();
    }

    /**
     * Keeps the whole card on screen, not just its title bar.
     *
     * <p>Clamping against {@code screenH - TITLE_H} let the body hang off the bottom edge: the card grows
     * with its content (and again when a picker opens), so the limit has to be the card's own height.
     */
    private void clampPosition() {
        var window = net.minecraft.client.Minecraft.getInstance().getWindow();
        int screenW = window.getGuiScaledWidth();
        int screenH = window.getGuiScaledHeight();
        x = Math.max(4, Math.min(screenW - width() - 4, x));
        y = Math.max(4, Math.min(screenH - height() - 4, y));
        if (y < 4) {
            // Taller than the screen: pin to the top and let it scroll rather than slide off.
            y = 4;
        }
    }

    // ---- drawing --------------------------------------------------------------------------

    void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!open) {
            return;
        }
        clampPosition();
        int h = height();
        int w = width();
        int radius = Math.max(6, GuiMetrics.current().cardRadius());
        // 0xF0 alpha: the card floats over the game and stays slightly see-through. The shift must be
        // parenthesised - `0xF0 << 24 >>> 8` evaluates to 0x00F00000, i.e. fully transparent.
        ClickGuiStyle.shadow(graphics, x, y, w, h, radius);
        GuiPixels.fillRoundedRect(graphics, x, y, w, h, radius,
                (0xF0 << 24) | (ClickGuiStyle.cardFloat() & 0xFFFFFF));
        GuiPixels.fillRoundedTopRect(graphics, x, y, w, TITLE_H, radius,
                (0xF0 << 24) | (ClickGuiStyle.cardFloatTitle() & 0xFFFFFF));

        ClickGuiFonts.drawText(graphics, title, x + PAD, y + (TITLE_H - ClickGuiFonts.lineHeight()) / 2,
                ClickGuiStyle.text(), true);
        String close = "x";
        ClickGuiFonts.drawText(graphics, close, x + w - PAD - ClickGuiFonts.width(close),
                y + (TITLE_H - ClickGuiFonts.lineHeight()) / 2, ClickGuiStyle.textDim(), true);

        int clipTop = contentTop();
        int clipBottom = y + h - PAD;
        graphics.enableScissor(x, clipTop, x + w, clipBottom);
        SettingsView.draw(state, graphics, rows(), contentX(), contentWidth(), mouseX, mouseY, clipTop,
                clipBottom);
        graphics.disableScissor();
        drawScrollbar(graphics, clipTop, clipBottom);
    }

    private void drawScrollbar(GuiGraphicsExtractor graphics, int clipTop, int clipBottom) {
        int content = contentHeight();
        int view = clipBottom - clipTop;
        if (content <= view) {
            return;
        }
        int barX = x + width() - 4;
        graphics.fill(barX, clipTop, barX + 2, clipBottom, 0x30FFFFFF);
        int thumb = Math.max(16, view * view / content);
        int maxScroll = Math.max(1, content - view);
        int thumbY = clipTop + (view - thumb) * Math.min(scroll, maxScroll) / maxScroll;
        graphics.fill(barX, thumbY, barX + 2, thumbY + thumb, ClickGuiStyle.textDim());
    }

    // ---- interaction ----------------------------------------------------------------------

    boolean mouseClicked(double mx, double my) {
        if (!open || !inside(mx, my)) {
            return false;
        }
        if (my < y + TITLE_H) {
            if (mx >= x + width() - PAD - 12) {
                close();
                return true;
            }
            dragging = true;
            grabX = mx - x;
            grabY = my - y;
            return true;
        }
        int clipTop = contentTop();
        int clipBottom = y + height() - PAD;
        if (my < clipTop || my > clipBottom) {
            return true;
        }
        // Rows, dropdown lists and pickers all go through the shared renderer; it knows the order.
        SettingsView.click(state, rows(), contentX(), contentWidth(), mx, my, () -> { });
        return true;
    }

    boolean mouseDragged(double mx, double my) {
        if (!open) {
            return false;
        }
        if (dragging) {
            x = (int) Math.round(mx - grabX);
            y = (int) Math.round(my - grabY);
            clampPosition();
            return true;
        }
        return SettingsView.drag(state, rows(), contentX(), contentWidth(), mx, my);
    }

    boolean mouseReleased() {
        if (!open) {
            return false;
        }
        boolean used = SettingsView.release(state);
        if (dragging) {
            dragging = false;
            return true;
        }
        return used;
    }

    /** Typed characters go to the focused field of an open picker. */
    boolean charTyped(char c) {
        return open && SettingsView.charTyped(state, rows(), c);
    }

    /** Keys go to the focused field first, so Backspace edits text instead of nudging the element. */
    boolean keyPressed(int key, boolean shift, boolean ctrl) {
        return open && SettingsView.keyPressed(state, rows(), key, shift, ctrl);
    }

    /** Wheel: scroll the card; true when it handled the event. */
    boolean mouseScrolled(double mx, double my, double amount) {
        if (!open || !inside(mx, my)) {
            return false;
        }
        int view = (y + height() - PAD) - contentTop();
        int max = Math.max(0, contentHeight() - view);
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.round(amount * 20)));
        return true;
    }
}
