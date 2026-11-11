package dev.skypocket.gui;

import java.util.List;

/**
 * Two-level navigation node for the Click GUI sidebar.
 *
 * <p>The mod is SkyBlock-focused, so the top level is the player-facing activity domain
 * (HUD / Mining / Combat / Fishing / ESP / Render / Notification / Movement), not a
 * separate "SkyBlock" container. Features reference their leaf node via
 * {@link Feature#category()}; containers remain supported for future grouping, but
 * there are no empty placeholder categories in the tree.
 */
public final class Category {
    private final String label;
    private final List<Category> children;

    /** Top-level activity domains. */
    public static final Category HUD = new Category("HUD");
    public static final Category MINING = new Category("挖矿");
    public static final Category COMBAT = new Category("战斗");
    public static final Category FISHING = new Category("钓鱼");
    public static final Category ESP = new Category("ESP");
    public static final Category RENDER = new Category("渲染");
    public static final Category NOTIFICATION = new Category("通知");
    public static final Category MOVEMENT = new Category("移动");

    private Category(String label, Category... children) {
        this.label = label;
        this.children = List.of(children);
    }

    /** Localized display name key (Chinese literal). */
    public String label() {
        return label;
    }

    public List<Category> children() {
        return children;
    }

    /** A container has child categories; a leaf holds features directly. */
    public boolean isContainer() {
        return !children.isEmpty();
    }

    /** Whether {@code node} is this category or one of its descendants. */
    public boolean contains(Category node) {
        if (node == this) {
            return true;
        }
        for (Category child : children) {
            if (child.contains(node)) {
                return true;
            }
        }
        return false;
    }

    public static List<Category> topLevel() {
        return List.of(HUD, MINING, COMBAT, FISHING, ESP, RENDER, NOTIFICATION, MOVEMENT);
    }
}
