package dev.skypocket.gui;

import dev.skypocket.SkyPocketMod;
import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;
import dev.skypocket.gui.setting.Setting;
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

    private int scrollY;
    private boolean settingsTab;
    private String search = "";
    private Category selected = Category.RENDER;
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
        float target = cfg.clickGuiScale * ClickGuiFonts.SCALE_BASE / guiScale;
        // The panel is a fixed 700x420 design surface. In a small window it must shrink to fit,
        // otherwise its right/bottom edge (and the content scissor derived from it) can leave the
        // drawable area. Keep the configured scale as the upper bound.
        float fitX = Math.max(0.05f, (width - 8f) / PANEL_W);
        float fitY = Math.max(0.05f, (height - 8f) / PANEL_H);
        target = Math.max(0.05f, Math.min(target, Math.min(fitX, fitY)));
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

    private boolean statsPageActive() {
        // Every module with settings now uses the v2 page; empty modules cannot be opened.
        return settingsTab || (viewingFeature != null && viewingFeature.hasSettings());
    }

    private boolean modulesPageActive() {
        return !settingsTab && viewingFeature == null;
    }

    private boolean moduleChromeActive() {
        return !settingsTab && viewingFeature != null && transitionProgress() >= 1f;
    }

    private final ModulesPage.Host modulesHost = new ModulesPage.Host() {
        @Override public String search() { return search; }
        @Override public void setSearch(String value) { search = value; scrollY = 0; }
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

    private void ensureModulesPage() {
        if (!modulesPageActive()) {
            return;
        }
        float pageHeight = Math.max(20f, contentBottom() - TAB_BAR_H);
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

    private boolean inModuleChrome(double lx, double ly) {
        return moduleChromeActive() && ly >= TAB_BAR_H && ly < contentBottom()
                && (lx < SIDEBAR_W || ly < statsPageTop());
    }

    private void ensureModuleChrome() {
        if (!moduleChromeActive()) {
            return;
        }
        float pageHeight = Math.max(20f, contentBottom() - TAB_BAR_H);
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
        return contentTop() + (settingsTab ? 8f : 18f);
    }

    private float statsPageWidth() {
        // Main settings has no sidebar/search/back column, so it gets the full panel width. Module
        // pages still live beside the sidebar.
        return settingsTab ? PANEL_W - 32f : contentWidth();
    }

    private float statsPageLeft() {
        return settingsTab ? 16f : SIDEBAR_W;
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
        Feature target = settingsTab ? panelFeature : viewingFeature;
        if (target == null) {
            return;
        }
        float height = Math.max(20f, contentBottom() - statsPageTop());
        if (statsPage == null || statsPageFeature != target || statsPageHeight != height) {
            float pageWidth = statsPageWidth();
            float columnWidth = settingsTab
                    ? pageWidth - 30f
                    : Math.min(520f, pageWidth - 30f);
            statsPage = new SettingsPage(target, Ui2Theme.palette(),
                    pageWidth, height, columnWidth);
            statsPageFeature = target;
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
                        // Developer-only switching lives under an explicit card description below.

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
                        })
                ), false).subtitle("字体与渲染调试选项")
        );
    }

    private void rebuildSettingsWidgets() {
        clearWidgets();
        ensureStatsPage();
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
        devDrawer.draw(graphics, mouseX, mouseY, width, height);
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
            ClickGuiFonts.drawText(graphics, feature.name(), SIDEBAR_W + 20,
                    contentTop() - paneScroll + 2,
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
        return new PanelView(false, feature, selected, paneScroll, search, ModuleRegistry.features());
    }
    /** Browser-style history for the card pane: "" is the grid, otherwise a module id. */
    private final java.util.ArrayDeque<String> backStack = new java.util.ArrayDeque<>();
    private final java.util.ArrayDeque<String> forwardStack = new java.util.ArrayDeque<>();
    /** Module the settings tab was opened from, re-inserted when returning to the module grid. */
    private String settingsReturnId = "";

    /** Opens a module (or the grid when {@code id} is null), remembering where we came from. */
    private void navigate(String id) {
        String target = id == null ? "" : id;
        if (target.equals(currentId())) {
            return;
        }
        if (!target.isEmpty()) {
            Feature feature = ModuleRegistry.byId(target);
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
        Feature next = id == null || id.isEmpty() ? null : ModuleRegistry.byId(id);
        if (next != null && !next.hasSettings()) {
            return;
        }
        if (modulesPage != null) {
            modulesPage.clearHover();
        }
        settingsReturnId = "";
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
        graphics.nextStratum();
        GuiPanelCache.PanelContent shellContent =
                (shellGraphics, shellMouseX, shellMouseY, shellDelta) -> {
                    PanelRenderer.drawTabs(shellGraphics, view(), shellMouseX, shellMouseY);
                    PanelRenderer.drawFooter(shellGraphics, shellMouseX, shellMouseY);
                };
        if (!shellCache.draw(graphics, shellCacheKey(lmx, lmy, scaleX, scaleY),
                PANEL_W, PANEL_H, scaleX, scaleY, !animFinished(), shellContent, lmx, lmy, 0f)) {
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
            scissorContentArea(graphics, settingsTab ? 0 : SIDEBAR_W, top, PANEL_W);
            if (!settingsTab) {
                dev.skypocket.ui.mc.McUiPainter titlePainter =
                        new dev.skypocket.ui.mc.McUiPainter(graphics);
                titlePainter.text(viewingFeature.name(), SIDEBAR_W + 20,
                        top - scrollY + 2, dev.skypocket.gui.widget.ClickGuiStyle.text());
            }
            ensureStatsPage();
            statsPage.layout();
            Matrix3x2fStack pagePose = graphics.pose();
            pagePose.pushMatrix();
            pagePose.translate(statsPageDrawLeft(), statsPageDrawTop());
            statsPage.draw(graphics, scaleX, scaleY, false);
            pagePose.popMatrix();
            graphics.disableScissor();
        }

        // Title sits above the cached shell; the footer is part of the shell slot.
        ClickGuiFonts.drawTextScaled(graphics, "SkyPocket", 16, 13,
                dev.skypocket.gui.widget.ClickGuiStyle.text(), true, ClickGuiFonts.HEADING_SCALE);
    }

    private long shellCacheKey(int lmx, int lmy, float scaleX, float scaleY) {
        long key = System.identityHashCode(this);
        key = key * 31 + (settingsTab ? 1 : 0);
        key = key * 31 + ConfigManager.revision();
        key = key * 31 + Math.round(ClickGuiFonts.deviceFactor() * 1000f);
        key = key * 31 + Math.round(scaleX * 1000f);
        key = key * 31 + Math.round(scaleY * 1000f);
        int hover = 0;
        if (lmy >= PanelGeometry.TAB_Y && lmy <= PanelGeometry.TAB_Y + PanelGeometry.TAB_H) {
            if (lmx >= PanelGeometry.tabX() && lmx <= PanelGeometry.tabX() + PanelGeometry.TAB_W) {
                hover = 1;
            } else if (lmx >= PanelGeometry.tab2X()
                    && lmx <= PanelGeometry.tab2X() + PanelGeometry.TAB_W) {
                hover = 2;
            }
        }
        int footerY = PanelGeometry.footerBtnY();
        if (lmx >= PanelGeometry.FOOTER_BTN_X
                && lmx <= PanelGeometry.FOOTER_BTN_X + PanelGeometry.FOOTER_BTN_W
                && lmy >= footerY && lmy <= footerY + PanelGeometry.FOOTER_BTN_H) {
            hover = 3;
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

        if (!dev.skypocket.gui.widget.MouseButtons.isLeft(event.button())) {
            return super.mouseClicked(new MouseButtonEvent(lx, ly, event.buttonInfo()), doubled);
        }

        // The homepage owns everything between the tab bar and the footer.
        if (modulesPageActive() && ly >= TAB_BAR_H && ly < contentBottom()) {
            ensureModulesPage();
            modulesPage.layout();
            modulesPage.pointerDown((float) lx, (float) ly - TAB_BAR_H,
                    shiftDown ? Modifiers.SHIFT : 0);
            return true;
        }

        if (inModuleChrome(lx, ly)) {
            ensureModuleChrome();
            moduleChrome.layout();
            moduleChrome.pointerDown((float) lx, (float) ly - TAB_BAR_H,
                    shiftDown ? Modifiers.SHIFT : 0);
            return true;
        }

        // Shell chrome is only the main tabs and the footer button now.
        PanelInput.Hit hit = PanelInput.hit(lx, ly);
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
        return super.mouseClicked(new MouseButtonEvent(lx, ly, event.buttonInfo()), doubled);
    }



    private void showModules() {
        String returnId = settingsTab ? settingsReturnId : "";
        boolean changed = settingsTab || viewingFeature != null;
        if (modulesPage != null) {
            modulesPage.clearHover();
        }
        settingsTab = false;
        viewingFeature = null;
        settingsReturnId = "";
        if (changed) {
            if (!returnId.isEmpty()) {
                // Returning from the settings tab should keep the module page it replaced in
                // history, so Back goes to that module rather than the empty grid.
                backStack.push(returnId);
                forwardStack.clear();
            }
            scrollY = 0;
            rebuildSettingsWidgets();
        }
    }

    private void showSettings() {
        if (settingsTab) {
            return;
        }
        if (modulesPage != null) {
            modulesPage.clearHover();
        }
        settingsReturnId = currentId();
        settingsTab = true;
        viewingFeature = null;
        scrollY = 0;
        rebuildSettingsWidgets();
        ensureStatsPage();
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (devDrawer.mouseDragged(event.x(), event.y(), width, height)) {
            return true;
        }
        layout();
        double lx = localX(event.x());
        double ly = localY(event.y());
        if (modulesPageActive()) {
            ensureModulesPage();
            modulesPage.layout();
            modulesPage.pointerMove((float) lx, (float) ly - TAB_BAR_H,
                    shiftDown ? Modifiers.SHIFT : 0);
            return true;
        }
        if (moduleChromeActive() && moduleChrome != null && moduleChrome.hasPointerCapture()) {
            ensureModuleChrome();
            moduleChrome.layout();
            moduleChrome.pointerMove((float) lx, (float) ly - TAB_BAR_H,
                    shiftDown ? Modifiers.SHIFT : 0);
            return true;
        }
        if (statsPageActive()) {
            ensureStatsPage();
            statsPage.layout();
            statsPage.pointerMove(statsLocalX(lx), statsLocalY(ly), shiftDown ? Modifiers.SHIFT : 0);
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
        if (modulesPageActive()) {
            ensureModulesPage();
            modulesPage.layout();
            modulesPage.pointerUp((float) lx, (float) ly - TAB_BAR_H,
                    shiftDown ? Modifiers.SHIFT : 0);
            return true;
        }
        if (moduleChromeActive() && moduleChrome != null && moduleChrome.hasPointerCapture()) {
            ensureModuleChrome();
            moduleChrome.layout();
            moduleChrome.pointerUp((float) lx, (float) ly - TAB_BAR_H,
                    shiftDown ? Modifiers.SHIFT : 0);
            return true;
        }
        if (statsPageActive()) {
            ensureStatsPage();
            statsPage.layout();
            statsPage.pointerUp(statsLocalX(lx), statsLocalY(ly),
                    Math.max(0, event.button()), shiftDown ? Modifiers.SHIFT : 0);
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
        if (modulesPageActive()) {
            ensureModulesPage();
            modulesPage.layout();
            modulesPage.wheel((float) lx, (float) ly - TAB_BAR_H, (float) verticalAmount,
                    shiftDown ? Modifiers.SHIFT : 0);
            return true;
        }
        if (moduleChromeActive() && inModuleChrome(lx, ly)) {
            ensureModuleChrome();
            moduleChrome.layout();
            moduleChrome.wheel((float) lx, (float) ly - TAB_BAR_H, (float) verticalAmount,
                    shiftDown ? Modifiers.SHIFT : 0);
            return true;
        }
        if (statsPageActive()) {
            ensureStatsPage();
            statsPage.layout();
            statsPage.wheel(statsLocalX(lx), statsLocalY(ly), (float) verticalAmount,
                    shiftDown ? Modifiers.SHIFT : 0);
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
        if (modulesPageActive() || moduleChromeActive() || statsPageActive()) {
            layout();
            double lx = localX(mouseX);
            double ly = localY(mouseY);
            if (modulesPageActive()) {
                ensureModulesPage();
                modulesPage.layout();
                modulesPage.pointerMove((float) lx, (float) ly - TAB_BAR_H,
                        shiftDown ? Modifiers.SHIFT : 0);
            } else if (moduleChromeActive()) {
                ensureModuleChrome();
                moduleChrome.layout();
                moduleChrome.pointerMove((float) lx, (float) ly - TAB_BAR_H,
                        shiftDown ? Modifiers.SHIFT : 0);
                if (!inModuleChrome(lx, ly)) {
                    ensureStatsPage();
                    statsPage.layout();
                    statsPage.pointerMove(statsLocalX(lx), statsLocalY(ly),
                            shiftDown ? Modifiers.SHIFT : 0);
                }
            } else if (inBody(lx, ly)) {
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
        if (modulesPageActive()) {
            ensureModulesPage();
            modulesPage.keyUp(new dev.skypocket.ui.input.KeyEvent(canonicalKey(event), 0,
                    pageModifiers(event), false));
            return true;
        }
        if (moduleChromeActive() && moduleChrome != null && moduleChrome.hasFocus()) {
            ensureModuleChrome();
            moduleChrome.keyUp(new dev.skypocket.ui.input.KeyEvent(canonicalKey(event), 0,
                    pageModifiers(event), false));
            return true;
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
        if (modulesPageActive()) {
            ensureModulesPage();
            modulesPage.layout();
            if (event.isEscape()) {
                if (modulesPage.hasFocus()) {
                    modulesPage.clearFocus();
                } else {
                    onClose();
                }
                return true;
            }
            if (modulesPage.keyDown(new dev.skypocket.ui.input.KeyEvent(canonicalKey(event), 0,
                    pageModifiers(event), true))) {
                return true;
            }
            return super.keyPressed(event);
        }
        if (moduleChromeActive() && moduleChrome != null && moduleChrome.hasFocus()) {
            ensureModuleChrome();
            moduleChrome.layout();
            if (event.isEscape()) {
                moduleChrome.clearFocus();
                return true;
            }
            moduleChrome.keyDown(new dev.skypocket.ui.input.KeyEvent(canonicalKey(event), 0,
                    pageModifiers(event), true));
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
            if (modulesPageActive()) {
                ensureModulesPage();
                if (modulesPage.hasFocus()) {
                    modulesPage.layout();
                    modulesPage.textInput(typed);
                    return true;
                }
                return false;
            }
            if (moduleChromeActive() && moduleChrome != null && moduleChrome.hasFocus()) {
                ensureModuleChrome();
                moduleChrome.layout();
                moduleChrome.textInput(typed);
                return true;
            }
            if (statsPageActive()) {
                ensureStatsPage();
                statsPage.layout();
                statsPage.textInput(typed);
                return true;
            }
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
        if (modulesPageActive()) {
            ensureModulesPage();
            modulesPage.layout();
            modulesPage.preedit(new dev.skypocket.ui.input.PreeditEvent(
                    event == null ? "" : event.fullText(), event == null ? 0 : event.caretPosition()));
            return true;
        }
        if (moduleChromeActive() && moduleChrome != null && moduleChrome.hasFocus()) {
            ensureModuleChrome();
            moduleChrome.layout();
            moduleChrome.preedit(new dev.skypocket.ui.input.PreeditEvent(
                    event == null ? "" : event.fullText(), event == null ? 0 : event.caretPosition()));
            return true;
        }
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
