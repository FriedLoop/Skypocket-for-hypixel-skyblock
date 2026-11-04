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
import dev.skypocket.ui.widget.Slider;
import dev.skypocket.ui.widget.Switch;
import dev.skypocket.ui.widget.WidgetPalette;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Content-only player-stats settings page. It owns an element tree, its input router and its popup
 * layers, but no Screen; the standalone debug page and the unified ClickGUI can both host it.
 */
public final class StatsSettingsPage {
    private final Feature feature;
    private final WidgetPalette palette;
    private final float width;
    private final float height;
    private final List<ColorField> colorFields = new ArrayList<>();
    private Box root;
    private ScrollView scroll;
    private InputRouter input;
    private long renderRevision;
    private UiElement lastFocused;

    public StatsSettingsPage(Feature feature, WidgetPalette palette, float width, float height) {
        this.feature = feature;
        this.palette = palette;
        this.width = width;
        this.height = height;
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
        Box page = new Box(Style.builder()
                .width(width)
                .height(height)
                .direction(Style.Direction.COLUMN)
                .build());
        if (feature == null) {
            page.add(new Text(Localization.tr("找不到 stats_display 模块"),
                    Style.builder().height(18f).build()));
        } else {
            Box content = new Box(Style.builder()
                    .direction(Style.Direction.COLUMN)
                    .gap(4f)
                    .build());
            buildSettings(content, feature.settings());
            scroll = new ScrollView(Style.builder()
                    .height(height)
                    .padding(2f)
                    .build(), content);
            page.add(scroll);
        }
        root = page;
        input = new InputRouter(root);
    }

    public void layout() {
        TextMeasurer measurer = (text, maxWidth) ->
                new Size(ClickGuiFonts.width(text), ClickGuiFonts.lineHeight());
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
        if (!cached) {
            drawElement(painter, root);
        } else {
            drawDynamicControls(painter);
        }
        for (ColorField field : colorFields) {
            if (field.isOpen()) {
                field.syncFields();
                drawElement(painter, field.popup());
            }
        }
        pose.popMatrix();
    }

    private long pageCacheKey() {
        long scrollHash = scroll == null ? 0L : Float.floatToIntBits(scroll.scrollY());
        return renderRevision * 31_415_926_535L + scrollHash;
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
            drawDynamicElement(painter, root, dynamic);
        }
    }

    private static void collectDynamic(UiElement element, Set<UiElement> dynamic) {
        if (element instanceof Control control
                && (control.isFocused() || control.isPressed() || control.flashActive()
                || (control instanceof ColorField color && color.isOpen()))) {
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
        if (!element.visible()) {
            return;
        }
        Matrix3x2fStack pose = painter.graphics().pose();
        pose.pushMatrix();
        pose.translate(element.offsetX(), element.offsetY());
        if (element instanceof ScrollView view) {
            painter.surface(view.bounds(), 4f, 0xFF20232A, 0);
            painter.pushClip(view.bounds());
        }
        if (element instanceof Painted painted) {
            painted.paint(painter);
        } else if (element instanceof Text text) {
            painter.text(text.value(), text.bounds().x(), text.bounds().y(), 0xFFE6EAF2);
        }
        for (UiElement child : element.children()) {
            drawElement(painter, child);
        }
        if (element instanceof ScrollView) {
            painter.popClip();
        }
        pose.popMatrix();
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
        return false;
    }

    public void closePopups() {
        for (ColorField field : colorFields) {
            field.close();
        }
        input.focus(null);
    }

    private void closePopupsOutside() {
        for (ColorField field : colorFields) {
            field.closeIfFocusOutside(input.focused());
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
                card = new Card(Style.builder()
                        .width(Dimension.percent(100f))
                        .padding(8f)
                        .gap(2f)
                        .direction(Style.Direction.COLUMN)
                        .build(), palette);
                card.add(new Text(Localization.tr(header.label()),
                        Style.builder().height(16f).build()));
                if (header.subtitle() != null && !header.subtitle().isEmpty()) {
                    card.add(new Text(Localization.tr(header.subtitle()),
                            Style.builder().height(12f).build()));
                }
                continue;
            }
            if (card == null) {
                card = new Card(Style.builder()
                        .width(Dimension.percent(100f))
                        .padding(8f)
                        .gap(2f)
                        .direction(Style.Direction.COLUMN)
                        .build(), palette);
            }
            addSetting(card, setting);
        }
        if (card != null) {
            content.add(card);
        }
    }

    private void addSetting(Box parent, Setting setting) {
        if (setting instanceof Setting.BooleanSetting bool) {
            parent.add(new Switch(Localization.tr(bool.label()), bool::value, value -> {
                if (value != bool.value()) {
                    bool.toggle();
                }
            }, Style.builder().width(Dimension.percent(100f)).height(20f).build(), palette));
        } else if (setting instanceof Setting.SliderSetting slider) {
            parent.add(new Slider(Localization.tr(slider.label()), slider.min(), slider.max(),
                    slider::value, slider::setValue,
                    slider.max() - slider.min() > 20 ? "%.0f" : "%.2f",
                    Style.builder().width(Dimension.percent(100f)).height(20f).build(), palette));
        } else if (setting instanceof Setting.ColorSetting color) {
            ColorField field = new ColorField(Localization.tr(color.label()), color::value,
                    color::setValue, Localization.tr("关闭"),
                    Style.builder().width(Dimension.percent(100f)).height(20f).build(), palette);
            parent.add(field);
            colorFields.add(field);
        } else if (setting instanceof Setting.ActionSetting action) {
            parent.add(new Button(action.text(), () -> {
                action.run();
                rebuild();
            }, Style.builder().width(Dimension.percent(100f)).height(24f).build(), palette));
        } else if (setting instanceof Setting.GroupSetting group) {
            if (group.hasSwitch()) {
                parent.add(new Switch(Localization.tr(group.label()), group::switchValue, value -> {
                    if (value != group.switchValue()) {
                        group.toggleSwitch();
                    }
                }, Style.builder().width(Dimension.percent(100f)).height(20f).build(), palette));
            } else {
                parent.add(new Text(Localization.tr(group.label()),
                        Style.builder().height(14f).build()));
            }
            for (Setting child : group.children()) {
                if (child.isVisible()) {
                    addSetting(parent, child);
                }
            }
        } else if (setting instanceof Setting.TextFieldSetting) {
            parent.add(new Text(Localization.tr(setting.label()) + " "
                    + Localization.tr("（待迁移）"), Style.builder().height(16f).build()));
        }
    }
}
