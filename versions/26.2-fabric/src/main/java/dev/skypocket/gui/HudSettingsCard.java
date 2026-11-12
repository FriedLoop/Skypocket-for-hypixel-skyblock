package dev.skypocket.gui;

import dev.skypocket.gui.setting.Setting;
import dev.skypocket.gui.widget.ClickGuiStyle;
import dev.skypocket.render.ClickGuiFonts;
import dev.skypocket.render.GuiPixels;
import dev.skypocket.ui.input.KeyEvent;
import dev.skypocket.ui.input.Keys;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The settings card of the HUD position editor: right-clicking an element opens the module settings
 * for that element. The body is a full UI v2 {@link SettingsPage}, so rows, dropdowns, segmented
 * choices and colour pickers look and behave exactly like the main settings pages.
 */
final class HudSettingsCard {
    private static final int WIDTH = 380;
    private static final int TITLE_H = 22;
    private static final int PAD = 6;
    private static final int MIN_BODY_H = 80;

    private String title = "";
    private List<Setting> rows = List.of();
    private SettingsPage page;
    private int popupWidth = WIDTH;
    private int bodyH = 200;

    private boolean open;
    private int x;
    private int y;
    private boolean dragging;
    private double grabX;
    private double grabY;

    boolean isOpen() {
        return open;
    }

    // ---- opening --------------------------------------------------------------------------

    void open(String title, List<Setting> settings, String element, int nearX, int nearY,
              int screenW, int screenH) {
        this.title = title;
        this.rows = filter(settings, element);
        this.popupWidth = Math.max(240, Math.min(WIDTH, screenW - 16));
        int pageW = popupWidth - PAD * 2;
        int maxBody = Math.max(MIN_BODY_H, screenH - TITLE_H - 40);
        page = buildPage(pageW, maxBody);
        page.layout();
        float preferred = page.contentHeight() + PAD * 2f;
        bodyH = (int) Math.max(MIN_BODY_H, Math.min(maxBody, Math.ceil(preferred)));
        if (bodyH < maxBody) {
            page = buildPage(pageW, bodyH);
            page.layout();
        }
        this.open = true;
        this.dragging = false;
        int preferX = nearX + 24;
        this.x = preferX + width() <= screenW - 8 ? preferX : Math.max(8, nearX - width() - 24);
        this.y = nearY;
        clampPosition();
    }

    private SettingsPage buildPage(int pageW, int pageH) {
        Feature popupFeature = new Feature() {
            @Override public String id() { return "hud_settings_popup"; }
            @Override public String name() { return title; }
            @Override public String rawName() { return title; }
            @Override public Category category() { return Category.RENDER; }
            @Override public boolean isEnabled() { return true; }
            @Override public void setEnabled(boolean value) { }
            @Override public List<Setting> settings() { return rows; }
        };
        return new SettingsPage(popupFeature, Ui2Theme.palette(), pageW, pageH,
                Math.max(180f, pageW - 30f));
    }

    void close() {
        if (page != null) {
            page.closePopups();
        }
        open = false;
        dragging = false;
    }

    private static List<Setting> filter(List<Setting> source, String element) {
        List<Setting> out = new ArrayList<>();
        for (Setting setting : source) {
            if (setting instanceof Setting.GroupSetting group) {
                List<Setting> children = filter(group.children(), element);
                if (!children.isEmpty()) {
                    out.add(new Setting.Header(group.label(), group.subtitle()));
                    out.addAll(children);
                }
            } else if (setting instanceof Setting.Header) {
                out.add(setting);
            } else if (belongs(setting.label(), element)) {
                out.add(setting);
            }
        }
        return out;
    }

    /** Element keywords, one per HUD element - see {@link #belongs}. */
    private static final String[][] ELEMENT_KEYS = {
            {"BAR_HEALTH", "生命"}, {"BAR_MANA", "法力"}, {"BAR_AIR", "空气"},
            {"BAR_DEFENSE", "防御"}, {"BAR_SPEED", "速度"}, {"BAR_VITALITY", "活力"},
            {"STAT_HEALTH", "生命"}, {"STAT_MANA", "法力"}, {"STAT_DEFENSE", "防御"},
            {"STAT_SPEED", "速度"}, {"STAT_VITALITY", "活力"},
            {"MINING_ABILITY", "冷却"}, {"MINING_ABILITY_SUBTITLE", "提醒"},
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

    // ---- geometry -------------------------------------------------------------------------

    private int width() {
        return popupWidth;
    }

    private int height() {
        return TITLE_H + bodyH + PAD;
    }

    private int pageX() {
        return x + PAD;
    }

    private int pageY() {
        return y + TITLE_H;
    }

    boolean inside(double mx, double my) {
        return open && mx >= x && mx <= x + width() && my >= y && my <= y + height();
    }

    private void clampPosition() {
        var window = net.minecraft.client.Minecraft.getInstance().getWindow();
        int screenW = window.getGuiScaledWidth();
        int screenH = window.getGuiScaledHeight();
        x = Math.max(4, Math.min(screenW - width() - 4, x));
        y = Math.max(4, Math.min(screenH - height() - 4, y));
    }

    // ---- drawing --------------------------------------------------------------------------

    void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!open || page == null) {
            return;
        }
        clampPosition();
        int w = width();
        int h = height();
        int radius = 6;
        ClickGuiStyle.shadow(graphics, x, y, w, h, radius);
        GuiPixels.fillRoundedRect(graphics, x, y, w, h, radius,
                (0xF0 << 24) | (ClickGuiStyle.cardFloat() & 0xFFFFFF));
        GuiPixels.fillRoundedTopRect(graphics, x, y, w, TITLE_H, radius,
                (0xF0 << 24) | (ClickGuiStyle.cardFloatTitle() & 0xFFFFFF));

        // The HUD editor is screen space: use half-size text so the settings page matches the
        // old 9px HUD UI instead of drawing 18px ClickGUI text at screen scale.
        ClickGuiFonts.pushScreenTextScale(0.5f);
        try {
            ClickGuiFonts.drawText(graphics, title, x + PAD,
                    y + (TITLE_H - ClickGuiFonts.lineHeight()) / 2, ClickGuiStyle.text(), true);
            String close = "x";
            ClickGuiFonts.drawText(graphics, close, x + w - PAD - ClickGuiFonts.width(close),
                    y + (TITLE_H - ClickGuiFonts.lineHeight()) / 2, ClickGuiStyle.textDim(), true);

            int clipTop = pageY();
            int clipBottom = y + TITLE_H + bodyH;
            graphics.enableScissor(x, clipTop, x + w, clipBottom);
            page.layout();
            Matrix3x2fStack pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(pageX(), pageY());
            page.draw(graphics, 1f, 1f, false);
            pose.popMatrix();
            graphics.disableScissor();
        } finally {
            ClickGuiFonts.popScreenTextScale();
        }
    }

    // ---- interaction ----------------------------------------------------------------------

    boolean mouseClicked(double mx, double my) {
        if (!open || !inside(mx, my) || page == null) {
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
        page.pointerDown(pageX(mx), pageY(my), 0, 0);
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
        if (page != null) {
            page.pointerMove(pageX(mx), pageY(my), 0);
        }
        return true;
    }

    boolean mouseReleased(MouseButtonEvent event) {
        if (!open || page == null) {
            return false;
        }
        page.pointerUp(pageX(event.x()), pageY(event.y()), 0, 0);
        if (dragging) {
            dragging = false;
            return true;
        }
        return true;
    }

    boolean charTyped(char c) {
        return open && page != null && page.textInput(String.valueOf(c));
    }

    boolean keyPressed(int key, boolean shift, boolean ctrl) {
        if (!open || page == null) {
            return false;
        }
        if (key == Keys.ESCAPE && page.hasOpenPopup()) {
            page.closePopups();
            return true;
        }
        int modifiers = shift ? dev.skypocket.ui.input.Modifiers.SHIFT : 0;
        if (ctrl) {
            modifiers |= dev.skypocket.ui.input.Modifiers.CONTROL;
        }
        return page.keyDown(new KeyEvent(key, 0, modifiers, true));
    }

    boolean keyReleased(int key, boolean shift, boolean ctrl) {
        if (!open || page == null) {
            return false;
        }
        int modifiers = shift ? dev.skypocket.ui.input.Modifiers.SHIFT : 0;
        if (ctrl) {
            modifiers |= dev.skypocket.ui.input.Modifiers.CONTROL;
        }
        return page.keyUp(new KeyEvent(key, 0, modifiers, false));
    }

    /** Wheel: scroll the v2 page; true when it handled the event. */
    boolean mouseScrolled(double mx, double my, double amount) {
        if (!open || !inside(mx, my) || page == null) {
            return false;
        }
        page.wheel(pageX(mx), pageY(my), (float) amount, 0);
        return true;
    }

    private float pageX(double mx) {
        return (float) mx - pageX();
    }

    private float pageY(double my) {
        return (float) my - pageY();
    }
}
