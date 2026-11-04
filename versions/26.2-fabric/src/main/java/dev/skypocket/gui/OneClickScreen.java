package dev.skypocket.gui;

import dev.skypocket.SkyPocketMod;
import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;
import dev.skypocket.gui.setting.Setting;
import dev.skypocket.gui.widget.ToggleAnimation;
import dev.skypocket.gui.widget.ActionButton;
import dev.skypocket.gui.widget.DropdownListWidget;
import dev.skypocket.gui.widget.TextFieldWidget;
import dev.skypocket.i18n.Localization;
import dev.skypocket.render.ClickGuiBlur;
import dev.skypocket.render.ClickGuiFonts;
import dev.skypocket.render.FontLab;
import dev.skypocket.render.GuiPanelCache;
import dev.skypocket.ui.input.Keys;
import dev.skypocket.ui.input.Modifiers;
import dev.skypocket.ui.input.PreeditEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Set;

import static dev.skypocket.gui.PanelGeometry.*;

import static dev.skypocket.render.GuiPixels.popDeviceSpace;
import static dev.skypocket.render.GuiPixels.pushDeviceSpace;
import static dev.skypocket.render.GuiPixels.scissorFloor;
import static dev.skypocket.render.GuiPixels.scissorRow;

/**
 * The Click GUI, styled after modern Lunar/Badlion. Everything is laid out in a fixed
 * "panel-local" coordinate space ({@link #PANEL_W} x {@link #PANEL_H}) and then scaled
 * by {@code clickGuiScale} at render time, so the panel's real size is independent of
 * the game's GUI Scale.
 *
 * <p>The top bar is a dark main-tab strip: the title on the left, and two tabs
 * ("Modules" / "Settings") on the right. "Settings" hosts the panel settings as a
 * standalone entry (not a feature module). The Modules tab shows a two-level
 * navigation sidebar on the left and a centered card grid on the right.
 */
public class OneClickScreen extends Screen {

    // All panel geometry (sizes, positions, hit tests) lives in PanelGeometry; the constants below
    // are imported statically so the drawing code keeps reading as plain names.

    /** Scale factor from panel-local to screen (gui-scaled) coordinates, per axis. */
    private float scaleX = 1.0f;
    private float scaleY = 1.0f;
    /** Panel top-left in screen (gui-scaled) coordinates. */
    private float offX;
    private float offY;

    /** Open/close animation: the panel shrinks toward this factor from full size. */
    private static final float ANIM_MIN_SCALE = 0.8f;
    /** True while the close animation plays; the screen closes when it finishes. */
    private boolean closing;
    private long animStartMs;

    /** Sidebar highlight bar: its y slides between rows when the selection changes. */
    private static final int SIDEBAR_HIGHLIGHT_MS = 160;

    private float sidebarHighlightY = Float.NaN;
    private float sidebarFromY;
    private float sidebarToY;
    private long sidebarAnimStartMs;

    private int scrollY;
    private boolean settingsTab;
    private String search = "";
    private Category selected = Category.RENDER;
    private Feature viewingFeature;
    private final List<Feature> features;
    private final List<Setting> panelSettings;
    private SettingsPage statsPage;
    private Feature statsPageFeature;
    private float statsPageHeight = -1f;

    public OneClickScreen() {
        super(Component.literal("SkyPocket"));
        ModuleRegistry.init();
        this.features = ModuleRegistry.features();
        this.panelSettings = buildPanelSettings();
    }

    @Override
    protected void init() {
        ClickGuiBlur.apply();
        layout();
        rebuildSettingsWidgets();
        minecraft.onTextInputFocusChange(this, true);
        // Start the open animation once (init also runs on window resize).
        if (animStartMs == 0) {
            animStartMs = System.currentTimeMillis();
        }
        closing = false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Recomputes the render scale and the panel's on-screen position. */
    private void layout() {
        ModConfig cfg = ConfigManager.get();
        float guiScale = minecraft.getWindow().getGuiScale();
        float target = cfg.clickGuiScale * ClickGuiFonts.SCALE_BASE / guiScale;
        // Snap the panel's on-screen size to whole device pixels. GuiPanelCache bakes the
        // panel into an integer-sized texture and blits it 1:1, so a fractional device size
        // makes the texture's pixel grid drift from the screen's: the cached panel shifts by
        // up to a pixel towards its far edges and every component sits a fraction of a pixel
        // off, while a live frame does not. The snap moves the scale by well under 0.1%.
        scaleX = Math.round(PANEL_W * target * guiScale) / (float) (PANEL_W * guiScale);
        scaleY = Math.round(PANEL_H * target * guiScale) / (float) (PANEL_H * guiScale);
        ClickGuiFonts.setRenderScale(scaleX);
        // Snap the panel's origin to whole *gui-scaled units*. It has to be a whole
        // gui-scaled unit - not merely a whole device pixel (1/guiScale), and certainly
        // not a 1/(scale * guiScale) grid - for two independent reasons:
        //  1. A whole gui-scaled unit makes `offX * guiScale` a whole number of device
        //     pixels as well, so elements that are not themselves device-snapped (plain
        //     fills: search box, icon placeholder, sidebar selection, settings buttons)
        //     round identically in a live frame and inside the cached texture. Otherwise
        //     they sit 1px right/down while the cache is warm.
        //  2. The scissor cannot be placed on the device grid at all: `ScreenRectangle`
        //     stores whole gui-scaled units and `enableScissor` rounds there
        //     (ScreenRectangle.transformAxisAligned(pose)). A live frame adds the origin
        //     *before* that rounding, the cached frame rounds the panel-local value and the
        //     blit adds the origin *after*: with a fractional origin the two orders differ
        //     by up to guiScale device pixels. That is the "content bottom mask jumps up
        //     1px as soon as the mouse moves" bug; a whole-unit origin makes both orders
        //     agree exactly.
        offX = Math.round((width - PANEL_W * scaleX) / 2f);
        offY = Math.round((height - PANEL_H * scaleY) / 2f);
        clampScroll();
    }

    /** Snapshot of the state the panel geometry reads (see {@link PanelGeometry}). */
    private PanelView view() {
        return new PanelView(settingsTab, viewingFeature, selected, scrollY, search, features);
    }

    /** Modules already using the v2 settings page tree. */
    private static final Set<String> V2_PAGE_IDS =
            Set.of("stats_display", "clock", "day_count", "fps_tps_ping");

    private boolean statsPageActive() {
        return !settingsTab && viewingFeature != null
                && V2_PAGE_IDS.contains(viewingFeature.id());
    }

    private float statsPageTop() {
        return contentTop() + 18f;
    }

    private float statsPageWidth() {
        // Use the full content pane: the page's right padding reserves the scrollbar strip, while
        // the cards keep the same visual width instead of leaving dead space at the right edge.
        return contentWidth();
    }

    private float statsPageLeft() {
        return SIDEBAR_W;
    }

    /** Page-cache blit origin snapped to a whole GUI-scaled unit. */
    private float statsPageDrawLeft() {
        return (Math.round(offX + statsPageLeft() * scaleX) - offX) / scaleX;
    }

    private float statsPageDrawTop() {
        return (Math.round(offY + statsPageTop() * scaleY) - offY) / scaleY;
    }

    private void ensureStatsPage() {
        if (!statsPageActive()) {
            // Keep the page through the 180 ms slide-out so drawPane can animate the outgoing side.
            return;
        }
        float height = Math.max(20f, contentBottom() - statsPageTop());
        if (statsPage == null || statsPageFeature != viewingFeature || statsPageHeight != height) {
            statsPage = new SettingsPage(viewingFeature, Ui2Theme.palette(),
                    statsPageWidth(), height);
            statsPageFeature = viewingFeature;
            statsPageHeight = height;
        }
    }

    private float statsLocalX(double lx) {
        float scale = statsPage == null ? 1f : statsPage.inputScaleX();
        return ((float) lx - statsPageDrawLeft()) / scale;
    }

    private float statsLocalY(double ly) {
        float scale = statsPage == null ? 1f : statsPage.inputScaleY();
        return ((float) ly - statsPageDrawTop()) / scale;
    }

    private int canonicalKey(KeyEvent event) {
        if (event.isCycleFocus()) {
            return Keys.TAB;
        }
        if (event.isEscape()) {
            return Keys.ESCAPE;
        }
        if (event.isConfirmation()) {
            return Keys.ENTER;
        }
        if (event.isLeft()) {
            return Keys.LEFT;
        }
        if (event.isRight()) {
            return Keys.RIGHT;
        }
        if (event.input() == 32 || event.input() == 44) {
            return 32;
        }
        return switch (event.input()) {
            case 42 -> Keys.BACKSPACE;
            case 76 -> Keys.DELETE;
            case 74 -> Keys.HOME;
            case 77 -> Keys.END;
            default -> event.input();
        };
    }

    private int pageModifiers(KeyEvent event) {
        int bits = 0;
        if (event.hasShiftDown()) {
            bits |= Modifiers.SHIFT;
        }
        if (event.hasControlDown()) {
            bits |= Modifiers.CONTROL;
        }
        if (event.hasAltDown()) {
            bits |= Modifiers.ALT;
        }
        return bits;
    }

    /**
     * Left edge of a settings row.
     *
     * <p>The settings tab owns the whole width, but a module's settings sit in the card area: the sidebar
     * keeps its place beside them, which is why the rows are centred inside that pane instead of the panel.
     */
    private int contentLeft() {
        if (settingsTab) {
            return (PANEL_W - Setting.WIDGET_WIDTH) / 2;
        }
        int paneW = PANEL_W - PanelGeometry.SIDEBAR_W;
        return PanelGeometry.SIDEBAR_W + (paneW - Setting.WIDGET_WIDTH) / 2;
    }

    private int contentTop() {
        PanelView view = view();
        if (!view.settingsTab() && view.viewingFeature() != null) {
            // Same top as the card grid, so the module's settings start where its card was.
            return PanelGeometry.CONTENT_TOP_MODULES;
        }
        return PanelGeometry.contentTop(view);
    }

    private void clampScroll() {
        scrollY = Math.min(scrollY, PanelGeometry.maxScroll(view(), panelSettingsHeight()));
        if (scrollY < 0) {
            scrollY = 0;
        }
    }

    private int sidebarTargetY() {
        return PanelGeometry.sidebarTargetY(view());
    }

    /** Extra scale factor from the open/close animation, eased (1 = fully open). */
    private float animScale() {
        int duration = ConfigManager.get().clickGuiAnimDuration;
        if (duration <= 0) {
            return 1f;
        }
        float t = (System.currentTimeMillis() - animStartMs) / (float) duration;
        t = Math.max(0f, Math.min(1f, t));
        float progress = closing ? 1f - t : t; // 0 = shrunk, 1 = full size
        float eased = Easing.byId(ConfigManager.get().clickGuiEasing).apply(progress);
        return ANIM_MIN_SCALE + (1f - ANIM_MIN_SCALE) * eased;
    }

    private boolean animFinished() {
        int duration = ConfigManager.get().clickGuiAnimDuration;
        return duration <= 0 || System.currentTimeMillis() - animStartMs >= duration;
    }

    /** Screen (gui-scaled) X -> panel-local X, inverting the animation + panel scale. */
    private double localX(double screenX) {
        float a = animScale();
        double cx = width / 2.0;
        return ((screenX - cx) / a + cx - offX) / scaleX;
    }

    /** Screen (gui-scaled) Y -> panel-local Y, inverting the animation + panel scale. */
    private double localY(double screenY) {
        float a = animScale();
        double cy = height / 2.0;
        return ((screenY - cy) / a + cy - offY) / scaleY;
    }

    // ---- content area / grid ---------------------------------------------------------------
    // The queries below delegate to PanelGeometry, which owns the design constants and the pure
    // "what is where / what is under the cursor" logic (see PanelView). Keeping a one-line façade
    // here means the drawing and the event code keep reading naturally while nothing re-derives a
    // position of its own.




    private int panelSettingsHeight() {
        int h = 0;
        for (Setting s : panelSettings) {
            h += s.height();
        }
        return h;
    }

    private List<Setting> buildPanelSettings() {
        ModConfig cfg = ConfigManager.get();
        return List.of(
                new Setting.BooleanSetting("毛玻璃效果", () -> cfg.clickGuiBlur, v -> {
                    cfg.clickGuiBlur = v;
                    ClickGuiBlur.apply();
                }),
                new Setting.BooleanSetting("文字阴影", () -> cfg.clickGuiTextShadow, v -> cfg.clickGuiTextShadow = v),

                new Setting.BooleanSetting("面板渲染缓存", () -> cfg.clickGuiPanelCache, v -> {
                    cfg.clickGuiPanelCache = v;
                    if (!v) {
                        GuiPanelCache.release();
                    }
                }),
                new Setting.SliderSetting("毛玻璃强度", 0, 100, () -> cfg.clickGuiBlurIntensity, v -> {
                    cfg.clickGuiBlurIntensity = (int) Math.round(v);
                    ClickGuiBlur.apply();
                }, "%.0f%%", 1),
                new Setting.SliderSetting("背景透明度", 0, 255, () -> cfg.clickGuiBackgroundAlpha,
                        v -> cfg.clickGuiBackgroundAlpha = (int) Math.round(v), "%.0f", 1),
                new Setting.SliderSetting("ClickGUI 缩放", ClickGuiFonts.SCALE_MIN, ClickGuiFonts.SCALE_MAX,
                        () -> cfg.clickGuiScale, v -> cfg.clickGuiScale = (float) v, "%.1f", ClickGuiFonts.SCALE_STEP),
                new Setting.BooleanSetting("SkyBlock 模块仅在 SkyBlock 生效", () -> cfg.skyBlockModulesOnly,
                        v -> cfg.skyBlockModulesOnly = v),
                new Setting.ActionSetting(
                        () -> Localization.tr("语言") + ": " + Localization.displayName(),
                        () -> {
                            Localization.cycleLanguage();
                            ConfigManager.save();
                            Minecraft mc = Minecraft.getInstance();
                            mc.schedule(() -> {
                                if (mc.gui.screen() instanceof OneClickScreen s) {
                                    s.rebuildAfterLanguageChange();
                                }
                            });
                        }),
                new Setting.SliderSetting("动画时长", 0, 600, () -> cfg.clickGuiAnimDuration,
                        v -> cfg.clickGuiAnimDuration = (int) Math.round(v), "%.0fms", 10),
                new Setting.DropdownSetting("缓动曲线", Easing::ids,
                        () -> cfg.clickGuiEasing, v -> cfg.clickGuiEasing = v),
                // Palette. The setter applies it immediately; Setting already wraps it with touch(), so
                // the panel cache drops its baked texture and the very next frame is drawn with the new
                // colours (see GuiTheme).
                new Setting.DropdownSetting("主题", dev.skypocket.gui.widget.ThemeStore::names,
                        () -> cfg.clickGuiTheme, v -> {
                            cfg.clickGuiTheme = v;
                            dev.skypocket.gui.widget.ThemeStore.apply(v);
                        }),
                // Everything diagnostic lives here, folded away: these rows only write to the log and
                // are of no use to a player. Giving the next debug switch an obvious home beats
                // sprinkling them through the panel's settings.
                new Setting.GroupSetting("调试", List.of(
                        // Live token editor: gallery on the left, tokens in the middle, editor on the right.
                        new Setting.ActionSetting("主题工作室", this::toggleDevDrawer),
                        // Master switch for the mod's information logs; warnings and errors always print.
                        new Setting.BooleanSetting("调试日志", () -> cfg.debugLog, v -> {
                            cfg.debugLog = v;
                            dev.skypocket.SkyPocketLog.apply(v);
                        }),
                        // Five-second line: frames, blits, bakes, and which gate a frame stopped at.
                        new Setting.BooleanSetting("面板缓存统计", () -> cfg.panelCacheStats,
                                v -> cfg.panelCacheStats = v),
                        // UI v2 font comparison. These knobs are intentionally not persisted config:
                        // they are temporary experiment controls and disappear when a policy is chosen.
                        new Setting.Header("字体实验"),
                        new Setting.BooleanSetting("实验：整数 advance", FontLab::integerAdvance,
                                FontLab::setIntegerAdvance),
                        new Setting.DropdownSetting("实验：原点定位", FontLab::originIds,
                                FontLab::originId, FontLab::setOrigin),
                        new Setting.DropdownSetting("实验：Hinting", FontLab::hintingIds,
                                FontLab::hintingId, FontLab::setHinting),
                        new Setting.DropdownSetting("实验：字重", FontLab::weightIds,
                                FontLab::weightId, FontLab::setWeight),
                        new Setting.DropdownSetting("实验：光栅倍率", FontLab::oversampleIds,
                                FontLab::oversampleId, FontLab::setOversample),
                        new Setting.BooleanSetting("实验：线性字体采样", FontLab::linearFilter,
                                FontLab::setLinearFilter),
                        new Setting.BooleanSetting("实验：自研字形图集", FontLab::atlasText,
                                FontLab::setAtlasText),
                        new Setting.SliderSetting("实验：覆盖率 Gamma", 0.8, 1.4,
                                FontLab::gamma, FontLab::setGamma, "%.2f", 0.05),
                        new Setting.ActionSetting("实验：应用最佳预设", () -> {
                            FontLab.applyBestPreset();
                            ConfigManager.touch();
                        }),
                        new Setting.ActionSetting("实验：应用 2x 平滑", () -> {
                            FontLab.applySmoothPreset();
                            ConfigManager.touch();
                        }),
                        new Setting.ActionSetting("实验：恢复原生字体", () -> {
                            FontLab.applyCrispPreset();
                            ConfigManager.touch();
                        })
                ), false)
        );
    }

    /** True when a setting row at {@code y} (height {@code h}) intersects the view. */
    private boolean isRowVisible(int y, int h) {
        return y + h >= contentTop() && y <= contentBottom();
    }

    private void rebuildSettingsWidgets() {
        clearWidgets();
        TextFieldWidget.clearFocus();
        if (statsPageActive()) {
            ensureStatsPage();
            return;
        }
        int left = contentLeft();
        int top = contentTop() - scrollY;
        if (settingsTab) {
            int y = top;
            for (Setting s : panelSettings) {
                int h = s.height();
                // Skip rows scrolled out of view: MC still meshes every widget's text,
                // so building off-screen rows costs real frame time.
                if (isRowVisible(y, h)) {
                    if (s instanceof Setting.GroupSetting group) {
                        // The header draws itself; the children that still need widgets are built here.
                        group.buildChildren(left, y, this::addRenderableWidget);
                    } else if (!s.immediate()) {
                        s.build(left, y, this::addRenderableWidget);
                    }
                }
                y += h;
            }
            return;
        }
        if (viewingFeature == null) {
            return;
        }
        int y = top + 28;
        for (Setting s : viewingFeature.settings()) {
            if (!s.isVisible()) {
                // A row that does not apply to the current mode still has to keep its widget out of
                // the panel entirely, or it would draw (and respond to the mouse) where nothing is.
                continue;
            }
            int h = s.totalHeight();
            if (isRowVisible(y, h)) {
                if (s instanceof Setting.GroupSetting group) {
                    group.buildChildren(left, y, this::addRenderableWidget);
                } else if (!s.immediate()) {
                    // buildWithHint, not build: the note is a widget of its own, built by the page.
                    s.buildWithHint(left, y, this::addRenderableWidget);
                }
            }
            y += h;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        dev.skypocket.render.ScissorTracker.reset(graphics);
        // Finish the close animation (then actually leave the screen).
        if (closing && animFinished()) {
            closing = false;
            GuiPanelCache.release();
            Minecraft mc = minecraft;
            mc.schedule(() -> mc.gui.setScreen(null));
            return;
        }
        layout();
        // Advance the sidebar highlight before the cache key is taken: the panel's drawing
        // must be a pure function of the frame's state, and the highlight animates over
        // several frames (updating it inside drawSidebar would step it twice on the frame
        // that records the panel, and make the recorded frame differ from the live one).
        updateSidebarHighlight();
        int lmx = (int) Math.round(localX(mouseX));
        int lmy = (int) Math.round(localY(mouseY));

        float anim = animScale();
        boolean animating = !animFinished();
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        // Scale the whole panel about the screen centre for the open/close animation.
        pose.translate(width / 2f, height / 2f);
        pose.scale(anim, anim);
        pose.translate(-width / 2f, -height / 2f);
        // offX/offY and scaleX/scaleY are already snapped to the device pixel grid (see
        // layout), so the panel's origin and size are whole device pixels: the cached blit
        // then lands exactly on the same pixels the live drawing does.
        pose.translate(offX, offY);
        pose.scale(scaleX, scaleY);
        // Everything above is the live transform (animation, drag, scale). The translucent
        // shell is drawn live (6 fills), then the opaque content either comes from the
        // cache as a single blit or is drawn live. The blit goes through this same pose,
        // so it animates and drags exactly like the live drawing does.
        // The whole panel is cached as a single image: with the premultiplied blit the
        // composite is exact for every pixel (translucent shell, antialiased text, corner
        // arcs, hover states), so nothing needs to be drawn live except while the cache is
        // cold or the panel is animating.
        boolean moving = transitionProgress() < 1f;
        if (moving) {
            // Keep the key moving so the cache never records a mid-slide frame.
            //
            // Passing animating=true only suppresses the *blit*; the recorder still bakes once the key
            // repeats, and here it repeats every frame (same view, same scroll) - so the texture would
            // hold a half-slid panel. After the animation the key matched that bake and the stale frame
            // was blitted until the mouse moved and changed the hover cell.
            contentRevision++;
        }
        long cacheKey = PanelCacheKey.of(view(), lmx, lmy, sidebarHighlightY, contentRevision,
                ToggleAnimation.signature(), animFinished());
        // The v2 stats page owns live controls and its own popup layers; baking the whole panel would
        // freeze its focused/pressed states. Draw the shell and page live while it is open.
        if (statsPageActive() || !GuiPanelCache.draw(graphics, cacheKey, PANEL_W, PANEL_H, scaleX,
                scaleY, animating || moving, this::drawPanel, lmx, lmy, delta)) {
            drawPanel(graphics, lmx, lmy, delta);
        }
        pose.popMatrix();

        // Row notes are hover tooltips now. Drawn after the panel and outside its transform/cache: the
        // tip follows the cursor (so it would otherwise force a re-bake every frame) and must not be
        // clipped by the content scissor.
        if (!closing && !statsPageActive() && inBody(lmx, lmy)) {
            String note = tooltipAt(lmx, lmy);
            if (note != null && !note.isEmpty()) {
                drawTooltip(graphics, Localization.tr(note), mouseX, mouseY);
            }
        }
        // The theme drawer is drawn last, in screen space: it floats above the panel and is never part
        // of the panel's cached image.
        devDrawer.draw(graphics, mouseX, mouseY, width, height);
    }

    /** The note attached to the row under a panel-local point, or null. */
    private String tooltipAt(double lx, double ly) {
        return dev.skypocket.gui.setting.SettingsView.tooltipAt(rowsWithY(), lx, ly);
    }

    /** A one-line tip next to the cursor, kept inside the window. */
    private void drawTooltip(GuiGraphicsExtractor graphics, String text, int mouseX, int mouseY) {
        int w = ClickGuiFonts.width(text) + 10;
        int h = ClickGuiFonts.lineHeight() + 6;
        int x = Math.max(4, Math.min(mouseX + 12, width - w - 4));
        int y = Math.max(4, Math.min(mouseY + 12, height - h - 4));
        dev.skypocket.gui.widget.ClickGuiStyle.shadow(graphics, x, y, w, h, 3);
        dev.skypocket.render.GuiPixels.fillRoundedRect(graphics, x, y, w, h, 3, 0xF0101010);
        ClickGuiFonts.drawText(graphics, text, x + 5, y + 3,
                dev.skypocket.gui.widget.ClickGuiStyle.textDim(), true);
    }

    /**
     * Everything the panel draws, in panel-local coordinates: the translucent shell, the
     * opaque content (including the settings widgets) and the overlay, in that order.
     * {@link GuiPanelCache} records this as one image, so nothing here may be drawn
     * separately by the caller.
     */
    /**
     * Draws the whole panel in panel-local coordinates: the shell, then the content, then the
     * pieces that sit on the translucent shell (title, sidebar).
     *
     * <p>Called either with the live graphics (every frame) or by the cache's recorder (once per
     * re-bake), so it must depend on nothing but the panel state covered by
     * {@link #panelCacheKey}. Painting itself lives in {@link PanelRenderer}. The widget pages are
     * drawn here, inside the content scissor, because their widgets belong to this screen.
     */
    /** Duration of the card-pane slide, ms. */
    private static final int TRANSITION_MS = 180;

    /** The view that was showing before the last navigation, drawn sliding out. */
    private Feature transitionFrom;
    /** +1 when a module opens (slides in from the right), -1 when it closes (from the left). */
    private int transitionDir = 1;
    private int transitionFromScroll;
    private long transitionStartMs;

    /** 0..1 eased: how far the card-pane slide has run. */
    private float transitionProgress() {
        if (transitionStartMs == 0L) {
            return 1f;
        }
        float t = Math.min(1f, (System.currentTimeMillis() - transitionStartMs) / (float) TRANSITION_MS);
        return Easing.EASE_OUT.apply(t);
    }

    /**
     * Draws one side of the transition: the card grid when {@code feature} is null, otherwise that
     * module's settings.
     *
     * <p>Widgets are not drawn here: MC hands them absolute coordinates, so they cannot slide with the
     * pane and are simply left out for the ~180 ms the animation runs.
     */
    private void drawPane(GuiGraphicsExtractor graphics, Feature feature, int paneScroll, int dx,
                          int lmx, int lmy) {
        org.joml.Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(dx, 0);
        if (feature == null) {
            PanelRenderer.drawCards(graphics, viewWith(null, paneScroll), lmx - dx, lmy);
        } else {
            ClickGuiFonts.drawText(graphics, feature.name(), SIDEBAR_W + 20,
                    contentTop() - paneScroll + 2,
                    dev.skypocket.gui.widget.ClickGuiStyle.text(), true);
            if (statsPage != null && statsPageFeature == feature) {
                statsPage.layout();
                pose.translate(statsPageDrawLeft(), statsPageDrawTop() - paneScroll);
                statsPage.draw(graphics, scaleX, scaleY, true);
                pose.translate(-statsPageDrawLeft(), -(statsPageDrawTop() - paneScroll));
            } else {
                java.util.List<dev.skypocket.gui.setting.SettingsView.RowAt> rows =
                        dev.skypocket.gui.setting.SettingsView.layout(feature.settings(),
                                contentTop() - paneScroll + 28);
                dev.skypocket.gui.setting.SettingsView.draw(viewState, graphics, rows, contentLeft(),
                        Setting.WIDGET_WIDTH, lmx - dx, lmy, contentTop(), contentBottom());
            }
        }
        pose.popMatrix();
    }

    /** The current view with a different feature/scroll, for drawing the outgoing side. */
    private PanelView viewWith(Feature feature, int paneScroll) {
        return new PanelView(false, feature, selected, paneScroll, search, ModuleRegistry.features());
    }
    /** Browser-style history for the card pane: "" is the grid, otherwise a module id. */
    private final java.util.ArrayDeque<String> backStack = new java.util.ArrayDeque<>();
    private final java.util.ArrayDeque<String> forwardStack = new java.util.ArrayDeque<>();

    /** Opens a module (or the grid when {@code id} is null), remembering where we came from. */
    private void navigate(String id) {
        String target = id == null ? "" : id;
        if (target.equals(currentId())) {
            return;
        }
        backStack.push(currentId());
        forwardStack.clear();
        applyNavigation(target);
    }

    private void goBack() {
        if (backStack.isEmpty()) {
            return;
        }
        forwardStack.push(currentId());
        applyNavigation(backStack.pop());
    }

    private void goForward() {
        if (forwardStack.isEmpty()) {
            return;
        }
        backStack.push(currentId());
        applyNavigation(forwardStack.pop());
    }

    private String currentId() {
        return viewingFeature == null ? "" : viewingFeature.id();
    }

    private void applyNavigation(String id) {
        if (statsPage != null) {
            statsPage.closePopups();
        }
        transitionFrom = viewingFeature;
        transitionFromScroll = scrollY;
        transitionDir = id.isEmpty() ? -1 : 1;
        transitionStartMs = System.currentTimeMillis();
        viewingFeature = id.isEmpty() ? null : ModuleRegistry.byId(id);
        scrollY = 0;
        layout();
        rebuildSettingsWidgets();
        ensureStatsPage();
    }

    /** Back/forward buttons, to the left of the search box; they never scroll with the content. */
    private void drawNavButtons(GuiGraphicsExtractor graphics, int lmx, int lmy) {
        drawNavButton(graphics, 0, lmx, lmy, !backStack.isEmpty());
        drawNavButton(graphics, 1, lmx, lmy, !forwardStack.isEmpty());
    }

    private void drawNavButton(GuiGraphicsExtractor graphics, int index, int lmx, int lmy,
                               boolean enabled) {
        int x = PanelGeometry.NAV_X + index * (PanelGeometry.NAV_SIZE + PanelGeometry.NAV_GAP);
        int y = PanelGeometry.SEARCH_Y;
        int size = PanelGeometry.NAV_SIZE;
        boolean hovered = enabled && lmx >= x && lmx < x + size && lmy >= y && lmy < y + size;
        dev.skypocket.gui.widget.ClickGuiStyle.button(graphics, x, y, size, size, hovered, false);
        String glyph = index == 0 ? "←" : "→";
        ClickGuiFonts.drawText(graphics, glyph, x + (size - ClickGuiFonts.width(glyph)) / 2,
                y + (size - ClickGuiFonts.lineHeight()) / 2,
                enabled ? dev.skypocket.gui.widget.ClickGuiStyle.text()
                        : dev.skypocket.gui.widget.ClickGuiStyle.textMuted(), true);
    }

    private boolean inNav(double lx, double ly, int index) {
        int x = PanelGeometry.NAV_X + index * (PanelGeometry.NAV_SIZE + PanelGeometry.NAV_GAP);
        return lx >= x && lx < x + PanelGeometry.NAV_SIZE && ly >= PanelGeometry.SEARCH_Y
                && ly < PanelGeometry.SEARCH_Y + PanelGeometry.NAV_SIZE;
    }
    private void drawPanel(GuiGraphicsExtractor graphics, int lmx, int lmy, float delta) {
        // The cache records drawPanel with its own extractor. Start that recording with an empty
        // scissor tracker so custom text states capture the scissor that belongs to this pass.
        dev.skypocket.render.ScissorTracker.reset(graphics);
        PanelRenderer.drawBackground(graphics, view());
        PanelRenderer.drawTabs(graphics, view(), lmx, lmy);

        int top = contentTop();
        if (!settingsTab) {
            // Fixed row above the scroll area: these never move, whatever the content does.
            PanelRenderer.drawSearch(graphics, view(), lmx, lmy);
            drawNavButtons(graphics, lmx, lmy);
        }
        if (transitionProgress() < 1f) {
            // Card pane transition: the outgoing view slides left, the incoming one in from the right.
            scissorContentArea(graphics, SIDEBAR_W, top, PANEL_W);
            int paneW = PANEL_W - PanelGeometry.SIDEBAR_W;
            float p = transitionProgress();
            // Opening a module: settings slide in from the right, the grid leaves to the left. Closing:
            // the grid slides back in from the left and the settings leave to the right.
            drawPane(graphics, transitionFrom, transitionFromScroll,
                    -Math.round(paneW * p) * transitionDir, lmx, lmy);
            drawPane(graphics, viewingFeature, scrollY,
                    Math.round(paneW * (1 - p)) * transitionDir, lmx, lmy);
            graphics.disableScissor();
        } else if (!settingsTab && viewingFeature == null) {
            // Modules list: scrollable card grid on the right. Scissor takes panel-local
            // coordinates (NOT screen coords); the helper snaps them to whole device pixels (and
            // pins the bottom to a row a scissor can reach) so the live and cached frames clip
            // identically and the mask meets the footer strip.
            scissorContentArea(graphics, SIDEBAR_W, top, PANEL_W);
            PanelRenderer.drawCards(graphics, view(), lmx, lmy);
            graphics.disableScissor();
        } else {
            // Settings tab spans the panel; a module's settings stay inside the card pane.
            scissorContentArea(graphics, settingsTab ? 0 : SIDEBAR_W, top, PANEL_W);
            if (!settingsTab) {
                // Feature title beside the back button.
                ClickGuiFonts.drawText(graphics, viewingFeature.name(), SIDEBAR_W + 20,
                        top - scrollY + 2,
                dev.skypocket.gui.widget.ClickGuiStyle.text(), true);
            }
            // Masked like the card hover: widgets scrolled below the footer (or above the tab
            // bar) are clipped away by the scissor, so they must not report a hover either.
            boolean body = inBody(lmx, lmy);
            if (statsPageActive()) {
                ensureStatsPage();
                statsPage.layout();
                Matrix3x2fStack pagePose = graphics.pose();
                pagePose.pushMatrix();
                pagePose.translate(statsPageDrawLeft(), statsPageDrawTop());
                statsPage.draw(graphics, scaleX, scaleY, false);
                pagePose.popMatrix();
            } else {
                super.extractRenderState(graphics, body ? lmx : FAR_AWAY, body ? lmy : FAR_AWAY, delta);
                // Rows that converted away from widgets draw here: inside the same scissor, after the widget
                // layer, so they clip and scroll exactly like the rest of the page.
                drawImmediateRows(graphics, body ? lmx : FAR_AWAY, body ? lmy : FAR_AWAY);
            }
            graphics.disableScissor();
        }

        PanelRenderer.drawFooter(graphics, lmx, lmy);
        // Title and sidebar sit on the translucent shell; the sidebar rows are masked so the ones
        // below the footer cannot hover.
        ClickGuiFonts.drawTextScaled(graphics, "SkyPocket", 16, 13,
                dev.skypocket.gui.widget.ClickGuiStyle.text(), true, ClickGuiFonts.HEADING_SCALE);
        if (!settingsTab) {
            PanelRenderer.drawSidebar(graphics, view(), sidebarHighlightY, lmx, lmy);
        }
    }

    /** The theme editor drawer (replaces the old studio screen; see {@link DevDrawer}). */
    private final DevDrawer devDrawer = new DevDrawer();

    /** Opens the drawer, or closes it - closing restores the palette it was opened with. */
    private void toggleDevDrawer() {
        if (devDrawer.isOpen()) {
            devDrawer.close();
        } else {
            devDrawer.open();
        }
    }

    /** Drag state for the rows the page draws itself (see {@link SettingsView}). */
    private final dev.skypocket.gui.setting.SettingsView.State viewState =
            new dev.skypocket.gui.setting.SettingsView.State();

    /** Rows of the current page with their y - the shared renderer's input. */
    private java.util.List<dev.skypocket.gui.setting.SettingsView.RowAt> rowsWithY() {
        int startY = contentTop() - scrollY + (settingsTab ? 0 : 28);
        return dev.skypocket.gui.setting.SettingsView.layout(activeRows(), startY);
    }

    /** Bumped whenever the panel's content is rebuilt from scratch (language, layout). */
    private int contentRevision;

    /** Animates the highlight bar's y toward the selected row (ease-out). */
    private void updateSidebarHighlight() {
        int target = sidebarTargetY();
        long now = System.currentTimeMillis();
        if (Float.isNaN(sidebarHighlightY)) {
            sidebarHighlightY = target;
            sidebarFromY = target;
            sidebarToY = target;
            sidebarAnimStartMs = now;
            return;
        }
        if (target != sidebarToY) {
            sidebarFromY = sidebarHighlightY;
            sidebarToY = target;
            sidebarAnimStartMs = now;
        }
        float t = (now - sidebarAnimStartMs) / (float) SIDEBAR_HIGHLIGHT_MS;
        t = Math.max(0f, Math.min(1f, t));
        sidebarHighlightY = sidebarFromY + (sidebarToY - sidebarFromY) * Easing.EASE_OUT.apply(t);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (closing) {
            return false;
        }
        // The drawer is the topmost layer while it is open.
        if (devDrawer.mouseClicked(event.x(), event.y(), width, height, shiftDown, ctrlDown)) {
            return true;
        }
        layout();
        double lx = localX(event.x());
        double ly = localY(event.y());

        // Unfocus the text field when clicking outside it.
        TextFieldWidget field = TextFieldWidget.focusedField();
        if (field != null && !field.contains(lx, ly)) {
            TextFieldWidget.clearFocus();
        }

        if (event.button() != 0) {
            return super.mouseClicked(new MouseButtonEvent(lx, ly, event.buttonInfo()), doubled);
        }

        // Back/forward live in the fixed row above the content, so they are hit-tested first.
        if (!settingsTab) {
            if (inNav(lx, ly, 0)) {
                goBack();
                return true;
            }
            if (inNav(lx, ly, 1)) {
                goForward();
                return true;
            }
        }

        // Panel chrome: PanelInput answers *what* was hit (tabs, footer, sidebar, cards - see its
        // javadoc), the switch below decides what to do about it. Anything that is not a panel
        // element falls through to the widget layer.
        PanelInput.Hit hit = PanelInput.hit(view(), lx, ly);
        switch (hit.kind()) {
            case MODULES_TAB -> {
                showModules();
                return true;
            }
            case SETTINGS_TAB -> {
                showSettings();
                return true;
            }
            case FOOTER_BUTTON -> {
                Minecraft.getInstance().gui.setScreen(new HudPositionEditorScreen(this));
                return true;
            }
            case SIDEBAR -> {
                // Choosing a category leaves the open module: its rows belong to the pane we are
                // navigating away from.
                navigate(null);
                selected = hit.category();
                scrollY = 0;
                layout();
                rebuildSettingsWidgets();
                return true;
            }
            case CARD_TOGGLE -> {
                Feature target = hit.feature();
                target.setEnabled(!target.isEnabled());
                return true;
            }
            case CARD_OPEN -> {
                navigate(hit.feature().id());
                return true;
            }
            case CARD_BODY -> {
                return true;
            }
            case NONE -> {
            }
        }

        // Widgets (settings page / feature detail) live in the panel body: a click outside it -
        // outside the panel, over the tab strip, or over the footer - must not reach them.
        // They are clipped away by the content scissor, so they must not react either.
        if (!inBody(lx, ly)) {
            return false;
        }
        if (statsPageActive()) {
            ensureStatsPage();
            statsPage.layout();
            statsPage.pointerDown(statsLocalX(lx), statsLocalY(ly),
                    Math.max(0, event.button()), shiftDown ? Modifiers.SHIFT : 0);
            return true;
        }
        // Rows without widgets are handled by the shared renderer: it knows the row types, the open
        // dropdown lists and the slider drag, and the settings card in the HUD editor uses the same code.
        int rowLeft = contentLeft();
        if (dev.skypocket.gui.setting.SettingsView.click(viewState, rowsWithY(), rowLeft,
                Setting.WIDGET_WIDTH, lx, ly, this::rebuildSettingsWidgets)) {
            return true;
        }
        return super.mouseClicked(new MouseButtonEvent(lx, ly, event.buttonInfo()), doubled);
    }



    private java.util.List<Setting> activeRows() {
        if (settingsTab) {
            return panelSettings;
        }
        return viewingFeature == null ? java.util.List.of() : viewingFeature.settings();
    }

    /**
     * Draws the rows that no longer have a widget: section headings and on/off switches.
     *
     * <p>Drawn with the same primitives the widgets used ({@code ClickGuiStyle.button} +
     * {@code ToggleButton.drawSwitch}), so the page looks identical - the difference is that these can be
     * clipped by the content scissor and scaled into the theme studio's preview.
     */
    private void drawImmediateRows(GuiGraphicsExtractor graphics, int mx, int my) {
        dev.skypocket.gui.setting.SettingsView.draw(viewState, graphics, rowsWithY(),
                contentLeft(), Setting.WIDGET_WIDTH, mx, my,
                contentTop(), contentBottom());
    }


    /** The expanded option list under a dropdown row. */

    /**
     * Handles a click inside an open dropdown's list.
     *
     * <p>The list is drawn by the page now, so it is no longer a widget that can answer for itself; this
     * walks the rows to find the expanded one and maps the point to an option.
     */

    /** Wheel over an open dropdown's list; returns true when it scrolled. */
    private boolean scrollDropdownList(double lx, double ly, double amount) {
        return dev.skypocket.gui.setting.SettingsView.scrollDropdownList(rowsWithY(),
                contentLeft(), Setting.WIDGET_WIDTH, lx, ly, amount);
    }

    /** Writes a slider's value from a panel-local x, the way clicking the track used to. */

    private void showModules() {
        boolean changed = settingsTab || viewingFeature != null;
        settingsTab = false;
        viewingFeature = null;
        if (changed) {
            scrollY = 0;
            rebuildSettingsWidgets();
        }
    }

    private void showSettings() {
        if (settingsTab) {
            return;
        }
        settingsTab = true;
        viewingFeature = null;
        scrollY = 0;
        rebuildSettingsWidgets();
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (devDrawer.mouseDragged(event.x(), event.y(), width, height)) {
            return true;
        }
        layout();
        double lx = localX(event.x());
        double ly = localY(event.y());
        if (statsPageActive()) {
            ensureStatsPage();
            statsPage.layout();
            statsPage.pointerMove(statsLocalX(lx), statsLocalY(ly), shiftDown ? Modifiers.SHIFT : 0);
            return true;
        }
        if (dev.skypocket.gui.setting.SettingsView.drag(viewState, rowsWithY(),
                contentLeft(), Setting.WIDGET_WIDTH, lx, ly)) {
            return true;
        }
        float anim = animScale();
        return super.mouseDragged(new MouseButtonEvent(lx, ly, event.buttonInfo()),
                deltaX / (scaleX * anim), deltaY / (scaleY * anim));
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        devDrawer.mouseReleased();
        layout();
        double lx = localX(event.x());
        double ly = localY(event.y());
        if (statsPageActive()) {
            ensureStatsPage();
            statsPage.layout();
            statsPage.pointerUp(statsLocalX(lx), statsLocalY(ly),
                    Math.max(0, event.button()), shiftDown ? Modifiers.SHIFT : 0);
            return true;
        }
        if (dev.skypocket.gui.setting.SettingsView.release(viewState)) {
            return true;
        }
        return super.mouseReleased(new MouseButtonEvent(lx, ly, event.buttonInfo()));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (devDrawer.mouseScrolled(mouseX, mouseY, verticalAmount, width, height)) {
            return true;
        }
        // Expanded dropdown lists consume the wheel when the cursor is over them
        // (they live in panel-local coordinates like every other widget).
        double lx = localX(mouseX);
        double ly = localY(mouseY);
        if (!inBody(lx, ly)) {
            // The wheel must not scroll anything while the cursor is outside the panel body
            // (outside the GUI, over the tab strip, or over the footer).
            return false;
        }
        if (statsPageActive()) {
            ensureStatsPage();
            statsPage.layout();
            statsPage.wheel(statsLocalX(lx), statsLocalY(ly), (float) verticalAmount,
                    shiftDown ? Modifiers.SHIFT : 0);
            return true;
        }
        // An open dropdown's list owns the wheel while the cursor is over it (it is drawn by the page
        // now, so it answers through the setting rather than as a widget).
        if (scrollDropdownList(lx, ly, verticalAmount)) {
            return true;
        }
        scrollY -= (int) (verticalAmount * 20);
        clampScroll();
        rebuildSettingsWidgets();
        return true;
    }

    /**
     * Modifier state, tracked from the key events themselves - the same way the HUD editor does it.
     *
     * <p>{@code Screen} has no portable "is control down" helper in this mapping, and polling GLFW while
     * an event is dispatched proved unreliable. Without this the settings page passed {@code false} for
     * both modifiers, so Ctrl+C/V worked in the card's fields but not in the page's - the two hosts had
     * silently drifted apart.
     */
    private boolean shiftDown;
    private boolean ctrlDown;

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (statsPageActive()) {
            layout();
            double lx = localX(mouseX);
            double ly = localY(mouseY);
            if (inBody(lx, ly)) {
                ensureStatsPage();
                statsPage.layout();
                statsPage.pointerMove(statsLocalX(lx), statsLocalY(ly),
                        shiftDown ? Modifiers.SHIFT : 0);
            }
        }
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_LEFT_SHIFT || key == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            shiftDown = false;
        }
        if (key == GLFW.GLFW_KEY_LEFT_CONTROL || key == GLFW.GLFW_KEY_RIGHT_CONTROL) {
            ctrlDown = false;
        }
        if (statsPageActive()) {
            ensureStatsPage();
            statsPage.keyUp(new dev.skypocket.ui.input.KeyEvent(canonicalKey(event), 0,
                    pageModifiers(event), false));
            return true;
        }
        return super.keyReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (closing) {
            return true;
        }
        int key = event.key();
        if (key == GLFW.GLFW_KEY_LEFT_SHIFT || key == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            shiftDown = true;
        }
        if (key == GLFW.GLFW_KEY_LEFT_CONTROL || key == GLFW.GLFW_KEY_RIGHT_CONTROL) {
            ctrlDown = true;
        }
        if (SkyPocketMod.getClickGuiKey() != null && SkyPocketMod.getClickGuiKey().matches(event)) {
            onClose();
            return true;
        }
        // The drawer owns its picker's fields; ESC closes the drawer before it closes the screen.
        if (devDrawer.keyPressed(key, shiftDown, ctrlDown)) {
            return true;
        }
        if (devDrawer.isOpen() && key == GLFW.GLFW_KEY_ESCAPE) {
            devDrawer.close();
            return true;
        }
        if (statsPageActive()) {
            ensureStatsPage();
            if (event.isEscape()) {
                if (statsPage.hasOpenPopup()) {
                    statsPage.closePopups();
                } else {
                    onClose();
                }
                return true;
            }
            statsPage.layout();
            statsPage.keyDown(new dev.skypocket.ui.input.KeyEvent(canonicalKey(event), 0,
                    pageModifiers(event), true));
            return true;
        }
        // A focused field inside an open colour picker owns the keys (the picker draws its own fields).
        if (dev.skypocket.gui.setting.SettingsView.keyPressed(viewState, rowsWithY(), key, shiftDown,
                ctrlDown)) {
            return true;
        }
        TextFieldWidget field = TextFieldWidget.focusedField();
        if (field != null) {
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                field.backspace();
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                TextFieldWidget.clearFocus();
                return true;
            }
            return false;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE && !search.isEmpty()) {
            search = search.substring(0, search.length() - 1);
            scrollY = 0;
            layout();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (closing) {
            return false;
        }
        // Open picker fields first: they are drawn, not widgets, so nothing would forward to them.
        if (event.isAllowedChatCharacter()) {
            String typed = event.codepointAsString();
            if (!typed.isEmpty() && devDrawer.charTyped(typed.charAt(0))) {
                return true;
            }
            if (statsPageActive()) {
                ensureStatsPage();
                statsPage.layout();
                statsPage.textInput(typed);
                return true;
            }
            if (!typed.isEmpty()
                    && dev.skypocket.gui.setting.SettingsView.charTyped(viewState, rowsWithY(), typed.charAt(0))) {
                return true;
            }
        }
        TextFieldWidget field = TextFieldWidget.focusedField();
        if (field != null) {
            if (event.isAllowedChatCharacter()) {
                field.append(event.codepointAsString());
            }
            return true;
        }
        if (!event.isAllowedChatCharacter()) {
            return false;
        }
        search += event.codepointAsString();
        scrollY = 0;
        layout();
        return true;
    }

    @Override
    public boolean preeditUpdated(net.minecraft.client.input.PreeditEvent event) {
        if (statsPageActive()) {
            ensureStatsPage();
            statsPage.preedit(new dev.skypocket.ui.input.PreeditEvent(
                    event == null ? "" : event.fullText(), event == null ? 0 : event.caretPosition()));
            return true;
        }
        return super.preeditUpdated(event);
    }

    @Override
    public void onClose() {
        if (closing) {
            return;
        }
        TextFieldWidget.clearFocus();
        ConfigManager.save();
        minecraft.onTextInputFocusChange(this, false);
        // Play the close animation, then actually leave the screen.
        if (ConfigManager.get().clickGuiAnimDuration <= 0) {
            minecraft.gui.setScreen(null);
            return;
        }
        closing = true;
        animStartMs = System.currentTimeMillis();
    }

    /** Rebuilds widgets after a config change (e.g. language switch) so labels update. */
    public void rebuildAfterLanguageChange() {
        contentRevision++;
        layout();
        rebuildSettingsWidgets();
    }

    /** Re-lays out and rebuilds widgets after an inline setting changed the layout (e.g. color editor expanded). */
    public void rebuildAfterSettingChange() {
        contentRevision++;
        layout();
        rebuildSettingsWidgets();
    }

    /**
     * Clips to the content area: panel-local bounds snapped to the device grid, with the bottom
     * pinned to {@link PanelGeometry#contentBottomRow()} - the row the scissor can actually sit on.
     *
     * <p>A scissor bound has to be a whole device pixel for the same reason the drawing helpers
     * need it: a live frame transforms the rectangle with the real screen pose (where the panel's
     * origin adds a whole number of device pixels), while a cached frame transforms the recorded
     * one with the panel's own transform and the blit then shifts it by that same origin. If a
     * bound is fractionary the two transforms round differently and the clip edge lands a pixel
     * off in one of the two - observed as "the content area's bottom mask is correct while static
     * but jumps up 1px as soon as the mouse moves".
     *
     * <p>The bottom additionally has to be a row the scissor can address at all: it is rounded to
     * whole gui-scaled units, so only multiples of guiScale device pixels are reachable. Passing
     * {@link PanelGeometry#contentBottomRow()} directly (a whole device pixel and a whole unit)
     * makes the clip land exactly on the footer strip's top edge.
     */
    private void scissorContentArea(GuiGraphicsExtractor g, int x0, int y0, int x1) {
        pushDeviceSpace(g);
        // Top/left/right round outwards (never cut into the content); the bottom is pinned to the
        // row the footer strip is drawn on. All four are whole gui-scaled units, because
        // transformAxisAligned floors the rect's size as well as its position.
        g.enableScissor(scissorFloor(x0), scissorFloor(y0), scissorFloor(x1), contentBottomRow());
        popDeviceSpace(g);
    }

}
