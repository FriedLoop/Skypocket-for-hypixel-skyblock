package dev.skypocket.gui;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.gui.setting.Setting;
import dev.skypocket.i18n.Localization;
import dev.skypocket.gui.widget.ClickGuiStyle;
import dev.skypocket.render.ClickGuiFonts;
import dev.skypocket.render.GuiPanelCache;
import dev.skypocket.ui.Box;
import dev.skypocket.ui.Dimension;
import dev.skypocket.ui.LayoutEngine;
import dev.skypocket.ui.Painted;
import dev.skypocket.ui.ScrollView;
import dev.skypocket.ui.Size;
import dev.skypocket.ui.Style;
import dev.skypocket.ui.Text;
import dev.skypocket.ui.TextMeasurer;
import dev.skypocket.ui.TextWeight;
import dev.skypocket.ui.UiElement;
import dev.skypocket.ui.input.InputRouter;
import dev.skypocket.ui.input.KeyEvent;
import dev.skypocket.ui.input.PointerEvent;
import dev.skypocket.ui.input.PreeditEvent;
import dev.skypocket.ui.input.TextInputEvent;
import dev.skypocket.ui.input.WheelEvent;
import dev.skypocket.ui.mc.McUiPainter;
import dev.skypocket.ui.widget.Button;
import dev.skypocket.ui.widget.Card;
import dev.skypocket.ui.widget.ColorField;
import dev.skypocket.ui.widget.Control;
import dev.skypocket.ui.widget.Dropdown;
import dev.skypocket.ui.widget.Scrollbar;
import dev.skypocket.ui.widget.Segmented;
import dev.skypocket.ui.widget.Slider;
import dev.skypocket.ui.widget.Switch;
import dev.skypocket.ui.widget.TextField;
import dev.skypocket.ui.widget.WidgetPalette;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Content-only settings page for a feature. It owns an element tree, its input router and its popup
 * layers, but no Screen; the unified ClickGUI hosts it inside the content pane.
 */
public final class SettingsPage extends dev.skypocket.ui.UiPage {
    private final Feature feature;
    private final WidgetPalette palette;
    private final float columnWidth;
    private final List<ColorField> colorFields = new ArrayList<>();
    private final List<Dropdown> dropdowns = new ArrayList<>();
    /**
     * Dropdowns owned by custom rows.
     *
     * <p>Kept apart from {@link #dropdowns} because they are registered while a row is being built
     * rather than by {@code addSetting}, and because the list is rebuilt with the page: a row that is
     * no longer shown must not keep its popup on the layer stack.
     */
    private final List<Dropdown> customDropdowns = new ArrayList<>();

    /**
     * The context handed to custom rows that own a popup.
     *
     * <p>Bound to this page's router so a row's floating layer is drawn and hit-tested with the same
     * stack as the page's own dropdowns and colour pickers.
     */
    private final Setting.V2CustomSetting.Host customHost = new Setting.V2CustomSetting.Host() {
        @Override
        public void registerDropdown(Dropdown dropdown) {
            if (dropdown != null && !customDropdowns.contains(dropdown)) {
                customDropdowns.add(dropdown);
            }
        }

        @Override
        public void rebuild() {
            SettingsPage.this.rebuild();
        }
    };
    /** The scrolling column; kept because the page still styles its scrollbar after building. */
    private ScrollView scroll;

    public SettingsPage(Feature feature, WidgetPalette palette, float width, float height) {
        this(feature, palette, width, height, Math.min(1002f, Math.max(386f, width - 58f)));
    }

    public SettingsPage(Feature feature, WidgetPalette palette, float width, float height,
                        float columnWidth) {
        this.feature = feature;
        this.palette = palette;
        this.columnWidth = Math.max(386f, Math.min(columnWidth, width - 58f));
        // The page's size is fixed at construction; UiPage keeps it and the cache key uses it.
        bounds(width, height);
        rebuild();
    }

    public boolean isFocused() {
        return input().focused() != null;
    }

    /**
     * Everything that changes this page's appearance.
     *
     * <p>Only the config: every widget here writes through a {@code Setting} setter, and those wrap
     * {@code ConfigManager.touch()}, so the config revision covers them.
     */
    @Override
    protected long revision() {
        return ConfigManager.revision();
    }

    @Override
    protected dev.skypocket.ui.UiElement build() {
        colorFields.clear();
        dropdowns.clear();
        // Custom-row dropdowns are re-registered by the rows that are built again below, so the old
        // list has to go: keeping it would keep closed popups on the layer stack.
        customDropdowns.clear();
        Box page = new Box(Style.builder()
                .width(width())
                .height(height())
                .direction(Style.Direction.COLUMN)
                .build());
        if (feature == null) {
            page.add(new Text(Localization.tr("找不到设置模块"),
                    Style.builder().height(36f).build()));
        } else {
            Box content = new Box(Style.builder()
                    .direction(Style.Direction.COLUMN)
                    .gap(8f)
                    .build());
            buildSettings(content, feature.settings());
            float columnLeft = (width() - columnWidth) / 2f;
            scroll = new ScrollView(Style.builder()
                    .position(Style.Position.ABSOLUTE)
                    .left(columnLeft)
                    .top(0f)
                    .width(columnWidth)
                    .height(height())
                    .padding(4f)
                    .build(), content, 46f, false, palette);
            page.add(scroll);
            Scrollbar bar = scroll.scrollbar();
            bar.style(Style.builder()
                    .position(Style.Position.ABSOLUTE)
                    .left(columnLeft + columnWidth + 10f)
                    .top(8f)
                    .bottom(8f)
                    .width(12f)
                    .build());
            page.add(bar);
        }
        return page;
    }

    /** No-argument entry point kept for existing callers; delegates to the framework's layout. */
    public void layout() {
        TextMeasurer measurer = (text, maxWidth) ->
                new Size(ClickGuiFonts.width(
                        dev.skypocket.i18n.Localization.auto(text)), ClickGuiFonts.lineHeight());
        layout(measurer);
    }

    /**
     * Lays this page out, then registers its popups.
     *
     * <p>The body of this used to be here: layout the tree, refresh the scroll view, and run three
     * near-identical loops - colour pickers, dropdowns, custom-row dropdowns - each laying its popup
     * out against its anchor and adding or removing it as an input layer. All of that is now
     * {@code UiPage.layout} plus {@code trackPopup}, so this only has to say which controls own a
     * popup.
     */
    @Override
    public void layout(TextMeasurer measurer) {
        registerPopups();
        super.layout(measurer);
    }

    /** Registers this page's popup owners once; {@code trackPopup} is idempotent. */
    private void registerPopups() {
        for (ColorField field : colorFields) {
            trackPopup(field);
        }
        for (Dropdown dropdown : dropdowns) {
            trackPopup(dropdown);
        }
        // Custom rows own their popups, so the page only learns about them through the host.
        for (Dropdown dropdown : customDropdowns) {
            trackPopup(dropdown);
        }
    }

    /** Height of the laid-out scroll content, used when a host wants a content-sized popup. */
    public float contentHeight() {
        return scroll == null ? 0f : scroll.content().bounds().height();
    }

    /**
     * Scale the last frame was drawn at.
     *
     * <p>Kept as a named alias because the host converts screen coordinates with it: the page may be
     * drawn 1:1 or through a cached texture whose exact scale differs slightly from the requested
     * one, and input has to use what was actually used.
     */
    public float inputScaleX() {
        return renderScaleX();
    }

    public float inputScaleY() {
        return renderScaleY();
    }

    public boolean pointerDown(float x, float y, int button, int modifiers) {
        input().pointerDown(new PointerEvent(x, y, button, modifiers));
        closePopupsOutside();
        return true;
    }

    public boolean pointerMove(float x, float y, int modifiers) {
        input().pointerMove(PointerEvent.move(x, y, modifiers));
        return true;
    }

    public boolean pointerUp(float x, float y, int button, int modifiers) {
        input().pointerUp(new PointerEvent(x, y, button, modifiers));
        closePopupsOutside();
        return true;
    }

    public boolean wheel(float x, float y, float delta, int modifiers) {
        input().wheel(new WheelEvent(x, y, delta, modifiers));
        return true;
    }

    public boolean keyDown(KeyEvent event) {
        input().keyDown(event);
        closePopupsOutside();
        return true;
    }

    public boolean keyUp(KeyEvent event) {
        input().keyUp(event);
        return true;
    }

    public boolean textInput(String text) {
        input().textInput(new TextInputEvent(text));
        return true;
    }

    public boolean preedit(PreeditEvent event) {
        input().preedit(event);
        return true;
    }

    public void clearFocus() {
        input().focus(null);
    }

    public boolean hasOpenPopup() {
        for (ColorField field : colorFields) {
            if (field.isOpen()) {
                return true;
            }
        }
        for (Dropdown dropdown : dropdowns) {
            if (dropdown.isOpen()) {
                return true;
            }
        }
        for (Dropdown dropdown : customDropdowns) {
            if (dropdown.isOpen()) {
                return true;
            }
        }
        return false;
    }

    public void closePopups() {
        for (ColorField field : colorFields) {
            field.close();
        }
        for (Dropdown dropdown : dropdowns) {
            dropdown.close();
        }
        for (Dropdown dropdown : customDropdowns) {
            dropdown.close();
        }
        input().focus(null);
    }

    private void closePopupsOutside() {
        for (ColorField field : colorFields) {
            field.closeIfFocusOutside(input().focused());
        }
        for (Dropdown dropdown : dropdowns) {
            dropdown.closeIfFocusOutside(input().focused());
        }
        for (Dropdown dropdown : customDropdowns) {
            dropdown.closeIfFocusOutside(input().focused());
        }
    }

    private void buildSettings(Box content, List<Setting> settings) {
        Card card = null;
        for (Setting setting : settings) {
            if (!setting.isVisible()) {
                continue;
            }
            if (setting instanceof Setting.Header header) {
                if (card != null) {
                    content.add(card);
                }
                card = newCard(Localization.tr(header.label()),
                        header.subtitle() == null || header.subtitle().isEmpty()
                                ? Localization.tr("调整此卡片的选项")
                                : Localization.tr(header.subtitle()));
                continue;
            }
            if (setting instanceof Setting.GroupSetting group) {
                // A group is a functional card, not a subsection of the previous card. The group
                // switch becomes the card's title row; groups without switches get a text title.
                if (card != null) {
                    content.add(card);
                    card = null;
                }
                String description = group.subtitle() == null || group.subtitle().isEmpty()
                        ? Localization.tr("调整此卡片的选项") : Localization.tr(group.subtitle());
                Card groupCard = newCard();
                Switch groupSwitch = null;
                if (group.hasSwitch()) {
                    groupSwitch = new Switch("", group::switchValue, value -> {
                        if (value != group.switchValue()) {
                            group.toggleSwitch();
                        }
                    }, Style.builder().width(82f).height(40f).build(), palette);
                    groupSwitch.flat(true);
                }
                groupCard.add(headerBlock(Localization.tr(group.label()), description, groupSwitch));
                for (Setting child : group.children()) {
                    if (child.isVisible()) {
                        addSetting(groupCard, child);
                    }
                }
                content.add(groupCard);
                continue;
            }
            if (card == null) {
                String title = feature == null ? Localization.tr("设置") : feature.name();
                card = newCard(title, Localization.tr("调整本模块的主要选项"));
            }
            addSetting(card, setting);
        }
        if (card != null) {
            content.add(card);
        }
    }

    /** Keeps the accent hue, raises saturation and slightly lifts value for header text. */
    private static int headerColor(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        float[] hsb = java.awt.Color.RGBtoHSB(r, g, b, null);
        hsb[1] = Math.min(1f, hsb[1] * 1.45f + 0.03f);
        hsb[2] = Math.min(1f, hsb[2] * 1.10f + 0.06f);
        int rgb = java.awt.Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]) & 0xFFFFFF;
        return (a << 24) | rgb;
    }

    private Card newCard() {
        return new Card(Style.builder()
                .width(Dimension.percent(100f))
                .padding(16f)
                .gap(4f)
                .direction(Style.Direction.COLUMN)
                .build(), palette);
    }

    private Card newCard(String title, String description) {
        Card card = newCard();
        card.add(headerBlock(title, description, null));
        return card;
    }

    /**
     * Header and explanation share one tightly packed block. The explanation starts directly
     * below the title line, while card children are separated by the card's 2px gap.
     */
    private Box headerBlock(String title, String description, Switch toggle) {
        float line = Math.max(18f, ClickGuiFonts.lineHeight());
        float descHeight = Math.max(18f, Math.round(line * 0.82f));
        float gap = 2f;
        float right = toggle == null ? 0f : 98f;
        Box header = new Box(Style.builder()
                .width(Dimension.percent(100f))
                .height(Math.max(toggle == null ? 0f : 39f, line + gap + descHeight))
                .build());
        header.add(new Text(title, Style.builder()
                .position(Style.Position.ABSOLUTE)
                .left(11f).right(right).top(0f).height(line).build(),
                1f, TextWeight.SEMIBOLD).color(headerColor(ClickGuiStyle.sectionTitle())));
        header.add(new Text(description, Style.builder()
                .position(Style.Position.ABSOLUTE)
                .left(11f).right(right).top(line + gap).height(descHeight).build(),
                0.82f, TextWeight.REGULAR).color(palette.description()));
        if (toggle != null) {
            toggle.style(Style.builder()
                    .position(Style.Position.ABSOLUTE)
                    .right(0f).top(0f)
                    .width(82f).height(40f).build());
            header.add(toggle);
        }
        return header;
    }

    private void addSetting(Box parent, Setting setting) {
        if (setting instanceof Setting.BooleanSetting bool) {
            parent.add(new Switch(Localization.tr(bool.label()), bool::value, value -> {
                if (value != bool.value()) {
                    bool.toggle();
                }
            }, Style.builder().width(Dimension.percent(100f)).height(40f).build(), palette)
                    .flat(true));
        } else if (setting instanceof Setting.SliderSetting slider) {
            parent.add(new Slider(Localization.tr(slider.label()), slider.min(), slider.max(),
                    slider::value, slider::setValue, slider.format(), slider.step(),
                    Style.builder().width(Dimension.percent(100f)).height(40f).build(), palette)
                    .flat(true));
        } else if (setting instanceof Setting.DropdownSetting dropdownSetting) {
            List<String> raw = dropdownSetting.options();
            List<String> shown = new ArrayList<>();
            for (String option : raw) {
                shown.add(Localization.tr(option));
            }
            int initial = Math.max(0, raw.indexOf(dropdownSetting.value()));
            Dropdown dropdown = new Dropdown(Localization.tr(setting.label()), shown,
                    () -> {
                        int selected = raw.indexOf(dropdownSetting.value());
                        return selected >= 0 ? selected : initial;
                    },
                    index -> {
                        if (index >= 0 && index < raw.size()) {
                            dropdownSetting.select(raw.get(index));
                            rebuild();
                        }
                    },
                    Style.builder().width(Dimension.percent(100f)).height(40f).build(), palette);
            parent.add(dropdown);
            dropdowns.add(dropdown);
        } else if (setting instanceof Setting.ColorSetting color) {
            ColorField field = new ColorField(Localization.tr(color.label()), color::value,
                    color::setValue, Localization.tr("关闭"),
                    Style.builder().width(Dimension.percent(100f)).height(40f).build(), palette);
            field.flat(true);
            parent.add(field);
            colorFields.add(field);
        } else if (setting instanceof Setting.ActionSetting action) {
            parent.add(new Button(action.text(), () -> {
                action.run();
                rebuild();
            }, Style.builder().width(Dimension.percent(100f)).height(46f).build(), palette));
        } else if (setting instanceof Setting.V2CustomSetting custom) {
            Box row = new Box(Style.builder()
                    .width(Dimension.percent(100f))
                    .height(custom.height())
                    .direction(Style.Direction.ROW)
                    .gap(15f)
                    .build());
            // A row with a popup is given the page's own router and rebuild hook, so its floating
            // layer joins the layer stack that draws and hit-tests above everything else.
            if (custom.hostedLayout() != null) {
                custom.hostedLayout().layout(row, palette, customHost);
            } else {
                custom.layout().layout(row, palette);
            }
            parent.add(row);
        } else if (setting instanceof Setting.ChoiceSetting choice) {
            List<String> options = new ArrayList<>();
            for (String option : choice.options()) {
                options.add(Localization.tr(option));
            }
            parent.add(new Segmented(options, choice::selected, index -> {
                choice.select(index);
                if (choice.rebuildsPage()) {
                    rebuild();
                }
            }, Style.builder().width(Dimension.percent(100f)).height(46f).build(), palette));
        } else if (setting instanceof Setting.GroupSetting group) {
            if (group.hasSwitch()) {
                parent.add(new Switch(Localization.tr(group.label()), group::switchValue, value -> {
                    if (value != group.switchValue()) {
                        group.toggleSwitch();
                    }
                }, Style.builder().width(Dimension.percent(100f)).height(40f).build(), palette)
                        .flat(true));
            } else {
                parent.add(new Text(Localization.tr(group.label()),
                        Style.builder().height(28f).build()));
            }
            for (Setting child : group.children()) {
                if (child.isVisible()) {
                    addSetting(parent, child);
                }
            }
        } else if (setting instanceof Setting.TextFieldSetting textSetting) {
            TextField field = new TextField(Localization.tr(textSetting.placeholder()),
                    Localization.tr(setting.label()),
                    Style.builder().width(Dimension.percent(100f)).height(40f).build(), palette)
                    .labelOutside(true);
            field.text(textSetting.value());
            field.onChange(textSetting::setValue);
            parent.add(field);
        }
    }
}

