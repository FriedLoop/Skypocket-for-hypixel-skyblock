package dev.skypocket.gui;

import dev.skypocket.gui.setting.Setting;
import dev.skypocket.gui.widget.ClickGuiStyle;
import dev.skypocket.render.ClickGuiFonts;
import dev.skypocket.render.ClickGuiMetrics;
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

    /**
     * Scale the card's contents are drawn at.
     *
     * <p>The HUD editor works in screen units at 1:1, but the settings controls inside this card are
     * sized for the ClickGUI design canvas. Drawing them at 1:1 made every row far too tall for its
     * text - a 52px row wrapped around 9px glyphs. The card used to compensate by shrinking only the
     * text ({@code pushScreenTextScale}), which left the geometry untouched and produced exactly that
     * mismatch.
     *
     * <p>Scaling the whole content block fixes it: a row becomes 52 × this many screen pixels and its
     * text 18 × this many, so the ratio matches the main panel. The rasterisation scale is set to the
     * same value through {@link ClickGuiMetrics#withScale}; those two must agree or the glyphs get
     * resampled and go soft.
     */
    private static final float CONTENT_SCALE = 0.5f;

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

        // Two unit systems meet here. The shell is in screen pixels - it is positioned against the
        // window - while the page is in design units and scaled by CONTENT_SCALE when drawn. So the
        // page is built at screen size / CONTENT_SCALE, and bodyH (a shell measurement) comes back
        // through the same factor.
        int pageW = Math.round((popupWidth - PAD * 2) / CONTENT_SCALE);
        int bodyMax = Math.round(Math.max(MIN_BODY_H, screenH - TITLE_H - 40) / CONTENT_SCALE);

        // Two passes: build at the maximum, measure what the rows actually need, then rebuild at that
        // height so the scroll view is sized to the content rather than to the window.
        page = buildPage(pageW, bodyMax);
        page.layout();
        int bodyDesign = Math.max(Math.round(MIN_BODY_H / CONTENT_SCALE),
                Math.min(bodyMax, (int) Math.ceil(page.contentHeight())));
        if (bodyDesign < bodyMax) {
            page = buildPage(pageW, bodyDesign);
            page.layout();
        }
        bodyH = Math.round(bodyDesign * CONTENT_SCALE);

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

        // Everything inside the shell is drawn under one scale, and the rasterisation scale is set to
        // match it - see CONTENT_SCALE. withScale restores the previous raster scale afterwards, so
        // the HUD editor's own 1:1 text is unaffected.
        //
        // The title bar is inside that scale too. It used to be drawn before the pose was scaled
        // while the rasterisation scale was already lowered, so its glyphs were rasterised at
        // CONTENT_SCALE and then drawn at 1:1 - stretched 2x and soft. Anything drawn inside this
        // block must go through the same pose.
        ClickGuiMetrics.withScale(CONTENT_SCALE, () -> {
            Matrix3x2fStack pose = graphics.pose();
            pose.pushMatrix();
            // Translate first, then scale: the title is drawn in the card's own coordinate space, so
            // without the translate it landed at the screen origin. Its coordinates below are relative
            // to the card, which is why the bar's metrics are divided by CONTENT_SCALE.
            pose.translate(x, y);
            pose.scale(CONTENT_SCALE, CONTENT_SCALE);
            float barH = TITLE_H / CONTENT_SCALE;
            float pad = PAD / CONTENT_SCALE;
            float fullW = w / CONTENT_SCALE;
            float lineH = ClickGuiFonts.lineHeight();
            ClickGuiFonts.drawText(graphics, title, pad,
                    (barH - lineH) / 2, ClickGuiStyle.text(), true);
            String close = "x";
            ClickGuiFonts.drawText(graphics, close,
                    fullW - pad - ClickGuiFonts.width(close),
                    (barH - lineH) / 2, ClickGuiStyle.textDim(), true);
            pose.popMatrix();

            int clipTop = pageY();
            int clipBottom = y + TITLE_H + bodyH;
            graphics.enableScissor(x, clipTop, x + w, clipBottom);
            page.layout();
            pose.pushMatrix();
            pose.translate(pageX(), pageY());
            // Design units -> screen: the page is laid out at CONTENT_SCALE of its authored size, so
            // its rows and its text shrink together instead of the text shrinking alone.
            pose.scale(CONTENT_SCALE, CONTENT_SCALE);
            page.draw(graphics, 1f, 1f, false);
            pose.popMatrix();
            graphics.disableScissor();
        });
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
        // Shared modifier bitfield: the ad-hoc version here was the same shape as the theme
        // drawer's, and that one forgot Ctrl entirely. One definition keeps them from drifting.
        return page.keyDown(new KeyEvent(key, 0,
                dev.skypocket.ui.mc.InputBridge.modifiersOf(shift, ctrl, false), true));
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

    /**
     * Screen point -> page (design) coordinates.
     *
     * <p>Divides by {@link #CONTENT_SCALE} because the page is drawn under {@code pose.scale}, so one
     * page unit covers fewer than one screen pixel. Subtracting the page origin is not enough on its
     * own: without the division every click would land further from the card's corner than the cursor,
     * which is the same class of error as forgetting an ancestor offset.
     */
    private float pageX(double mx) {
        return (float) ((mx - pageX()) / CONTENT_SCALE);
    }

    private float pageY(double my) {
        return (float) ((my - pageY()) / CONTENT_SCALE);
    }
}
