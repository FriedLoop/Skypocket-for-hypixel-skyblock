package dev.skypocket.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.skypocket.config.ConfigManager;
import dev.skypocket.gui.widget.GuiTheme;
import dev.skypocket.gui.widget.ThemeStore;
import dev.skypocket.i18n.Localization;
import dev.skypocket.render.ClickGuiMetrics;
import dev.skypocket.ui.Box;
import dev.skypocket.ui.Dimension;
import dev.skypocket.ui.Insets;
import dev.skypocket.ui.LayoutEngine;
import dev.skypocket.ui.PaintPass;
import dev.skypocket.ui.ScrollView;
import dev.skypocket.ui.Size;
import dev.skypocket.ui.Style;
import dev.skypocket.ui.Text;
import dev.skypocket.ui.TextMeasurer;
import dev.skypocket.ui.TextWeight;
import dev.skypocket.ui.UiElement;
import dev.skypocket.ui.UiPlatform;
import dev.skypocket.ui.input.InputRouter;
import dev.skypocket.ui.input.KeyEvent;
import dev.skypocket.ui.input.Modifiers;
import dev.skypocket.ui.input.PointerEvent;
import dev.skypocket.ui.input.TextInputEvent;
import dev.skypocket.ui.input.WheelEvent;
import dev.skypocket.ui.mc.McUiPainter;
import dev.skypocket.ui.widget.Button;
import dev.skypocket.ui.widget.Card;
import dev.skypocket.ui.widget.ColorPickerPanel;
import dev.skypocket.ui.widget.Slider;
import dev.skypocket.ui.widget.TextField;
import dev.skypocket.ui.widget.WidgetPalette;
import dev.skypocket.util.GuiMetrics;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/**
 * The theme editor, as a drawer beside the real panel.
 *
 * <p>This replaces the old studio screen, which rendered a miniature copy of the panel next to the
 * editor. That copy was a second implementation of the panel and kept drifting from the real one.
 * Here there is nothing to reproduce: edits go straight into the live palette, and the panel beside
 * the drawer is the result.
 *
 * <p>Because edits are live, closing the drawer without saving restores the snapshot taken when it
 * opened. Only "save" writes a theme file, so the old draft behaviour survives as revert-on-exit.
 *
 * <h2>Built on the v2 kernel</h2>
 *
 * <p>The drawer used to hand-compose every row, button and scrollbar with immediate-mode calls. It is
 * now a retained element tree: {@link LayoutEngine} places it, {@link InputRouter} routes input to it,
 * and {@link PaintPass} draws it.
 *
 * <p>It is deliberately not a {@link dev.skypocket.ui.UiPage}. The drawer floats above the panel
 * rather than occupying a page, and it is never cached: theme editing repaints continuously, so a
 * cache would only add invalidation bugs. ADR-0004 applies the same reasoning to {@code ModulesPage}.
 */
final class DevDrawer {
    private static final int WIDTH = 340;
    private static final int PAD = 12;
    private static final int TITLE_H = 28;
    private static final int THEME_ROW = 26;
    /** Height reserved for the editor under the theme row; a colour picker is taller than a slider. */
    private static final int PICKER_H = 156;
    private static final int METRIC_H = 44;
    private static final int LIST_ROW_H = 24;
    private static final int BUTTON_H = 20;

    private static final TextMeasurer MEASURER = (text, maxWidth) ->
            new Size(UiPlatform.textWidth(Localization.auto(text)), UiPlatform.lineHeight());

    // The floating palette, not the page one: a drawer has a single layer, so a button painted with
    // the same colour as the surface under it would be invisible.
    private final WidgetPalette palette = Ui2Theme.floatingPalette();
    private final List<String> tokens = new ArrayList<>();
    private final List<Boolean> isColor = new ArrayList<>();
    private final List<String> themes = new ArrayList<>();

    private boolean open;
    private String selected;
    private String target;
    private int themeIndex;
    private String status = "";
    private long statusUntil;
    private int left = -1;
    private int top = -1;
    private boolean dragging;
    private double grabX;
    private double grabY;
    /** Modal shown over the drawer: naming a new theme, or confirming a delete. */
    private static final int MODAL_NONE = 0;
    private static final int MODAL_NAME = 1;
    private static final int MODAL_DELETE = 2;

    private int modal = MODAL_NONE;
    private String nameDraft = "";
    private GuiTheme snapshotColors;
    private GuiMetrics snapshotMetrics;
    private String snapshotName;

    // The retained tree. Rebuilt when the selection, the theme list or the status line changes.
    private Box root;
    private InputRouter input;
    private int builtForHash;

    DevDrawer() {
        rebuildTokenList();
    }

    // ---- public surface, unchanged so OneClickScreen keeps compiling ----------------------

    boolean isOpen() {
        return open;
    }

    void open() {
        if (!open) {
            snapshotColors = GuiTheme.current();
            snapshotMetrics = GuiMetrics.current();
            snapshotName = ConfigManager.get().clickGuiTheme;
            target = snapshotName;
            // Open with nothing selected: a selected colour token used to reserve the picker's height
            // while drawing the metric editor.
            selected = null;
            themes.clear();
            themes.addAll(ThemeStore.names());
            themeIndex = Math.max(0, themes.indexOf(target));
        }
        open = true;
        invalidate();
    }

    void close() {
        if (!open) {
            return;
        }
        open = false;
        restore();
        hint(Localization.tr("已还原未保存的改动"));
    }

    void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int screenW, int screenH) {
        if (!open) {
            return;
        }
        int x = x(screenW);
        int y = y(screenH);
        int h = height(screenH);

        ensureBuilt();
        LayoutEngine.layout(root, WIDTH, h, MEASURER);

        // The drawer is one design canvas scaled by the same factor as the panel, so its text is the
        // same size and rasterised for the same pixel grid. Two earlier attempts got this wrong in
        // different ways: drawing at 1:1 with the panel's render scale made glyphs oversized (the
        // atlas is built for DESIGN_SIZE × renderScale, so drawing it unscaled stretches it), and
        // setting the render scale to 1 for this draw made them sharp but 1/renderScale too large,
        // because the geometry around them stayed at screen pixels.
        //
        // The drawer is laid out in screen units - its design constants are multiplied by the panel
        // scale before they reach the layout engine - so the pose only translates and never scales.
        //
        // That matters for more than tidiness: ColorPickerPanel bakes its static layer into its own
        // texture, sized from the pose scale it sees. Under a scaled pose it recorded a
        // half-resolution texture while drawing full-size content, and the picker was clipped at the
        // bottom and right until something invalidated its cache. A 1:1 pose keeps that inner cache
        // consistent.
        //
        // Text stays the right size because the rasterisation scale is pinned to the same factor the
        // panel uses, so a fontScale of 1 renders the same number of pixels here as it does there.
        float scale = scale();
        McUiPainter painter = new McUiPainter(graphics);
        org.joml.Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        PaintPass.paintStatic(painter, root);
        pose.popMatrix();
    }

    boolean mouseClicked(double mx, double my, int screenW, int screenH, boolean shift, boolean ctrl) {
        if (!open) {
            return false;
        }
        int x = x(screenW);
        int y = y(screenH);
        int h = height(screenH);
        // Bounds are compared in screen units: mx/my arrive in screen space, and the drawer's
        // on-screen extent is its design size times the pose scale.
        int wScreen = Math.round(widthDesign() * scale());
        int hScreen = heightScreen(screenH);
        if (mx < x || mx >= x + wScreen || my < y || my >= y + hScreen) {
            return false;
        }
        // Dragging the title bar, in screen units. Claimed before the tree sees the press, except
        // over the title buttons, which need their own clicks, and except while a modal is open - the
        // modal is then the only interactive thing.
        float titleScreen = TITLE_H * scale();
        if (modal == MODAL_NONE && my < y + titleScreen && !insideTitleButton(mx - x)) {
            dragging = true;
            grabX = mx - x;
            grabY = my - y;
            return true;
        }
        ensureBuilt();
        LayoutEngine.layout(root, WIDTH, h, MEASURER);
        input.pointerDown(new PointerEvent(designX(mx, x), designY(my, y), 0,
                shift ? Modifiers.SHIFT : 0));
        return true;
    }

    /** True inside the title-bar buttons, which occupy the right end of the bar. */
    private static boolean insideTitleButton(double localScreenX) {
        // Converted to design units first: the button row is laid out in design space, so comparing
        // against a screen distance would make the excluded zone drift with the window scale.
        double localDesignX = localScreenX / scale();
        return localDesignX >= WIDTH - PAD - 140;
    }

    boolean mouseDragged(double mx, double my, int screenW, int screenH) {
        if (!open) {
            return false;
        }
        if (dragging) {
            left = (int) Math.round(mx - grabX);
            top = (int) Math.round(my - grabY);
            return true;
        }
        int x = x(screenW);
        int y = y(screenH);
        if (input != null) {
            input.pointerMove(new PointerEvent(designX(mx, x), designY(my, y), 0, 0));
        }
        return true;
    }

    /**
     * Hover updates while the cursor moves and no button is held.
     *
     * <p>Separate from {@link #mouseDragged}: the host receives these as different events, and the
     * screen used to forward only the drag one. The drawer's router therefore never saw a pointer
     * move, so a row's hover state stayed at whatever it was last set to - the row under the cursor
     * only highlighted after a scroll happened to re-run hover detection.
     */
    void mouseMoved(double mx, double my, int screenW, int screenH) {
        if (!open) {
            return;
        }
        int x = x(screenW);
        int y = y(screenH);
        ensureBuilt();
        LayoutEngine.layout(root, WIDTH, height(screenH), MEASURER);
        input.pointerMove(new PointerEvent(designX(mx, x), designY(my, y), 0, 0));
    }

    void mouseReleased() {
        if (!open) {
            return;
        }
        dragging = false;
        if (input != null) {
            input.pointerUp(new PointerEvent(0f, 0f, 0, 0));
        }
    }

    boolean mouseScrolled(double mx, double my, double amount, int screenW, int screenH) {
        if (!open) {
            return false;
        }
        int x = x(screenW);
        int y = y(screenH);
        ensureBuilt();
        LayoutEngine.layout(root, WIDTH, height(screenH), MEASURER);
        input.wheel(new WheelEvent(designX(mx, x), designY(my, y), (float) amount, 0));
        return true;
    }

    boolean keyPressed(int key, boolean shift, boolean ctrl) {
        if (!open) {
            return false;
        }
        if (key == InputConstants.KEY_ESCAPE) {
            close();
            return true;
        }
        if (input != null) {
            input.keyDown(new KeyEvent(key, 0, shift ? Modifiers.SHIFT : 0, true));
        }
        return true;
    }

    boolean charTyped(char c) {
        if (!open) {
            return false;
        }
        if (input != null) {
            input.textInput(new TextInputEvent(String.valueOf(c)));
        }
        return true;
    }

    // ---- tree construction --------------------------------------------------------------

    private void rebuildTokenList() {
        tokens.clear();
        isColor.clear();
        for (String name : ThemeStore.colorNames()) {
            tokens.add(name);
            isColor.add(true);
        }
        for (String name : ThemeStore.metricNames()) {
            tokens.add(name);
            isColor.add(false);
        }
    }

    /** Forces a rebuild on the next frame. */
    private void invalidate() {
        builtForHash = 0;
    }

    private void ensureBuilt() {
        // The status line is part of the tree and expires on a clock, so its presence has to be part
        // of the rebuild key - otherwise a message would stay until something else changed.
        boolean statusLive = !status.isEmpty() && System.currentTimeMillis() <= statusUntil;
        int hash = (selected == null ? 0 : selected.hashCode()) * 31
                + themeIndex * 17
                + (target == null ? 0 : target.hashCode())
                + (statusLive ? 1 : 0);
        if (root != null && builtForHash == hash) {
            return;
        }
        builtForHash = hash;
        build();
    }

    private void build() {
        Box page = new Box(Style.builder()
                .width(Dimension.points(WIDTH))
                .direction(Style.Direction.COLUMN)
                .build());
        // A Box paints nothing, so the surface has to be a Card.
        Card shell = new Card(Style.builder()
                .width(Dimension.points(WIDTH))
                .height(Dimension.percent(100f))
                .direction(Style.Direction.COLUMN)
                .build(), palette);
        page.add(shell);

        shell.add(titleBar());
        shell.add(themeRow());

        int editorH = selected != null && isColorToken(selected) ? PICKER_H : METRIC_H;
        // flexShrink(0) on both boxes. A column shrinks its children when they overflow by default,
        // and the token list below grows into whatever is left - without this the fixed-height editor
        // was compressed from 156 to 118 and the picker was cut off mid-row. The editor's height is a
        // design constraint, not a preference.
        Box editor = new Box(Style.builder()
                .width(Dimension.percent(100f))
                .height(Dimension.points(editorH))
                .flexShrink(0f)
                .padding(new Insets(PAD, PAD, 0f, PAD))
                .direction(Style.Direction.COLUMN)
                .build());
        if (selected != null) {
            Box slot = new Box(Style.builder()
                    .width(Dimension.percent(100f))
                    .height(Dimension.points(isColorToken(selected)
                            ? ColorPickerPanel.CONTENT_H : METRIC_H))
                    .flexShrink(0f)
                    .direction(Style.Direction.COLUMN)
                    .build());
            slot.add(isColorToken(selected) ? colorEditor(selected) : metricEditor(selected));
            editor.add(slot);
        }
        shell.add(editor);

        shell.add(tokenList());

        if (!status.isEmpty() && System.currentTimeMillis() <= statusUntil) {
            // Absolute so it does not disturb the list, pinned just above the bottom padding.
            shell.add(new Text(status,
                    Style.builder()
                            .position(Style.Position.ABSOLUTE)
                            .left(PAD).bottom(PAD)
                            .build(),
                    0.65f, TextWeight.REGULAR));
        }

        if (modal != MODAL_NONE) {
            page.add(modalLayer());
        }

        root = page;
        input = new InputRouter(root);
        // The name field is the reason this modal exists; focusing it means the user can type
        // immediately rather than having to click the field first.
        if (modal == MODAL_NAME) {
            TextField name = findNameField(root);
            if (name != null) input.focus(name);
        }
    }

    /**
     * The modal over the drawer: a scrim, a centred card, a title, and either a name field or a pair
     * of buttons.
     *
     * <p>Everything is absolutely positioned against the drawer, and the scrim is the first child so
     * it sits under the card. While a modal is open it covers the drawer, so clicks that miss the
     * card land on the scrim - which accepts no pointer and therefore does nothing, matching the
     * original behaviour where the confirmation was the only clickable thing.
     */
    private UiElement modalLayer() {
        Box layer = new Box(Style.builder()
                .position(Style.Position.ABSOLUTE)
                .left(0f).top(0f).right(0f).bottom(0f)
                .build());
        // Scrim. A Card rather than a Box because a Box paints nothing.
        layer.add(new Card(Style.builder()
                .position(Style.Position.ABSOLUTE)
                .left(0f).top(0f).right(0f).bottom(0f)
                .build(), palette));

        float cardW = WIDTH - 48f;
        float cardH = modal == MODAL_NAME ? 108f : 88f;
        // Centred horizontally by left + width; vertically by top and bottom both set to a
        // fraction of the layer, plus a fixed height. Absolute positioning resolves the pair
        // against the content box, so equal insets centre the card without knowing its height.
        Box card = new Box(Style.builder()
                .position(Style.Position.ABSOLUTE)
                .left((WIDTH - cardW) / 2f)
                .top(Dimension.percent(50f))
                .width(Dimension.points(cardW))
                .height(Dimension.points(cardH))
                .direction(Style.Direction.COLUMN)
                .padding(Insets.all(12f))
                .gap(8f)
                .build());
        card.add(new Text(modal == MODAL_NAME
                ? Localization.tr("另存为主题")
                : Localization.tr("删除主题 ") + target,
                Style.builder().width(Dimension.percent(100f)).build(),
                0.75f, TextWeight.SEMIBOLD));

        if (modal == MODAL_NAME) {
            TextField field = new TextField(Localization.tr("名称"),
                    Style.builder().width(Dimension.percent(100f))
                            .height(Dimension.points(24f)).build(), palette);
            field.text(nameDraft);
            field.onChange(text -> nameDraft = text);
            card.add(field);
        }

        Box buttons = new Box(Style.builder()
                .width(Dimension.percent(100f))
                .height(Dimension.points(BUTTON_H))
                .direction(Style.Direction.ROW)
                .justify(Style.Justify.END)
                .gap(8f)
                .build());
        buttons.add(menuButton(Localization.tr("取消"), 56f, this::cancelModal));
        buttons.add(menuButton(modal == MODAL_NAME ? Localization.tr("确认") : Localization.tr("删除"),
                56f,
                modal == MODAL_NAME ? this::confirmCreate : this::confirmDelete));
        card.add(buttons);
        layer.add(card);
        return layer;
    }

    /** First TextField in the subtree, used to focus the modal's name input. */
    private static TextField findNameField(UiElement element) {
        if (element instanceof TextField field) {
            return field;
        }
        for (UiElement child : element.children()) {
            TextField found = findNameField(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private UiElement titleBar() {
        Box bar = new Box(Style.builder()
                .width(Dimension.percent(100f))
                .height(Dimension.points(TITLE_H))
                .flexShrink(0f)
                .direction(Style.Direction.ROW)
                .alignItems(Style.Align.CENTER)
                .padding(new Insets(0f, PAD, 0f, PAD))
                .gap(6f)
                .build());
        bar.add(new Text(Localization.tr("主题") + " · " + target,
                Style.builder().width(Dimension.points(120f)).build(),
                0.75f, TextWeight.SEMIBOLD));
        bar.add(new Box(Style.builder().width(Dimension.points(0f)).flexGrow(1f).build()));
        bar.add(menuButton(Localization.tr("保存"), 52f, this::save));
        bar.add(menuButton(Localization.tr("还原"), 52f, this::restore));
        bar.add(menuButton("×", 24f, this::close));
        return bar;
    }

    private UiElement themeRow() {
        Box row = new Box(Style.builder()
                .width(Dimension.percent(100f))
                .height(Dimension.points(THEME_ROW))
                .flexShrink(0f)
                .direction(Style.Direction.ROW)
                .alignItems(Style.Align.CENTER)
                .padding(new Insets(0f, PAD, 0f, PAD))
                .gap(6f)
                .build());
        row.add(menuButton("‹", 22f, () -> cycleTheme(-1)));
        row.add(new Text(target,
                Style.builder().width(Dimension.points(150f)).build(),
                0.75f, TextWeight.MEDIUM));
        row.add(menuButton("›", 22f, () -> cycleTheme(1)));
        row.add(menuButton("+", 22f, this::createTheme));
        row.add(menuButton("−", 22f, this::deleteTheme));
        return row;
    }

    private Button menuButton(String label, float width, Runnable action) {
        return new Button(label, action,
                Style.builder().width(Dimension.points(width))
                        .height(Dimension.points(BUTTON_H)).build(),
                palette, Button.SurfaceMode.MENU_ONLY);
    }

    private UiElement colorEditor(String token) {
        // The complete editor - square, bars, presets, hex and RGBA fields - comes from one factory.
        // Assembling it here is what produced a picker with no inputs on an earlier attempt: the
        // panel is only the visual half, and the numeric fields are separate TextFields.
        // cacheStaticLayer=false: this drawer draws under pose.scale(effectiveScale), and the
        // picker's internal cache sizes its texture from that pose - at half scale it baked a
        // half-resolution copy and clipped the bottom and right of the panel until something
        // invalidated the cache. Drawing the static layer live costs a few hundred fills per frame
        // only while a colour token is selected.
        return ColorPickerPanel.editor(
                () -> ThemeStore.color(token),
                value -> {
                    ThemeStore.setColor(token, value);
                    ConfigManager.touch();
                },
                palette, false);
    }

    private UiElement metricEditor(String token) {
        // Metric values are doubles all the way through ThemeStore, so they pass straight through.
        return new Slider(token, 0, 100, () -> ThemeStore.metric(token),
                value -> {
                    ThemeStore.setMetric(token, value);
                    ConfigManager.touch();
                }, "%.0f", 1,
                Style.builder().width(Dimension.percent(100f))
                        .height(Dimension.points(28f)).build(),
                palette);
    }

    private UiElement tokenList() {
        Box content = new Box(Style.builder()
                .width(Dimension.percent(100f))
                .direction(Style.Direction.COLUMN)
                .build());
        for (String token : tokens) {
            // TokenRow rather than Button: a button has no selected state - only hover and press -
            // and no room for the swatch and hex code that make a colour token recognisable.
            content.add(new TokenRow(token, isColorToken(token), token.equals(selected),
                    Dimension.percent(100f), Dimension.points(LIST_ROW_H),
                    () -> select(token), palette));
        }
        return new ScrollView(Style.builder()
                .width(Dimension.percent(100f))
                .flexGrow(1f)
                .padding(new Insets(0f, PAD, PAD, PAD))
                .build(), content, 24f);
    }

    // ---- behaviour ----------------------------------------------------------------------

    private void select(String token) {
        selected = token;
        invalidate();
    }

    private boolean isColorToken(String token) {
        int index = tokens.indexOf(token);
        return index >= 0 && isColor.get(index);
    }

    private void cycleTheme(int direction) {
        if (themes.isEmpty()) {
            return;
        }
        themeIndex = Math.floorMod(themeIndex + direction, themes.size());
        switchTo(themes.get(themeIndex));
    }

    private void switchTo(String name) {
        // Loading from disk discards unsaved edits, which is the point of switching.
        ThemeStore.apply(name);
        target = name;
        ConfigManager.get().clickGuiTheme = name;
        ConfigManager.touch();
        hint(Localization.tr("已切换到 ") + name);
    }

    private void createTheme() {
        // Naming happens in a modal: the drawer has no inline input row, and asking for a name is a
        // decision that should be visible rather than a keystroke that silently renames a copy.
        nameDraft = suggestThemeName();
        modal = MODAL_NAME;
        invalidate();
    }

    /** A free name based on the current one, so confirming without editing still does something. */
    private String suggestThemeName() {
        String base = target == null ? "Theme" : target;
        String name = base + "-copy";
        int suffix = 2;
        while (themes.contains(name)) {
            name = base + "-copy" + suffix++;
        }
        return name;
    }

    private void confirmCreate() {
        String name = nameDraft == null ? "" : nameDraft.trim();
        modal = MODAL_NONE;
        if (name.isEmpty()) {
            hint(Localization.tr("名称不能为空"));
            return;
        }
        if (ThemeStore.save(name, GuiTheme.current(), GuiMetrics.current())) {
            themes.clear();
            themes.addAll(ThemeStore.names());
            target = name;
            themeIndex = Math.max(0, themes.indexOf(name));
            // Saving makes this the new baseline, so closing must not undo it.
            snapshotColors = GuiTheme.current();
            snapshotMetrics = GuiMetrics.current();
            snapshotName = name;
            hint(Localization.tr("已另存为 ") + name);
        } else {
            hint(Localization.tr("另存失败，见日志"));
        }
    }

    private void deleteTheme() {
        if (!deletable()) {
            hint(Localization.tr("内置主题不可删除"));
            return;
        }
        modal = MODAL_DELETE;
        invalidate();
    }

    private void confirmDelete() {
        modal = MODAL_NONE;
        if (ThemeStore.delete(target)) {
            themes.clear();
            themes.addAll(ThemeStore.names());
            target = themes.isEmpty() ? "Dark" : themes.get(0);
            themeIndex = 0;
            switchTo(target);
            hint(Localization.tr("已删除"));
        } else {
            hint(Localization.tr("删除失败，见日志"));
        }
    }

    private void cancelModal() {
        modal = MODAL_NONE;
        invalidate();
    }

    /** Built-in themes are the fallback when everything else is gone, so they stay. */
    private boolean deletable() {
        return target != null
                && !target.equalsIgnoreCase("Dark") && !target.equalsIgnoreCase("Light");
    }

    private void restore() {
        if (snapshotColors != null && snapshotMetrics != null) {
            ThemeStore.apply(snapshotColors, snapshotMetrics);
            ConfigManager.get().clickGuiTheme = snapshotName;
            ConfigManager.touch();
        }
        invalidate();
    }

    private void save() {
        if (ThemeStore.save(target, GuiTheme.current(), GuiMetrics.current())) {
            snapshotColors = GuiTheme.current();
            snapshotMetrics = GuiMetrics.current();
            snapshotName = target;
            ConfigManager.get().clickGuiTheme = target;
            hint(Localization.tr("已保存到 ") + target + ".json");
        } else {
            hint(Localization.tr("保存失败，见日志"));
        }
    }

    private void hint(String text) {
        status = text;
        statusUntil = System.currentTimeMillis() + 3000;
        // The message lives in the tree; rebuilding is what puts it on screen.
        invalidate();
    }

    // ---- coordinate conversion ----------------------------------------------------------

    /** Pose scale the drawer is drawn at; input divides by the same factor. */
    private static float scale() {
        return Math.max(0.05f, ClickGuiMetrics.effectiveScale());
    }

    /** Screen point -> drawer design coordinates. */
    private static float designX(double screenX, int drawerX) {
        return (float) ((screenX - drawerX) / scale());
    }

    private static float designY(double screenY, int drawerY) {
        return (float) ((screenY - drawerY) / scale());
    }

    // ---- geometry -----------------------------------------------------------------------
    //
    // Two unit systems meet here. The tree is laid out in design units (WIDTH, the editor sizes),
    // and drawn through pose.scale(scale), so its on-screen size is design × scale. Positions and
    // the drag rectangle are in *screen* units, because that is what the host passes in and what
    // the pose origin needs. height() returns design units for the layout, heightOnScreen() the
    // matching screen extent.

    /** Drawer width in design units. */
    private static float widthDesign() {
        return WIDTH;
    }

    /** Drawer height in design units, for layout. */
    static int height(int screenH) {
        return (int) Math.max(120f, Math.min((screenH - 24) / scale(), 460f));
    }

    /** Drawer height in screen units, for positioning and hit bounds. */
    private static int heightScreen(int screenH) {
        return Math.round(height(screenH) * scale());
    }

    int x(int screenW) {
        int docked = Math.round(screenW - widthDesign() * scale() - 12);
        int value = left >= 0 ? left : docked;
        // Clamped on read as well as on write: a window resize can leave a dragged position outside.
        return Math.max(12, Math.min(Math.max(12, docked), value));
    }

    int y(int screenH) {
        int hScreen = heightScreen(screenH);
        int docked = Math.max(12, (screenH - hScreen) / 2);
        int value = top >= 0 ? top : docked;
        return Math.max(12, Math.min(Math.max(12, screenH - hScreen - 12), value));
    }
}
