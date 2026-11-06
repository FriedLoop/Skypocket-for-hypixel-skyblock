package dev.skypocket.ui.mc;

import dev.skypocket.render.ClickGuiFonts;
import dev.skypocket.render.GuiPixels;
import dev.skypocket.ui.Rect;
import dev.skypocket.ui.UiPainter;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The single production painter for the reusable UI: MC rounded surfaces and device-pixel snapping
 * live here, while widgets only see the platform-neutral UiPainter contract.
 */
public final class McUiPainter implements UiPainter {
    private final GuiGraphicsExtractor graphics;
    private final java.util.ArrayDeque<Boolean> clipStack = new java.util.ArrayDeque<>();

    public McUiPainter(GuiGraphicsExtractor graphics) {
        this.graphics = graphics;
    }

    public GuiGraphicsExtractor graphics() {
        return graphics;
    }

    @Override
    public void fill(Rect rect, int color) {
        int x0 = Math.round(rect.x());
        int y0 = Math.round(rect.y());
        int x1 = Math.max(x0, Math.round(rect.right()));
        int y1 = Math.max(y0, Math.round(rect.bottom()));
        graphics.fill(x0, y0, x1, y1, color);
    }

    @Override
    public void fillRounded(Rect rect, float radius, int color) {
        int x = Math.round(rect.x());
        int y = Math.round(rect.y());
        int w = Math.max(1, Math.round(rect.width()));
        int h = Math.max(1, Math.round(rect.height()));
        GuiPixels.fillRoundedRect(graphics, x, y, w, h, Math.max(0, Math.round(radius)), color);
    }

    @Override
    public void fillRoundedTop(Rect rect, float radius, int color) {
        int x = Math.round(rect.x());
        int y = Math.round(rect.y());
        int w = Math.max(1, Math.round(rect.width()));
        int h = Math.max(1, Math.round(rect.height()));
        GuiPixels.fillRoundedTopRect(graphics, x, y, w, h, Math.max(0, Math.round(radius)), color);
    }

    @Override
    public void fillRoundedBottom(Rect rect, float radius, int color) {
        int x = Math.round(rect.x());
        int y = Math.round(rect.y());
        int w = Math.max(1, Math.round(rect.width()));
        int h = Math.max(1, Math.round(rect.height()));
        GuiPixels.fillRoundedBottomRect(graphics, x, y, w, h, Math.max(0, Math.round(radius)),
                color);
    }

    @Override
    public void outline(Rect rect, int color) {
        int x = Math.round(rect.x());
        int y = Math.round(rect.y());
        int w = Math.max(1, Math.round(rect.width()));
        int h = Math.max(1, Math.round(rect.height()));
        graphics.outline(x, y, w, h, color);
    }

    @Override
    public void outlineRounded(Rect rect, float radius, int color) {
        surface(rect, radius, 0, color);
    }

    @Override
    public void strokeRounded(Rect rect, float radius, int color) {
        int x = Math.round(rect.x());
        int y = Math.round(rect.y());
        int w = Math.max(1, Math.round(rect.width()));
        int h = Math.max(1, Math.round(rect.height()));
        GuiPixels.strokeRoundedRect(graphics, x, y, w, h, Math.max(0, Math.round(radius)),
                color);
    }

    @Override
    public void surface(Rect rect, float radius, int fill, int border) {
        if (border == 0) {
            // Card language: brightness and shadow, not a permanent border ring.
            fillRounded(rect, radius, fill);
            return;
        }
        int x = Math.round(rect.x());
        int y = Math.round(rect.y());
        int w = Math.max(1, Math.round(rect.width()));
        int h = Math.max(1, Math.round(rect.height()));
        GuiPixels.fillRoundedBorderedRect(graphics, x, y, w, h,
                Math.max(0, Math.round(radius)), fill, border);
    }

    @Override
    public void pill(Rect rect, int fill, int border) {
        int x = Math.round(rect.x());
        int y = Math.round(rect.y());
        int w = Math.max(1, Math.round(rect.width()));
        int h = Math.max(1, Math.round(rect.height()));
        int radius = Math.max(1, h / 2);
        if (border == 0) {
            GuiPixels.fillRoundedRect(graphics, x, y, w, h, radius, fill);
        } else {
            GuiPixels.fillRoundedBorderedRect(graphics, x, y, w, h, radius, fill, border);
        }
    }

    @Override
    public void shadow(Rect rect, float radius) {
        int x = Math.round(rect.x());
        int y = Math.round(rect.y());
        int w = Math.max(1, Math.round(rect.width()));
        int h = Math.max(1, Math.round(rect.height()));
        int r = Math.max(1, Math.round(radius));
        // Four low-alpha rings instead of two hard layers. This is still pure fills; the page cache
        // pays for them once per bake, not every frame.
        GuiPixels.fillRoundedRect(graphics, x - 4, y + 4, w + 8, h + 4, r + 4, 0x0A000000);
        GuiPixels.fillRoundedRect(graphics, x - 3, y + 3, w + 6, h + 3, r + 3, 0x0E000000);
        GuiPixels.fillRoundedRect(graphics, x - 2, y + 2, w + 4, h + 2, r + 2, 0x12000000);
        GuiPixels.fillRoundedRect(graphics, x - 1, y + 1, w + 2, h + 1, r + 1, 0x18000000);
    }

    @Override
    public void fillGradient(Rect rect, int top, int bottom) {
        int x = Math.round(rect.x());
        int y = Math.round(rect.y());
        int w = Math.max(1, Math.round(rect.width()));
        int h = Math.max(1, Math.round(rect.height()));
        graphics.fillGradient(x, y, x + w, y + h, top, bottom);
    }

    @Override
    public void fillHorizontalGradient(Rect rect, int left, int right) {
        int x = Math.round(rect.x());
        int y = Math.round(rect.y());
        int w = Math.max(1, Math.round(rect.width()));
        int h = Math.max(1, Math.round(rect.height()));
        // GuiGraphicsExtractor only exposes a vertical gradient; rotate the pose for horizontal.
        org.joml.Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.rotate((float) (Math.PI / 2));
        graphics.fillGradient(0, -w, h, 0, right, left);
        pose.popMatrix();
    }

    @Override
    public void text(String text, float x, float y, int color) {
        ClickGuiFonts.drawText(graphics, dev.skypocket.i18n.Localization.auto(text),
                x, y, color, false);
    }

    @Override
    public void textRaw(String text, float x, float y, int color) {
        ClickGuiFonts.drawText(graphics, text, x, y, color, false);
    }

    @Override
    public void textScaled(String text, float x, float y, int color, float scale) {
        ClickGuiFonts.drawTextScaled(graphics, dev.skypocket.i18n.Localization.auto(text),
                x, y, color, false, scale);
    }

    @Override
    public void textWeighted(String text, float x, float y, int color, float scale,
                             dev.skypocket.ui.TextWeight weight) {
        dev.skypocket.render.FontLab.Weight mapped = switch (weight) {
            case MEDIUM -> dev.skypocket.render.FontLab.Weight.MEDIUM;
            case SEMIBOLD -> dev.skypocket.render.FontLab.Weight.SEMIBOLD;
            default -> dev.skypocket.render.FontLab.Weight.REGULAR;
        };
        ClickGuiFonts.drawTextWeighted(graphics, dev.skypocket.i18n.Localization.auto(text),
                x, y, color, scale, mapped);
    }

    @Override
    public float textWidth(String text) {
        return ClickGuiFonts.width(dev.skypocket.i18n.Localization.auto(text));
    }

    @Override
    public float lineHeight() {
        return ClickGuiFonts.lineHeight();
    }

    @Override
    public void pushClip(Rect rect) {
        int x0 = (int) Math.floor(rect.x());
        int y0 = (int) Math.floor(rect.y());
        int x1 = (int) Math.ceil(rect.right());
        int y1 = (int) Math.ceil(rect.bottom());
        if (x1 <= x0 || y1 <= y0) {
            // Empty clips are legal in layout but not in RenderPearl's scissor API. Remember that this
            // level did not enable a scissor so the matching pop does not disable the parent's.
            clipStack.push(false);
            return;
        }
        graphics.enableScissor(x0, y0, x1, y1);
        clipStack.push(true);
    }

    @Override
    public void popClip() {
        Boolean enabled = clipStack.poll();
        if (enabled != null && enabled) {
            graphics.disableScissor();
        }
    }
}
