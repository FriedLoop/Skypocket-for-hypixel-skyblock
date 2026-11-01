package dev.skypocket.gui;

import java.util.List;

/**
 * Two-level navigation node for the Click GUI sidebar.
 *
 * <p>A category is either a <b>leaf</b> (it holds features directly, e.g. Render) or
 * a <b>container</b> (it holds child categories, e.g. SkyBlock's sub-categories).
 * Features reference their leaf node via {@link Feature#category()}; selecting a
 * container filters to every feature under it, while selecting a leaf filters to
 * just that leaf.
 */
public final class Category {
    private final String label;
    private final List<Category> children;

    /** Top-level leaf nodes. */
    public static final Category RENDER = new Category("渲染");
    public static final Category HUD = new Category("界面");
    public static final Category MOVEMENT = new Category("移动");
    public static final Category NOTIFICATION = new Category("通知");

    /** SkyBlock sub-categories (leaf nodes under {@link #SKYBLOCK}). */
    public static final Category SKYBLOCK_DUNGEONS = new Category("地牢");
    public static final Category SKYBLOCK_MINING = new Category("挖矿");
    public static final Category SKYBLOCK_FARMING = new Category("农耕");
    public static final Category SKYBLOCK_COMBAT = new Category("战斗");
    public static final Category SKYBLOCK_FISHING = new Category("钓鱼");
    public static final Category SKYBLOCK_EVENTS = new Category("事件");
    /**
     * SkyBlock's own HUD pieces (health/mana/defence rows, the damage and comms readouts). Separate
     * from the top-level {@link #HUD} because these are only meaningful on SkyBlock - the label is
     * "HUD" both to match what players call it and to keep it from reading as a duplicate "界面" in the
     * sidebar, where it is nested under SkyBlock anyway.
     */
    public static final Category SKYBLOCK_HUD = new Category("HUD");

    /** SkyBlock container grouping its sub-categories (extend as features are added). */
    public static final Category SKYBLOCK = new Category("SkyBlock",
            SKYBLOCK_HUD,
            SKYBLOCK_DUNGEONS,
            SKYBLOCK_MINING,
            SKYBLOCK_FARMING,
            SKYBLOCK_COMBAT,
            SKYBLOCK_FISHING,
            SKYBLOCK_EVENTS);

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
        return List.of(RENDER, HUD, MOVEMENT, NOTIFICATION, SKYBLOCK);
    }
}
