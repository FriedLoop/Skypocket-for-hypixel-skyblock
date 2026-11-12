package dev.skypocket.gui;

import dev.skypocket.gui.widget.ClickGuiStyle;
import dev.skypocket.render.ClickGuiFonts;
import dev.skypocket.ui.Rect;
import dev.skypocket.ui.Style;
import dev.skypocket.ui.UiPainter;
import dev.skypocket.ui.input.PointerEvent;
import dev.skypocket.ui.mc.McUiPainter;
import dev.skypocket.ui.widget.Control;
import dev.skypocket.ui.widget.WidgetPalette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import static dev.skypocket.gui.PanelGeometry.CARD_BTN_H;
import static dev.skypocket.gui.PanelGeometry.CARD_BTN_W;
import static dev.skypocket.gui.PanelGeometry.CARD_BTN_X;
import static dev.skypocket.gui.PanelGeometry.CARD_BTN_Y;
import static dev.skypocket.gui.PanelGeometry.CARD_H;
import static dev.skypocket.gui.PanelGeometry.CARD_PAD;
import static dev.skypocket.gui.PanelGeometry.CARD_STATUS_H;
import static dev.skypocket.gui.PanelGeometry.CARD_STATUS_W;
import static dev.skypocket.gui.PanelGeometry.CARD_STATUS_X;
import static dev.skypocket.gui.PanelGeometry.CARD_STATUS_Y;
import static dev.skypocket.gui.PanelGeometry.CARD_TITLE_MAX_W;
import static dev.skypocket.gui.PanelGeometry.CARD_TITLE_X;
import static dev.skypocket.gui.PanelGeometry.CARD_TITLE_Y;
import static dev.skypocket.gui.PanelGeometry.CARD_W;
import static dev.skypocket.gui.PanelGeometry.ICON_SIZE;
import dev.skypocket.gui.widget.GuiMetrics;

/** One module card in the v2 homepage grid. */
final class ModuleCard extends Control {
    private final Feature feature;
    private final Runnable open;
    private final Runnable toggle;

    ModuleCard(Feature feature, Runnable open, Runnable toggle, Style style,
               WidgetPalette palette) {
        super(style, palette);
        this.feature = feature;
        this.open = open;
        this.toggle = toggle;
    }

    Feature feature() {
        return feature;
    }

    @Override
    public boolean focusable() {
        return false;
    }

    @Override
    protected void activate() {
        // Pointer handling decides between opening and toggling.
    }

    @Override
    public boolean onPointerDown(PointerEvent event) {
        float x = event.x() - ancestorOffsetX();
        float y = event.y() - ancestorOffsetY();
        if (inside(x, y, CARD_STATUS_X, CARD_STATUS_Y, CARD_STATUS_W, CARD_STATUS_H)) {
            toggle.run();
            return true;
        }
        boolean configurable = feature.hasSettings();
        if (!configurable) {
            return true;
        }
        if (inside(x, y, CARD_BTN_X, CARD_BTN_Y, CARD_BTN_W, CARD_BTN_H)
                || inside(x, y, 0, 0, CARD_W, CARD_H)) {
            open.run();
            return true;
        }
        return true;
    }

    @Override
    public boolean onPointerUp(PointerEvent event) {
        pressed = false;
        return true;
    }

    private boolean inside(float x, float y, float rx, float ry, float rw, float rh) {
        float lx = x - bounds().x();
        float ly = y - bounds().y();
        return lx >= rx && lx < rx + rw && ly >= ry && ly < ry + rh;
    }

    private static final int MARKER_SIZE = 40;

    @Override
    public void paint(UiPainter painter) {
        McUiPainter mc = (McUiPainter) painter;
        int cardColor = hovered ? ClickGuiStyle.cardHover() : ClickGuiStyle.card();
        int cardRadius = GuiMetrics.current().cardRadius();
        painter.shadow(bounds(), cardRadius);
        // Close the antialiased mask edge so the shadow cannot show as a dark 1px line.
        painter.fillRounded(bounds(), cardRadius, cardColor);
        painter.fillRounded(bounds(), cardRadius, cardColor);

        Identifier tex = ModuleIcon.texture(feature.id());
        if (!Minecraft.getInstance().getResourceManager().getResourceStack(tex).isEmpty()) {
            mc.graphics().blit(RenderPipelines.GUI_TEXTURED, tex,
                    (int) bounds().x() + CARD_PAD, (int) bounds().y() + CARD_PAD, 0, 0,
                    ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
        } else {
            painter.fill(new Rect(bounds().x() + CARD_PAD, bounds().y() + CARD_PAD,
                    ICON_SIZE, ICON_SIZE), ClickGuiStyle.accent());
            String letter = feature.name().isEmpty() ? "?" : feature.name().substring(0, 1);
            painter.text(letter,
                    bounds().x() + CARD_PAD + (ICON_SIZE - painter.textWidth(letter)) / 2f,
                    bounds().y() + CARD_PAD + (ICON_SIZE - painter.lineHeight()) / 2f,
                    ClickGuiStyle.text());
        }

        float markerX = bounds().x() + CARD_W - CARD_PAD - MARKER_SIZE;
        float markerY = bounds().y() + CARD_TITLE_Y - (MARKER_SIZE - painter.lineHeight()) / 2f;
        float titleMax = feature.skyBlockOnly()
                ? markerX - (bounds().x() + CARD_TITLE_X) - 3f
                : CARD_TITLE_MAX_W;
        painter.text(fitTitle(feature.name(), titleMax),
                bounds().x() + CARD_TITLE_X, bounds().y() + CARD_TITLE_Y,
                ClickGuiStyle.text());
        if (feature.skyBlockOnly()) {
            Identifier marker = ModuleIcon.marker();
            if (!Minecraft.getInstance().getResourceManager().getResourceStack(marker).isEmpty()) {
                mc.graphics().blit(RenderPipelines.GUI_TEXTURED, marker,
                        (int) markerX, (int) markerY, 0, 0,
                        MARKER_SIZE, MARKER_SIZE, MARKER_SIZE, MARKER_SIZE);
            } else {
                painter.text("SB", markerX + (MARKER_SIZE - painter.textWidth("SB")) / 2f,
                        markerY + (MARKER_SIZE - painter.lineHeight()) / 2f,
                        ClickGuiStyle.accent());
            }
        }

        float btnX = bounds().x() + CARD_BTN_X;
        float btnY = bounds().y() + CARD_BTN_Y;
        boolean configurable = feature.hasSettings();
        String options = dev.skypocket.i18n.Localization.tr(configurable ? "设置" : "无设置");
        if (configurable) {
            painter.surface(new Rect(btnX, btnY, CARD_BTN_W, CARD_BTN_H),
                    ClickGuiStyle.controlRadius(),
                    hovered ? ClickGuiStyle.buttonHover() : ClickGuiStyle.button(), 0);
        } else {
            painter.surface(new Rect(btnX, btnY, CARD_BTN_W, CARD_BTN_H),
                    ClickGuiStyle.controlRadius(), ClickGuiStyle.disabled(), 0);
        }
        painter.text(options, btnX + (CARD_BTN_W - painter.textWidth(options)) / 2f,
                btnY + 9f, configurable ? ClickGuiStyle.text() : ClickGuiStyle.disabledText());

        float stX = bounds().x() + CARD_STATUS_X;
        float stY = bounds().y() + CARD_STATUS_Y;
        painter.surface(new Rect(stX, stY, CARD_STATUS_W, CARD_STATUS_H),
                ClickGuiStyle.controlRadius(),
                feature.isEnabled() ? ClickGuiStyle.accent() : ClickGuiStyle.button(), 0);
        String status = feature.isEnabled() ? "ENABLED" : "DISABLED";
        painter.text(status, stX + (CARD_STATUS_W - painter.textWidth(status)) / 2f,
                stY + 7f, feature.isEnabled() ? ClickGuiStyle.text() : ClickGuiStyle.textMuted());
    }

    private static String fitTitle(String text, float maxWidth) {
        if (ClickGuiFonts.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int end = text.length();
        while (end > 1 && ClickGuiFonts.width(text.substring(0, end) + ellipsis) > maxWidth) {
            end--;
        }
        return text.substring(0, end) + ellipsis;
    }
}
