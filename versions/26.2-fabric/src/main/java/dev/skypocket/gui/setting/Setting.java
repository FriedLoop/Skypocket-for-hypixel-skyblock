package dev.skypocket.gui.setting;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.gui.OneClickScreen;
import dev.skypocket.gui.widget.ActionButton;
import dev.skypocket.gui.widget.ColorToggleWidget;
import dev.skypocket.gui.widget.DropdownListWidget;
import dev.skypocket.gui.widget.DropdownWidget;
import dev.skypocket.gui.widget.GroupHeaderWidget;
import dev.skypocket.gui.widget.ClickGuiStyle;
import dev.skypocket.gui.widget.LabelWidget;
import dev.skypocket.gui.widget.SliderWidget;
import dev.skypocket.gui.widget.TextFieldWidget;
import dev.skypocket.gui.widget.ToggleAnimation;
import dev.skypocket.gui.widget.ToggleButton;
import dev.skypocket.i18n.Localization;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Declarative description of a single settings row in the Click GUI. Each concrete
 * type builds one (or, for {@link CustomSetting}, several) widgets at a given
 * position, reports its occupied height, and can match a search query.
 */
public abstract class Setting {
    /** Fixed row height: 20px widget + 5px spacing. */
    /**
     * Row height, from the theme's measurements.
     *
     * <p>One value for every row: the second line that {@code .hint()} used to add made those rows 38 px
     * and broke the page's rhythm, so hints became hover tooltips and the height is uniform again.
     */
    public static int rowHeight() {
        return dev.skypocket.gui.widget.GuiMetrics.current().rowHeight();
    }
    /** Standard widget width inside the panel. */
    public static final int WIDGET_WIDTH = 400;

    protected final String label;

    /**
     * True when the page draws (and handles) this row itself instead of building a widget for it.
     *
     * <p>Widgets are drawn by the screen and cannot be clipped or scaled, which blocks two things: the
     * settings page's own scrolling/preview, and the theme studio's miniature ClickGUI. Rows convert one
     * at a time - see {@code OneClickScreen.drawImmediateRows}.
     */
    public boolean immediate() {
        return false;
    }

    /** The untranslated label; the page draws immediate rows itself and needs it. */
    public String label() {
        return label;
    }

    protected Setting(String label) {
        this.label = label;
    }

    /**
     * Whether the row is built at all. This is what lets a module hide the settings that only apply
     * to one of its modes (two display styles, say) instead of leaving dead controls for the other
     * one on screen.
     */
    private BooleanSupplier visibleWhen = () -> true;

    /**
     * Hides this row while {@code predicate} is false. Returns {@code this} so it can be chained onto
     * a constructor call inside a settings list.
     */
    public Setting visibleWhen(BooleanSupplier predicate) {
        this.visibleWhen = predicate;
        return this;
    }

    /** Whether the panel should build this row right now. */
    public boolean isVisible() {
        return visibleWhen.getAsBoolean();
    }

    /**
     * A one-line note under the row, for the settings whose purpose is not obvious from their label
     * (see {@code HintWidget}). It is a separate widget rather than part of the row's own drawing, so
     * no setting subclass has to know about it.
     */
    private String hint;

    /** Height the note needs below the row: none, since notes are hover tooltips now. */
    public int hintHeight() {
        return 0;
    }

    /**
     * The row's full height - identical to {@link #height()} now that notes are tooltips.
     *
     * <p>Rows used to reserve 13 px for the note line. Once rows moved to page drawing the note was no
     * longer drawn for them, but the space was still reserved: the page showed gaps that depended on
     * whether a row happened to carry a note. Notes are hover tooltips now, so every row is one height.
     */
    public int totalHeight() {
        return height();
    }

    /** Attaches a note to this row; chainable onto a constructor call. */
    public Setting hint(String text) {
        this.hint = text;
        return this;
    }

    public String hint() {
        return hint;
    }

    /**
     * Builds this row together with its note (if any). Both the page and {@code GroupSetting} call
     * this rather than {@link #build}, so a note works the same wherever the row lives - a group's
     * children are built by the group, not by the page.
     */
    public final void buildWithHint(int x, int y, Consumer<AbstractWidget> add) {
        // The note is drawn by the page as a hover tooltip (see OneClickScreen), so only the row itself
        // is built here.
        build(x, y, add);
    }

    /** Fixed height of the widget part of a row, matching what the subclasses build. */
    /** Height of the control a row draws, from the theme's measurements. */
    public static int controlHeight() {
        return dev.skypocket.gui.widget.GuiMetrics.current().controlHeight();
    }

    /** Vertical space (px) this setting occupies. */
    public abstract int height();

    /** Builds the widget(s) for this setting, passing each to {@code add}. */
    public abstract void build(int x, int y, Consumer<AbstractWidget> add);

    /**
     * Wraps a setting's write so the Click GUI panel cache notices it.
     *
     * <p>The definitions and the panel settings write plain config fields - no
     * {@link ConfigManager#save()} - while the panel cache keys on
     * {@link ConfigManager#revision()}. Without this wrapper a toggle left the cached panel on
     * screen until some other part of the key changed (moving the mouse, or the 600-frame
     * backstop), which is the "switching a module updates the button very slowly, it feels laggy"
     * report. Every setter a {@code Setting} hands to a widget goes through here.
     */
    protected static <T> Consumer<T> touch(Consumer<T> setter) {
        return v -> {
            setter.accept(v);
            ConfigManager.touch();
        };
    }

    /** Double overload of {@link #touch(Consumer)} (sliders). */
    protected static DoubleConsumer touch(DoubleConsumer setter) {
        return v -> {
            setter.accept(v);
            ConfigManager.touch();
        };
    }

    /** Int overload of {@link #touch(Consumer)} (colour channels). */
    protected static IntConsumer touch(IntConsumer setter) {
        return v -> {
            setter.accept(v);
            ConfigManager.touch();
        };
    }

    /**
     * Re-lays the Click GUI out on the next tick, for a setting whose height just changed (an expanded
     * picker, a folded group). Doing it next tick rather than now is what keeps the widget tree from
     * being mutated in the middle of an event.
     */
    protected static void relayout() {
        Minecraft mc = Minecraft.getInstance();
        mc.schedule(() -> {
            if (mc.gui.screen() instanceof OneClickScreen s) {
                s.rebuildAfterSettingChange();
            }
        });
    }

    /** Whether this setting matches the search query (case-insensitive, both languages). */
    public boolean matches(String q) {
        if (label == null || label.isEmpty()) {
            return false;
        }
        return label.toLowerCase().contains(q) || Localization.tr(label).toLowerCase().contains(q);
    }

    /** A labeled on/off switch, the same animated control the group headers use. */
    public static final class BooleanSetting extends Setting {
        private final BooleanSupplier getter;
        private final Consumer<Boolean> setter;
        /** Owned here (not by the widget) so the animation survives the rebuilds scrolling causes. */
        private final ToggleAnimation animation = new ToggleAnimation();

        /**
         * Drawn and handled by the page instead of a widget - see {@code OneClickScreen}. Rows that are
         * immediate can be clipped and scrolled, which is what the theme studio's preview needs.
         */
        @Override public boolean immediate() { return true; }

        public boolean value() { return getter.getAsBoolean(); }

        public void toggle() { setter.accept(!getter.getAsBoolean()); }

        /** Animation progress 0..1, advanced on the frame it is drawn. */
        public float progress() {
            animation.to(value());
            return animation.progress();
        }

        public BooleanSetting(String label, BooleanSupplier getter, Consumer<Boolean> setter) {
            super(label);
            this.getter = getter;
            this.setter = touch(setter);
        }

        @Override public int height() { return rowHeight(); }

        @Override public void build(int x, int y, Consumer<AbstractWidget> add) {
            add.accept(new ToggleButton(x, y, WIDGET_WIDTH, 20, Localization.tr(label), getter, setter,
                    animation));
        }
    }

    /**
     * A slider over [min, max]. <b>Every slider has a step</b>: a continuous one makes the value
     * unreadable (the formatted number jitters with every pixel of drag) and lets two sliders over the
     * same range behave differently for no reason. Pass one explicitly when the range's units suggest
     * it (a pixel count steps by 1, a scale factor by 0.05); otherwise the constructor derives a step
     * of about a hundredth of the range.
     */
    public static final class SliderSetting extends Setting {
        private final double min;
        private final double max;
        private final DoubleSupplier getter;
        private final DoubleConsumer setter;
        private final String format;
        private final double step;

        public SliderSetting(String label, double min, double max, DoubleSupplier getter, DoubleConsumer setter, String format) {
            this(label, min, max, getter, setter, format, defaultStep(min, max));
        }

        public SliderSetting(String label, double min, double max, DoubleSupplier getter, DoubleConsumer setter, String format, double step) {
            super(label);
            this.min = min;
            this.max = max;
            this.getter = getter;
            this.setter = touch(setter);
            this.format = format;
            this.step = step;
        }

        @Override public int height() { return rowHeight(); }

        /** Drawn and handled by the page - see {@link #immediate()} on the base class. */
        @Override public boolean immediate() { return true; }

        @Override public void build(int x, int y, Consumer<AbstractWidget> add) {
            add.accept(new SliderWidget(x, y, WIDGET_WIDTH, 20, Localization.tr(label), min, max,
                    getter.getAsDouble(), format, setter, step));
        }

        public double min() { return min; }

        public double max() { return max; }

        public double value() { return getter.getAsDouble(); }

        /** The row text, exactly as the widget composed it. */
        public String display() {
            return Localization.tr(label) + ": " + String.format(format, value());
        }

        /** Clamps, snaps to the step and writes - the arithmetic {@code SliderWidget} used. */
        public void setValue(double v) {
            double clamped = Math.max(min, Math.min(max, v));
            if (step > 0) {
                clamped = Math.round(clamped / step) * step;
                clamped = Math.max(min, Math.min(max, clamped));
            }
            setter.accept(clamped);
        }

        /**
         * A step of about a hundredth of the range, rounded down to a whole number of decimals - so a
         * 0-200 slider steps by 1 and a 0-1 slider by 0.01, and no slider is ever continuous.
         */
        private static double defaultStep(double min, double max) {
            double range = Math.abs(max - min);
            if (!(range > 0.0) || !Double.isFinite(range)) {
                return 1.0;
            }
            return Math.max(1.0e-4, Math.pow(10.0, Math.floor(Math.log10(range / 100.0))));
        }
    }

    /**
     * An editable colour: the row is drawn by the page, and opening it shows the immediate picker
     */
    public static final class ColorSetting extends Setting {
        private final IntSupplier getter;
        private final IntConsumer setter;
        private boolean expanded;
        /**
         * The parts of the colour RGB cannot carry - the hue on greys, the hue and the saturation
         * on black. Shared with the picker, and kept here rather than in the widget because rows
         * are rebuilt on every scroll; see {@link dev.skypocket.gui.widget.PickerMemory} for why the
         * markers must not be derived from the colour itself.
         */
        private final dev.skypocket.gui.widget.PickerMemory memory = new dev.skypocket.gui.widget.PickerMemory();

        public ColorSetting(String label, IntSupplier getter, IntConsumer setter) {
            this(label, getter, setter, false);
        }

        public ColorSetting(String label, IntSupplier getter, IntConsumer setter, boolean defaultExpanded) {
            super(label);
            this.getter = getter;
            this.setter = touch(setter);
            this.expanded = defaultExpanded;
        }

        @Override public int height() {
            return expanded ? rowHeight() + dev.skypocket.gui.widget.ImmediateColorPicker.CONTENT_H : rowHeight();
        }

        /** The row is drawn by the page; only the picker below it needs to be a widget. */
        @Override public boolean immediate() { return true; }

        /**
         * Widget version, for layouts that place rows themselves ({@code CustomSetting} - the crosshair's
         * grid, for instance). The settings page skips immediate rows and draws them instead; emptying
         * this method made those custom layouts lose their colour rows entirely.
         */
        @Override public void build(int x, int y, Consumer<AbstractWidget> add) {
            add.accept(new ColorToggleWidget(x, y, WIDGET_WIDTH, 20, Localization.tr(label), getter,
                    () -> expanded, this::toggle));
        }

        public int value() { return getter.getAsInt(); }

        /** Writes a new colour, for hosts that draw the picker themselves. */
        public void setValue(int argb) { setter.accept(argb); }

        public boolean expanded() { return expanded; }

        /** Opens/closes the inline picker - the page handles the row's click now. */
        public void toggleExpanded() { toggle(); }


        /** Flips the expanded state and re-lays out the Click GUI on the next tick. */
        private void toggle() {
            expanded = !expanded;
            relayout();
        }
    }

    /**
     * A dropdown picker with a live search field. Collapsed it is a single row
     * showing the current value; clicking it expands into a search box plus a
     * scrollable, filterable option list (reusing the shared text-field focus).
     */
    public static final class DropdownSetting extends Setting {
        private static final int LIST_ROWS = 8;

        private final Supplier<List<String>> options;
        private final Supplier<String> getter;
        private final Consumer<String> setter;
        private boolean expanded;
        /** Search text; kept on the setting so panel scrolling doesn't lose it. */
        private String filter = "";

        public DropdownSetting(String label, Supplier<List<String>> options,
                               Supplier<String> getter, Consumer<String> setter) {
            super(label);
            this.options = options;
            this.getter = getter;
            this.setter = touch(setter);
        }

        @Override public int height() {
            return expanded ? rowHeight() * 2 + LIST_ROWS * DropdownListWidget.ITEM_H : rowHeight();
        }

        /** Drawn and handled by the page - see {@link #immediate()}. */
        @Override public boolean immediate() { return true; }

        /**
         * Widget version, for custom layouts. The settings page draws the row and the list itself (so they
         * clip and can be rendered into the studio's preview); this stays for anything that builds rows by
         * hand, and only the search field is ever needed on the page path.
         */
        @Override public void build(int x, int y, Consumer<AbstractWidget> add) {
            add.accept(new DropdownWidget(x, y, WIDGET_WIDTH, 20, Localization.tr(label), getter,
                    this::toggle));
            if (!expanded) {
                return;
            }
            add.accept(new DropdownListWidget(x, y + rowHeight() * 2, WIDGET_WIDTH,
                    LIST_ROWS * DropdownListWidget.ITEM_H, options, getter, () -> filter,
                    v -> {
                        setter.accept(v);
                        toggle();
                    }));
        }


        /**
         * Filter text, with a cache invalidation.
         *
         * <p>The panel is drawn from a cached texture keyed on things like the scroll offset and the
         * hover cell; neither the filter nor the list's scroll position is part of that key, so typing in
         * the search box only became visible after the mouse moved and changed the hover cell. Touching
         * the config revision (which <em>is</em> in the key) makes the change appear immediately - and it
         * also makes the scroll case below work.
         */
        public void setFilter(String value) {
            filter = value;
            dev.skypocket.config.ConfigManager.touch();
        }

        public boolean expanded() { return expanded; }

        /** Opens/closes the list - the page owns the click now, so it needs the entry point. */
        public void toggleExpanded() { toggle(); }

        public String value() { return getter.get(); }

        /** Picks an option and folds the list back up, like the old list widget did. */
        public void select(String option) {
            setter.accept(option);
            toggle();
        }

        public static int itemHeight() { return DropdownListWidget.ITEM_H; }

        public static int listHeight() { return LIST_ROWS * DropdownListWidget.ITEM_H; }

        public int scrollIndex() { return scrollIndex; }

        /** Scrolls the option list; invalidates the cache for the same reason {@link #setFilter} does. */
        public void scrollBy(int amount) {
            scrollIndex += amount;
            dev.skypocket.config.ConfigManager.touch();
        }

        /** Options matching the filter, refreshed only when the filter text changes. */
        public List<String> filtered() {
            String key = filter == null ? "" : filter.trim().toLowerCase();
            if (key.equals(lastFilter)) {
                return filteredCache;
            }
            lastFilter = key;
            List<String> all = options.get();
            List<String> out = new ArrayList<>();
            for (String option : all) {
                if (key.isEmpty() || option.toLowerCase().contains(key)) {
                    out.add(option);
                }
            }
            filteredCache = out;
            scrollIndex = 0;
            return out;
        }

        /** Keeps the scroll index inside the list - call after scrolling or filtering. */
        public void clampScroll(int visibleRows) {
            int max = Math.max(0, filtered().size() - Math.max(1, visibleRows));
            scrollIndex = Math.max(0, Math.min(max, scrollIndex));
        }

        /** The filter box, drawn by the page (no widget: it lives inside the cached panel). */
        private final dev.skypocket.gui.widget.ImmediateTextField search =
                new dev.skypocket.gui.widget.ImmediateTextField("dropdown",
                        Localization.tr("搜索"), "", 32, c -> true);

        public dev.skypocket.gui.widget.ImmediateTextField searchField() {
            return search;
        }

        private int scrollIndex;
        private List<String> filteredCache = List.of();
        private String lastFilter;

        /** Flips the expanded state and re-lays out the Click GUI on the next tick. */
        private void toggle() {
            expanded = !expanded;
            if (expanded) {
                // Seed the box with the filter that is still in effect.
                search.setText(filter);
            } else {
                search.blur();
            }
            relayout();
        }
    }

    /**
     * A collapsible group of settings, optionally with its own on/off switch.
     *
     * <p>The switch belongs to groups that are real sub-features: it writes that feature's own enable
     * flag, so turning it off actually stops the feature (and an existing config keeps meaning what it
     * did). A group that is only a cluster of parameters - a position, a set of colours - has no flag
     * to write and gets a header without a switch; it still folds away, there is just nothing to turn
     * off. A switch that did nothing would be worse than none.
     *
     * <p>Children keep the full width of the page (no indent): the group header's darker surface and
     * its "+/-" indicator already say what belongs to what, and indenting would mean shrinking the
     * pickers and grids whose layouts are tuned to the full width.
     */
    public static final class GroupSetting extends Setting {
        /** Null when the group has no switch of its own. */
        private final BooleanSupplier getter;
        private final Consumer<Boolean> setter;
        private final List<Setting> children;
        private final ToggleAnimation animation = new ToggleAnimation();
        private boolean expanded;

        /** A foldable section without a switch. */
        public GroupSetting(String label, List<Setting> children) {
            this(label, null, null, children, true);
        }

        /** A foldable sub-feature: the switch writes {@code setter}'s feature flag. */
        public GroupSetting(String label, BooleanSupplier getter, Consumer<Boolean> setter,
                            List<Setting> children) {
            this(label, getter, setter, children, true);
        }

        /** A foldable section without a switch that starts folded away (rarely used settings). */
        public GroupSetting(String label, List<Setting> children, boolean defaultExpanded) {
            this(label, null, null, children, defaultExpanded);
        }

        public GroupSetting(String label, BooleanSupplier getter, Consumer<Boolean> setter,
                            List<Setting> children, boolean defaultExpanded) {
            super(label);
            this.getter = getter;
            this.setter = getter == null ? null : touch(setter);
            this.children = List.copyOf(children);
            this.expanded = defaultExpanded;
        }

        @Override public int height() {
            int h = rowHeight();
            if (expanded) {
                for (Setting child : children) {
                    // totalHeight, not height: a child with a note is taller than its own row.
                    if (child.isVisible()) {
                        h += child.totalHeight();
                    }
                }
            }
            return h;
        }

        @Override public void build(int x, int y, Consumer<AbstractWidget> add) {
            add.accept(new GroupHeaderWidget(x, y, WIDGET_WIDTH, 20, Localization.tr(label),
                    getter, setter, animation, () -> expanded, this::toggle));
            if (!expanded) {
                return;
            }
            int childY = y + rowHeight();
            for (Setting child : children) {
                if (!child.isVisible()) {
                    continue;
                }
                // Rows that converted to page drawing build nothing here - the page walks the children
                // (see OneClickScreen.activeRowList) and draws them inside the same scissor. Their
                // half-widget parts (a dropdown's search field, a colour's picker) still need building.
                if (!child.immediate()) {
                    child.buildWithHint(x, childY, add);
                }
                childY += child.totalHeight();
            }
        }

        /** Folded or not. The header row stays visible either way (the page draws it). */
        public boolean isExpanded() { return expanded; }

        public List<Setting> children() { return children; }

        /** The header is a row like any other, so the page draws it instead of a widget. */
        @Override public boolean immediate() { return true; }

        public void toggleExpanded() { toggle(); }

        public boolean hasSwitch() { return getter != null; }

        public boolean switchValue() { return getter != null && getter.getAsBoolean(); }

        public void toggleSwitch() {
            if (getter != null) {
                setter.accept(!getter.getAsBoolean());
            }
        }

        /** 0..1 switch animation, advanced when the row is drawn. */
        public float switchProgress() {
            animation.to(switchValue());
            return animation.progress();
        }

        /**
         * Builds only the children that still need widgets (a text field, a picker).
         *
         * <p>The page skips immediate rows when building, so a group that became immediate would
         * otherwise stop building its children's widgets entirely.
         */
        public void buildChildren(int x, int y, Consumer<AbstractWidget> add) {
            if (!expanded) {
                return;
            }
            int childY = y + rowHeight();
            for (Setting child : children) {
                if (!child.isVisible()) {
                    continue;
                }
                if (!child.immediate()) {
                    child.buildWithHint(x, childY, add);
                }
                childY += child.totalHeight();
            }
        }

        /** A group matches when its own label or any child does, so search still reaches children. */
        @Override public boolean matches(String q) {
            if (super.matches(q)) {
                return true;
            }
            for (Setting child : children) {
                if (child.matches(q)) {
                    return true;
                }
            }
            return false;
        }

        /** Flips the folded state and re-lays out the page on the next tick. */
        private void toggle() {
            expanded = !expanded;
            relayout();
        }
    }

    /** A single-line text input. */
    public static final class TextFieldSetting extends Setting {
        private final Supplier<String> getter;
        private final Consumer<String> setter;

        public TextFieldSetting(String label, Supplier<String> getter, Consumer<String> setter) {
            super(label);
            this.getter = getter;
            this.setter = touch(setter);
        }

        @Override public int height() { return rowHeight(); }

        @Override public void build(int x, int y, Consumer<AbstractWidget> add) {
            add.accept(new TextFieldWidget(x, y, WIDGET_WIDTH, 20, Localization.tr(label), getter.get(), setter));
        }
    }

    /** A mutually exclusive choice rendered as a segmented control in UI v2. */
    public static final class ChoiceSetting extends Setting {
        private final List<String> options;
        private final IntSupplier getter;
        private final IntConsumer setter;

        public ChoiceSetting(String label, List<String> options, IntSupplier getter,
                             IntConsumer setter) {
            super(label);
            this.options = List.copyOf(options);
            this.getter = getter;
            this.setter = setter;
        }

        public List<String> options() {
            return options;
        }

        public int selected() {
            return Math.max(0, Math.min(options.size() - 1, getter.getAsInt()));
        }

        public void select(int index) {
            if (index >= 0 && index < options.size() && index != selected()) {
                setter.accept(index);
            }
        }

        @Override
        public int height() {
            return rowHeight();
        }

        @Override
        public void build(int x, int y, Consumer<AbstractWidget> add) {
            // Legacy SettingsView fallback: UI v2 renders this as a Segmented control.
            add.accept(new LabelWidget(x, y, WIDGET_WIDTH, height(),
                    Localization.tr(label) + ": " + Localization.tr(options.get(selected())),
                    ClickGuiStyle.text(), false));
        }

        @Override
        public boolean matches(String q) {
            return false;
        }
    }

    /** A button that runs an action; supports a static or dynamic label. */
    public static final class ActionSetting extends Setting {
        private final Supplier<String> display;
        private final Runnable action;

        public ActionSetting(String label, Runnable action) {
            this(() -> Localization.tr(label), action);
        }

        public ActionSetting(Supplier<String> display, Runnable action) {
            super(null);
            this.display = display;
            this.action = action;
        }

        @Override public int height() { return rowHeight(); }

        /** A button is pure drawing plus a callback, so the page draws it like any other row. */
        @Override public boolean immediate() { return true; }

        /** The already-translated caption. */
        public String text() { return display.get(); }

        public void run() { action.run(); }

        @Override public void build(int x, int y, Consumer<AbstractWidget> add) {
            add.accept(new ActionButton(x, y, WIDGET_WIDTH, 20, display, action));
        }

        @Override public boolean matches(String q) { return false; }
    }

    /** Arbitrary custom layout (e.g. the crosshair pixel grid + preview side by side). */
    public static final class CustomSetting extends Setting {
        @FunctionalInterface
        public interface Layout {
            void layout(int x, int y, Consumer<AbstractWidget> add);
        }

        private final int h;
        private final Layout layout;

        public CustomSetting(int height, Layout layout) {
            super(null);
            this.h = height;
            this.layout = layout;
        }

        @Override public int height() { return h; }

        @Override public void build(int x, int y, Consumer<AbstractWidget> add) {
            layout.layout(x, y, add);
        }

        @Override public boolean matches(String q) { return false; }
    }

    /**
     * A section heading: a small title with an optional second line, drawn as one of the settings rows
     * so a long page can be read in blocks without folding anything.
     *
     * <p>It is deliberately not a {@code GroupSetting}: a group carries a switch and a collapse state,
     * while a heading is only a label. Use it when the rows below it are not a sub-feature of their own
     * - the sections of a page's settings that share a mode, say.
     */
    public static final class Header extends Setting {
        private final String subtitle;

        /** A heading is pure text, so the page draws it (and never builds a widget for it). */
        @Override public boolean immediate() { return true; }

        public String subtitle() { return subtitle; }

        public Header(String label) {
            this(label, null);
        }

        public Header(String label, String subtitle) {
            super(label);
            this.subtitle = subtitle;
        }

        /**
         * A gap above, then one title line: the gap is what separates sections now that rows have no
         * margin of their own.
         */
        @Override public int height() {
            return dev.skypocket.gui.widget.GuiMetrics.current().sectionGap()
                    + dev.skypocket.gui.widget.GuiMetrics.current().sectionTitleHeight();
        }

        @Override public void build(int x, int y, Consumer<AbstractWidget> add) {
            add.accept(new LabelWidget(x, y, WIDGET_WIDTH, controlHeight(), Localization.tr(label),
                    ClickGuiStyle.accent(), true));
        }

        /** A heading is not a setting: it never matches a search by itself. */
        @Override public boolean matches(String q) {
            return false;
        }
    }
}
