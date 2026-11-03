package dev.skypocket.ui;

/** A generic container. Visual properties will be attached by the renderer later. */
public class Box extends UiElement {
    public Box() {
        this(Style.DEFAULT);
    }

    public Box(Style style) {
        super(style);
    }

    public static Box of(Style style, UiElement... children) {
        Box box = new Box(style);
        box.add(children);
        return box;
    }
}
