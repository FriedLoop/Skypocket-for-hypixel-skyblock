package dev.skypocket.gui;

import dev.skypocket.gui.setting.Setting;
import dev.skypocket.i18n.Localization;
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
public final class SettingsPage {
    private final Feature feature;
    private final WidgetPalette palette;
    private final float width;
    private final float height;
    private final float columnWidth;
    private final List<ColorField> colorFields = new ArrayList<>();
    private final List<Dropdown> dropdowns = new ArrayList<>();
    private Box root;
    private ScrollView scroll;
    private InputRouter input;
    private long renderRevision;
    private UiElement lastFocused;
    private float renderScaleX = 1f;
    private float renderScaleY = 1f;

    public SettingsPage(Feature feature, WidgetPalette palette, float width, float height) {
        this(feature, palette, width, height, Math.min(520f, Math.max(200f, width - 30f)));
    }

    public SettingsPage(Feature feature, WidgetPalette palette, float width, float height,
                        float columnWidth) {
        this.feature = feature;
        this.palette = palette;
        this.width = width;
        this.height = height;
        this.columnWidth = Math.max(200f, Math.min(columnWidth, width - 30f));
        rebuild();
    }

    public InputRouter input() {
        return input;
    }

    public boolean isFocused() {
        return input.focused() != null;
    }

    public void rebuild() {
        colorFields.clear();
        dropdowns.clear();
        Box page = new Box(Style.builder()
                .width(width)
                .height(height)
                .direction(Style.Direction.COLUMN)
                .build());
        if (feature == null) {
            page.add(new Text(Localization.tr("找不到设置模块"),
                    Style.builder().height(18f).build()));
        } else {
            Box content = new Box(Style.builder()
                    .direction(Style.Direction.COLUMN)
                    .gap(4f)
                    .build());
            buildSettings(content, feature.settings());
            float columnLeft = (width - columnWidth) / 2f;
            scroll = new ScrollView(Style.builder()
                    .position(Style.Position.ABSOLUTE)
                    .left(columnLeft)
                    .top(0f)
                    .width(columnWidth)
                    .height(height)
                    .padding(2f)
                    .build(), content, 24f, false);
            page.add(scroll);
            Scrollbar bar = scroll.scrollbar();
            bar.style(Style.builder()
                    .position(Style.Position.ABSOLUTE)
                    .left(columnLeft + columnWidth + 5f)
                    .top(4f)
                    .bottom(4f)
                    .width(6f)
                    .build());
            page.add(bar);
        }
        root = page;
        input = new InputRouter(root);
        // The page cache must not reuse the texture from before a style/section rebuild.
        renderRevision++;
    }

    public void layout() {
        TextMeasurer measurer = (text, maxWidth) ->
                new Size(ClickGuiFonts.width(
                        dev.skypocket.i18n.Localization.auto(text)), ClickGuiFonts.lineHeight());
        LayoutEngine.layout(root, width, height, measurer);
        if (scroll != null) {
            scroll.refresh();
        }
        for (ColorField field : colorFields) {
            field.layoutPopup(measurer, width, height);
            if (field.isOpen()) {
                if (!input.layers().contains(field.popup())) {
                    input.addLayer(field.popup());
                }
            } else {
                input.removeLayer(field.popup());
            }
        }
        for (Dropdown dropdown : dropdowns) {
            dropdown.layoutPopup(measurer, width, height);
            if (dropdown.isOpen()) {
                if (!input.layers().contains(dropdown.popup())) {
                    input.addLayer(dropdown.popup());
                }
            } else {
                input.removeLayer(dropdown.popup());
            }
        }
    }

    public void draw(GuiGraphicsExtractor graphics, float scaleX, float scaleY, boolean animating) {
        UiElement focused = input.focused();
        if (focused != lastFocused) {
            lastFocused = focused;
            renderRevision++;
        }
        McUiPainter painter = new McUiPainter(graphics);
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        boolean cached = GuiPanelCache.draw(graphics, pageCacheKey(),
                Math.max(1, Math.round(width)), Math.max(1, Math.round(height)),
                scaleX, scaleY, animating,
                (pageGraphics, pageMouseX, pageMouseY, pageDelta) -> drawBase(pageGraphics),
                0, 0, 0f);
        renderScaleX = cached ? GuiPanelCache.lastBlitScaleX() : 1f;
        renderScaleY = cached ? GuiPanelCache.lastBlitScaleY() : 1f;
        if (!cached) {
            drawElement(painter, root);
        } else {
            // The cached texture is blitted with exactScale to stay 1:1 with texels. Live
            // overlays must use the same scale about the same page origin, otherwise a popup
            // drifts horizontally by a pixel at its right edge.
            pose.scale(renderScaleX, renderScaleY);
            drawDynamicControls(painter);
        }
        for (ColorField field : colorFields) {
            if (field.isOpen()) {
                field.syncFields();
                drawElement(painter, field.popup());
            }
        }
        for (Dropdown dropdown : dropdowns) {
            if (dropdown.isOpen()) {
                drawElement(painter, dropdown.popup());
            }
        }
        pose.popMatrix();
    }

    public float inputScaleX() {
        return renderScaleX;
    }

    public float inputScaleY() {
        return renderScaleY;
    }

    /** Height of the laid-out scroll content, used when a host wants a content-sized popup. */
    public float contentHeight() {
        return scroll == null ? 0f : scroll.content().bounds().height();
    }

    private long pageCacheKey() {
        long scrollHash = scroll == null ? 0L : Float.floatToIntBits(scroll.scrollY());
        // A rebuilt page can reuse renderRevision 0 and the same scroll offset; its identity prevents
        // the global GuiPanelCache from blitting the previous layout's texture.
        return System.identityHashCode(this) * 1_000_003L
                + renderRevision * 31_415_926_535L + scrollHash;
    }

    private void drawBase(GuiGraphicsExtractor graphics) {
        dev.skypocket.render.ScissorTracker.reset(graphics);
        Control.neutralPaint(true);
        try {
            drawElement(new McUiPainter(graphics), root);
        } finally {
            Control.neutralPaint(false);
        }
    }

    private void drawDynamicControls(McUiPainter painter) {
        Set<UiElement> dynamic = new HashSet<>();
        collectDynamic(root, dynamic);
        if (!dynamic.isEmpty()) {
            Control.overlayPaint(true);
            try {
                drawDynamicElement(painter, root, dynamic);
            } finally {
                Control.overlayPaint(false);
            }
        }
    }

    private static void collectDynamic(UiElement element, Set<UiElement> dynamic) {
        if (element instanceof Control control
                && (control.dynamicOverlay() || control.isFocused() || control.isPressed()
                || control.flashActive()
                || (control instanceof ColorField color && color.isOpen())
                || (control instanceof Dropdown dropdown && dropdown.isOpen()))) {
            dynamic.add(control);
        }
        for (UiElement child : element.children()) {
            collectDynamic(child, dynamic);
        }
    }

    private boolean drawDynamicElement(McUiPainter painter, UiElement element,
                                       Set<UiElement> dynamic) {
        boolean any = dynamic.contains(element);
        for (UiElement child : element.children()) {
            any |= containsDynamic(child, dynamic);
        }
        if (!any || !element.visible()) {
            return false;
        }
        Matrix3x2fStack pose = painter.graphics().pose();
        pose.pushMatrix();
        pose.translate(element.offsetX(), element.offsetY());
        boolean scroll = element instanceof ScrollView;
        if (scroll) {
            painter.pushClip(element.bounds());
        }
        if (dynamic.contains(element) && element instanceof Control control) {
            control.paint(painter);
        }
        for (UiElement child : element.children()) {
            drawDynamicElement(painter, child, dynamic);
        }
        if (scroll) {
            painter.popClip();
        }
        pose.popMatrix();
        return true;
    }

    private static boolean containsDynamic(UiElement element, Set<UiElement> dynamic) {
        if (dynamic.contains(element)) {
            return true;
        }
        for (UiElement child : element.children()) {
            if (containsDynamic(child, dynamic)) {
                return true;
            }
        }
        return false;
    }

    private void drawElement(McUiPainter painter, UiElement element) {
        drawElement(painter, element, null);
    }

    private void drawElement(McUiPainter painter, UiElement element, ScrollView viewport) {
        if (!element.visible()) {
            return;
        }
        if (viewport != null && !intersects(element, viewport)) {
            return;
        }
        Matrix3x2fStack pose = painter.graphics().pose();
        pose.pushMatrix();
        pose.translate(element.offsetX(), element.offsetY());
        ScrollView nextViewport = element instanceof ScrollView view ? view : viewport;
        if (element instanceof ScrollView view) {
            // The cards already sit on the ClickGUI shell; an extra dark page fill makes the
            // whole content pane look like a second surface behind them.
            painter.pushClip(view.bounds());
        }
        if (element instanceof Painted painted) {
            painted.paint(painter);
        } else if (element instanceof Text text) {
            if (text.fontScale() == 1f && text.weight() == TextWeight.REGULAR) {
                painter.text(text.value(), text.bounds().x(), text.bounds().y(), text.color());
            } else {
                painter.textWeighted(text.value(), text.bounds().x(), text.bounds().y(),
                        text.color(), text.fontScale(), text.weight());
            }
        }
        for (UiElement child : element.children()) {
            drawElement(painter, child, nextViewport);
        }
        if (element instanceof ScrollView) {
            painter.popClip();
        }
        pose.popMatrix();
    }

    private static boolean intersects(UiElement element, ScrollView viewport) {
        float ex = element.visualX();
        float ey = element.visualY();
        float vx = viewport.visualX();
        float vy = viewport.visualY();
        return ex + element.bounds().width() >= vx
                && ex <= vx + viewport.bounds().width()
                && ey + element.bounds().height() >= vy
                && ey <= vy + viewport.bounds().height();
    }

    public boolean pointerDown(float x, float y, int button, int modifiers) {
        input.pointerDown(new PointerEvent(x, y, button, modifiers));
        closePopupsOutside();
        return true;
    }

    public boolean pointerMove(float x, float y, int modifiers) {
        input.pointerMove(PointerEvent.move(x, y, modifiers));
        return true;
    }

    public boolean pointerUp(float x, float y, int button, int modifiers) {
        input.pointerUp(new PointerEvent(x, y, button, modifiers));
        closePopupsOutside();
        return true;
    }

    public boolean wheel(float x, float y, float delta, int modifiers) {
        input.wheel(new WheelEvent(x, y, delta, modifiers));
        return true;
    }

    public boolean keyDown(KeyEvent event) {
        input.keyDown(event);
        closePopupsOutside();
        return true;
    }

    public boolean keyUp(KeyEvent event) {
        input.keyUp(event);
        return true;
    }

    public boolean textInput(String text) {
        input.textInput(new TextInputEvent(text));
        return true;
    }

    public boolean preedit(PreeditEvent event) {
        input.preedit(event);
        return true;
    }

    public void clearFocus() {
        input.focus(null);
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
        return false;
    }

    public void closePopups() {
        for (ColorField field : colorFields) {
            field.close();
        }
        for (Dropdown dropdown : dropdowns) {
            dropdown.close();
        }
        input.focus(null);
    }

    private void closePopupsOutside() {
        for (ColorField field : colorFields) {
            field.closeIfFocusOutside(input.focused());
        }
        for (Dropdown dropdown : dropdowns) {
            dropdown.closeIfFocusOutside(input.focused());
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
                card = newCard();
                card.add(new Text(Localization.tr(header.label()),
                        Style.builder().height(16f).build(), 1f, TextWeight.SEMIBOLD));
                if (header.subtitle() != null && !header.subtitle().isEmpty()) {
                    card.add(new Text(Localization.tr(header.subtitle()),
                            Style.builder().height(12f).build(), 0.82f, TextWeight.REGULAR)
                            .color(0xFFC5CDD9));
                }
                continue;
            }
            if (setting instanceof Setting.GroupSetting group) {
                // A group is a functional card, not a subsection of the previous card. The group
                // switch becomes the card's title row; groups without switches get a text title.
                if (card != null) {
                    content.add(card);
                    card = null;
                }
                Card groupCard = newCard();
                if (group.hasSwitch()) {
                    Box titleRow = new Box(Style.builder()
                            .width(Dimension.percent(100f))
                            .height(20f)
                            .direction(Style.Direction.ROW)
                            .build());
                    titleRow.add(new Text(Localization.tr(group.label()),
                            Style.builder().height(16f).flexGrow(1f).build(), 1f,
                            TextWeight.SEMIBOLD));
                    titleRow.add(new Switch("", group::switchValue, value -> {
                        if (value != group.switchValue()) {
                            group.toggleSwitch();
                        }
                    }, Style.builder().width(42f).height(20f).build(), palette).flat(true));
                    groupCard.add(titleRow);
                } else {
                    groupCard.add(new Text(Localization.tr(group.label()),
                            Style.builder().height(16f).build(), 1f, TextWeight.SEMIBOLD));
                }
                for (Setting child : group.children()) {
                    if (child.isVisible()) {
                        addSetting(groupCard, child);
                    }
                }
                content.add(groupCard);
                continue;
            }
            if (card == null) {
                card = newCard();
            }
            addSetting(card, setting);
        }
        if (card != null) {
            content.add(card);
        }
    }

    private Card newCard() {
        return new Card(Style.builder()
                .width(Dimension.percent(100f))
                .padding(8f)
                .gap(2f)
                .direction(Style.Direction.COLUMN)
                .build(), palette);
    }

    private void addSetting(Box parent, Setting setting) {
        if (setting instanceof Setting.BooleanSetting bool) {
            parent.add(new Switch(Localization.tr(bool.label()), bool::value, value -> {
                if (value != bool.value()) {
                    bool.toggle();
                }
            }, Style.builder().width(Dimension.percent(100f)).height(20f).build(), palette)
                    .flat(true));
        } else if (setting instanceof Setting.SliderSetting slider) {
            parent.add(new Slider(Localization.tr(slider.label()), slider.min(), slider.max(),
                    slider::value, slider::setValue, slider.format(), slider.step(),
                    Style.builder().width(Dimension.percent(100f)).height(20f).build(), palette)
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
                    Style.builder().width(Dimension.percent(100f)).height(20f).build(), palette);
            parent.add(dropdown);
            dropdowns.add(dropdown);
        } else if (setting instanceof Setting.ColorSetting color) {
            ColorField field = new ColorField(Localization.tr(color.label()), color::value,
                    color::setValue, Localization.tr("关闭"),
                    Style.builder().width(Dimension.percent(100f)).height(20f).build(), palette);
            field.flat(true);
            parent.add(field);
            colorFields.add(field);
        } else if (setting instanceof Setting.ActionSetting action) {
            parent.add(new Button(action.text(), () -> {
                action.run();
                rebuild();
            }, Style.builder().width(Dimension.percent(100f)).height(24f).build(), palette));
        } else if (setting instanceof Setting.V2CustomSetting custom) {
            Box row = new Box(Style.builder()
                    .width(Dimension.percent(100f))
                    .height(custom.height())
                    .direction(Style.Direction.ROW)
                    .gap(8f)
                    .build());
            custom.layout().layout(row, palette);
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
            }, Style.builder().width(Dimension.percent(100f)).height(24f).build(), palette));
        } else if (setting instanceof Setting.GroupSetting group) {
            if (group.hasSwitch()) {
                parent.add(new Switch(Localization.tr(group.label()), group::switchValue, value -> {
                    if (value != group.switchValue()) {
                        group.toggleSwitch();
                    }
                }, Style.builder().width(Dimension.percent(100f)).height(20f).build(), palette)
                        .flat(true));
            } else {
                parent.add(new Text(Localization.tr(group.label()),
                        Style.builder().height(14f).build()));
            }
            for (Setting child : group.children()) {
                if (child.isVisible()) {
                    addSetting(parent, child);
                }
            }
        } else if (setting instanceof Setting.TextFieldSetting textSetting) {
            TextField field = new TextField(Localization.tr(textSetting.placeholder()),
                    Localization.tr(setting.label()),
                    Style.builder().width(Dimension.percent(100f)).height(20f).build(), palette)
                    .labelOutside(true);
            field.text(textSetting.value());
            field.onChange(textSetting::setValue);
            parent.add(field);
        }
    }
}

