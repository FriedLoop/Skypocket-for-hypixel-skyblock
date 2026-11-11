package dev.skypocket.gui;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.i18n.Localization;
import dev.skypocket.render.ClickGuiFonts;
import dev.skypocket.render.GuiPanelCache;
import dev.skypocket.ui.Box;
import dev.skypocket.ui.Dimension;
import dev.skypocket.ui.Insets;
import dev.skypocket.ui.LayoutEngine;
import dev.skypocket.ui.Painted;
import dev.skypocket.ui.Rect;
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
import dev.skypocket.ui.widget.Control;
import dev.skypocket.ui.widget.TextField;
import dev.skypocket.ui.widget.WidgetPalette;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/** The homepage's v2 element tree: sidebar, search, navigation and module cards. */
final class ModulesPage {
    interface Host {
        String search();

        void setSearch(String value);

        Category selected();

        void setSelected(Category category);

        boolean canGoBack();

        boolean canGoForward();

        void goBack();

        void goForward();

        void open(Feature feature);

        void toggle(Feature feature);
    }

    private static final int SIDEBAR_TOP_LOCAL = PanelGeometry.SIDEBAR_TOP - PanelGeometry.TAB_BAR_H;
    private static final int SEARCH_TOP = PanelGeometry.SEARCH_Y - PanelGeometry.TAB_BAR_H;
    private static final int CARDS_TOP = PanelGeometry.CONTENT_TOP_MODULES - PanelGeometry.TAB_BAR_H;
    private static final int HIGHLIGHT_MS = 160;

    private final Host host;
    private final WidgetPalette palette;
    private final float width;
    private final float height;
    private final boolean withCards;
    private final List<ModuleCard> cards = new ArrayList<>();
    private final List<SidebarRow> rows = new ArrayList<>();
    private final List<NavButton> navButtons = new ArrayList<>();
    private final GuiPanelCache.Slot pageCache = new GuiPanelCache.Slot();
    private Box root;
    private Box grid;
    private ScrollView scroll;
    private TextField search;
    private InputRouter input;
    private UiElement lastFocused;
    private long renderRevision;
    private float renderScaleX = 1f;
    private float renderScaleY = 1f;
    private int highlightIndex;
    private float highlightY;
    private float highlightFromY;
    private float highlightToY;
    private long highlightStartMs;
    private boolean highlightInitialized;

    ModulesPage(Host host, WidgetPalette palette, float width, float height) {
        this(host, palette, width, height, true);
    }

    ModulesPage(Host host, WidgetPalette palette, float width, float height, boolean withCards) {
        this.host = host;
        this.palette = palette;
        this.width = width;
        this.height = height;
        this.withCards = withCards;
        rebuild();
    }

    void rebuild() {
        Box page = new Box(Style.builder().width(width).height(height)
                .direction(Style.Direction.COLUMN).build());

        boolean hadHighlight = highlightInitialized;
        float previousHighlight = highlightY;
        Box sidebar = new Box(Style.builder()
                .position(Style.Position.ABSOLUTE)
                .left(0f).top(0f).width(PanelGeometry.SIDEBAR_W).height(height)
                .direction(Style.Direction.COLUMN)
                .padding(new Insets(SIDEBAR_TOP_LOCAL, 0f, 0f, 0f))
                .build());
        rows.clear();
        highlightIndex = 0;
        addSidebarRow(sidebar, null, 0f, false, false, host.selected() == null,
                () -> host.setSelected(null));
        for (Category category : Category.topLevel()) {
            boolean expanded = host.selected() != null && category.contains(host.selected());
            boolean selected = category.isContainer() ? expanded : category == host.selected();
            addSidebarRow(sidebar, category, 0f, category.isContainer(), expanded, selected,
                    () -> host.setSelected(category));
            if (expanded) {
                for (Category child : category.children()) {
                    addSidebarRow(sidebar, child, 14f, false, false, child == host.selected(),
                            () -> host.setSelected(child));
                }
            }
        }
        float targetHighlight = SIDEBAR_TOP_LOCAL + highlightIndex * PanelGeometry.SIDEBAR_ITEM_H;
        if (!hadHighlight) {
            highlightY = targetHighlight;
            highlightFromY = targetHighlight;
            highlightToY = targetHighlight;
            highlightStartMs = System.currentTimeMillis();
        } else {
            highlightFromY = previousHighlight;
            highlightToY = targetHighlight;
            highlightStartMs = System.currentTimeMillis();
        }
        highlightInitialized = true;
        page.add(sidebar);

        search = new TextField(Localization.tr("搜索..."), null,
                Style.builder().position(Style.Position.ABSOLUTE)
                        .left(PanelGeometry.SEARCH_X).top(SEARCH_TOP)
                        .width(PanelGeometry.SEARCH_W).height(PanelGeometry.SEARCH_H).build(),
                palette);
        search.text(host.search());
        search.onChange(value -> {
            host.setSearch(value);
            refreshCards();
        });
        page.add(search);

        navButtons.clear();
        NavButton back = new NavButton("←", host::canGoBack, host::goBack, palette,
                PanelGeometry.NAV_X, SEARCH_TOP);
        NavButton forward = new NavButton("→", host::canGoForward, host::goForward, palette,
                PanelGeometry.NAV_X + PanelGeometry.NAV_SIZE + PanelGeometry.NAV_GAP, SEARCH_TOP);
        navButtons.add(back);
        navButtons.add(forward);
        page.add(back);
        page.add(forward);

        if (withCards) {
            grid = new Box(Style.builder().width(PanelGeometry.contentWidth())
                    .height(Math.max(1f, cardContentHeight())).build());
            scroll = new ScrollView(Style.builder()
                    .position(Style.Position.ABSOLUTE)
                    .left(PanelGeometry.SIDEBAR_W).top(CARDS_TOP)
                    .width(PanelGeometry.contentWidth()).height(height - CARDS_TOP)
                    .build(), grid, 24f, true, palette);
            page.add(scroll);
        } else {
            grid = null;
            scroll = null;
        }
        root = page;
        input = new InputRouter(root);
        refreshCards();
        renderRevision++;
    }

    private void addSidebarRow(Box sidebar, Category category, float indent, boolean container,
                               boolean expanded, boolean selected, Runnable action) {
        SidebarRow row = new SidebarRow(category, indent, container, expanded, selected,
                action, palette);
        if (selected) {
            highlightIndex = rows.size();
        }
        rows.add(row);
        sidebar.add(row);
    }

    private float cardContentHeight() {
        int count = visibleFeatures(host.search(), host.selected()).size();
        int cols = PanelGeometry.columns();
        int rows = (count + cols - 1) / cols;
        return Math.max(1f, rows * (PanelGeometry.CARD_H + PanelGeometry.CARD_GAP));
    }

    /** Rebuilds only the card children, keeping the search field and its focus intact. */
    void refreshCards() {
        if (!withCards || grid == null) {
            return;
        }
        grid.clearChildren();
        cards.clear();
        List<Feature> visible = visibleFeatures(host.search(), host.selected());
        int cols = PanelGeometry.columns();
        for (int i = 0; i < visible.size(); i++) {
            Feature feature = visible.get(i);
            int row = i / cols;
            int col = i % cols;
            int rowCount = Math.min(cols, visible.size() - row * cols);
            float x = PanelGeometry.cardRowLeft(rowCount) - PanelGeometry.SIDEBAR_W
                    + col * (PanelGeometry.CARD_W + PanelGeometry.CARD_GAP);
            float y = row * (PanelGeometry.CARD_H + PanelGeometry.CARD_GAP);
            ModuleCard card = new ModuleCard(feature, () -> host.open(feature),
                    () -> host.toggle(feature), Style.builder()
                    .position(Style.Position.ABSOLUTE)
                    .left(x).top(y)
                    .width(PanelGeometry.CARD_W).height(PanelGeometry.CARD_H).build(), palette);
            cards.add(card);
            grid.add(card);
        }
        grid.style(Style.builder().from(grid.style())
                .height(Math.max(1f, cardContentHeight())).build());
        layout();
        if (scroll != null) {
            scroll.scrollTo(0f);
        }
    }

    private static List<Feature> visibleFeatures(String query, Category selected) {
        return dev.skypocket.gui.PanelGeometry.visibleFeatures(
                new PanelView(false, null, selected, 0, query, ModuleRegistry.features()));
    }

    void layout() {
        TextMeasurer measurer = (text, maxWidth) ->
                new Size(ClickGuiFonts.width(Localization.auto(text)), ClickGuiFonts.lineHeight());
        LayoutEngine.layout(root, width, height, measurer);
        if (scroll != null) {
            scroll.refresh();
        }
    }

    /** Transition drawing: chrome stays fixed while only the card pane slides. */
    void drawTransition(GuiGraphicsExtractor graphics, float cardOffsetX) {
        if (root == null) {
            return;
        }
        updateHighlight();
        McUiPainter painter = new McUiPainter(graphics);
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(0f, PanelGeometry.TAB_BAR_H);
        drawHighlight(painter);
        // Draw the sidebar/search/navigation without the scroll pane.
        boolean scrollVisible = scroll != null && scroll.visible();
        if (scroll != null) {
            scroll.visible(false);
        }
        drawElement(painter, root);
        if (scroll != null) {
            scroll.visible(scrollVisible);
            painter.pushClip(new Rect(PanelGeometry.SIDEBAR_W, CARDS_TOP,
                    PanelGeometry.contentWidth(), height - CARDS_TOP));
            pose.pushMatrix();
            pose.translate(cardOffsetX, 0f);
            drawElement(painter, scroll);
            pose.popMatrix();
            painter.popClip();
        }
        pose.popMatrix();
    }

    void draw(GuiGraphicsExtractor graphics, float scaleX, float scaleY, boolean animating) {
        UiElement focused = input.focused();
        if (focused != lastFocused) {
            lastFocused = focused;
            renderRevision++;
        }
        updateHighlight();
        McUiPainter painter = new McUiPainter(graphics);
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        if (!withCards) {
            // The module page already draws SettingsPage through the same global page cache.
            // Caching this chrome as a second page in one frame makes the two textures compete
            // for the cache target, which showed up as the sidebar/search blitted into the
            // settings pane. Chrome is small enough to draw live.
            drawHighlight(painter);
            drawElement(painter, root);
            pose.popMatrix();
            return;
        }
        boolean cached = pageCache.draw(graphics, pageCacheKey(),
                Math.max(1, Math.round(width)), Math.max(1, Math.round(height)),
                scaleX, scaleY, animating || highlightAnimating(),
                (pageGraphics, pageMouseX, pageMouseY, pageDelta) -> drawBase(pageGraphics),
                0, 0, 0f);
        renderScaleX = cached ? pageCache.lastBlitScaleX() : 1f;
        renderScaleY = cached ? pageCache.lastBlitScaleY() : 1f;
        if (!cached) {
            drawHighlight(painter);
            drawElement(painter, root);
        } else {
            pose.scale(renderScaleX, renderScaleY);
            if (search != null && search.isFocused()) {
                drawElement(painter, search);
            }
        }
        pose.popMatrix();
    }

    private long pageCacheKey() {
        long hash = System.identityHashCode(this) * 1_000_003L;
        hash = hash * 31 + renderRevision;
        hash = hash * 31 + Float.floatToIntBits(scroll == null ? 0f : scroll.scrollY());
        hash = hash * 31 + host.search().hashCode();
        hash = hash * 31 + (host.selected() == null ? 0 : host.selected().hashCode());
        hash = hash * 31 + ConfigManager.revision();
        hash = hash * 31 + (host.canGoBack() ? 1 : 0);
        hash = hash * 31 + (host.canGoForward() ? 1 : 0);
        hash = hash * 31 + Math.round(highlightY);
        hash = hash * 31 + hoverHash();
        hash = hash * 31 + (input.focused() == null ? 0 : input.focused().hashCode());
        return hash;
    }

    private void updateHighlight() {
        if (!highlightInitialized) {
            return;
        }
        long elapsed = System.currentTimeMillis() - highlightStartMs;
        if (elapsed >= HIGHLIGHT_MS) {
            highlightY = highlightToY;
            return;
        }
        float t = Math.max(0f, elapsed / (float) HIGHLIGHT_MS);
        highlightY = highlightFromY + (highlightToY - highlightFromY)
                * Easing.EASE_OUT.apply(t);
    }

    private boolean highlightAnimating() {
        return highlightInitialized && System.currentTimeMillis() - highlightStartMs < HIGHLIGHT_MS;
    }

    private void drawHighlight(McUiPainter painter) {
        if (!highlightInitialized) {
            return;
        }
        painter.fillRounded(new Rect(8f, highlightY, PanelGeometry.SIDEBAR_W - 16f,
                PanelGeometry.SIDEBAR_ITEM_H), 4f,
                dev.skypocket.gui.widget.ClickGuiStyle.sidebarSelected());
    }

    private long hoverHash() {
        long hash = 0L;
        for (ModuleCard card : cards) {
            if (card.isHovered()) {
                hash = hash * 31 + System.identityHashCode(card);
            }
        }
        for (SidebarRow row : rows) {
            if (row.isHovered()) {
                hash = hash * 31 + System.identityHashCode(row);
            }
        }
        for (NavButton button : navButtons) {
            if (button.isHovered()) {
                hash = hash * 31 + System.identityHashCode(button);
            }
            if (button.isPressed()) {
                // Pressed is visual state too; without it the cached page keeps the unpressed
                // surface while the button is held down.
                hash = hash * 31 + 17L * System.identityHashCode(button);
            }
        }
        if (search != null && search.isHovered()) {
            hash = hash * 31 + System.identityHashCode(search);
        }
        return hash;
    }

    private void drawBase(GuiGraphicsExtractor graphics) {
        dev.skypocket.render.ScissorTracker.reset(graphics);
        McUiPainter painter = new McUiPainter(graphics);
        Control.neutralPaint(true);
        try {
            drawHighlight(painter);
            drawElement(painter, root);
        } finally {
            Control.neutralPaint(false);
        }
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
            painter.pushClip(view.bounds());
        }
        if (element instanceof Painted painted) {
            painted.paint(painter);
        } else if (element instanceof Text text) {
            painter.textWeighted(text.value(), text.bounds().x(), text.bounds().y(),
                    text.color(), text.fontScale(), text.weight());
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

    boolean pointerDown(float x, float y, int modifiers) {
        // The search field lives above the scroll layer. Resolve it by its visual box first:
        // if the generic hit-test lands on a decorator instead, the user still gets a focused field.
        if (search != null && search.visible() && insideSearch(x, y)) {
            input.focus(search);
            input.capture(search);
            search.onPointerDown(new PointerEvent(x, y, 0, modifiers));
            return true;
        }
        input.pointerDown(new PointerEvent(x, y, 0, modifiers));
        return true;
    }

    private boolean insideSearch(float x, float y) {
        float sx = search.visualX();
        float sy = search.visualY();
        return x >= sx && y >= sy && x < sx + search.bounds().width()
                && y < sy + search.bounds().height();
    }

    boolean pointerMove(float x, float y, int modifiers) {
        input.pointerMove(PointerEvent.move(x, y, modifiers));
        return true;
    }

    /** Releases hover, focus and pointer capture before the page is hidden or re-shown. */
    void clearHover() {
        if (input != null) {
            // pointerMove is not enough: a card click can navigate away before pointerUp, so the
            // router may still be captured and would ignore the synthetic out-of-bounds move.
            input.clear();
        }
    }

    boolean pointerUp(float x, float y, int modifiers) {
        input.pointerUp(new PointerEvent(x, y, 0, modifiers));
        return true;
    }

    boolean wheel(float x, float y, float delta, int modifiers) {
        input.wheel(new WheelEvent(x, y, delta, modifiers));
        return true;
    }

    boolean keyDown(KeyEvent event) {
        input.keyDown(event);
        return true;
    }

    boolean keyUp(KeyEvent event) {
        input.keyUp(event);
        return true;
    }

    boolean hasFocus() {
        return input.focused() != null;
    }

    /**
     * Sends a character straight to the homepage search field. The homepage has no other
     * text target, so this avoids depending on the generic focus router's current node
     * when the cursor is already visibly inside the search box.
     */
    boolean searchInput(String value) {
        return search != null && search.onTextInput(new TextInputEvent(value));
    }

    boolean hasPointerCapture() {
        return input.captured() != null;
    }

    boolean textInput(String value) {
        input.textInput(new TextInputEvent(value));
        return true;
    }

    boolean preedit(PreeditEvent event) {
        input.preedit(event);
        return true;
    }

    void clearFocus() {
        input.focus(null);
    }

    private static final class NavButton extends Control {
        private final String glyph;
        private final BooleanSupplier enabled;
        private final Runnable action;

        NavButton(String glyph, BooleanSupplier enabled, Runnable action, WidgetPalette palette,
                  float x, float y) {
            super(Style.builder().position(Style.Position.ABSOLUTE)
                    .left(x).top(y)
                    .width(PanelGeometry.NAV_SIZE).height(PanelGeometry.NAV_SIZE).build(),
                    palette);
            this.glyph = glyph;
            this.enabled = enabled;
            this.action = action;
        }

        @Override
        public boolean focusable() {
            return false;
        }

        @Override
        protected void activate() {
            if (enabled.getAsBoolean()) {
                action.run();
            }
        }

        @Override
        public boolean onPointerDown(PointerEvent event) {
            if (!enabled.getAsBoolean()) {
                return true;
            }
            pressed = true;
            return true;
        }

        @Override
        public boolean onPointerUp(PointerEvent event) {
            if (pressed) {
                activate();
            }
            pressed = false;
            return true;
        }

        @Override
        public void paint(dev.skypocket.ui.UiPainter painter) {
            boolean on = enabled.getAsBoolean();
            painter.surface(bounds(), dev.skypocket.gui.widget.ClickGuiStyle.RADIUS,
                    pressed ? palette.pressed()
                            : hovered && on ? palette.hover() : palette.background(), 0);
            painter.text(glyph, bounds().x() + (bounds().width() - painter.textWidth(glyph)) / 2f,
                    bounds().y() + (bounds().height() - painter.lineHeight()) / 2f,
                    on ? palette.text() : palette.muted());
        }
    }
}
