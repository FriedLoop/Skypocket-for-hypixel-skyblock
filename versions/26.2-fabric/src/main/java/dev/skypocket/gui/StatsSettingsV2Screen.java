package dev.skypocket.gui;

import dev.skypocket.gui.setting.Setting;
import dev.skypocket.i18n.Localization;
import dev.skypocket.ui.Box;
import dev.skypocket.ui.Dimension;
import dev.skypocket.ui.ScrollView;
import dev.skypocket.ui.Style;
import dev.skypocket.ui.Text;
import dev.skypocket.ui.TextMeasurer;
import dev.skypocket.ui.mc.McUiPainter;
import dev.skypocket.ui.widget.Button;
import dev.skypocket.ui.widget.Card;
import dev.skypocket.ui.widget.ColorField;
import dev.skypocket.ui.widget.Slider;
import dev.skypocket.ui.widget.Switch;
import dev.skypocket.ui.widget.WidgetPalette;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * First real UI v2 page: the player-stats display settings. It uses the same declarative Setting
 * objects as the old panel but renders them with the reusable layout/input/widget stack.
 *
 * <p>Colour editing is intentionally not in this slice: it needs a v2 colour picker. Everything else
 * (style switch, vanilla-HUD group, text rows, classic/modern bar rows and advanced actions) is live.</p>
 */
public final class StatsSettingsV2Screen extends Ui2Screen {
    private static final float DESIGN_W = 440f;
    private static final float DESIGN_H = 560f;
    private final WidgetPalette palette = Ui2Theme.palette();
    private final List<ColorField> colorFields = new ArrayList<>();
    private Feature feature;

    public StatsSettingsV2Screen() {
        super(Component.literal(Localization.tr("玩家数值显示")), DESIGN_W, DESIGN_H);
        ModuleRegistry.init();
        feature = ModuleRegistry.byId("stats_display");
        setRoot(buildPage());
    }

    private Box buildPage() {
        colorFields.clear();
        Card root = new Card(Style.builder()
                .width(DESIGN_W)
                .height(DESIGN_H)
                .padding(10f)
                .gap(8f)
                .direction(Style.Direction.COLUMN)
                .build(), palette);

        Box header = Box.of(Style.builder()
                        .direction(Style.Direction.ROW)
                        .height(24f)
                        .gap(8f)
                        .build(),
                new Text(feature == null ? Localization.tr("玩家数值显示") : feature.name(),
                        Style.builder().height(20f).flexGrow(1f).build()));
        if (feature != null) {
            header.add(new Switch(Localization.tr("启用"), feature::isEnabled, feature::setEnabled,
                    Style.builder().width(140f).height(20f).build(), palette));
        }
        root.add(header);

        Box content = new Box(Style.builder()
                .direction(Style.Direction.COLUMN)
                .gap(4f)
                .build());
        if (feature == null) {
            content.add(new Text(Localization.tr("找不到 stats_display 模块"),
                    Style.builder().height(18f).build()));
        } else {
            buildSettings(content, feature.settings());
        }
        ScrollView scroll = new ScrollView(Style.builder()
                .height(466f)
                .padding(2f)
                .build(), content);
        root.add(scroll);
        root.add(new Button(Localization.tr("关闭"), this::onClose, Style.builder()
                .width(Dimension.percent(100f)).height(24f).build(), palette));
        return root;
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
                    slider::value,
                    slider::setValue, slider.max() - slider.min() > 20 ? "%.0f" : "%.2f",
                    Style.builder().width(Dimension.percent(100f)).height(20f).build(), palette));
        } else if (setting instanceof Setting.ActionSetting action) {
            parent.add(new Button(action.text(), () -> {
                action.run();
                rebuildPage();
            }, Style.builder().width(Dimension.percent(100f)).height(24f).build(), palette));
        } else if (setting instanceof Setting.ColorSetting color) {
            ColorField field = new ColorField(Localization.tr(color.label()), color::value,
                    color::setValue, Localization.tr("关闭"),
                    Style.builder().width(Dimension.percent(100f)).height(20f).build(), palette);
            parent.add(field);
            colorFields.add(field);
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

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        boolean handled = super.mouseClicked(event, doubled);
        closeColorPopupsOutside();
        invalidateLayout();
        return handled;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        boolean handled = super.keyPressed(event);
        closeColorPopupsOutside();
        invalidateLayout();
        return handled;
    }

    private void closeColorPopupsOutside() {
        for (ColorField field : colorFields) {
            field.closeIfFocusOutside(input.focused());
        }
    }

    @Override
    protected void onLayout(TextMeasurer measurer) {
        for (ColorField field : colorFields) {
            field.layoutPopup(measurer);
            if (field.isOpen()) {
                if (!input.layers().contains(field.popup())) {
                    input.addLayer(field.popup());
                }
            } else {
                input.removeLayer(field.popup());
            }
        }
    }

    @Override
    protected void drawOverlays(McUiPainter painter) {
        for (ColorField field : colorFields) {
            if (field.isOpen()) {
                // The popup fields must follow the SV square/alpha bar even when the page layout is
                // clean; syncing them here avoids forcing a full Flex relayout every frame.
                field.syncFields();
                drawElement(painter, field.popup());
            }
        }
    }

    private void rebuildPage() {
        setRoot(buildPage());
    }
}
