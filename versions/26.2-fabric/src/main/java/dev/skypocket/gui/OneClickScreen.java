package dev.skypocket.gui;

import dev.skypocket.util.Easing;
import dev.skypocket.util.MouseButtons;
import dev.skypocket.SkyPocketMod;
import dev.skypocket.util.Easing;
import dev.skypocket.util.MouseButtons;
import dev.skypocket.config.ConfigManager;
import dev.skypocket.util.Easing;
import dev.skypocket.util.MouseButtons;
import dev.skypocket.config.ModConfig;
import dev.skypocket.util.Easing;
import dev.skypocket.util.MouseButtons;
import dev.skypocket.gui.setting.Setting;
import dev.skypocket.util.Easing;
import dev.skypocket.util.MouseButtons;
import dev.skypocket.i18n.Localization;
import dev.skypocket.util.Easing;
import dev.skypocket.util.MouseButtons;
import dev.skypocket.render.ClickGuiBlur;
import dev.skypocket.util.Easing;
import dev.skypocket.util.MouseButtons;
import dev.skypocket.util.GuiMetrics;
import dev.skypocket.util.Easing;
import dev.skypocket.util.MouseButtons;
import dev.skypocket.gui.widget.GuiTheme;
import dev.skypocket.util.Easing;
import dev.skypocket.util.MouseButtons;
import dev.skypocket.render.ClickGuiFonts;
import dev.skypocket.util.Easing;
import dev.skypocket.util.MouseButtons;
import dev.skypocket.render.ClickGuiMetrics;
import dev.skypocket.util.Easing;
import dev.skypocket.util.MouseButtons;
import dev.skypocket.render.FontLab;
import dev.skypocket.util.Easing;
import dev.skypocket.util.MouseButtons;
import dev.skypocket.render.GuiPanelCache;
import dev.skypocket.util.Easing;
import dev.skypocket.util.MouseButtons;
import dev.skypocket.ui.input.InputKeys;
import dev.skypocket.util.Easing;
import dev.skypocket.util.MouseButtons;
import dev.skypocket.ui.input.Modifiers;
import dev.skypocket.util.Easing;
import dev.skypocket.util.MouseButtons;
import dev.skypocket.ui.input.PreeditEvent;
import dev.skypocket.util.Easing;
import dev.skypocket.util.MouseButtons;
import net.minecraft.client.Minecraft;
import dev.skypocket.util.Easing;
import dev.skypocket.util.MouseButtons;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import dev.skypocket.util.Easing;
import dev.skypocket.util.MouseButtons;
import net.minecraft.client.gui.screens.Screen;
import dev.skypocket.util.Easing;
import dev.skypocket.util.MouseButtons;
import net.minecraft.client.input.CharacterEvent;
import dev.skypocket.util.Easing;
import dev.skypocket.util.MouseButtons;
import net.minecraft.client.input.KeyEvent;
import dev.skypocket.util.Easing;
import dev.skypocket.util.MouseButtons;
import net.minecraft.client.input.MouseButtonEvent;
import dev.skypocket.util.Easing;
import dev.skypocket.util.MouseButtons;
import net.minecraft.network.chat.Component;
import dev.skypocket.util.Easing;
import dev.skypocket.util.MouseButtons;
import org.joml.Matrix3x2fStack;

import dev.skypocket.util.Easing;
import dev.skypocket.util.MouseButtons;
import java.util.List;

import dev.skypocket.util.Easing;
import dev.skypocket.util.MouseButtons;
import static dev.skypocket.gui.PanelGeometry.*;

import dev.skypocket.util.Easing;
import dev.skypocket.util.MouseButtons;
import static dev.skypocket.render.GuiPixels.popDeviceSpace;
import dev.skypocket.util.Easing;
import dev.skypocket.util.MouseButtons;
import static dev.skypocket.render.GuiPixels.pushDeviceSpace;
import dev.skypocket.util.Easing;
import dev.skypocket.util.MouseButtons;
import static dev.skypocket.render.GuiPixels.scissorFloor;
import dev.skypocket.util.Easing;
import dev.skypocket.util.MouseButtons;
import static dev.skypocket.render.GuiPixels.scissorRow;

/**
 * The Click GUI, styled after modern Lunar/Badlion. Everything is laid out in a fixed
 * "panel-local" coordinate space ({@link #PANEL_W} x {@link #PANEL_H}) and then scaled
 * by {@code clickGuiScale} at render time, so the panel's real size is independent of
 * the game's GUI Scale. The design canvas is calibrated directly in current units
 * (1348 x 810); there is no legacy scale multiplier in rendering.
 *
 * <p>The top bar is a dark main-tab strip: the title on the left, and two tabs
 * ("Modules" / "Settings") on the right. "Settings" hosts the panel settings as a
 * standalone entry (not a feature module). The Modules tab shows a two-level
 * navigation sidebar on the left and a centered card grid on the right.
 */
public final class OneClickScreen extends dev.skypocket.ui.mc.host.UiHost {

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

    private int scrollY;
    /** Id of the synthetic feature that carries the panel-wide settings page. */
    private static final String PANEL_SETTINGS_ID = "__settings";
    private String search = "";
    private Category selected = null;
    private Feature viewingFeature;
    private final List<Feature> features;
    private final List<Setting> panelSettings;
    private final Feature panelFeature;
    private final GuiPanelCache.Slot shellCache = new GuiPanelCache.Slot();
    private ModulesPage modulesPage;
    private float modulesPageHeight = -1f;
    private ModulesPage moduleChrome;
    private float moduleChromeHeight = -1f;
    private SettingsPage statsPage;
    private Feature statsPageFeature;
    private SettingsPage transitionPage;
    private Feature transitionPageFeature;
    private float statsPageHeight = -1f;

    // The three panes own separate InputRouters, so each can hold a focused text field at the same
    // time and none of them knows about the others. Typing then went to whichever pane the dispatch
    // code happened to check first - the sidebar search box - rather than to the field the user had
    // just clicked.

    /** Last theme/metric generation the cached page objects were built for. */
    private int builtThemeGeneration = -1;

    public OneClickScreen() {
        super(Component.literal("SkyPocket"));
        ModuleRegistry.init();
        this.features = ModuleRegistry.features();
        this.panelSettings = buildPanelSettings();
        this.panelFeature = new Feature() {
            @Override public String id() { return "__settings"; }
            @Override public String name() { return Localization.tr("设置"); }
            @Override public String rawName() { return "设置"; }
            @Override public Category category() { return Category.RENDER; }
            @Override public boolean isEnabled() { return true; }
            @Override public void setEnabled(boolean value) { }
            @Override public List<Setting> settings() { return panelSettings; }
        };
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
        float target = ClickGuiMetrics.projectedScale(guiScale);
        // The panel is a fixed 1348x810 design surface. In a small window it must shrink to fit,
        // otherwise its right/bottom edge (and the content scissor derived from it) can leave the
        // drawable area. Keep the configured scale as the upper bound.
        float fitX = Math.max(0.05f, (width - 16f) / PANEL_W);
        float fitY = Math.max(0.05f, (height - 16f) / PANEL_H);
        target = Math.max(0.05f, Math.min(target, Math.min(fitX, fitY)));
        // Snap the panel's on-screen size to whole device pixels. GuiPanelCache bakes the
        // panel into an integer-sized texture and blits it 1:1, so a fractional device size
        // makes the texture's pixel grid drift from the screen's: the cached panel shifts by
        // up to a pixel towards its far edges and every component sits a fraction of a pixel
        // off, while a live frame does not. The snap moves the scale by well under 0.1%.
        scaleX = Math.round(PANEL_W * target * guiScale) / (float) (PANEL_W * guiScale);
        scaleY = Math.round(PANEL_H * target * guiScale) / (float) (PANEL_H * guiScale);
        ClickGuiMetrics.setEffectiveScale(scaleX);
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
        return new PanelView(viewingFeature, selected, scrollY, search, features);
    }

    /**
     * Whether the panel-wide settings page is the current content.
     *
     * <p>This used to be its own boolean field, set in parallel with {@code viewingFeature} and
     * checked from eighteen places. It is now derived from the same navigation state every other page
     * uses, so the settings page sits in the back/forward history like a module does and cannot drift
     * out of sync with what is on screen.
     */
    private boolean settingsTabActive() {
        return viewingFeature == panelFeature;
    }

    /**
     * Resolves a navigation id to its feature.
     *
     * <p>The panel settings page is not a registered module - it must not appear in the card grid -
     * so the registry cannot find it and this screen resolves that one id itself. Everything else
     * goes through {@link ModuleRegistry}.
     */
    private Feature resolve(String id) {
        if (PANEL_SETTINGS_ID.equals(id)) {
            return panelFeature;
        }
        return ModuleRegistry.byId(id);
    }

    private boolean statsPageActive() {
        // Every module with settings now uses the v2 page; empty modules cannot be opened.
        return settingsTabActive()
                || (viewingFeature != null && viewingFeature.hasSettings());
    }

    private boolean modulesPageActive() {
        return viewingFeature == null;
    }

    private boolean moduleChromeActive() {
        return viewingFeature != null && transitionProgress() >= 1f;
    }

    private final ModulesPage.Host modulesHost = new ModulesPage.Host() {
        @Override public String search() { return search; }
        /**
         * Search text changed.
         *
         * <p>The card grid has to be rebuilt here: the search box lives in the top bar now, so it no
         * longer shares a page with the grid and cannot call the grid's own refresh. It used to, and
         * the grid filtered itself as a side effect of owning the field.
         */
        @Override public void setSearch(String value) {
            search = value;
            scrollY = 0;
            if (modulesPage != null) {
                modulesPage.refreshCards();
            }
        }
        @Override public Category selected() { return selected; }
        @Override public void setSelected(Category category) {
            if (viewingFeature != null) {
                navigate(null);
            }
            selected = category;
            scrollY = 0;
            if (modulesPage != null) {
                modulesPage.rebuild();
            }
            if (moduleChrome != null) {
                moduleChrome.rebuild();
            }
        }
        @Override public boolean canGoBack() { return !backStack.isEmpty(); }
        @Override public boolean canGoForward() { return !forwardStack.isEmpty(); }
        @Override public void goBack() { OneClickScreen.this.goBack(); }
        @Override public void goForward() { OneClickScreen.this.goForward(); }
        @Override public void open(Feature feature) { navigate(feature.id()); }
        @Override public void toggle(Feature feature) {
            feature.setEnabled(!feature.isEnabled());
        }
    };

    /**
     * The top bar's search box and navigation arrows.
     *
     * <p>They are not part of any page: they sit above the content area, outside its scissor, and stay
     * put across every page. Created once, because they own focus and caret state.
     */
    private final TopBarControls topBar = new TopBarControls(modulesHost, Ui2Theme.palette());

    private void ensureModulesPage() {
        if (!modulesPageActive()) {
            return;
        }
        float pageHeight = Math.max(35f, contentBottom() - TAB_BAR_H);
        if (modulesPage == null || modulesPageHeight != pageHeight) {
            modulesPage = new ModulesPage(modulesHost, Ui2Theme.palette(), PANEL_W, pageHeight);
            modulesPageHeight = pageHeight;
        }
    }

    private void drawModulesPage(GuiGraphicsExtractor graphics) {
        ensureModulesPage();
        if (modulesPage == null) {
            return;
        }
        modulesPage.layout();
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(0f, TAB_BAR_H);
        modulesPage.draw(graphics, scaleX, scaleY, false);
        pose.popMatrix();
    }


    private void ensureModuleChrome() {
        if (!moduleChromeActive()) {
            return;
        }
        float pageHeight = Math.max(35f, contentBottom() - TAB_BAR_H);
        if (moduleChrome == null || moduleChromeHeight != pageHeight) {
            moduleChrome = new ModulesPage(modulesHost, Ui2Theme.palette(), PANEL_W, pageHeight, false);
            moduleChromeHeight = pageHeight;
        }
    }

    private void drawModuleChrome(GuiGraphicsExtractor graphics) {
        ensureModuleChrome();
        if (moduleChrome == null) {
            return;
        }
        moduleChrome.layout();
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(0f, TAB_BAR_H);
        moduleChrome.draw(graphics, scaleX, scaleY, false);
        pose.popMatrix();
    }

    private float statsPageTop() {
        return contentTop() + 32f;
    }

    private float statsPageWidth() {
        // Every content page, the panel-wide settings included, lives beside the sidebar now. The
        // settings page used to take the full panel width because it had no sidebar of its own.
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

    /**
     * The settings page, built if this frame needs it - or null when there is nothing to show.
     *
     * <p>Callers used to assume ensureStatsPage() always produced a page and dereferenced the field
     * directly. It does not: with no feature open there is no page to build, and the very first mouse
     * move after opening the GUI hit that null. Every forwarding site now asks this instead.
     */
    private SettingsPage statsPageOrNull() {
        if (!statsPageActive()) {
            return null;
        }
        ensureStatsPage();
        return statsPage;
    }

    private void ensureStatsPage() {
        if (!statsPageActive()) {
            // Keep the page through the 180 ms slide-out so drawPane can animate the outgoing side.
            return;
        }
        Feature target = viewingFeature;
        if (target == null) {
            return;
        }
        float height = Math.max(35f, contentBottom() - statsPageTop());
        if (statsPage == null || statsPageFeature != target || statsPageHeight != height) {
            float pageWidth = statsPageWidth();
            float columnWidth = settingsTabActive()
                    ? pageWidth - 30f
                    : Math.min(1002f, pageWidth - 58f);
            statsPage = new SettingsPage(target, Ui2Theme.palette(),
                    pageWidth, height, columnWidth);
            statsPageFeature = target;
            statsPageHeight = height;
        }
    }



    private int canonicalKey(KeyEvent event) {
        return InputKeys.canonical(event);
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

    private int contentTop() {
        // Every page starts at the same row now: the search box that used to sit above the content
        // moved into the top bar.
        return PanelGeometry.CONTENT_TOP;
    }

    private void clampScroll() {
        scrollY = Math.min(scrollY, PanelGeometry.maxScroll(view(), panelSettingsHeight()));
        if (scrollY < 0) {
            scrollY = 0;
        }
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
                new Setting.Header("外观", "调整主题、毛玻璃、透明度和文字阴影"),
                // Palette. The setter applies it immediately; Setting already wraps it with touch(), so
                // the panel cache drops its baked texture and the very next frame is drawn with the new
                // colours (see GuiTheme).
                new Setting.DropdownSetting("主题", dev.skypocket.gui.widget.ThemeStore::names,
                        () -> cfg.clickGuiTheme, v -> {
                            cfg.clickGuiTheme = v;
                            dev.skypocket.gui.widget.ThemeStore.apply(v);
                        }),
                new Setting.BooleanSetting("毛玻璃效果", () -> cfg.clickGuiBlur, v -> {
                    cfg.clickGuiBlur = v;
                    ClickGuiBlur.apply();
                }),
                new Setting.SliderSetting("毛玻璃强度", 0, 100, () -> cfg.clickGuiBlurIntensity, v -> {
                    cfg.clickGuiBlurIntensity = (int) Math.round(v);
                    ClickGuiBlur.apply();
                }, "%.0f%%", 1),
                new Setting.SliderSetting("背景透明度", 0, 255, () -> cfg.clickGuiBackgroundAlpha,
                        v -> cfg.clickGuiBackgroundAlpha = (int) Math.round(v), "%.0f", 1),
                new Setting.BooleanSetting("文字阴影", () -> cfg.clickGuiTextShadow, v -> cfg.clickGuiTextShadow = v),

                new Setting.Header("布局与动画", "调整 ClickGUI 缩放和打开/关闭动画"),
                new Setting.SliderSetting("ClickGUI 缩放",
                        ModConfig.CLICK_GUI_SCALE_MIN, ModConfig.CLICK_GUI_SCALE_MAX,
                        () -> cfg.clickGuiScale, v -> cfg.clickGuiScale = (float) v, "%.1f",
                        ModConfig.CLICK_GUI_SCALE_STEP),
                new Setting.SliderSetting("动画时长", 0, 600, () -> cfg.clickGuiAnimDuration,
                        v -> cfg.clickGuiAnimDuration = (int) Math.round(v), "%.0fms", 10),
                new Setting.DropdownSetting("缓动曲线", Easing::ids,
                        () -> cfg.clickGuiEasing, v -> cfg.clickGuiEasing = v),

                new Setting.Header("行为", "控制模块生效范围和界面语言"),
                new Setting.BooleanSetting("SkyBlock 模块仅在 SkyBlock 生效", () -> cfg.skyBlockModulesOnly,
                        v -> cfg.skyBlockModulesOnly = v),
                new Setting.DropdownSetting("语言",
                        () -> List.of("跟随系统", "中文", "English"),
                        Localization::displayName,
                        value -> {
                            String code = switch (value) {
                                case "中文" -> "zh_cn";
                                case "English" -> "en_us";
                                default -> "system";
                            };
                            if (code.equals(cfg.language)) {
                                return;
                            }
                            cfg.language = code;
                            ConfigManager.save();
                            Minecraft mc = Minecraft.getInstance();
                            mc.schedule(() -> {
                                if (mc.gui.screen() instanceof OneClickScreen s) {
                                    s.rebuildAfterLanguageChange();
                                }
                            });
                        }),

                new Setting.Header("性能", "控制面板渲染缓存"),
                new Setting.BooleanSetting("面板渲染缓存", () -> cfg.clickGuiPanelCache, v -> {
                    cfg.clickGuiPanelCache = v;
                    if (!v) {
                        GuiPanelCache.release();
                    }
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
                        new Setting.Header("字体实验", "字体栅格、字重与渲染实验"),
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
                        }),
                        new Setting.ActionSetting("UI v2 测试页", () ->
                                Minecraft.getInstance().gui.setScreen(new DevUiTestScreen())),
                        // Shell layering experiments. Not persisted: these answer whether the panel
                        // still needs its own stratum and its shell texture, then go away.
                        new Setting.BooleanSetting("实验：面板分层 stratum",
                                dev.skypocket.render.PanelExperiment::panelStratum,
                                dev.skypocket.render.PanelExperiment::setPanelStratum),
                        new Setting.BooleanSetting("实验：外壳缓存",
                                dev.skypocket.render.PanelExperiment::shellCache,
                                dev.skypocket.render.PanelExperiment::setShellCache)
                ), false).subtitle("字体与渲染调试选项"),

                new Setting.Header("HUD 字体", "选择 HUD 文本使用的字体；切换后立即重新测量"),
                new Setting.BooleanSetting("使用 SkyPocket 字体", () -> cfg.hudSkyPocketFont,
                        v -> {
                            cfg.hudSkyPocketFont = v;
                            ConfigManager.touch();
                        })
        );
    }

    private void rebuildSettingsWidgets() {
        clearWidgets();
        ensureStatsPage();
    }

    /**
     * v2 pages keep the WidgetPalette they were constructed with. A token edit changes
     * GuiTheme/GuiMetrics rather than those snapshots, so the page objects (and their page
     * caches) have to be rebuilt when either generation changes. This is what makes the
     * theme studio live instead of requiring a trip through a module settings page.
     */
    private void refreshThemePagesIfNeeded() {
        int generation = GuiTheme.generation() * 31 + GuiMetrics.generation();
        if (generation == builtThemeGeneration) {
            return;
        }
        builtThemeGeneration = generation;
        modulesPage = null;
        modulesPageHeight = -1f;
        moduleChrome = null;
        moduleChromeHeight = -1f;
        statsPage = null;
        statsPageFeature = null;
        statsPageHeight = -1f;
        transitionPage = null;
        transitionPageFeature = null;
        GuiPanelCache.release();
        rebuildSettingsWidgets();
    }

    /**
     * Paints the whole panel at the current animation and scale.
     *
     * <p>Takes the pointer because several parts of the shell resolve hover parametrically rather
     * than keeping it on an element - the footer buttons and the shell cache key both read it.
     */
    private void paintPanel(GuiGraphicsExtractor graphics, double mouseX, double mouseY, float delta) {
        dev.skypocket.render.ScissorTracker.reset(graphics);
        // Finish the close animation (then actually leave the screen).
        if (closing && animFinished()) {
            closing = false;
            GuiPanelCache.release();
            Minecraft mc = minecraft;
            mc.schedule(() -> mc.gui.setScreen(null));
            return;
        }
        refreshThemePagesIfNeeded();
        layout();
        int lmx = (int) Math.round(localX(mouseX));
        int lmy = (int) Math.round(localY(mouseY));

        float anim = animScale();
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        // Scale the whole panel about the screen centre for the open/close animation.
        pose.translate(width / 2f, height / 2f);
        pose.scale(anim, anim);
        pose.translate(-width / 2f, -height / 2f);
        // offX/offY and scaleX/scaleY are already snapped to the device pixel grid (see
        // layout). The shell and page chrome draw live; each v2 page owns its own cache.
        pose.translate(offX, offY);
        pose.scale(scaleX, scaleY);
        drawPanel(graphics, lmx, lmy, delta);
        pose.popMatrix();

        // The theme drawer is drawn last, in screen space: it floats above the panel and is never part
        // of the panel's cached image.
        devDrawer.draw(graphics, (int) mouseX, (int) mouseY, width, height);
    }


    /** Draws the shell, v2 chrome and page content in panel-local coordinates. */

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
        if (feature == null) {
            ensureModulesPage();
            if (modulesPage != null) {
                modulesPage.layout();
                modulesPage.drawTransition(graphics, dx);
            }
        } else {
            pose.translate(dx, 0);
            ClickGuiFonts.drawText(graphics, feature.name(), SIDEBAR_W + 35,
                    contentTop() - paneScroll + 4,
                    dev.skypocket.gui.widget.ClickGuiStyle.text(), true);
            SettingsPage page = feature == transitionPageFeature && transitionPage != null
                    ? transitionPage
                    : statsPageFeature == feature ? statsPage : null;
            if (page != null) {
                page.layout();
                pose.translate(statsPageDrawLeft(), statsPageDrawTop() - paneScroll);
                page.draw(graphics, scaleX, scaleY, true);
                pose.translate(-statsPageDrawLeft(), -(statsPageDrawTop() - paneScroll));
            }
        }
        pose.popMatrix();
    }

    /** The current view with a different feature/scroll, for drawing the outgoing side. */
    private PanelView viewWith(Feature feature, int paneScroll) {
        return new PanelView(feature, selected, paneScroll, search, ModuleRegistry.features());
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
        if (!target.isEmpty()) {
            Feature feature = resolve(target);
            // A module without settings has no detail page; the card click is already swallowed,
            // but keyboard/history paths must not create one either.
            if (feature == null || !feature.hasSettings()) {
                return;
            }
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
        Feature next = id == null || id.isEmpty() ? null : resolve(id);
        if (next != null && !next.hasSettings()) {
            return;
        }
        if (modulesPage != null) {
            modulesPage.clearHover();
        }
        if (statsPage != null) {
            statsPage.closePopups();
            transitionPage = statsPage;
            transitionPageFeature = statsPageFeature;
        }
        transitionFrom = viewingFeature;
        transitionFromScroll = scrollY;
        transitionDir = next == null ? -1 : 1;
        transitionStartMs = System.currentTimeMillis();
        viewingFeature = next;
        scrollY = 0;
        layout();
        rebuildSettingsWidgets();
        ensureStatsPage();
    }

    private void drawPanel(GuiGraphicsExtractor graphics, int lmx, int lmy, float delta) {
        // The cache records drawPanel with its own extractor. Start that recording with an empty
        // scissor tracker so custom text states capture the scissor that belongs to this pass.
        dev.skypocket.render.ScissorTracker.reset(graphics);
        // The translucent body must stay live: a cached body blit can be layered over live page
        // content and grey it out. Cache only the non-overlapping tabs/footer strip.
        PanelRenderer.drawBackground(graphics, view());
        // GUI blits can be reordered by their bounds; put the background in its own lower
        // stratum so page content is always composited above it.
        //
        // Behind a switch while the claim is being tested: see PanelExperiment.panelStratum().
        if (dev.skypocket.render.PanelExperiment.panelStratum()) {
            graphics.nextStratum();
        }
        GuiPanelCache.PanelContent shellContent =
                (shellGraphics, shellMouseX, shellMouseY, shellDelta) -> {
                    // The main tabs are gone: the top bar carries only the logo and title, which are
                    // drawn after the content so they stay above it. The footer holds the actions.
                    PanelRenderer.drawFooter(shellGraphics, shellMouseX, shellMouseY);
                };
        boolean shellFromCache = dev.skypocket.render.PanelExperiment.shellCache()
                && shellCache.draw(graphics, shellCacheKey(lmx, lmy, scaleX, scaleY),
                        PANEL_W, PANEL_H, scaleX, scaleY, !animFinished(), shellContent,
                        lmx, lmy, 0f);
        if (!shellFromCache) {
            shellContent.drawContent(graphics, lmx, lmy, 0f);
        }

        int top = contentTop();
        if (transitionProgress() < 1f) {
            // The outgoing/incoming panes slide; the homepage page carries its own sidebar.
            scissorContentArea(graphics, 0, TAB_BAR_H, PANEL_W);
            int paneW = PANEL_W - PanelGeometry.SIDEBAR_W;
            float p = transitionProgress();
            drawPane(graphics, transitionFrom, transitionFromScroll,
                    -Math.round(paneW * p) * transitionDir, lmx, lmy);
            drawPane(graphics, viewingFeature, scrollY,
                    Math.round(paneW * (1 - p)) * transitionDir, lmx, lmy);
            graphics.disableScissor();
        } else if (modulesPageActive()) {
            scissorContentArea(graphics, 0, TAB_BAR_H, PANEL_W);
            drawModulesPage(graphics);
            graphics.disableScissor();
        } else {
            if (moduleChromeActive()) {
                // The module page's sidebar/search/navigation are the same v2 chrome as the homepage.
                drawModuleChrome(graphics);
            }
            // Settings tab spans the panel; a module's settings stay inside the card pane.
            scissorContentArea(graphics, SIDEBAR_W, top, PANEL_W);
            if (viewingFeature != null) {
                dev.skypocket.ui.mc.McUiPainter titlePainter =
                        new dev.skypocket.ui.mc.McUiPainter(graphics);
                titlePainter.text(viewingFeature.name(), SIDEBAR_W + 35,
                        top - scrollY + 4, dev.skypocket.gui.widget.ClickGuiStyle.text());
            }
            SettingsPage settingsPage = statsPageOrNull();
            if (settingsPage != null) {
                // Lay it out before drawing. A page built this frame - which happens on every theme
                // edit, because the palette it captured has been replaced - has no laid-out tree yet,
                // so every element still reports a zero-sized box and the whole page piles up in the
                // top-left corner. ModulesPage and SettingsPage's other hosts both call layout() on
                // the same line; this path lost it.
                settingsPage.layout();
                Matrix3x2fStack pagePose = graphics.pose();
                pagePose.pushMatrix();
                pagePose.translate(statsPageDrawLeft(), statsPageDrawTop());
                settingsPage.draw(graphics, scaleX, scaleY, false);
                pagePose.popMatrix();
            }
            graphics.disableScissor();
        }

        // The search box and arrows live in the top bar, above the content area and outside its
        // scissor - which is why they cannot be part of a page's tree. Drawn after the content so
        // nothing can overlap them.
        topBar.sync();
        topBar.layout();
        topBar.draw(graphics);

        // Title sits above the cached shell; the footer is part of the shell slot.
        int logoSource = ClickGuiMetrics.nearestLogoSample(46);
        net.minecraft.resources.Identifier logo = ModuleIcon.logo(logoSource);
        boolean hasLogo = !Minecraft.getInstance().getResourceManager()
                .getResourceStack(logo).isEmpty();
        int titleX = 32;
        if (hasLogo) {
            graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, logo,
                    24, 16, 0, 0, 46, 46, logoSource, logoSource, logoSource, logoSource,
                    0xFFFFFFFF);
            titleX = 82;
        }
        ClickGuiFonts.drawTextWeighted(graphics, "SkyPocket", titleX, 26,
                dev.skypocket.gui.widget.ClickGuiStyle.text(), true,
                ClickGuiFonts.HEADING_SCALE, dev.skypocket.render.FontLab.Weight.SEMIBOLD);
    }

    private long shellCacheKey(int lmx, int lmy, float scaleX, float scaleY) {
        long key = System.identityHashCode(this);
        key = key * 31 + (settingsTabActive() ? 1 : 0);
        key = key * 31 + ConfigManager.revision();
        key = key * 31 + Math.round(ClickGuiFonts.deviceFactor() * 1000f);
        key = key * 31 + Math.round(scaleX * 1000f);
        key = key * 31 + Math.round(scaleY * 1000f);
        // Hover is part of the key because the cached strip paints it. The main tabs used to be in
        // here too; they are gone, so only the footer's two buttons remain.
        int hover = 0;
        int footerY = PanelGeometry.footerBtnY();
        if (lmy >= footerY && lmy <= footerY + PanelGeometry.FOOTER_BTN_H) {
            if (lmx >= PanelGeometry.FOOTER_BTN_X
                    && lmx <= PanelGeometry.FOOTER_BTN_X + PanelGeometry.FOOTER_BTN_W) {
                hover = 1;
            } else if (lmx >= PanelGeometry.FOOTER_GEAR_X
                    && lmx <= PanelGeometry.FOOTER_GEAR_X + PanelGeometry.FOOTER_GEAR_SIZE) {
                hover = 2;
            }
        }
        return key * 31 + hover;
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


    // ---- UiHost: the top bar is the first pane migrated -------------------------------------

    /** The top bar, as a layer. Created once; its bounds and visibility never change. */
    private final PaneLayer topBarLayer = new PaneLayer(
            () -> new dev.skypocket.ui.Rect(0f, 0f, PanelGeometry.PANEL_W, PanelGeometry.TAB_BAR_H),
            () -> true,
            topBar);

    /**
     * The homepage, as a layer.
     *
     * <p>Its bounds start at the content top rather than at the panel origin: the pane is laid out
     * relative to the tab bar, which is why the hand-written path used to subtract TAB_BAR_H from
     * every coordinate it forwarded.
     */
    private final PaneLayer modulesLayer = new PaneLayer(
            () -> new dev.skypocket.ui.Rect(0f, TAB_BAR_H, PanelGeometry.PANEL_W,
                    contentBottom() - TAB_BAR_H),
            this::modulesPageActive,
            new PaneLayer.Pane() {
                @Override public void paint(dev.skypocket.ui.UiPainter painter) {
                    // Drawing still goes through the existing path; see paintHost.
                }
                @Override public float scale() { return 1f; }
                @Override public boolean pointerDown(float x, float y, int b, int m) {
                    ensureModulesPage();
                    return modulesPage != null && modulesPage.pointerDown(x, y, b, m);
                }
                @Override public boolean pointerMove(float x, float y, int m) {
                    return modulesPage != null && modulesPage.pointerMove(x, y, m);
                }
                @Override public boolean pointerUp(float x, float y, int b, int m) {
                    return modulesPage != null && modulesPage.pointerUp(x, y, b, m);
                }
                @Override public boolean wheel(float x, float y, float d, int m) {
                    return modulesPage != null && modulesPage.wheel(x, y, d, m);
                }
                @Override public boolean keyDown(dev.skypocket.ui.input.KeyEvent e) {
                    return modulesPage != null && modulesPage.keyDown(e);
                }
                @Override public boolean keyUp(dev.skypocket.ui.input.KeyEvent e) {
                    return modulesPage != null && modulesPage.keyUp(e);
                }
                @Override public boolean textInput(String t) {
                    return modulesPage != null && modulesPage.textInput(t);
                }
                @Override public boolean preedit(dev.skypocket.ui.input.PreeditEvent e) {
                    return modulesPage != null && modulesPage.preedit(e);
                }
                @Override public boolean hasFocus() {
                    return modulesPage != null && modulesPage.hasFocus();
                }
                @Override public void clearFocus() {
                    if (modulesPage != null) {
                        modulesPage.clearFocus();
                    }
                }
                @Override public boolean hasPointerCapture() {
                    return modulesPage != null && modulesPage.hasPointerCapture();
                }
            });

    /**
     * Layers the host dispatches over, lowest first.
     *
     * <p>The top bar and the homepage so far. The remaining panes are still routed by the
     * hand-written handlers, which is deliberate: this migration is done one pane at a time so a
     * regression can be attributed to a pane instead of to a 400-line commit.
     */
    /**
     * The module page's chrome: the sidebar and the strip above the settings column.
     *
     * <p>Its interactive area is an L, not a rectangle - everything in the content column belongs to
     * the settings page instead - which is why it overrides the hit test rather than relying on the
     * bounding box. {@code inModuleChrome} already expressed exactly this shape; the adapter reuses
     * it rather than restating the arithmetic.
     */
    private final PaneLayer moduleChromeLayer = new PaneLayer(
            () -> new dev.skypocket.ui.Rect(0f, TAB_BAR_H, PanelGeometry.PANEL_W,
                    contentBottom() - TAB_BAR_H),
            this::moduleChromeActive,
            new PaneLayer.Pane() {
                @Override public void paint(dev.skypocket.ui.UiPainter painter) {
                    // Drawing still goes through the existing path; see paintHost.
                }
                @Override public float scale() { return 1f; }
                @Override public boolean pointerDown(float x, float y, int b, int m) {
                    ensureModuleChrome();
                    return moduleChrome != null && moduleChrome.pointerDown(x, y, b, m);
                }
                @Override public boolean pointerMove(float x, float y, int m) {
                    return moduleChrome != null && moduleChrome.pointerMove(x, y, m);
                }
                @Override public boolean pointerUp(float x, float y, int b, int m) {
                    return moduleChrome != null && moduleChrome.pointerUp(x, y, b, m);
                }
                @Override public boolean wheel(float x, float y, float d, int m) {
                    return moduleChrome != null && moduleChrome.wheel(x, y, d, m);
                }
                @Override public boolean keyDown(dev.skypocket.ui.input.KeyEvent e) {
                    return moduleChrome != null && moduleChrome.keyDown(e);
                }
                @Override public boolean keyUp(dev.skypocket.ui.input.KeyEvent e) {
                    return moduleChrome != null && moduleChrome.keyUp(e);
                }
                @Override public boolean textInput(String t) {
                    return moduleChrome != null && moduleChrome.textInput(t);
                }
                @Override public boolean preedit(dev.skypocket.ui.input.PreeditEvent e) {
                    return moduleChrome != null && moduleChrome.preedit(e);
                }
                @Override public boolean hasFocus() {
                    return moduleChrome != null && moduleChrome.hasFocus();
                }
                @Override public void clearFocus() {
                    if (moduleChrome != null) {
                        moduleChrome.clearFocus();
                    }
                }
                @Override public boolean hasPointerCapture() {
                    return moduleChrome != null && moduleChrome.hasPointerCapture();
                }
            },
            // The L: the sidebar column, plus the strip above the settings page. Same test the
            // hand-written dispatcher used, expressed against design-space coordinates.
            (x, y) -> moduleChromeActive()
                    && y >= TAB_BAR_H && y < contentBottom()
                    && (x < PanelGeometry.SIDEBAR_W || y < statsPageTop()));

    /**
     * The settings column, as a layer.
     *
     * <p>Two details make this one different from the panes above.
     *
     * <p>Its bounds are the <em>device-snapped</em> origin, not the design one. The page is blitted
     * from a cache whose texture size is snapped to whole GUI-scaled units, so it is drawn at
     * {@code statsPageDrawLeft/Top} rather than at {@code statsPageLeft/Top}; hit testing has to use
     * the same number or the pointer is off by up to one device pixel from the pixels it is aiming
     * at. This is exactly what {@code statsLocalX/Y} did by hand.
     *
     * <p>Its scale is the page's own, which is 1 unless the page was built smaller than its column.
     */
    private final PaneLayer statsLayer = new PaneLayer(
            () -> new dev.skypocket.ui.Rect(statsPageDrawLeft(), statsPageDrawTop(),
                    statsPageWidth(), Math.max(1f, contentBottom() - statsPageTop())),
            this::statsPageActive,
            new PaneLayer.Pane() {
                @Override public void paint(dev.skypocket.ui.UiPainter painter) {
                    // Drawing still goes through the existing path; see paintHost.
                }
                @Override public float scale() {
                    return statsPage == null ? 1f : statsPage.inputScaleX();
                }
                @Override public boolean pointerDown(float x, float y, int b, int m) {
                    SettingsPage p = statsPageOrNull();
                    return p != null && p.pointerDown(x, y, b, m);
                }
                @Override public boolean pointerMove(float x, float y, int m) {
                    SettingsPage p = statsPageOrNull();
                    return p != null && p.pointerMove(x, y, m);
                }
                @Override public boolean pointerUp(float x, float y, int b, int m) {
                    SettingsPage p = statsPageOrNull();
                    return p != null && p.pointerUp(x, y, b, m);
                }
                @Override public boolean wheel(float x, float y, float d, int m) {
                    SettingsPage p = statsPageOrNull();
                    return p != null && p.wheel(x, y, d, m);
                }
                @Override public boolean keyDown(dev.skypocket.ui.input.KeyEvent e) {
                    SettingsPage p = statsPageOrNull();
                    return p != null && p.keyDown(e);
                }
                @Override public boolean keyUp(dev.skypocket.ui.input.KeyEvent e) {
                    SettingsPage p = statsPageOrNull();
                    return p != null && p.keyUp(e);
                }
                @Override public boolean textInput(String t) {
                    SettingsPage p = statsPageOrNull();
                    return p != null && p.textInput(t);
                }
                @Override public boolean preedit(dev.skypocket.ui.input.PreeditEvent e) {
                    SettingsPage p = statsPageOrNull();
                    return p != null && p.preedit(e);
                }
                @Override public boolean hasFocus() {
                    SettingsPage p = statsPageOrNull();
                    return p != null && p.hasFocus();
                }
                @Override public void clearFocus() {
                    if (statsPage != null) {
                        statsPage.clearFocus();
                    }
                }
                @Override public boolean hasPointerCapture() {
                    SettingsPage p = statsPageOrNull();
                    return p != null && p.hasPointerCapture();
                }
            });

    @Override
    protected java.util.List<dev.skypocket.ui.host.Layer> layers() {
        // Lowest first. The top bar is above both pages; the settings column sits to the right of
        // the chrome, so their hit areas do not overlap except on the boundary row.
        return java.util.List.of(modulesLayer, moduleChromeLayer, statsLayer, topBarLayer);
    }

    /**
     * Screen to panel design space: inverts the open/close animation, the panel offset and the panel
     * scale, in that order. The animation is why this cannot live in a layer - it is a property of
     * the whole panel, not of anything inside it.
     */
    @Override
    protected float toDesignX(double screenX) {
        return (float) localX(screenX);
    }

    @Override
    protected float toDesignY(double screenY) {
        return (float) localY(screenY);
    }

    /** Sizes the panel and lays out its panes. Idempotent; called before every event and frame. */
    @Override
    protected void ensureLaidOut() {
        layout();
    }

    @Override
    protected void paintHost(GuiGraphicsExtractor graphics, float partialTick) {
        // The pointer is read from the game rather than plumbed through the host contract: the
        // panel's hover state is resolved here, and UiHost has no reason to know about it.
        double mxs = minecraft == null ? 0 : minecraft.mouseHandler.xpos();
        double mys = minecraft == null ? 0 : minecraft.mouseHandler.ypos();
        double gs = minecraft == null ? 1 : minecraft.getWindow().getGuiScale();
        paintPanel(graphics, mxs / gs, mys / gs, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (closing) {
            return false;
        }
        // The drawer is not a layer yet, and it floats above every layer that is. It has to be asked
        // before the host, or a press inside it lands on the page underneath: the host dispatches
        // top-down over layers only, and the drawer is not in that list.
        if (devDrawer.mouseClicked(event.x(), event.y(), width, height, shiftDown, ctrlDown)) {
            return true;
        }
        if (super.mouseClicked(event, doubled)) {
            return true;
        }
        layout();
        double lx = localX(event.x());
        double ly = localY(event.y());

        if (!dev.skypocket.util.MouseButtons.isLeft(event.button())) {
            return super.mouseClicked(new MouseButtonEvent(lx, ly, event.buttonInfo()), doubled);
        }

        // The top bar is a layer now; the host gave it the press above.

        // The homepage used to be handled here. It is a layer now, so the host above already gave it
        // the press if it was under the pointer.

        // The module chrome and the settings column are layers now; the host routed the press to
        // whichever of them owned the point.

        // Shell chrome is only the footer's two buttons now.
        PanelInput.Hit hit = PanelInput.hit(lx, ly);
        switch (hit.kind()) {
            case FOOTER_BUTTON -> {
                Minecraft.getInstance().gui.setScreen(new HudPositionEditorScreen(this));
                return true;
            }
            case FOOTER_SETTINGS -> {
                showSettings();
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
        // The settings column is a layer; the host routed the press to it.
        return super.mouseClicked(new MouseButtonEvent(lx, ly, event.buttonInfo()), doubled);
    }



    private void showModules() {
        if (viewingFeature == null) {
            return;
        }
        if (modulesPage != null) {
            modulesPage.clearHover();
        }
        // Goes through the same history as any other navigation, so Back returns to whatever was
        // open rather than always landing on the grid.
        backStack.push(currentId());
        forwardStack.clear();
        applyNavigation("");
    }

    private void showSettings() {
        if (settingsTabActive()) {
            return;
        }
        if (modulesPage != null) {
            modulesPage.clearHover();
        }
        backStack.push(currentId());
        forwardStack.clear();
        applyNavigation(PANEL_SETTINGS_ID);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        // Layers already migrated are dispatched by the host; the panes not yet migrated are
        // still routed by hand below. The host runs first: the top bar sits above the content area.
        if (super.mouseDragged(event, deltaX, deltaY)) {
            return true;
        }
        if (devDrawer.mouseDragged(event.x(), event.y(), width, height)) {
            return true;
        }
        layout();
        double lx = localX(event.x());
        double ly = localY(event.y());
        // The top bar is a layer; the host already routed the drag.
        // The homepage is a layer; the host already routed the move.
        // The module chrome is a layer too, and it is the one that captures a drag that started on
        // its sidebar; the host asked it before reaching here.
        // The settings column is a layer; the host routed the drag.
        float anim = animScale();
        return super.mouseDragged(new MouseButtonEvent(lx, ly, event.buttonInfo()),
                deltaX / (scaleX * anim), deltaY / (scaleY * anim));
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (super.mouseReleased(event)) {
            return true;
        }
        devDrawer.mouseReleased();
        layout();
        double lx = localX(event.x());
        double ly = localY(event.y());
        // The top bar is a layer; the host already routed the release.
        // The homepage is a layer; the host already routed the release.
        // The module chrome is a layer; it released its own capture.
        // The settings column is a layer; the host routed the release.
        return super.mouseReleased(new MouseButtonEvent(lx, ly, event.buttonInfo()));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
            return true;
        }
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
        // The homepage is a layer; the host already routed the wheel.
        // The module chrome is a layer; the host already offered it the wheel.
        // The settings column is a layer; the host offered it the wheel.
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
        super.mouseMoved(mouseX, mouseY);
        // Every page is a layer and the host gave the move to whichever one is under the pointer.
        // The drawer is the last holdout, and it floats above everything, so it gets the move after
        // the host - and swallows it while open.
        if (devDrawer.isOpen()) {
            devDrawer.mouseMoved(mouseX, mouseY, width, height);
        }
    }


    @Override
    public boolean keyReleased(KeyEvent event) {
        if (super.keyReleased(event)) {
            return true;
        }
        if (devDrawer.keyReleased(event)) {
            return true;
        }
        int key = event.key();
        if (InputKeys.isShift(event)) {
            shiftDown = false;
        }
        if (InputKeys.isControl(event)) {
            ctrlDown = false;
        }
        // The module chrome is a layer; the host already offered it the key.
        // The settings column is a layer; the host offered it the key.
        return super.keyReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (super.keyPressed(event)) {
            return true;
        }
        if (closing) {
            return true;
        }
        int key = event.key();
        if (InputKeys.isShift(event)) {
            shiftDown = true;
        }
        if (InputKeys.isControl(event)) {
            ctrlDown = true;
        }
        if (SkyPocketMod.getClickGuiKey() != null && SkyPocketMod.getClickGuiKey().matches(event)) {
            onClose();
            return true;
        }
        // The drawer owns its picker's fields; ESC closes the drawer before it closes the screen.
        if (devDrawer.keyPressed(event)) {
            return true;
        }
        if (devDrawer.isOpen() && InputKeys.isEscape(event)) {
            devDrawer.close();
            return true;
        }
        // Every pane is a layer now, and the host has already offered the key to the one that owns
        // focus - and closed the screen on ESC when none did. What is left is the screen's own key
        // handling: the shortcut that opens this GUI, which no pane should see.
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (super.charTyped(event)) {
            return true;
        }
        if (closing) {
            return false;
        }
        // The drawer is not a layer yet, so it still gets typed characters here. Every page moved to
        // the host, which is why the old "append to search when nothing else wanted it" fallback is
        // gone: the search box is a control in the top bar layer and takes its own input.
        if (event.isAllowedChatCharacter()) {
            String typed = event.codepointAsString();
            if (!typed.isEmpty() && devDrawer.charTyped(typed.charAt(0))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean preeditUpdated(net.minecraft.client.input.PreeditEvent event) {
        if (super.preeditUpdated(event)) {
            return true;
        }
        // Only the drawer is left outside the host; every page takes its own composition now.
        return devDrawer.preedit(event == null ? "" : event.fullText());
    }

    @Override
    public void onClose() {
        if (closing) {
            return;
        }
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
        modulesPage = null;
        modulesPageHeight = -1f;
        if (statsPage != null) {
            statsPage.rebuild();
        }
        layout();
        rebuildSettingsWidgets();
    }

    /** Re-lays out and rebuilds widgets after an inline setting changed the layout (e.g. color editor expanded). */
    public void rebuildAfterSettingChange() {
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
        int left = scissorFloor(x0);
        int top = scissorFloor(y0);
        int right = scissorFloor(x1);
        int bottom = contentBottomRow();
        if (right > left && bottom > top) {
            g.enableScissor(left, top, right, bottom);
        }
        popDeviceSpace(g);
    }

}
