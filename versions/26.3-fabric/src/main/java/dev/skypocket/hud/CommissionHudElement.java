package dev.skypocket.hud;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;
import dev.skypocket.net.SkyBlockDetector;
import dev.skypocket.render.ColorUtil;
import dev.skypocket.render.HudText;
import dev.skypocket.skyblock.CommissionTracker;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Draggable HUD element showing the SkyBlock mining commissions that the tab list carries:
 *
 * <pre>
 * Commissions:
 * Glacite Collector: 1.5%
 * Maniac Slayer: DONE
 * </pre>
 *
 * <p>The header keeps the tab list's own wording and colour (default blue); every task line is
 * coloured by its progress - the "pending" colour at 0% through to the "done" colour at 100%, so a
 * finished commission is green without needing a separate marker. The element is registered like
 * the clock, its position/scale live in the unified HUD position editor, and it hides itself
 * outside SkyBlock and whenever there is nothing to show.
 */
public final class CommissionHudElement {
    private static final Identifier ID = Identifier.fromNamespaceAndPath("skypocket", "commissions");
    /** Extra spacing between lines; the tab list packs them, a hair of air reads better. */
    private static final int LINE_GAP = 1;
    /** Header text, exactly as the tab list shows it (a game term, not translated). */
    private static final String HEADER = "Commissions:";
    /** Shown in the position editor before the player has ever been in a commission area. */
    private static final List<String> SAMPLE = List.of(HEADER, "Glacite Collector: 1.5%", "Maniac Slayer: DONE");

    /** One rendered line. */
    private record Line(String text, int color) {
    }

    private CommissionHudElement() {
    }

    public static void register() {
        HudElementRegistry.addLast(ID, CommissionHudElement::render);
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        // Poll first (throttled inside) so the list is up to date even while the element is off.
        CommissionTracker.update(mc);
        ModConfig cfg = ConfigManager.get();
        HudPositionSync.sync(mc, cfg);
        if (!cfg.commissionsEnabled || !SkyBlockDetector.isActive()) {
            return;
        }
        List<Line> lines = lines(cfg);
        if (lines.isEmpty()) {
            return;
        }
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(cfg.commissionsX, cfg.commissionsY);
        pose.scale(cfg.commissionsScale, cfg.commissionsScale);
        int lineH = lineHeight(mc);
        int y = 0;
        for (Line line : lines) {
            HudText.draw(graphics, cfg, line.text(), 0, y, line.color(), true);
            y += lineH;
        }
        pose.popMatrix();
    }

    /** Line spacing in unscaled units, so the position editor can size the box. */
    public static int lineHeight(Minecraft mc) {
        // The parameter stays because the position editor sizes its box through this method; the font
        // itself is chosen centrally (see HudText) so measuring and drawing cannot disagree.
        return HudText.lineHeight(ConfigManager.get()) + LINE_GAP;
    }

    /**
     * The block to draw: the header plus one line per commission, or an empty list when there is
     * nothing to show (not in a commission area, or everything is done and completed ones are
     * hidden).
     */
    private static List<Line> lines(ModConfig cfg) {
        List<Line> tasks = new ArrayList<>();
        for (CommissionTracker.Entry entry : ordered(cfg)) {
            if (cfg.commissionsHideDone && entry.done()) {
                continue;
            }
            tasks.add(new Line(entry.name() + ": " + entry.value(), progressColor(cfg, entry)));
        }
        if (tasks.isEmpty()) {
            return List.of();
        }
        List<Line> out = new ArrayList<>(tasks.size() + 1);
        out.add(new Line(HEADER, cfg.commissionsHeaderColor));
        out.addAll(tasks);
        return out;
    }

    /** Tab order by default; with "pending first" the finished ones sink to the bottom (stable). */
    private static List<CommissionTracker.Entry> ordered(ModConfig cfg) {
        List<CommissionTracker.Entry> entries = CommissionTracker.entries();
        if (!cfg.commissionsPendingFirst || entries.size() < 2) {
            return entries;
        }
        List<CommissionTracker.Entry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparing(CommissionTracker.Entry::done));
        return sorted;
    }

    /**
     * The line's colour carries its progress: the pending colour at 0% through to the done colour at
     * 100% (so a finished commission is the done colour), alpha included.
     *
     * <p>Interpolated in <b>HSV</b> rather than per RGB channel: the straight RGB midpoint of red and
     * green is a muddy olive ({@code #AAAA55} for the default colours), while rotating the hue along
     * the shorter arc (0 -&gt; 120 degrees) gives the yellow such a ramp is supposed to have
     * ({@code #FFFF55} at half). Saturation and value are interpolated linearly, so custom endpoint
     * colours keep behaving sensibly.
     *
     * <p>A value that is neither a percentage nor DONE stays white.
     */
    private static int progressColor(ModConfig cfg, CommissionTracker.Entry entry) {
        if (entry.progress() < 0f) {
            return 0xFFFFFFFF;
        }
        float t = ColorUtil.clamp(entry.progress(), 0f, 1f);
        int from = cfg.commissionsStartColor;
        int to = cfg.commissionsDoneColor;
        float[] a = ColorUtil.rgbToHsv(ColorUtil.red(from), ColorUtil.green(from), ColorUtil.blue(from));
        float[] b = ColorUtil.rgbToHsv(ColorUtil.red(to), ColorUtil.green(to), ColorUtil.blue(to));
        float hue = a[0] + shortestHueDelta(a[0], b[0]) * t;
        float saturation = a[1] + (b[1] - a[1]) * t;
        float value = a[2] + (b[2] - a[2]) * t;
        return ColorUtil.hsv(lerp(ColorUtil.alpha(from), ColorUtil.alpha(to), t),
                (hue % 360f + 360f) % 360f, saturation, value);
    }

    /** Signed shortest angular step from one hue to another, in degrees (-180, 180]. */
    private static float shortestHueDelta(float from, float to) {
        float delta = (to - from) % 360f;
        if (delta > 180f) {
            delta -= 360f;
        }
        if (delta <= -180f) {
            delta += 360f;
        }
        return delta;
    }

    private static int lerp(int from, int to, float t) {
        return Math.round(from + (to - from) * t);
    }

    /** Current text (or a sample) for the HUD position editor preview. */
    public static List<String> previewLines() {
        List<Line> live = lines(ConfigManager.get());
        if (!live.isEmpty()) {
            return live.stream().map(Line::text).toList();
        }
        return SAMPLE;
    }
}
