package dev.skypocket.tooltip;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;
import org.joml.Vector2i;
import org.joml.Vector2ic;

/**
 * Tracks the vertical scroll offset of a tooltip and applies it to the tooltip's
 * position. Over-tall tooltips are pinned to the top of the screen and scrolled
 * through; with the "always scrollable" option, tooltips that fit are also
 * scrollable while being kept fully on screen. Off-screen parts are clipped by the
 * framebuffer.
 */
public final class TooltipScroller {
    /** Screen margin the pinned tooltip keeps from the top/bottom edges. */
    private static final int MARGIN = 4;
    /** Scroll distance per wheel notch at 1.0 sensitivity, in pixels. */
    private static final int BASE_SCROLL_STEP = 12;
    /** A mouse move larger than this (or a height change) resets the scroll offset. */
    private static final int RESET_DISTANCE = 8;

    private static int scrollOffset = 0;
    private static int lastX = Integer.MIN_VALUE;
    private static int lastY = Integer.MIN_VALUE;
    private static int lastHeight = -1;

    private TooltipScroller() {
    }

    /** Adjusts a tooltip position, applying scroll when it overflows or when enabled. */
    public static Vector2ic adjust(int px, int py, int tooltipHeight, int screenHeight, int mouseX, int mouseY) {
        ModConfig cfg = ConfigManager.get();
        int visible = screenHeight - 2 * MARGIN;
        boolean overflow = tooltipHeight > visible;
        if (!overflow && !cfg.scrollableTooltipsBlock.scrollableTooltipsAlwaysScroll) {
            return new Vector2i(px, py);
        }
        resetIfChanged(mouseX, mouseY, tooltipHeight);

        int minTop = MARGIN;
        int maxTop = screenHeight - MARGIN - tooltipHeight;
        int lo;
        int hi;
        int baseTop;
        if (overflow) {
            baseTop = MARGIN;
            lo = 0;
            hi = tooltipHeight - visible;
        } else {
            baseTop = py;
            lo = py - maxTop; // <= 0 when the tooltip fits
            hi = py - minTop; // >= 0
        }
        scrollOffset = Math.max(lo, Math.min(hi, scrollOffset));
        return new Vector2i(px, baseTop - scrollOffset);
    }

    /** Applies a mouse-wheel scroll; positive {@code verticalAmount} scrolls up. */
    public static void scroll(double verticalAmount) {
        float sensitivity = ConfigManager.get().scrollableTooltipsBlock.scrollableTooltipsSensitivity;
        scrollOffset -= (int) Math.round(verticalAmount * BASE_SCROLL_STEP * sensitivity);
    }

    private static void resetIfChanged(int x, int y, int height) {
        if (Math.abs(x - lastX) > RESET_DISTANCE || Math.abs(y - lastY) > RESET_DISTANCE || height != lastHeight) {
            scrollOffset = 0;
            lastX = x;
            lastY = y;
            lastHeight = height;
        }
    }
}
