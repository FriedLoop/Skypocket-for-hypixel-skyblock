package dev.skypocket.render;

import net.minecraft.client.gui.navigation.ScreenRectangle;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Tracks the GUI scissor stack without touching the private ScissorStack type.
 *
 * <p>{@code GuiGraphicsExtractor} applies a scissor by calling {@code enableScissor(x,y,w,h)} and
 * {@code disableScissor()}. Vanilla render states capture the active rectangle for their own vertices;
 * a custom text render state has to do the same or it escapes the panel/header/footer clips. The
 * extractor owns one short-lived screen frame, so a thread-local stack is enough.</p>
 */
public final class ScissorTracker {
    private static final ThreadLocal<Deque<ScreenRectangle>> STACK =
            ThreadLocal.withInitial(ArrayDeque::new);

    private ScissorTracker() {
    }

    public static void reset() {
        STACK.get().clear();
    }

    public static void push(ScreenRectangle rect) {
        Deque<ScreenRectangle> stack = STACK.get();
        ScreenRectangle next = rect;
        if (!stack.isEmpty()) {
            next = next.intersection(stack.peek());
        }
        stack.push(next);
    }

    public static void pop() {
        Deque<ScreenRectangle> stack = STACK.get();
        if (!stack.isEmpty()) {
            stack.pop();
        }
    }

    public static ScreenRectangle current() {
        return STACK.get().peek();
    }
}
