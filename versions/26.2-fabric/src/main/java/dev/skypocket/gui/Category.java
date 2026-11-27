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
    /** Icon shown in the sidebar, or null for nodes that have none (containers, the "all" row). */
    private final IconAtlas.Icon icon;

    /**
     * The "show everything" node.
     *
     * <p>A Category rather than a null, so the row that renders it can carry an icon like every other
     * row. It is deliberately absent from {@link #topLevel()}: it is not an activity domain, it is the
     * absence of a filter, and including it there would make it selectable as one.
     */
    public static final Category ALL = new Category("全部", IconAtlas.Icon.LAYERS);

    /** Top-level activity domains. */
    public static final Category HUD = new Category("HUD", IconAtlas.Icon.MONITOR);
    public static final Category MINING = new Category("挖矿", IconAtlas.Icon.PICKAXE);
    public static final Category COMBAT = new Category("战斗", IconAtlas.Icon.SWORDS);
    public static final Category FISHING = new Category("钓鱼", IconAtlas.Icon.FISH);
    public static final Category ESP = new Category("ESP", IconAtlas.Icon.RADAR);
    public static final Category RENDER = new Category("渲染", IconAtlas.Icon.WIRE_CUBE);
    public static final Category NOTIFICATION = new Category("通知", IconAtlas.Icon.BELL);
    public static final Category MOVEMENT = new Category("移动", IconAtlas.Icon.ARROWS);

    private Category(String label, IconAtlas.Icon icon, Category... children) {
        this.label = label;
        this.icon = icon;
        this.children = List.of(children);
    }

    /** Localized display name key (Chinese literal). */
    public String label() {
        return label;
    }

    /**
     * The icon for this node, or null when it has none.
     *
     * <p>Nullable rather than returning a placeholder: the "all" row and container rows are not
     * activities and have no icon, and drawing a generic one for them would be noise.
     */
    public IconAtlas.Icon icon() {
        return icon;
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
