package dev.skypocket.gui;

import dev.skypocket.ui.Box;
import dev.skypocket.ui.Dimension;
import dev.skypocket.ui.LayoutEngine;
import dev.skypocket.ui.PaintPass;
import dev.skypocket.ui.Size;
import dev.skypocket.ui.Style;
import dev.skypocket.ui.TextMeasurer;
import dev.skypocket.ui.UiElement;
import dev.skypocket.ui.UiPlatform;
import dev.skypocket.ui.input.InputRouter;
import dev.skypocket.ui.input.KeyEvent;
import dev.skypocket.ui.input.Modifiers;
import dev.skypocket.ui.input.PointerEvent;
import dev.skypocket.ui.input.PreeditEvent;
import dev.skypocket.ui.input.TextInputEvent;
import dev.skypocket.ui.widget.TextField;
import dev.skypocket.ui.widget.WidgetPalette;
import dev.skypocket.render.ClickGuiMetrics;
import dev.skypocket.ui.mc.McUiPainter;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The search box and the back/forward buttons, in the top bar.
 *
 * <p>They used to be children of {@link ModulesPage}, which meant they existed twice - the homepage
 * page and the sidebar-only chrome each built their own - and they scrolled with content they do not
 * belong to. They are identity-of-panel furniture, not page content: they stay put across every page
 * and describe where you are, not what you are looking at.
 *
 * <p>Being in the top bar means they are outside the content area's scissor, so they cannot be part of
 * a page's tree. They get their own tree and their own {@link InputRouter} here, drawn at panel
 * coordinates instead of page-local ones.
 *
 * <p>The tree is built once: the widgets are stable, and only their text and enabled state change.
 * That is deliberate - a page rebuilds its tree on config changes, but these controls hold focus and
 * caret state that a rebuild would discard mid-typing.
 */
final class TopBarControls {
    private static final TextMeasurer MEASURER = (text, maxWidth) ->
            new Size(UiPlatform.textWidth(text), UiPlatform.lineHeight());

    private final ModulesPage.Host host;
    private final WidgetPalette palette;
    private final Box root;
    private final InputRouter input;
    private final TextField search;
    private final NavButton back;
    private final NavButton forward;

    TopBarControls(ModulesPage.Host host, WidgetPalette palette) {
        this.host = host;
        this.palette = palette;
        root = new Box(Style.builder()
                .width(Dimension.points(PanelGeometry.PANEL_W))
                .height(Dimension.points(PanelGeometry.TAB_BAR_H))
                .build());

        back = new NavButton("\u2190", host::canGoBack, host::goBack, palette,
                PanelGeometry.NAV_X, PanelGeometry.SEARCH_Y);
        forward = new NavButton("\u2192", host::canGoForward, host::goForward, palette,
                PanelGeometry.NAV_X + PanelGeometry.NAV_SIZE + PanelGeometry.NAV_GAP,
                PanelGeometry.SEARCH_Y);
        root.add(back);
        root.add(forward);

        search = new TextField(dev.skypocket.i18n.Localization.tr("搜索..."), null,
                Style.builder().position(Style.Position.ABSOLUTE)
                        .left(PanelGeometry.SEARCH_X).top(PanelGeometry.SEARCH_Y)
                        .width(PanelGeometry.SEARCH_W).height(PanelGeometry.SEARCH_H).build(),
                palette);
        search.text(host.search());
        search.onChange(host::setSearch);
        root.add(search);

        input = new InputRouter(root);
    }

    /** Re-reads the model. Called when the screen's state changed in a way these controls show. */
    void sync() {
        if (!search.isFocused() && !search.text().equals(host.search())) {
            search.text(host.search());
        }
    }

    void layout() {
        // The search box is the only thing here whose width could change, and it does not: it is
        // fixed by the sidebar's column. Layout still runs because the controls read their bounds
        // during paint and hit testing.
        root.offset(0f, 0f);
        LayoutEngine.layout(root, PanelGeometry.PANEL_W, PanelGeometry.TAB_BAR_H, MEASURER);
    }

    void draw(GuiGraphicsExtractor graphics) {
        // The bar draws at panel coordinates with no page transform, and the panel scale is already
        // applied by the caller. Rasterisation scale is pinned for the same reason DevDrawer pins it:
        // these glyphs are placed 1:1, so the atlas has to be built at 1:1.
        float previous = ClickGuiMetrics.effectiveScale();
        ClickGuiMetrics.setEffectiveScale(1f);
        try {
            McUiPainter painter = new McUiPainter(graphics);
            PaintPass.paintStatic(painter, root);
        } finally {
            ClickGuiMetrics.setEffectiveScale(previous);
        }
    }

    // ---- input ---------------------------------------------------------------------------

    /**
     * Panel-local point to these controls' space.
     *
     * <p>They live at the panel origin, so nothing has to be subtracted - but the method exists so a
     * future move of the bar does not silently break every hit test.
     */
    private static float localX(double x) {
        return (float) x;
    }

    private static float localY(double y) {
        return (float) y;
    }

    /** Only within the top bar: everything below belongs to the page. */
    private static boolean inBar(double x, double y) {
        return x >= 0 && x < PanelGeometry.PANEL_W && y >= 0 && y < PanelGeometry.TAB_BAR_H;
    }

    boolean pointerDown(double x, double y, int button, int modifiers) {
        if (!inBar(x, y)) {
            return false;
        }
        input.pointerDown(new PointerEvent(localX(x), localY(y), button, modifiers));
        return true;
    }

    boolean pointerMove(double x, double y, int modifiers) {
        if (!inBar(x, y)) {
            return false;
        }
        input.pointerMove(new PointerEvent(localX(x), localY(y), 0, modifiers));
        return true;
    }

    boolean pointerUp(double x, double y, int button, int modifiers) {
        if (!inBar(x, y)) {
            return false;
        }
        input.pointerUp(new PointerEvent(localX(x), localY(y), button, modifiers));
        return true;
    }

    boolean hasFocus() {
        return input.focused() != null;
    }

    /** True while a drag started inside the bar is still in progress. */
    boolean hasPointerCapture() {
        return input.captured() != null;
    }

    void clearFocus() {
        input.focus(null);
    }

    boolean keyDown(KeyEvent event) {
        if (!hasFocus()) {
            return false;
        }
        input.keyDown(event);
        return true;
    }

    boolean keyUp(KeyEvent event) {
        if (!hasFocus()) {
            return false;
        }
        input.keyUp(event);
        return true;
    }

    boolean textInput(String text) {
        if (!hasFocus()) {
            return false;
        }
        input.textInput(new TextInputEvent(text));
        return true;
    }

    boolean preedit(PreeditEvent event) {
        if (!hasFocus()) {
            return false;
        }
        input.preedit(event);
        return true;
    }
}
