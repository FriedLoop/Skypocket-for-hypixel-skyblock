package dev.skypocket.ui;

import dev.skypocket.ui.input.WheelEvent;

/**
 * A vertical scroll viewport with one content box. Content must be laid out with its intrinsic height;
 * the viewport clips it and applies a visual offset, so layout bounds stay stable while scrolling.
 */
public final class ScrollView extends Box {
    private final Box content;
    private final float scrollStep;
    private float scrollY;

    public ScrollView(Style style, Box content) {
        this(style, content, 24f);
    }

    public ScrollView(Style style, Box content, float scrollStep) {
        super(Style.builder().from(style).direction(Style.Direction.COLUMN).build());
        this.content = content;
        this.scrollStep = Math.max(1f, scrollStep);
        Style contentStyle = Style.builder().from(content.style())
                .flexShrink(0f)
                .width(content.style().width().isAuto() ? Dimension.percent(100f)
                        : content.style().width())
                .build();
        content.style(contentStyle);
        add(content);
        clipsChildren(true);
    }

    public Box content() {
        return content;
    }

    public float scrollY() {
        return scrollY;
    }

    public float maxScroll() {
        float viewport = Math.max(0f, bounds().height() - style().padding().vertical());
        return Math.max(0f, content.bounds().height() - viewport);
    }

    public void scrollTo(float value) {
        float max = maxScroll();
        scrollY = Math.max(0f, Math.min(max, value));
        content.offset(0f, -scrollY);
    }

    /** Re-clamps after a layout pass changed the content height. */
    public void refresh() {
        scrollTo(scrollY);
    }

    @Override
    public boolean onWheel(WheelEvent event) {
        if (maxScroll() <= 0f) {
            return false;
        }
        float before = scrollY;
        // Positive wheel delta means "scroll up" in the host event model, which moves the content
        // toward its start.
        scrollTo(scrollY - event.delta() * scrollStep);
        return scrollY != before;
    }
}
