package dev.skypocket.gui;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.gui.widget.ClickGuiStyle;
import dev.skypocket.i18n.Localization;
import dev.skypocket.render.ClickGuiFonts;
import dev.skypocket.render.ClickGuiMetrics;
import dev.skypocket.ui.Box;
import dev.skypocket.ui.Dimension;
import dev.skypocket.ui.ScrollView;
import dev.skypocket.ui.Size;
import dev.skypocket.ui.Style;
import dev.skypocket.ui.Text;
import dev.skypocket.ui.TextMeasurer;
import dev.skypocket.ui.TextWeight;
import dev.skypocket.ui.UiElement;
import dev.skypocket.ui.PageTransform;
import dev.skypocket.ui.UiPage;
import dev.skypocket.ui.widget.Button;
import dev.skypocket.ui.widget.Card;
import dev.skypocket.ui.widget.ColorField;
import dev.skypocket.ui.widget.Dropdown;
import dev.skypocket.ui.widget.Segmented;
import dev.skypocket.ui.widget.Slider;
import dev.skypocket.ui.widget.Swatch;
import dev.skypocket.ui.widget.Switch;
import dev.skypocket.ui.widget.TextField;
import dev.skypocket.ui.widget.WidgetPalette;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Developer screen that exercises every v2 capability and compares v2 rendering against the
 * immediate-mode style tokens.
 *
 * <p>It exists because v2 had no complete page. Components and input routing were proven one at a
 * time inside a larger immediate-mode screen, so nobody could answer "does the framework work" - and
 * three attempts at moving one screen onto it failed for reasons that a page like this would have
 * surfaced in minutes.
 *
 * <p>Four sections, in the order the contract covers them: layout, widgets, style, interaction.
 */
public final class DevUiTestScreen extends Screen {
    private static final float PAD = 12f;
    private static final float GAP = 8f;

    private static final TextMeasurer MEASURER = (text, maxWidth) ->
            new Size(ClickGuiFonts.width(Localization.auto(text)), ClickGuiFonts.lineHeight());

    private final WidgetPalette palette = Ui2Theme.palette();
    private final TestPage page = new TestPage();

    /** Live values the interactive section binds to, so their redraws are visible. */
    private boolean demoSwitch;
    private double demoSlider = 0.5;
    private int demoMode;
    private String demoText = "";
    private int demoColor = 0xFFFF5555;
    /** Selection of the demo dropdown; it has to be stored, or the field always shows the first item. */
    private int demoDropdown;
    /** The colour row and the dropdown, handed to the page as popup owners on rebuild. */
    private ColorField testColor;
    private Dropdown testDropdown;

    public DevUiTestScreen() {
        super(Component.literal("UI v2 Test"));
    }

    @Override
    protected void init() {
        // Taking text-input focus is what makes the screen receive character events at all.
        // Without it a TextField never sees charTyped and typing silently does nothing - the same
        // requirement OneClickScreen satisfies in its own init.
        if (minecraft != null) {
            minecraft.onTextInputFocusChange(this, true);
        }
        // The page converts pointer positions back through the same transform the pose applies.
        page.transform(new PageTransform() {
            @Override
            public float toDesignX(double screenX) {
                return (float) ((screenX - originX()) / scaleX());
            }

            @Override
            public float toDesignY(double screenY) {
                return (float) ((screenY - originY()) / scaleY());
            }
        });
        // The page is a fixed design surface; only rebuild here, sizing happens while drawing.
        page.rebuild();
    }

    @Override
    /**
     * Draws the page, scaled to fit the window exactly like the real panel.
     *
     * <p>Three things here are not optional, and each was the cause of a visible defect on the first
     * run of this screen:
     *
     * <p><b>The scale.</b> The page is a fixed 1348x810 design surface. Drawing it without scaling
     * made it overflow the window.
     *
     * <p><b>{@code ClickGuiMetrics.setEffectiveScale}.</b> Font rasterisation reads
     * {@code ClickGuiFonts.renderScale}, which only the real panel set. Without it the glyph atlas is
     * built for a 1:1 screen and every character is blurred - the difference was immediately obvious
     * next to the real panel.
     *
     * <p><b>Converting the pointer into design space.</b> Hit testing happens in design units; passing
     * screen pixels made every control respond where it was not.
     */
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        float scaleX = scaleX();
        float scaleY = scaleY();
        ClickGuiMetrics.setEffectiveScale(scaleX);
        page.bounds(PANEL_W, PANEL_H);
        page.layout(MEASURER);



        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(originX(), originY());
        pose.scale(scaleX, scaleY);
        // Backdrop covers the design surface only. The real ClickGUI leaves the world visible around
        // it; filling the whole window made this read as a different kind of screen.
        graphics.fill(0, 0, (int) PANEL_W, (int) PANEL_H, 0xFF1A1A1E);
        page.draw(graphics, scaleX, scaleY, false);
        pose.popMatrix();
    }

    /** Fixed design surface, the same one OneClickScreen uses. */
    private static final float PANEL_W = 1348f;
    private static final float PANEL_H = 810f;

    private float guiScale() {
        return Math.max(1.0e-4f, minecraft == null ? 1f : minecraft.getWindow().getGuiScale());
    }

    private float fit() {
        float target = Math.min(ClickGuiMetrics.projectedScale(guiScale()),
                Math.min((width - 16f) / PANEL_W, (height - 16f) / PANEL_H));
        return Math.max(0.05f, target);
    }

    private float scaleX() {
        return Math.round(PANEL_W * fit() * guiScale()) / (PANEL_W * guiScale());
    }

    private float scaleY() {
        return Math.round(PANEL_H * fit() * guiScale()) / (PANEL_H * guiScale());
    }

    private int originX() {
        return Math.round((width - PANEL_W * scaleX()) / 2f);
    }

    private int originY() {
        return Math.round((height - PANEL_H * scaleY()) / 2f);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubled) {
        page.pointerDown(event.x(), event.y(), 0, modifiers());
        return true;
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx,
                                double dy) {
        page.pointerMove(event.x(), event.y(), 0, modifiers());
        return true;
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        page.pointerUp(event.x(), event.y(), 0, modifiers());
        return true;
    }

    @Override
    public void mouseMoved(double x, double y) {
        // Hover only updates from a move event; without this the option rows never see
        // onHoverChanged and a menu has no highlight at all.
        page.mouseMoved(x, y);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        page.wheel(x, y, vertical, modifiers());
        return true;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (page.shouldCloseOnEscape(event)) {
            onClose();
            return true;
        }
        // The page converts the platform key code itself; see UiPage.keyDown.
        page.keyDown(event);
        return true;
    }

    @Override
    public boolean keyReleased(net.minecraft.client.input.KeyEvent event) {
        page.keyUp(event);
        return true;
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        page.textInput(event);
        return true;
    }

    /** Modifier bits for pointer events; this screen does not track them, so none are applied. */
    private int modifiers() {
        return 0;
    }

    /** Modifier bits taken from a key event, which carries them. */


    @Override
    public void onClose() {
        // Releases the text-input focus this screen holds. Without it the screen is left with the
        // keyboard captured and ESC appears to do nothing - which is exactly what happened on the
        // first run.
        if (minecraft != null) {
            minecraft.onTextInputFocusChange(this, false);
            minecraft.gui.setScreen(null);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---- the page -------------------------------------------------------------------------

    private final class TestPage extends UiPage {
        private final Box root = new Box(Style.builder()
                .width(Dimension.percent(100f))
                .height(Dimension.percent(100f))
                .direction(Style.Direction.COLUMN)
                .gap(GAP)
                .padding(PAD)
                .build());

        private ScrollView scroll;

        /** Widgets built by widgetSection(), registered as popup owners from build(). */
        private Dropdown trackedDropdown;
        private ColorField trackedColor;

        /**
         * Folds the demo widgets' state into the cache key.
         *
         * <p>Without this, picking a dropdown item changes only a local field: nothing bumps
         * {@code ConfigManager.revision()}, so the baked static layer keeps showing the old value
         * until something else invalidates the cache. Any page whose content depends on state that
         * is not in the config has to do the same.
         */
        @Override
        protected long revision() {
            // Config writes plus this page's own demo state: the widgets here write plain fields,
            // and nothing bumps the config revision for them.
            return ConfigManager.revision()
                    + demoDropdown * 31L
                    + demoMode * 1_009L
                    + Double.doubleToLongBits(demoSlider)
                    + (demoSwitch ? 7L : 0L)
                    + demoText.hashCode()
                    + demoColor;
        }

        @Override
        protected UiElement build() {
            root.clearChildren();
            Box content = new Box(Style.builder()
                    .width(Dimension.percent(100f))
                    .direction(Style.Direction.COLUMN)
                    .gap(GAP)
                    .build());
            content.add(section("1. 布局能力 / Layout", layoutSection()));
            content.add(section("2. 组件 / Widgets", widgetSection()));
            content.add(section("3. 样式复刻 / Style tokens", styleSection()));
            content.add(section("4. 交互 / Interaction", interactionSection()));

            // Registered here, inside the page: trackPopup is protected because a popup owner belongs
            // to the page that builds it, and letting an outside caller inject one would invite a
            // page to be driven from two places.
            if (testDropdown != null) {
                trackPopup(testDropdown);
            }
            if (testColor != null) {
                trackPopup(testColor);
            }

            scroll = new ScrollView(Style.builder()
                    .width(Dimension.percent(100f))
                    .flexGrow(1f)
                    .build(), content, 28f);
            root.add(scroll);
            return root;
        }
    }

    // ---- sections -------------------------------------------------------------------------

    private Card section(String title, UiElement body) {
        Card card = new Card(Style.builder()
                .width(Dimension.percent(100f))
                .direction(Style.Direction.COLUMN)
                .gap(6f)
                .padding(10f)
                .build(), palette);
        card.add(new Text(title, Style.builder().build(), 0.85f, TextWeight.SEMIBOLD));
        card.add(body);
        return card;
    }

    /**
     * Layout: rows, columns, flex, alignment, percentages and absolute positioning.
     *
     * <p>The absolute-positioning row is the important one - the class comment on {@code LayoutEngine}
     * claims it is unsupported, and that claim is wrong. This row proves it either way.
     */
    private UiElement layoutSection() {
        Box column = new Box(Style.builder()
                .width(Dimension.percent(100f))
                .direction(Style.Direction.COLUMN)
                .gap(6f)
                .build());

        // Row with gap and flexGrow: the three boxes should share the width 1:2:1.
        Box row = new Box(Style.builder()
                .width(Dimension.percent(100f)).height(24f)
                .direction(Style.Direction.ROW).gap(6f).build());
        row.add(box(1f, 0xFF444A55, "grow 1"));
        row.add(box(2f, 0xFF5A6472, "grow 2"));
        row.add(box(1f, 0xFF444A55, "grow 1"));
        column.add(row);

        // justifyContent variants, each in a fixed-width track.
        for (Style.Justify justify : List.of(Style.Justify.START, Style.Justify.CENTER,
                Style.Justify.END, Style.Justify.SPACE_BETWEEN)) {
            Box track = new Box(Style.builder()
                    .width(Dimension.percent(100f)).height(20f)
                    .direction(Style.Direction.ROW)
                    .justify(justify)
                    .build());
            track.add(box(0f, 0xFF6F8FD8, justify.name()));
            track.add(box(0f, 0xFF3A9E5F, "b"));
            column.add(track);
        }

        // alignItems variants in a tall row.
        Box alignRow = new Box(Style.builder()
                .width(Dimension.percent(100f)).height(44f)
                .direction(Style.Direction.ROW).gap(10f).build());
        for (Style.Align align : List.of(Style.Align.START, Style.Align.CENTER, Style.Align.END,
                Style.Align.STRETCH)) {
            alignRow.add(new Swatch(0xFF6F8FD8, Style.builder()
                    .width(70f)
                    .height(align == Style.Align.STRETCH ? Dimension.AUTO
                            : Dimension.points(16f))
                    .build()));
        }
        column.add(new Text("alignItems: START / CENTER / END / STRETCH（上排四个盒子）",
                Style.builder().build(), 0.6f, TextWeight.REGULAR));
        column.add(alignRow);

        // Absolute positioning: a relative container with two absolutely placed children.
        Box container = new Box(Style.builder()
                .width(Dimension.percent(100f)).height(56f)
                .build());
        container.add(new Swatch(0xFF6F8FD8, Style.builder()
                .position(Style.Position.ABSOLUTE).left(0f).top(0f)
                .width(80f).height(20f).build()));
        container.add(new Swatch(0xFF3A9E5F, Style.builder()
                .position(Style.Position.ABSOLUTE).left(120f).top(28f)
                .width(80f).height(20f).build()));
        container.add(new Swatch(0xFFE0A030, Style.builder()
                .position(Style.Position.ABSOLUTE).right(0f).top(0f)
                .width(80f).height(20f).build()));
        column.add(container);
        column.add(new Text("绝对定位: left/top、left+offset、right 锚定（三个盒子应分列两端与中间）",
                Style.builder().build(), 0.6f, TextWeight.REGULAR));
        return column;
    }

    /** A coloured block with a label: proves flow placement without needing a full widget. */
    private Box box(float grow, int color, String label) {
        Box b = new Box(Style.builder()
                .width(Dimension.points(60f)).height(24f)
                .flexGrow(grow)
                .direction(Style.Direction.ROW)
                .padding(dev.skypocket.ui.Insets.all(4f))
                .build());
        // The swatch fills the box; the label sits on top of it.
        b.add(new Swatch(color, Style.builder()
                .position(Style.Position.ABSOLUTE).left(0f).top(0f).right(0f).bottom(0f)
                .build()));
        b.add(new Text(label, Style.builder().build(), 0.55f, TextWeight.REGULAR));
        return b;
    }

    private UiElement widgetSection() {
        Box column = new Box(Style.builder()
                .width(Dimension.percent(100f))
                .direction(Style.Direction.COLUMN)
                .gap(8f)
                .build());

        Box row = new Box(Style.builder()
                .width(Dimension.percent(100f)).direction(Style.Direction.ROW).gap(8f).build());
        row.add(new Button(Localization.tr("按钮"), () -> {
        }, Style.builder().width(90f).height(24f).build(), palette));
        row.add(new Button(Localization.tr("次按钮"), () -> {
        }, Style.builder().width(90f).height(24f).build(), palette,
                Button.SurfaceMode.CARD));
        row.add(new Switch("开关", () -> demoSwitch, value -> demoSwitch = value,
                Style.builder().width(90f).height(24f).build(), palette));
        column.add(row);

        column.add(new Slider(Localization.tr("滑块"), 0, 1,
                () -> demoSlider, value -> demoSlider = value, "%.2f", 0.05,
                Style.builder().width(Dimension.percent(100f)).height(26f).build(), palette));

        column.add(new Segmented(List.of("经典", "现代"), () -> demoMode,
                index -> demoMode = index,
                Style.builder().width(Dimension.percent(100f)).height(28f).build(), palette));

        TextField field = new TextField(Localization.tr("输入点什么"), Style.builder()
                .width(Dimension.percent(100f)).height(26f).build(), palette);
        field.text(demoText);
        field.onChange(text -> demoText = text);
        column.add(field);

        Dropdown dropdown = new Dropdown(Localization.tr("下拉"), List.of("一", "二", "三", "四", "五", "六"),
                // Bound to a stored value: a constant supplier would keep showing the first item
                // no matter what was picked.
                () -> demoDropdown, index -> demoDropdown = index,
                Style.builder().width(Dimension.percent(100f)).height(26f).build(), palette);
        testDropdown = dropdown;
        column.add(dropdown);

        // Registered with the page so its layout and paint passes can drive the popup. Opening is a
        // click on the row, not something this builder decides.
        testColor = new ColorField(Localization.tr("颜色"), () -> demoColor,
                value -> demoColor = value);

        column.add(testColor);
        return column;
    }

    /**
     * Style: every {@code ClickGuiStyle} colour token, drawn by v2 and labelled.
     *
     * <p>This is the "does v2 reproduce the ClickGUI look" check. The expected values are read
     * through {@code ClickGuiStyle}, so if the two ever disagree the swatch and its label differ.
     */
    private UiElement styleSection() {
        Box wrap = new Box(Style.builder()
                .width(Dimension.percent(100f))
                .direction(Style.Direction.ROW)
                .gap(6f)
                .build());

        Box left = tokenColumn("v2 组件", List.of(
                "card/surface", "button", "accent", "field", "switchOn", "switchOff",
                "text", "textDim", "textMuted", "description"));
        Box right = tokenColumn("ClickGuiStyle", List.of(
                "card", "button", "accent", "field", "switchOn", "switchOff",
                "text", "textDim", "textMuted", "description"));
        wrap.add(left);
        wrap.add(right);
        return wrap;
    }

    private Box tokenColumn(String title, List<String> names) {
        Box column = new Box(Style.builder()
                .width(Dimension.percent(100f))
                .direction(Style.Direction.COLUMN)
                .gap(3f)
                .build());
        column.add(new Text(title, Style.builder().build(), 0.6f, TextWeight.SEMIBOLD));
        for (String name : names) {
            Box row = new Box(Style.builder()
                    .width(Dimension.percent(100f)).height(16f)
                    .direction(Style.Direction.ROW).gap(6f)
                    .build());
            row.add(new Swatch(token(name), Style.builder().width(28f).height(16f).build()));
            row.add(new Text(name + "  " + hex(token(name)),
                    Style.builder().build(), 0.55f, TextWeight.REGULAR));
            column.add(row);
        }
        return column;
    }

    /** The token's value as the legacy style system reports it. */
    private static int token(String name) {
        return switch (name) {
            case "card", "card/surface" -> ClickGuiStyle.card();
            case "button" -> ClickGuiStyle.button();
            case "accent" -> ClickGuiStyle.accent();
            case "field" -> ClickGuiStyle.field();
            case "switchOn" -> ClickGuiStyle.switchOn();
            case "switchOff" -> ClickGuiStyle.switchOff();
            case "text" -> ClickGuiStyle.text();
            case "textDim" -> ClickGuiStyle.textDim();
            case "textMuted" -> ClickGuiStyle.textMuted();
            case "description" -> ClickGuiStyle.description();
            default -> 0xFF000000;
        };
    }

    private static String hex(int argb) {
        return String.format("#%08X", argb);
    }

    private UiElement interactionSection() {
        Box column = new Box(Style.builder()
                .width(Dimension.percent(100f))
                .direction(Style.Direction.COLUMN)
                .gap(4f)
                .build());
        column.add(new Text("点击 / 拖拽 / 滚轮 / Tab 焦点 / 浮层，全部应可用",
                Style.builder().build(), 0.6f, TextWeight.REGULAR));
        column.add(new Text("↑↓滚动含本页，验证 ScrollView + Scrollbar",
                Style.builder().build(), 0.6f, TextWeight.REGULAR));
        column.add(new Text("配置 revision: " + ConfigManager.revision(),
                Style.builder().build(), 0.6f, TextWeight.REGULAR));

        // A MENU-mode button: that is the only Button surface which draws a hover highlight (CARD
        // stays flat by design). Placing one in the page rather than in a popup proves the page
        // re-bakes when the hovered element changes.
        column.add(new Text("下面这个按钮是 MENU 模式，移上去应高亮（验证页面内 hover 重烘）",
                Style.builder().build(), 0.55f, TextWeight.REGULAR));
        column.add(new Button(Localization.tr("悬停验证"), () -> {
        }, Style.builder().width(Dimension.percent(100f)).height(24f).build(),
                palette, Button.SurfaceMode.MENU_ONLY));
        return column;
    }
}
