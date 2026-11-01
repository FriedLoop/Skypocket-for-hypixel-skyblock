package dev.skypocket.gui;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;

import dev.skypocket.hud.ClockHudElement;
import dev.skypocket.hud.CommissionHudElement;
import dev.skypocket.hud.DayCountHudElement;
import dev.skypocket.hud.FpsTpsPingHudElement;
import dev.skypocket.hud.HeldItemNameHudElement;
import dev.skypocket.hud.MiningAbilityHudElement;
import dev.skypocket.hud.StatTextHudElement;
import dev.skypocket.hud.StatsBarHudElement;
import dev.skypocket.i18n.Localization;
import dev.skypocket.gui.widget.ClickGuiStyle;
import dev.skypocket.render.ClickGuiFonts;
import dev.skypocket.render.GuiPixels;
import dev.skypocket.render.HudText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ObjDoubleConsumer;
import java.util.function.ObjIntConsumer;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;

/**
 * Unified drag editor for the HUD elements (clock / world day / FPS-TPS-Ping / the stat rows and bars).
 *
 * <p>Every element is drawn at its real HUD position and can be selected by clicking it, dragged,
 * resized (bars: the edge handles; everything else: the wheel) and nudged with the arrow keys. The
 * controls live in a floating card that can be dragged out of the way, because the bottom of the screen
 * is exactly where HUD elements tend to be placed.
 */
public class HudPositionEditorScreen extends Screen {

    private enum Target {
        CLOCK, DAY_COUNT, FPS_TPS_PING, MINING_ABILITY, MINING_ABILITY_SUBTITLE, COMMISSION,
        /** Text rows: the classic readouts and the three values that never become icons. */
        STAT_HEALTH, STAT_MANA, STAT_DEFENSE, STAT_SPEED, STAT_VITALITY,
        /**
         * Vanilla's held item tooltip. Stored as an offset from its own vanilla baseline rather than as
         * a position (it belongs to the hotbar and must keep following it), so its box is recomputed on
         * demand - see {@link #heldItemNameBox}.
         */
        HELD_ITEM_NAME,
        /** The modern style's bars: one target each, so they can be placed individually. */
        BAR_HEALTH, BAR_MANA, BAR_DEFENSE, BAR_SPEED, BAR_VITALITY, BAR_AIR
    }

    /**
     * Read/write access to one target's stored position and scale.
     *
     * <p>This replaced six {@code switch} blocks (read and write, for x, y and scale). The writers were
     * the dangerous half: a {@code switch} <em>statement</em> silently does nothing for a constant it
     * does not list, which is how the bars once ended up selectable but immovable. With every target in
     * one table, adding an element is a single line here instead of six switch arms - and the compiler
     * insists that the entry is complete.
     */
    private record Spec(ToIntFunction<ModConfig> x, ObjIntConsumer<ModConfig> setX,
                        ToIntFunction<ModConfig> y, ObjIntConsumer<ModConfig> setY,
                        ToDoubleFunction<ModConfig> scale, ObjDoubleConsumer<ModConfig> setScale) {
    }

    /** One spec per target, filled once at class load. */
    private static final Map<Target, Spec> SPECS = new EnumMap<>(Target.class);

    static {
        put(Target.CLOCK, c -> c.clockX, (c, v) -> c.clockX = v,
                c -> c.clockY, (c, v) -> c.clockY = v,
                c -> c.clockScale, (c, v) -> c.clockScale = (float) v);
        put(Target.DAY_COUNT, c -> c.dayCountX, (c, v) -> c.dayCountX = v,
                c -> c.dayCountY, (c, v) -> c.dayCountY = v,
                c -> c.dayCountScale, (c, v) -> c.dayCountScale = (float) v);
        put(Target.FPS_TPS_PING, c -> c.fpsTpsPingX, (c, v) -> c.fpsTpsPingX = v,
                c -> c.fpsTpsPingY, (c, v) -> c.fpsTpsPingY = v,
                c -> c.fpsTpsPingScale, (c, v) -> c.fpsTpsPingScale = (float) v);
        put(Target.MINING_ABILITY, c -> c.miningAbilityX, (c, v) -> c.miningAbilityX = v,
                c -> c.miningAbilityY, (c, v) -> c.miningAbilityY = v,
                c -> c.miningAbilityScale, (c, v) -> c.miningAbilityScale = (float) v);
        put(Target.MINING_ABILITY_SUBTITLE, c -> c.miningAbilitySubtitleX,
                (c, v) -> c.miningAbilitySubtitleX = v,
                c -> c.miningAbilitySubtitleY, (c, v) -> c.miningAbilitySubtitleY = v,
                c -> c.miningAbilitySubtitleScale, (c, v) -> c.miningAbilitySubtitleScale = (float) v);
        put(Target.COMMISSION, c -> c.commissionsX, (c, v) -> c.commissionsX = v,
                c -> c.commissionsY, (c, v) -> c.commissionsY = v,
                c -> c.commissionsScale, (c, v) -> c.commissionsScale = (float) v);
        // Stat rows delegate to the shared stat store, exactly like the bars: the field names live in
        // one table (StatTextHudElement), not repeated here.
        putStat(Target.STAT_DEFENSE, StatTextHudElement.Kind.DEFENSE);
        putStat(Target.STAT_SPEED, StatTextHudElement.Kind.SPEED);
        putStat(Target.STAT_VITALITY, StatTextHudElement.Kind.VITALITY);
        putStat(Target.STAT_HEALTH, StatTextHudElement.Kind.HEALTH);
        putStat(Target.STAT_MANA, StatTextHudElement.Kind.MANA);
        // Stored as an offset from the hotbar anchor: shift it by however far the requested position is
        // from where it currently lands, so the writer stays right no matter how the box is computed.
        put(Target.HELD_ITEM_NAME, c -> heldItemNameBox(c)[0],
                (c, v) -> c.heldItemNameOffsetX += v - heldItemNameBox(c)[0],
                c -> heldItemNameBox(c)[1],
                (c, v) -> c.heldItemNameOffsetY += v - heldItemNameBox(c)[1],
                HeldItemNameHudElement::scale, (c, v) -> c.heldItemNameScale = (float) v);
        putBar(Target.BAR_HEALTH, StatsBarHudElement.Kind.HEALTH);
        putBar(Target.BAR_MANA, StatsBarHudElement.Kind.MANA);
        putBar(Target.BAR_DEFENSE, StatsBarHudElement.Kind.DEFENSE);
        putBar(Target.BAR_SPEED, StatsBarHudElement.Kind.SPEED);
        putBar(Target.BAR_VITALITY, StatsBarHudElement.Kind.VITALITY);
        putBar(Target.BAR_AIR, StatsBarHudElement.Kind.AIR);
    }

    private static void put(Target t, ToIntFunction<ModConfig> x, ObjIntConsumer<ModConfig> sx,
                            ToIntFunction<ModConfig> y, ObjIntConsumer<ModConfig> sy,
                            ToDoubleFunction<ModConfig> scale, ObjDoubleConsumer<ModConfig> ss) {
        SPECS.put(t, new Spec(x, sx, y, sy, scale, ss));
    }

    /** Stat text rows delegate to the shared stat store, keyed by kind. */
    private static void putStat(Target t, StatTextHudElement.Kind kind) {
        put(t, c -> StatTextHudElement.x(c, kind), (c, v) -> StatTextHudElement.setX(c, kind, v),
                c -> StatTextHudElement.y(c, kind), (c, v) -> StatTextHudElement.setY(c, kind, v),
                c -> StatTextHudElement.scale(c, kind),
                (c, v) -> StatTextHudElement.setScale(c, kind, (float) v));
    }

    /** Bars all delegate to the shared bar store, keyed by kind. */
    private static void putBar(Target t, StatsBarHudElement.Kind kind) {
        put(t, c -> StatsBarHudElement.x(c, kind), (c, v) -> StatsBarHudElement.setX(c, kind, v),
                c -> StatsBarHudElement.y(c, kind), (c, v) -> StatsBarHudElement.setY(c, kind, v),
                c -> StatsBarHudElement.scale(c, kind),
                (c, v) -> StatsBarHudElement.setScale(c, kind, (float) v));
    }

    private final Screen parent;
    private Target selected = Target.CLOCK;
    /** Right-click settings card: the selected element's module settings (see {@link HudSettingsCard}). */
    private final HudSettingsCard settingsCard = new HudSettingsCard();
    private boolean dragging;
    /**
     * The floating panel: a draggable card that shows the current selection and the hints.
     *
     * <p>It replaced the row of controls pinned to the bottom of the screen. That row sat exactly where
     * HUD elements usually go, so placing anything near the bottom meant fighting the UI; a card the
     * player can drag out of the way solves it without hiding the information.
     */
    private int panelX;
    private int panelY;
    private boolean panelPlaced;
    private boolean panelDragging;
    private double panelGrabX;
    private double panelGrabY;
    /** The card sizes itself to its widest row, within these bounds. */
    private static final int PANEL_MIN_W = 180;
    private static final int PANEL_MAX_W = 360;
    private int panelW = 250;
    /** Height of the title strip - the part you grab to move the panel. */
    private static final int PANEL_TITLE_H = 16;
    private static final int PANEL_PAD = 8;
    private static final int PANEL_LINE_H = 11;
    /** Rows of text inside the panel: selection, position, size, two hint rows. */
    private static final int PANEL_ROWS = 5;
    /** Grid spacing in GUI pixels, and the colour of a normal line. */
    private static final int GRID = 20;
    private static final int GRID_COLOR = 0x14FFFFFF;
    /**
     * 0 = moving, 1 = dragging the right edge (width), 2 = the bottom edge (height), 3 = the corner.
     *
     * <p>Bars are the only targets with an independent width and height, so only they can be resized;
     * every other element's size is either its text's or a single scale factor.
     */
    private int resizeMode;
    private StatsBarHudElement.Kind resizeBar;
    private Target resizeTarget;
    private int resizeOriginX;
    private int resizeOriginY;
    private Target dragTarget;
    /**
     * How far outside a box a click may land and still select it (GUI px). Some elements are only a
     * few pixels tall - a bar is 6 by default - so an exact hit is not something to require.
     */
    private static final int GRAB_SLACK = 8;
    /** What the player sees: a small square, 3 px across. */
    private static final int HANDLE_DOT = 3;
    /** What the mouse tests against - larger than the dot, so a 3 px square is still easy to grab. */
    private static final int HANDLE_HIT = 7;

    /** The value may be larger than the bar (it rises above the top edge); this caps it. */
    private static final float VALUE_MAX_SCALE = 3.0f;

    /** What one wheel notch changes the scale by: the same grid the sliders snap to. */
    private static final float WHEEL_STEP = ModConfig.SCALE_GRID;
    /** Resize mode 4: dragging a non-bar element's corner scales it. */
    private static final int MODE_SCALE = 4;
    private float resizeStartScale;
    private int resizeStartW;
    private int resizeStartH;
    private double dragOffsetX;
    private double dragOffsetY;
    private static final dev.skypocket.SkyPocketLog LOGGER =
            dev.skypocket.SkyPocketLog.of("hudEditor");
    /** Targets already reported as unresponsive, so a drag logs once instead of once per frame. */
    private final Set<Target> ignored = EnumSet.noneOf(Target.class);

    /**
     * Per-frame measurement caches.
     *
     * <p>{@code bounds()} runs for every target and internally asks for that target's lines and text
     * width; drawing then asks for the same lines again. Without this every string is built twice per
     * target per frame. The caches only live while {@code extractRenderState} runs - the mouse handlers
     * measure live values, where a stale box would show as a frame that lags behind the cursor.
     */
    private final Map<Target, List<String>> linesCache = new EnumMap<>(Target.class);
    private final Map<Target, int[]> boundsCache = new EnumMap<>(Target.class);
    private boolean renderPass;

    public HudPositionEditorScreen(Screen parent) {
        super(Component.literal(Localization.tr("HUD 位置调整")));
        this.parent = parent;
    }

    @Override
    protected void init() {
        // No widgets any more. The bottom of the screen belongs to the elements being placed, and the
        // scale slider went with them (the per-element settings card will own those controls); ESC closes
        // the screen, which the floating panel says out loud.
        clampPanel();
        // A target without a spec would be a NullPointerException on the first click; say so instead.
        if (SPECS.size() != Target.values().length) {
            LOGGER.warn("HUD position editor: {} of {} targets have a spec - the missing one cannot be "
                    + "moved or resized", SPECS.size(), Target.values().length);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // --- per-target config access ---

    private int x(Target t, ModConfig cfg) {
        return SPECS.get(t).x().applyAsInt(cfg);
    }

    private void setX(Target t, ModConfig cfg, int v) {
        SPECS.get(t).setX().accept(cfg, v);
        verify(t, x(t, cfg) == v, "X");
    }

    private int y(Target t, ModConfig cfg) {
        return SPECS.get(t).y().applyAsInt(cfg);
    }

    private void setY(Target t, ModConfig cfg, int v) {
        SPECS.get(t).setY().accept(cfg, v);
        verify(t, y(t, cfg) == v, "Y");
    }

    private float scale(Target t, ModConfig cfg) {
        return (float) SPECS.get(t).scale().applyAsDouble(cfg);
    }

    private void setScale(Target t, ModConfig cfg, float v) {
        SPECS.get(t).setScale().accept(cfg, v);
        verify(t, Math.abs(scale(t, cfg) - v) < 0.001f, "scale");
    }

    /**
     * Reports a target that did not accept a new value.
     *
     * <p>This exists because of the bug it just caught: the readers above are exhaustive
     * {@code switch} <em>expressions</em> (the compiler makes them list every constant) while the
     * writers were {@code switch} <em>statements</em> (which silently do nothing for the cases they do
     * not list). Forgetting the {@code BAR_*} arms therefore produced a target that could be selected,
     * framed and read back correctly - dragging, the wheel and the size slider simply never reached
     * it, while the same values set from the settings screen worked. A whole "the editor cannot edit
     * these" report comes down to a switch statement's default of doing nothing; a read-after-write
     * check is cheap enough to keep it from happening again.
     */
    /**
     * Whether a target belongs on this screen at all: its feature is switched on.
     *
     * <p>Only the feature flags are consulted, not the runtime ones (SkyBlock active, data available,
     * which display style is selected). A player places an element before it has anything to show, and
     * hiding it then would make it impossible to position - so a disabled feature disappears here, while
     * an enabled one that happens to be idle stays visible.
     */
    private static boolean visible(Target t, ModConfig cfg) {
        StatsBarHudElement.Kind bar = StatsBarHudElement.kindOf(t.name());
        if (bar != null) {
            // The bars are the modern style's readout: the classic style never draws them.
            return cfg.statsModernStyle && StatsBarHudElement.enabled(cfg, bar);
        }
        return switch (t) {
            case CLOCK -> cfg.clockEnabled;
            case DAY_COUNT -> cfg.dayCountEnabled;
            case FPS_TPS_PING -> cfg.fpsTpsPingEnabled;
            // The subtitle shares the reminder's switch: it is that element's second line.
            case MINING_ABILITY, MINING_ABILITY_SUBTITLE -> cfg.miningAbilityReminderEnabled;
            case COMMISSION -> cfg.commissionsEnabled;
            case STAT_DEFENSE -> cfg.statsDefenseEnabled;
            case STAT_SPEED -> cfg.statsSpeedEnabled;
            case STAT_VITALITY -> cfg.statsVitalityEnabled;
            // Health and mana have two homes. The text rows are the classic readout and are suppressed
            // in the modern style (the bars print those two numbers themselves), so exactly one of the
            // pair is on the screen at a time - which is also what stops them overlapping here.
            case STAT_HEALTH -> !cfg.statsModernStyle && cfg.statsHealthTextEnabled;
            case STAT_MANA -> !cfg.statsModernStyle && cfg.statsManaTextEnabled;
            case HELD_ITEM_NAME -> cfg.heldItemNameEnabled;
            // Bars are handled above; anything new defaults to visible rather than disappearing silently.
            default -> true;
        };
    }

    /** The first target worth showing, for when the current selection has been switched off. */
    private static Target firstVisible(ModConfig cfg) {
        for (Target t : Target.values()) {
            if (visible(t, cfg)) {
                return t;
            }
        }
        return Target.CLOCK;
    }

    private void verify(Target t, boolean applied, String what) {
        if (!applied && ignored.add(t)) {
            LOGGER.warn("HUD position editor: {} accepted no new {} - the target has no writer", t, what);
        }
    }

    private List<String> lines(Target t, Minecraft mc, ModConfig cfg) {
        if (renderPass) {
            List<String> cached = linesCache.get(t);
            if (cached != null) {
                return cached;
            }
        }
        List<String> computed = computeLines(t, mc, cfg);
        if (renderPass) {
            linesCache.put(t, computed);
        }
        return computed;
    }

    private List<String> computeLines(Target t, Minecraft mc, ModConfig cfg) {
        return switch (t) {
            case CLOCK -> List.of(ClockHudElement.formatTime(cfg));
            case DAY_COUNT -> List.of(DayCountHudElement.format(cfg));
            case FPS_TPS_PING -> List.of(FpsTpsPingHudElement.layoutLines(mc, cfg));
            case MINING_ABILITY -> MiningAbilityHudElement.previewLines();
            case MINING_ABILITY_SUBTITLE -> MiningAbilityHudElement.subtitlePreviewLines();
            case COMMISSION -> CommissionHudElement.previewLines();
            case STAT_DEFENSE -> List.of(statLine(StatTextHudElement.Kind.DEFENSE, cfg));
            case STAT_SPEED -> List.of(statLine(StatTextHudElement.Kind.SPEED, cfg));
            case STAT_VITALITY -> List.of(statLine(StatTextHudElement.Kind.VITALITY, cfg));
            // Bars have no text lines: their box comes from the bar's own size (see bounds).
            case STAT_HEALTH -> List.of(statLine(StatTextHudElement.Kind.HEALTH, cfg));
            case STAT_MANA -> List.of(statLine(StatTextHudElement.Kind.MANA, cfg));
            case HELD_ITEM_NAME -> List.of(
                    HeldItemNameHudElement.previewName(mc, Localization.tr("物品名")));
            case BAR_HEALTH, BAR_MANA, BAR_DEFENSE, BAR_SPEED, BAR_VITALITY, BAR_AIR -> List.of();
        };
    }

    /** The stats rows show live values; an empty one still needs something to size the box with. */
    private static String statLine(StatTextHudElement.Kind kind, ModConfig cfg) {
        String line = StatTextHudElement.line(kind, cfg);
        return line == null ? Localization.tr("（暂无数据）") : line;
    }

    private int lineHeight(Target t, Minecraft mc, ModConfig cfg) {
        return switch (t) {
            case FPS_TPS_PING -> FpsTpsPingHudElement.lineHeight(mc, cfg);
            case COMMISSION -> CommissionHudElement.lineHeight(mc);
            default -> HudText.lineHeight(cfg);
        };
    }

    /** Fills {@code out} with {x, y, width, height} of the target's HUD bounds. */
    private void bounds(Target t, Minecraft mc, ModConfig cfg, int[] out) {
        if (renderPass) {
            int[] cached = boundsCache.get(t);
            if (cached != null) {
                System.arraycopy(cached, 0, out, 0, 4);
                return;
            }
        }
        computeBounds(t, mc, cfg, out);
        if (renderPass) {
            boundsCache.put(t, out.clone());
        }
    }

    private void computeBounds(Target t, Minecraft mc, ModConfig cfg, int[] out) {
        StatsBarHudElement.Kind bar = StatsBarHudElement.kindOf(t.name());
        if (bar != null) {
            // A bar is a rectangle rather than a run of text: its box is the bar's own size, scaled.
            float s = StatsBarHudElement.scale(cfg, bar);
            // The icon sits to the left of the bar and is part of the element's box, so the selection
            // frame and the hit test cover it; the stored x stays the bar's own left edge.
            int icon = Math.round(StatsBarHudElement.iconWidth(cfg, bar) * s);
            out[0] = StatsBarHudElement.x(cfg, bar) - icon;
            out[1] = StatsBarHudElement.y(cfg, bar);
            out[2] = icon + Math.round(StatsBarHudElement.width(cfg, bar) * s);
            out[3] = Math.round(StatsBarHudElement.height(cfg, bar) * s);
            return;
        }
        if (t == Target.HELD_ITEM_NAME) {
            // The one target whose box is not a stored position: see heldItemNameBox.
            System.arraycopy(heldItemNameBox(cfg), 0, out, 0, 4);
            return;
        }
        List<String> ls = lines(t, mc, cfg);
        float s = scale(t, cfg);
        int w = 0;
        for (String line : ls) {
            // Plain metrics, measured with the font the HUD draws in (see HudText).
            w = Math.max(w, (int) Math.ceil(HudText.width(cfg, line) * s));
        }
        int pitch = lineHeight(t, mc, cfg);
        out[0] = x(t, cfg);
        out[1] = y(t, cfg);
        out[2] = w;
        out[3] = (int) Math.ceil(pitch * ls.size() * s);
    }

    /**
     * The held item name's box, which is the one target not backed by a stored position.
     *
     * <p>Vanilla anchors it at the screen's horizontal centre and 59 px above the bottom, and it has to
     * keep following the hotbar, so what is stored is an offset from that baseline. The box therefore
     * has to be recomputed here from the live GUI size and the name currently in hand - and every
     * reader and writer for this target goes through this one method, so the drag maths and the drawn
     * frame can never disagree.
     */
    private static int[] heldItemNameBox(ModConfig cfg) {
        Minecraft mc = Minecraft.getInstance();
        int[] out = new int[4];
        String name = HeldItemNameHudElement.previewName(mc, Localization.tr("物品名"));
        // The game's own font, not HudText's: this element is drawn by vanilla (we only wrap its pose),
        // so its frame has to be measured with the font vanilla draws it in.
        HeldItemNameHudElement.box(cfg, mc.getWindow().getGuiScaledWidth(),
                mc.getWindow().getGuiScaledHeight(), mc.font.width(name), mc.font.lineHeight, out);
        return out;
    }

    private String label(Target t) {
        return switch (t) {
            case CLOCK -> Localization.tr("时钟");
            case DAY_COUNT -> Localization.tr("世界天数");
            case FPS_TPS_PING -> Localization.tr("FPS/TPS/Ping");
            case MINING_ABILITY -> Localization.tr("冷却倒计时");
            case MINING_ABILITY_SUBTITLE -> Localization.tr("技能提醒");
            case COMMISSION -> Localization.tr("挖矿委托");
            case STAT_DEFENSE -> StatTextHudElement.label(StatTextHudElement.Kind.DEFENSE);
            case STAT_SPEED -> StatTextHudElement.label(StatTextHudElement.Kind.SPEED);
            case STAT_VITALITY -> StatTextHudElement.label(StatTextHudElement.Kind.VITALITY);
            case STAT_HEALTH -> StatTextHudElement.label(StatTextHudElement.Kind.HEALTH);
            case STAT_MANA -> StatTextHudElement.label(StatTextHudElement.Kind.MANA);
            case HELD_ITEM_NAME -> Localization.tr("手持物品名");
            case BAR_HEALTH -> StatsBarHudElement.label(StatsBarHudElement.Kind.HEALTH);
            case BAR_MANA -> StatsBarHudElement.label(StatsBarHudElement.Kind.MANA);
            case BAR_DEFENSE -> StatsBarHudElement.label(StatsBarHudElement.Kind.DEFENSE);
            case BAR_SPEED -> StatsBarHudElement.label(StatsBarHudElement.Kind.SPEED);
            case BAR_VITALITY -> StatsBarHudElement.label(StatsBarHudElement.Kind.VITALITY);
            case BAR_AIR -> StatsBarHudElement.label(StatsBarHudElement.Kind.AIR);
        };
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        // This screen draws at 1:1 (only the per-element previews scale), but ClickGuiFonts picks its
        // glyph raster from a *global* renderScale that the panel sets while it is open. Without this
        // the editor inherits whatever the panel last left there, so the raster is built for a
        // different size than the pose it is drawn at and every label is resampled - which is exactly
        // the soft text this screen showed. The panel re-sets it every frame, so nothing else changes.
        ClickGuiFonts.setRenderScale(1.0f);
        ModConfig cfg = ConfigManager.get();
        Minecraft mc = Minecraft.getInstance();

        // Measurement caches are per frame: each target's box and lines are computed once below and
        // reused by the drawing loop (see the fields).
        renderPass = true;
        linesCache.clear();
        boundsCache.clear();

        drawGrid(graphics);

        // A switched-off feature has no HUD element, so it must not appear here either - and the card
        // must not describe one. Keeping the selection on a visible target also keeps the wheel and the
        // arrow keys from editing something the player cannot see.
        if (!visible(selected, cfg)) {
            selected = firstVisible(cfg);
        }

        // Every enabled HUD element at its real position; the selected one framed green.
        int[] b = new int[4];
        for (Target t : Target.values()) {
            if (!visible(t, cfg)) {
                continue;
            }
            bounds(t, mc, cfg, b);
            Matrix3x2fStack pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(b[0], b[1]);
            float s = scale(t, cfg);
            pose.scale(s, s);
            StatsBarHudElement.Kind bar = StatsBarHudElement.kindOf(t.name());
            if (bar != null) {
                // A stand-in bar with the real geometry, icon and colour: the editor used to show bars as
                // an empty frame, so their colour could not be seen while placing them.
                StatsBarHudElement.preview(graphics, cfg, bar);
            } else {
                int lineH = lineHeight(t, mc, cfg);
                int ly = 0;
                int color = previewColor(t, cfg);
                for (String line : lines(t, mc, cfg)) {
                    // Same font the HUD itself will use, so the frame really does frame it - and the same
                    // colour, which used to be hardcoded white for every element.
                    HudText.draw(graphics, cfg, line, 0, ly, color, true);
                    ly += lineH;
                }
            }
            pose.popMatrix();
            graphics.outline(b[0] - 1, b[1] - 1, b[2] + 2, b[3] + 2,
                    t == selected ? 0xFF3A9E5F : 0xFFFFFF00);
            if (t == selected && b[2] > 0 && b[3] > 0) {
                // The boxes handleAt tests for, drawn so they can be aimed at. Bars get three (free
                // transform: width, height, both); everything else gets the one corner that scales it.
                int right = b[0] + b[2];
                int bottom = b[1] + b[3];
                if (StatsBarHudElement.kindOf(t.name()) != null) {
                    handle(graphics, right, b[1] + b[3] / 2, HANDLE_DOT);
                    handle(graphics, b[0] + b[2] / 2, bottom, HANDLE_DOT);
                }
                handle(graphics, right, bottom, HANDLE_DOT);
            }
        }

        drawPanel(graphics, cfg);
        settingsCard.draw(graphics, mouseX, mouseY);
        renderPass = false;
    }

    /**
     * The colour a preview should be drawn in: whatever the element itself draws with.
     *
     * <p>Elements whose colour is a setting return it here; the ones that have no configurable colour
     * (clock, day count, FPS readout, commission list) stay white, which is what they draw in game.
     */
    private static int previewColor(Target t, ModConfig cfg) {
        StatTextHudElement.Kind stat = statKind(t);
        return stat == null ? 0xFFFFFFFF : StatTextHudElement.color(cfg, stat);
    }

    /** The stat row a target stands for, or null when it is not one. */
    private static StatTextHudElement.Kind statKind(Target t) {
        return switch (t) {
            case STAT_DEFENSE -> StatTextHudElement.Kind.DEFENSE;
            case STAT_SPEED -> StatTextHudElement.Kind.SPEED;
            case STAT_VITALITY -> StatTextHudElement.Kind.VITALITY;
            case STAT_HEALTH -> StatTextHudElement.Kind.HEALTH;
            case STAT_MANA -> StatTextHudElement.Kind.MANA;
            default -> null;
        };
    }

    /** The floating card: selection, position, size and the hints. Drawn on top of everything. */
    private void drawPanel(GuiGraphicsExtractor graphics, ModConfig cfg) {
        updatePanelSize(cfg);
        ensurePanelPlaced();
        int x = panelX;
        int y = panelY;
        int h = panelHeight();
        // Rounded, no border: the card is meant to sit quietly over the game, not to frame itself in the
        // selection colour (which now belongs to the element being edited, not to the UI).
        GuiPixels.fillRoundedRect(graphics, x, y, panelW, h, 4,
                0xE6000000 | (ClickGuiStyle.cardFloat() & 0xFFFFFF));
        GuiPixels.fillRoundedTopRect(graphics, x, y, panelW, PANEL_TITLE_H, 4,
                0xE6000000 | (ClickGuiStyle.cardFloatTitle() & 0xFFFFFF));
        String title = Localization.tr("HUD 位置调整");
        graphics.text(font, title, x + panelW / 2 - font.width(title) / 2,
                y + (PANEL_TITLE_H - 8) / 2 + 1, ClickGuiStyle.text(), false);

        List<String> rows = panelRows(cfg);
        int tx = x + PANEL_PAD;
        int ty = y + PANEL_TITLE_H + PANEL_PAD;
        for (int i = 0; i < rows.size(); i++) {
            // One colour for the whole card. It used to fade (EE -> BB -> 9A) to mark the hints as
            // secondary, but five lines in a row read as a vertical gradient rather than as emphasis.
            // Trimmed rather than allowed to run past the card: the width is measured from these very
            // rows, so the only case that can overflow is a label longer than the screen.
            row(graphics, fit(rows.get(i), panelW - PANEL_PAD * 2), tx, ty, ClickGuiStyle.cardText());
            ty += PANEL_LINE_H;
        }
    }

    /** The card's rows; also what its width is measured from. */
    private List<String> panelRows(ModConfig cfg) {
        List<String> rows = new ArrayList<>(PANEL_ROWS);
        rows.add(Localization.tr("当前: ") + label(selected));
        rows.add(String.format(Localization.tr("位置 X=%d Y=%d"), x(selected, cfg),
                y(selected, cfg)));
        StatsBarHudElement.Kind bar = StatsBarHudElement.kindOf(selected.name());
        rows.add(bar == null
                ? String.format(Localization.tr("缩放 %.2f"), scale(selected, cfg))
                : String.format(Localization.tr("尺寸 %d×%d  缩放 %.2f"),
                        StatsBarHudElement.width(cfg, bar), StatsBarHudElement.height(cfg, bar),
                        scale(selected, cfg)));
        rows.add(Localization.tr("拖动移动 · 把手改尺寸"));
        rows.add(Localization.tr("方向键微调 · ESC 退出"));
        return rows;
    }

    /** Sizes the card to its widest row, so nothing is ever clipped by a fixed width. */
    private void updatePanelSize(ModConfig cfg) {
        // The title counts too, otherwise a long one would stick out of the card.
        int widest = font.width(Localization.tr("HUD 位置调整"));
        for (String row : panelRows(cfg)) {
            widest = Math.max(widest, font.width(row));
        }
        panelW = Math.max(PANEL_MIN_W,
                Math.min(Math.min(PANEL_MAX_W, width - 20), widest + PANEL_PAD * 2));
    }

    private void row(GuiGraphicsExtractor graphics, String text, int x, int y, int color) {
        graphics.text(font, text, x, y, color, false);
    }

    /** Shortens {@code text} with an ellipsis when it does not fit in {@code maxWidth}. */
    private String fit(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        int ellipsis = font.width("…");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (font.width(out.toString() + ch) + ellipsis > maxWidth) {
                break;
            }
            out.append(ch);
        }
        return out + "…";
    }

    private static int panelHeight() {
        return PANEL_TITLE_H + PANEL_PAD * 2 + PANEL_ROWS * PANEL_LINE_H;
    }

    private void ensurePanelPlaced() {
        if (!panelPlaced) {
            panelPlaced = true;
            panelX = width / 2 - panelW / 2;
            panelY = height - panelHeight() - 12;
        }
        clampPanel();
    }

    /** Keeps the card on screen after a drag or a window resize. */
    private void clampPanel() {
        int h = panelHeight();
        panelX = Math.max(2, Math.min(Math.max(2, width - panelW - 2), panelX));
        panelY = Math.max(2, Math.min(Math.max(2, height - h - 2), panelY));
    }

    private boolean insidePanel(double mx, double my) {
        return mx >= panelX && mx <= panelX + panelW
                && my >= panelY && my <= panelY + panelHeight();
    }

    /**
     * A faint grid with a heavier cross through the exact centre of the screen.
     *
     * <p>Grid lines step outwards from the centre, so one of them always lands on the middle (otherwise
     * the cross and the grid would disagree by a few pixels). The cross is 2 px wide rather than 3: an odd
     * width cannot be centred on a pixel boundary, and "the crossing point is exactly the middle" was the
     * requirement, so the even width wins.
     */
    private void drawGrid(GuiGraphicsExtractor graphics) {
        int cx = width / 2;
        int cy = height / 2;
        for (int gx = cx % GRID; gx < width; gx += GRID) {
            graphics.fill(gx, 0, gx + 1, height, GRID_COLOR);
        }
        for (int gy = cy % GRID; gy < height; gy += GRID) {
            graphics.fill(0, gy, width, gy + 1, GRID_COLOR);
        }
        int axis = 0x3AFFFFFF;
        graphics.fill(cx - 1, 0, cx + 1, height, axis);
        graphics.fill(0, cy - 1, width, cy + 1, axis);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        ModConfig cfg = ConfigManager.get();
        Minecraft mc = Minecraft.getInstance();
        // The settings card is the topmost layer: its own clicks first, so nothing behind it moves.
        if (settingsCard.mouseClicked(event.x(), event.y())) {
            return true;
        }
        // A left click anywhere else dismisses it, like any other popup.
        if (event.button() == 0 && settingsCard.isOpen()) {
            settingsCard.close();
        }
        // The floating card is on top of the canvas: a click on it must never reach the element behind it,
        // or dragging the card would also drag whatever it happens to cover.
        ensurePanelPlaced();
        if (insidePanel(event.x(), event.y())) {
            if (event.y() < panelY + PANEL_TITLE_H) {
                panelDragging = true;
                panelGrabX = event.x() - panelX;
                panelGrabY = event.y() - panelY;
            }
            return true;
        }
        int[] b = new int[4];
        Target best = null;
        int bestArea = Integer.MAX_VALUE;
        int bestDistance = Integer.MAX_VALUE;
        for (Target t : Target.values()) {
            if (!visible(t, cfg)) {
                continue;   // a switched-off element cannot be picked up either
            }
            bounds(t, mc, cfg, b);
            int dx = outside(event.x(), b[0], b[2]);
            int dy = outside(event.y(), b[1], b[3]);
            if (dx == 0 && dy == 0) {
                // Contained. Prefer the smallest such box: the bars sit 9 px apart and the rows they
                // share space with are wider, so "first match wins" handed clicks to the wrong one.
                int area = b[2] * b[3];
                if (area < bestArea) {
                    bestArea = area;
                    best = t;
                    bestDistance = 0;
                }
            } else if (bestArea == Integer.MAX_VALUE && dx + dy < bestDistance) {
                // Nothing contained the cursor yet: remember the closest box instead, because some
                // elements are only a few pixels tall and expecting an exact hit on them is unfair.
                bestDistance = dx + dy;
                best = t;
            }
        }
        if (event.button() == 1) {
            // Right click: select the element and show its settings; away from any element, close.
            if (best != null && bestDistance <= GRAB_SLACK) {
                selected = best;
                openSettingsCard(best);
            } else {
                settingsCard.close();
            }
            return true;
        }
        if (best != null && bestDistance <= GRAB_SLACK) {
            // Handles only apply to the element that was already selected: otherwise clicking near the
            // edge of another element would resize it instead of selecting it.
            boolean wasSelected = best == selected;
            selected = best;
            dragging = true;
            dragTarget = best;
            bounds(best, mc, cfg, b);
            // Anchored on the element's own origin rather than the box: a bar's box now starts at its
            // icon, but dragging must keep writing the bar's x, or the first drag jumps by the icon width.
            boolean barTarget = StatsBarHudElement.kindOf(best.name()) != null;
            dragOffsetX = event.x() - (barTarget ? x(best, cfg) : b[0]);
            dragOffsetY = event.y() - (barTarget ? y(best, cfg) : b[1]);
            // Grabbing one of the bar's handles resizes it - the two-axis counterpart of the wheel, which
            // scales both at once.
            resizeMode = 0;
            StatsBarHudElement.Kind bar = StatsBarHudElement.kindOf(best.name());
            int mode = wasSelected ? handleAt(b, event.x(), event.y(), bar != null) : 0;
            if (mode != 0) {
                resizeMode = mode;
                resizeBar = bar;
                resizeTarget = best;
                // Remembered for the whole drag: the scale mode works out a ratio from the box the
                // element had when the drag began, so moving the cursor does not compound.
                resizeStartScale = scale(best, cfg);
                resizeStartW = b[2];
                resizeStartH = b[3];
                // Same reason as the drag offsets: width is measured from the bar's own left edge,
                // and the box now starts one icon further left.
                resizeOriginX = x(best, cfg);
                resizeOriginY = y(best, cfg);
            }
            return true;
        }
        return super.mouseClicked(event, doubled);
    }

    /** The module a HUD element belongs to - its settings are what the card shows. */
    /**
     * Modifier state, tracked from the key events themselves.
     *
     * <p>{@code Screen} has no portable "is control down" helper in this mapping, and polling GLFW while
     * a key event is being dispatched proved unreliable for shortcuts. MC delivers the modifier keys as
     * ordinary presses, so recording them here is exact: Ctrl is down by the time C arrives.
     */
    private boolean shiftDown;
    private boolean ctrlDown;

    @Override
    public boolean keyReleased(KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_LEFT_SHIFT || key == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            shiftDown = false;
        }
        if (key == GLFW.GLFW_KEY_LEFT_CONTROL || key == GLFW.GLFW_KEY_RIGHT_CONTROL) {
            ctrlDown = false;
        }
        return super.keyReleased(event);
    }

    /**
     * Typed characters go to the card's fields.
     *
     * <p>The editor previously had no text input at all, so this is where a field inside the card gets its
     * characters: there is no widget to forward to, the card routes them to whichever field has focus.
     */
    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        if (!event.isAllowedChatCharacter()) {
            return false;
        }
        String text = event.codepointAsString();
        if (text.isEmpty()) {
            return false;
        }
        return settingsCard.charTyped(text.charAt(0));
    }

    private static String moduleId(Target t) {
        if (StatsBarHudElement.kindOf(t.name()) != null) {
            return "stats_display";
        }
        return switch (t) {
            case CLOCK -> "clock";
            case DAY_COUNT -> "day_count";
            case FPS_TPS_PING -> "fps_tps_ping";
            case MINING_ABILITY, MINING_ABILITY_SUBTITLE -> "mining_ability_reminder";
            case COMMISSION -> "commissions";
            case STAT_HEALTH, STAT_MANA, STAT_DEFENSE, STAT_SPEED, STAT_VITALITY -> "stats_display";
            case HELD_ITEM_NAME -> "held_item_name";
            // The six bars return early above; listing them again keeps the switch exhaustive.
            default -> "stats_display";
        };
    }

    /** Opens the settings card for an element, anchored on its right edge. */
    private void openSettingsCard(Target t) {
        Feature feature = ModuleRegistry.byId(moduleId(t));
        if (feature == null) {
            settingsCard.close();
            return;
        }
        int[] b = new int[4];
        bounds(t, Minecraft.getInstance(), ConfigManager.get(), b);
        settingsCard.open(label(t), feature.settings(), t.name(), b[0] + b[2], b[1], width, height);
    }

    /**
     * Which resize handle a point is on: 1 = width (right edge), 2 = height (bottom edge), 3 = both
     * (corner), 0 = none.
     *
     * <p>Small boxes rather than "within N px of an edge": a distance test along the whole edge made an
     * ordinary grab near the end of a bar resize it by accident, and shrinking the distance only made the
     * handles impossible to hit. The same boxes are drawn on the selected bar (see extractRenderState), so
     * the target is visible instead of guessed.
     */
    private static int handleAt(int[] b, double mx, double my, boolean bar) {
        int right = b[0] + b[2];
        int bottom = b[1] + b[3];
        if (!bar) {
            // Everything except a bar has a single handle: its corner, which scales the element. Bars get
            // their own three (free transform), so the two kinds do not compete for the same spot.
            return inHandle(right, bottom, mx, my, HANDLE_HIT) ? MODE_SCALE : 0;
        }
        if (inHandle(right, bottom, mx, my, HANDLE_HIT)) {
            return 3;
        }
        if (inHandle(right, b[1] + b[3] / 2, mx, my, HANDLE_HIT)) {
            return 1;
        }
        if (inHandle(b[0] + b[2] / 2, bottom, mx, my, HANDLE_HIT)) {
            return 2;
        }
        return 0;
    }

    private static boolean inHandle(int cx, int cy, double mx, double my, int size) {
        double half = size / 2.0;
        return Math.abs(mx - cx) <= half && Math.abs(my - cy) <= half;
    }

    /** A filled square centred on a handle position, in the selection colour. */
    private static void handle(GuiGraphicsExtractor graphics, int cx, int cy, int size) {
        int half = size / 2;
        graphics.fill(cx - half, cy - half, cx - half + size, cy - half + size, 0xFF3A9E5F);
    }

    /**
     * How far a point lies outside a box (GUI px), zero when it is inside. Used both to pick what was
     * clicked and to allow a near miss, which is what makes a 6 px tall bar reachable.
     */
    private static int outside(double value, int from, int size) {
        if (value < from) {
            return (int) Math.ceil(from - value);
        }
        if (value > from + size) {
            return (int) Math.ceil(value - from - size);
        }
        return 0;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (settingsCard.mouseDragged(event.x(), event.y())) {
            return true;
        }
        if (panelDragging) {
            panelX = (int) Math.round(event.x() - panelGrabX);
            panelY = (int) Math.round(event.y() - panelGrabY);
            clampPanel();
            return true;
        }
        if (dragging) {
            ModConfig cfg = ConfigManager.get();
            if (resizeMode == MODE_SCALE && resizeTarget != null) {
                // Corner drag for the elements that have no free transform (text rows, clock, FPS, ...):
                // the cursor's distance from the element's origin, relative to the box it started with,
                // becomes the new scale - then snapped to the shared grid so it lands on slider values.
                double baseW = Math.max(1, resizeStartW);
                double baseH = Math.max(1, resizeStartH);
                double fx = (event.x() - resizeOriginX) / baseW;
                double fy = (event.y() - resizeOriginY) / baseH;
                double factor = Math.max(fx, fy);
                float s = snapScale((float) (resizeStartScale * Math.max(0.05, factor)));
                setScale(resizeTarget, cfg, s);
                // Read-after-write, like the bar handles: a silent no-op would look like a dead handle.
                verify(resizeTarget, Math.abs(scale(resizeTarget, cfg) - s) < 0.001f, "scale");
                return true;
            }
            if (resizeMode != 0 && resizeBar != null) {
                // The stored size is in design units, so the cursor's distance from the bar's origin is
                // divided by the element's own scale - otherwise resizing a scaled bar would run away.
                float s = Math.max(0.01f, StatsBarHudElement.scale(cfg, resizeBar));
                int w = (resizeMode & 1) != 0
                        ? Math.round((float) ((event.x() - resizeOriginX) / s))
                        : StatsBarHudElement.width(cfg, resizeBar);
                int h = (resizeMode & 2) != 0
                        ? Math.round((float) ((event.y() - resizeOriginY) / s))
                        : StatsBarHudElement.height(cfg, resizeBar);
                StatsBarHudElement.setSize(cfg, resizeBar, w, h);
                // Read-after-write, like the position setters: a silent no-op would look like a dead
                // drag handle (see the note on verify).
                verify(resizeTarget, StatsBarHudElement.width(cfg, resizeBar) ==
                        Math.max(ModConfig.BAR_MIN_W, Math.min(ModConfig.BAR_MAX_W, w)), "width");
                return true;
            }
            setX(dragTarget, cfg, (int) Math.round(event.x() - dragOffsetX));
            setY(dragTarget, cfg, (int) Math.round(event.y() - dragOffsetY));
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (settingsCard.mouseReleased()) {
            return true;
        }
        if (panelDragging) {
            panelDragging = false;
            return true;
        }
        if (dragging) {
            dragging = false;
            resizeMode = 0;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // Inside the card the wheel scrolls its rows; over the canvas it still zooms the selection.
        if (settingsCard.mouseScrolled(mouseX, mouseY, verticalAmount)) {
            return true;
        }
        ModConfig cfg = ConfigManager.get();
        // Additive, one grid step per notch, and only the SIGN of the scroll is used.
        //
        // It used to be multiplicative (scale * (1 + amount * 0.1)), which made a notch worth +0.025 at
        // 0.25 and +0.6 at 6.0 - the "every scroll changes it by a different amount" report - and made
        // trackpads (which send fractional amounts) jump unpredictably. Snapping to the shared grid also
        // keeps the value identical to what the ClickGUI slider would show for it.
        double dir = Math.signum(verticalAmount);
        if (dir == 0) {
            return true;
        }
        float s = scale(selected, cfg) + (float) dir * WHEEL_STEP;
        setScale(selected, cfg, snapScale(s));
        return true;
    }

    /** Clamps a scale and lands it on the shared grid, so both entry points produce the same numbers. */
    private static float snapScale(float value) {
        float clamped = Math.max(ModConfig.SCALE_MIN, Math.min(ModConfig.SCALE_MAX, value));
        return Math.round(clamped / ModConfig.SCALE_GRID) * ModConfig.SCALE_GRID;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_LEFT_SHIFT || key == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            shiftDown = true;
        }
        if (key == GLFW.GLFW_KEY_LEFT_CONTROL || key == GLFW.GLFW_KEY_RIGHT_CONTROL) {
            ctrlDown = true;
        }
        // A focused field in the card owns the keys (Backspace edits text, not the element).
        if (settingsCard.keyPressed(key, shiftDown, ctrlDown)) {
            return true;
        }
        // ESC layers: the settings card closes first, the editor second.
        if (key == GLFW.GLFW_KEY_ESCAPE && settingsCard.isOpen()) {
            settingsCard.close();
            return true;
        }
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT
                || key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
            ModConfig cfg = ConfigManager.get();
            switch (key) {
                case GLFW.GLFW_KEY_LEFT -> setX(selected, cfg, x(selected, cfg) - 1);
                case GLFW.GLFW_KEY_RIGHT -> setX(selected, cfg, x(selected, cfg) + 1);
                case GLFW.GLFW_KEY_UP -> setY(selected, cfg, y(selected, cfg) - 1);
                case GLFW.GLFW_KEY_DOWN -> setY(selected, cfg, y(selected, cfg) + 1);
                default -> { }
            }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        ConfigManager.save();
        minecraft.gui.setScreen(parent);
    }
}
