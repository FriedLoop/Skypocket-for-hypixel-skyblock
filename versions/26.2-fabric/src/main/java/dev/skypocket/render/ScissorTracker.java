package dev.skypocket.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Tracks the active GUI scissor per extractor, without touching the private ScissorStack type.
 *
 * <p>The mixin only observes {@code enableScissor}/{@code disableScissor}; it never cancels them,
 * changes their arguments, or changes their return value. Keeping one stack per extractor also means
 * a nested extractor from another mod (or the panel cache) cannot clear or inherit our clip.</p>
 */
public final class ScissorTracker {
    private static final Map<GuiGraphicsExtractor, Deque<ScreenRectangle>> STACKS = new WeakHashMap<>();

    private ScissorTracker() {
    }

    private static Deque<ScreenRectangle> stack(GuiGraphicsExtractor extractor) {
        return STACKS.computeIfAbsent(extractor, ignored -> new ArrayDeque<>());
    }

    public static void reset(GuiGraphicsExtractor extractor) {
        STACKS.remove(extractor);
    }

    public static void push(GuiGraphicsExtractor extractor, ScreenRectangle rect) {
        Deque<ScreenRectangle> stack = stack(extractor);
        ScreenRectangle next = rect;
        if (next == null) {
            stack.push(ScreenRectangle.empty());
            return;
        }
        if (!stack.isEmpty()) {
            ScreenRectangle parent = stack.peek();
            // ScreenRectangle.intersection returns null for disjoint rectangles. Pushing null into
            // ArrayDeque throws an NPE, so degrade to an empty clip instead.
            next = parent == null ? next : next.intersection(parent);
            if (next == null) {
                next = ScreenRectangle.empty();
            }
        }
        stack.push(next);
    }

    public static void pop(GuiGraphicsExtractor extractor) {
        Deque<ScreenRectangle> stack = STACKS.get(extractor);
        if (stack != null && !stack.isEmpty()) {
            stack.pop();
        }
    }

    public static ScreenRectangle current(GuiGraphicsExtractor extractor) {
        Deque<ScreenRectangle> stack = STACKS.get(extractor);
        return stack == null ? null : stack.peek();
    }
}
