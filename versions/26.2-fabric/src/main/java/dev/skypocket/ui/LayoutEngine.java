package dev.skypocket.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * A small flex/box layout pass. It deliberately has no dependency on Minecraft or the renderer.
 *
 * <p>Width and height are border-box sizes (padding is inside, margin outside). The engine supports
 * auto/fixed/percent sizes, padding/margin, row/column flex, gap, grow/shrink, justification and
 * start/end/center/stretch alignment. It does not implement wrapping, grid, absolute positioning or
 * CSS cascade; those belong to later layers.</p>
 */
public final class LayoutEngine {
    private LayoutEngine() {
    }

    /** Measures and lays out the tree; returns the root's border box. */
    public static Rect layout(UiElement root, float availableWidth, float availableHeight,
                              TextMeasurer text) {
        Size measured = measureNode(root, availableWidth, availableHeight, text);
        // A root with an automatic size fills its host; an explicit root size is honoured.
        float rootWidth = root.style().width().isAuto() ? availableWidth : measured.width();
        float rootHeight = root.style().height().isAuto() ? availableHeight : measured.height();
        layoutNode(root, 0f, 0f, rootWidth, rootHeight, text);
        return root.bounds();
    }

    private static Size measureNode(UiElement node, float availableWidth, float availableHeight,
                                    TextMeasurer text) {
        Style style = node.style();
        Insets padding = style.padding();
        float width = style.width().resolve(availableWidth);
        float height = style.height().resolve(availableHeight);

        float contentAvailableWidth = width != width ? Math.max(0f, availableWidth - padding.horizontal())
                : Math.max(0f, width - padding.horizontal());
        float contentAvailableHeight = height != height ? Math.max(0f, availableHeight - padding.vertical())
                : Math.max(0f, height - padding.vertical());
        float contentWidth;
        float contentHeight;

        if (node instanceof Text textNode) {
            Size textSize = text.measure(textNode.value(), contentAvailableWidth);
            contentWidth = textSize.width();
            contentHeight = textSize.height();
        } else {
            List<UiElement> flow = new ArrayList<>();
            for (UiElement child : node.children()) {
                if (child.style().position() != Style.Position.ABSOLUTE) {
                    flow.add(child);
                }
            }
            List<Size> children = new ArrayList<>();
            for (UiElement child : flow) {
                children.add(measureNode(child, contentAvailableWidth, contentAvailableHeight, text));
            }
            float main = 0f;
            float cross = 0f;
            for (int i = 0; i < children.size(); i++) {
                UiElement child = flow.get(i);
                Size size = children.get(i);
                Insets margin = child.style().margin();
                if (style.direction() == Style.Direction.ROW) {
                    main += size.width() + margin.horizontal();
                    cross = Math.max(cross, size.height() + margin.vertical());
                } else {
                    main += size.height() + margin.vertical();
                    cross = Math.max(cross, size.width() + margin.horizontal());
                }
                if (i + 1 < children.size()) {
                    main += style.gap();
                }
            }
            contentWidth = style.direction() == Style.Direction.ROW ? main : cross;
            contentHeight = style.direction() == Style.Direction.ROW ? cross : main;
        }

        if (width != width) {
            width = contentWidth + padding.horizontal();
        }
        if (height != height) {
            height = contentHeight + padding.vertical();
        }
        return constrain(width, height, style, availableWidth, availableHeight);
    }

    private static void layoutNode(UiElement node, float x, float y, float width, float height,
                                   TextMeasurer text) {
        node.bounds(new Rect(x, y, Math.max(0f, width), Math.max(0f, height)));
        if (node instanceof Text || node.children().isEmpty()) {
            return;
        }

        Style style = node.style();
        Insets padding = style.padding();
        float contentX = x + padding.left();
        float contentY = y + padding.top();
        float contentWidth = Math.max(0f, width - padding.horizontal());
        float contentHeight = Math.max(0f, height - padding.vertical());
        boolean row = style.direction() == Style.Direction.ROW;

        List<UiElement> flow = new ArrayList<>();
        for (UiElement child : node.children()) {
            if (child.style().position() != Style.Position.ABSOLUTE) {
                flow.add(child);
            }
        }
        List<ChildLayout> children = new ArrayList<>();
        float mainBasis = 0f;
        float growTotal = 0f;
        float shrinkTotal = 0f;
        for (UiElement child : flow) {
            Size size = measureNode(child, contentWidth, contentHeight, text);
            Style childStyle = child.style();
            Insets margin = childStyle.margin();
            float basis = childStyle.flexBasis().isAuto()
                    ? (row ? size.width() : size.height())
                    : childStyle.flexBasis().resolve(row ? contentWidth : contentHeight);
            if (basis != basis) {
                basis = row ? size.width() : size.height();
            }
            mainBasis += basis + (row ? margin.horizontal() : margin.vertical());
            growTotal += childStyle.flexGrow();
            shrinkTotal += childStyle.flexShrink() * Math.max(1f, basis);
            children.add(new ChildLayout(child, size, margin, basis));
        }
        if (children.size() > 1) {
            mainBasis += style.gap() * (children.size() - 1);
        }

        float contentMain = row ? contentWidth : contentHeight;
        float free = contentMain - mainBasis;
        if (free > 0f && growTotal > 0f) {
            for (ChildLayout child : children) {
                child.mainSize = child.basis + free * child.child.style().flexGrow() / growTotal;
            }
        } else if (free < 0f && shrinkTotal > 0f) {
            float shrink = -free;
            for (ChildLayout child : children) {
                float share = shrinkTotal == 0f ? 0f
                        : shrink * child.child.style().flexShrink() * Math.max(1f, child.basis)
                          / shrinkTotal;
                child.mainSize = Math.max(0f, child.basis - share);
            }
        } else {
            for (ChildLayout child : children) {
                child.mainSize = child.basis;
            }
        }

        float used = 0f;
        for (ChildLayout child : children) {
            used += child.mainSize + (row ? child.margin.horizontal() : child.margin.vertical());
        }
        if (children.size() > 1) {
            used += style.gap() * (children.size() - 1);
        }
        float leftover = Math.max(0f, contentMain - used);
        float leading;
        float between;
        switch (style.justify()) {
            case END -> {
                leading = leftover;
                between = 0f;
            }
            case CENTER -> {
                leading = leftover / 2f;
                between = 0f;
            }
            case SPACE_BETWEEN -> {
                leading = 0f;
                between = children.size() > 1 ? leftover / (children.size() - 1) : 0f;
            }
            case SPACE_AROUND -> {
                float unit = children.isEmpty() ? 0f : leftover / children.size();
                leading = unit / 2f;
                between = unit;
            }
            case SPACE_EVENLY -> {
                float unit = leftover / (children.size() + 1f);
                leading = unit;
                between = unit;
            }
            default -> {
                leading = 0f;
                between = 0f;
            }
        }

        float cursor = (row ? contentX : contentY) + leading;
        for (int i = 0; i < children.size(); i++) {
            ChildLayout child = children.get(i);
            Style childStyle = child.child.style();
            float mainStart = row ? child.margin.left() : child.margin.top();
            float crossMarginStart = row ? child.margin.top() : child.margin.left();
            float crossMarginEnd = row ? child.margin.bottom() : child.margin.right();
            float crossBase = row ? child.size.height() : child.size.width();
            float contentCross = row ? contentHeight : contentWidth;
            boolean crossAuto = row ? childStyle.height().isAuto() : childStyle.width().isAuto();
            float crossSize = style.alignItems() == Style.Align.STRETCH && crossAuto
                    ? Math.max(0f, contentCross - crossMarginStart - crossMarginEnd)
                    : crossBase;
            float crossLeftover = Math.max(0f,
                    contentCross - crossSize - crossMarginStart - crossMarginEnd);
            float crossOffset = switch (style.alignItems()) {
                case END -> crossLeftover;
                case CENTER -> crossLeftover / 2f;
                default -> 0f;
            };

            float childX;
            float childY;
            float childWidth;
            float childHeight;
            if (row) {
                childX = cursor + mainStart;
                childY = contentY + crossMarginStart + crossOffset;
                childWidth = child.mainSize;
                childHeight = crossSize;
            } else {
                childX = contentX + crossMarginStart + crossOffset;
                childY = cursor + mainStart;
                childWidth = crossSize;
                childHeight = child.mainSize;
            }
            layoutNode(child.child, childX, childY, childWidth, childHeight, text);
            cursor += mainStart + child.mainSize
                    + (row ? child.margin.right() : child.margin.bottom());
            if (i + 1 < children.size()) {
                cursor += style.gap() + between;
            }
        }
        layoutAbsoluteChildren(node, contentX, contentY, contentWidth, contentHeight, text);
    }

    /** Lays out absolute children relative to this node's content box. */
    private static void layoutAbsoluteChildren(UiElement node, float contentX, float contentY,
                                               float contentWidth, float contentHeight,
                                               TextMeasurer text) {
        for (UiElement child : node.children()) {
            Style style = child.style();
            if (style.position() != Style.Position.ABSOLUTE) {
                continue;
            }
            float left = resolveOr(style.left(), contentWidth, Float.NaN);
            float right = resolveOr(style.right(), contentWidth, Float.NaN);
            float top = resolveOr(style.top(), contentHeight, Float.NaN);
            float bottom = resolveOr(style.bottom(), contentHeight, Float.NaN);
            float width = resolveOr(style.width(), contentWidth, Float.NaN);
            float height = resolveOr(style.height(), contentHeight, Float.NaN);
            if (width != width) {
                width = left == left && right == right ? Math.max(0f, contentWidth - left - right)
                        : measureNode(child, contentWidth, contentHeight, text).width();
            }
            if (height != height) {
                height = top == top && bottom == bottom ? Math.max(0f, contentHeight - top - bottom)
                        : measureNode(child, contentWidth, contentHeight, text).height();
            }
            if (left != left) {
                left = right == right ? contentWidth - right - width : 0f;
            }
            if (top != top) {
                top = bottom == bottom ? contentHeight - bottom - height : 0f;
            }
            layoutNode(child, contentX + left, contentY + top, width, height, text);
        }
    }

    private static Size constrain(float width, float height, Style style, float availableWidth,
                                  float availableHeight) {
        float minWidth = resolveOr(style.minWidth(), availableWidth, 0f);
        float minHeight = resolveOr(style.minHeight(), availableHeight, 0f);
        float maxWidth = resolveOr(style.maxWidth(), availableWidth, Float.POSITIVE_INFINITY);
        float maxHeight = resolveOr(style.maxHeight(), availableHeight, Float.POSITIVE_INFINITY);
        width = Math.max(minWidth, Math.min(maxWidth, width));
        height = Math.max(minHeight, Math.min(maxHeight, height));
        return new Size(width, height);
    }

    private static float resolveOr(Dimension dimension, float base, float fallback) {
        float value = dimension.resolve(base);
        return value == value ? value : fallback;
    }

    private static final class ChildLayout {
        private final UiElement child;
        private final Size size;
        private final Insets margin;
        private final float basis;
        private float mainSize;

        private ChildLayout(UiElement child, Size size, Insets margin, float basis) {
            this.child = child;
            this.size = size;
            this.margin = margin;
            this.basis = basis;
            this.mainSize = basis;
        }
    }
}
