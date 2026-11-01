package dev.skypocket.gui.widget;

/**
 * Mouse button numbers.
 *
 * <p>26.2 numbers them like GLFW (left 0, right 1); 26.3 renumbered the click event, where a left click
 * arrives as 1. Comparing against a literal {@code 0} therefore silently stopped every left click from
 * reaching the panel - hovering still worked because it uses coordinates, not buttons.
 */
public final class MouseButtons {

    private MouseButtons() {
    }

    /** True when this is a left click, whatever the platform numbers it. */
    public static boolean isLeft(int button) {
//#if MC>=260300
        return button == 1;
//#else
        return button == 0;
//#endif
    }

    /** True when this is a right click. */
    public static boolean isRight(int button) {
//#if MC>=260300
        return button == 2;
//#else
        return button == 1;
//#endif
    }
}
