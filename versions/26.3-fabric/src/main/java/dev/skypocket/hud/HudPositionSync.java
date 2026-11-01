package dev.skypocket.hud;

import dev.skypocket.config.ModConfig;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ObjIntConsumer;
import java.util.function.ToIntFunction;

/**
 * Keeps the stored HUD positions aligned when the GUI scale - or window size - changes, and pulls any
 * element that ends up entirely off-screen back to a default so it can never be lost.
 *
 * <p>Positions are absolute pixels, so a GUI-scale change would otherwise strand them; they are rescaled
 * by the old/new gui-scaled dimension ratio once the change is detected.
 *
 * <p>The elements live in one table (see {@link #REPAIRED} and {@link #BARS}). They used to be written
 * out by hand here - 56 assignments across the two passes - which meant adding a HUD element required
 * remembering this file as well, and forgetting it was invisible until a player resized their window and
 * the new element stayed where it was. The invariant that makes this safe to table-drive: the defaults
 * below are exactly the literals the hand-written version used, so a resize repairs to the same places.
 */
public final class HudPositionSync {
    private static int lastGuiWidth = -1;
    private static int lastGuiHeight = -1;

    /** One element's stored position, plus where it goes when it is found off-screen. */
    private record Entry(ToIntFunction<ModConfig> x, ObjIntConsumer<ModConfig> setX,
                         ToIntFunction<ModConfig> y, ObjIntConsumer<ModConfig> setY,
                         int defaultX, int defaultY) {
    }

    /**
     * The elements repaired one by one, in the order the hand-written version repaired them.
     *
     * <p>Order matters only for readability - except that the bars are deliberately not in this list:
     * they are a set, and one member out of reach resets the whole set (see {@link #BARS}).
     */
    private static final List<Entry> REPAIRED = List.of(
            entry(c -> c.clockX, (c, v) -> c.clockX = v,
                    c -> c.clockY, (c, v) -> c.clockY = v, 10, 10),
            entry(c -> c.fpsTpsPingX, (c, v) -> c.fpsTpsPingX = v,
                    c -> c.fpsTpsPingY, (c, v) -> c.fpsTpsPingY = v, 4, 4),
            entry(c -> c.dayCountX, (c, v) -> c.dayCountX = v,
                    c -> c.dayCountY, (c, v) -> c.dayCountY = v, 10, 40),
            entry(c -> c.miningAbilityX, (c, v) -> c.miningAbilityX = v,
                    c -> c.miningAbilityY, (c, v) -> c.miningAbilityY = v, 10, 70),
            entry(c -> c.miningAbilitySubtitleX, (c, v) -> c.miningAbilitySubtitleX = v,
                    c -> c.miningAbilitySubtitleY, (c, v) -> c.miningAbilitySubtitleY = v, 120, 90),
            entry(c -> c.commissionsX, (c, v) -> c.commissionsX = v,
                    c -> c.commissionsY, (c, v) -> c.commissionsY = v, 10, 120),
            stat(StatTextHudElement.Kind.DEFENSE, 10, 70),
            stat(StatTextHudElement.Kind.SPEED, 10, 90),
            stat(StatTextHudElement.Kind.VITALITY, 10, 110),
            stat(StatTextHudElement.Kind.HEALTH, 100, 221),
            stat(StatTextHudElement.Kind.MANA, 335, 221));

    /** The bars. Their defaults are not per bar: {@link StatsBarHudElement#resetToDefaults} owns them. */
    private static final List<Entry> BARS = bars();

    private HudPositionSync() {
    }

    private static Entry entry(ToIntFunction<ModConfig> x, ObjIntConsumer<ModConfig> setX,
                               ToIntFunction<ModConfig> y, ObjIntConsumer<ModConfig> setY,
                               int defaultX, int defaultY) {
        return new Entry(x, setX, y, setY, defaultX, defaultY);
    }

    /** A stat text row, using the shared accessors so the field names live in one place. */
    private static Entry stat(StatTextHudElement.Kind kind, int defaultX, int defaultY) {
        return entry(c -> StatTextHudElement.x(c, kind), (c, v) -> StatTextHudElement.setX(c, kind, v),
                c -> StatTextHudElement.y(c, kind), (c, v) -> StatTextHudElement.setY(c, kind, v),
                defaultX, defaultY);
    }

    /** A bar, ditto. The defaults are unused: the set is reset together. */
    private static Entry bar(StatsBarHudElement.Kind kind) {
        return entry(c -> StatsBarHudElement.x(c, kind),
                (c, v) -> StatsBarHudElement.setX(c, kind, v),
                c -> StatsBarHudElement.y(c, kind),
                (c, v) -> StatsBarHudElement.setY(c, kind, v), 0, 0);
    }

    private static List<Entry> bars() {
        List<Entry> list = new ArrayList<>(StatsBarHudElement.Kind.values().length);
        for (StatsBarHudElement.Kind kind : StatsBarHudElement.Kind.values()) {
            list.add(bar(kind));
        }
        return List.copyOf(list);
    }

    /** Every element whose position is carried across a GUI-size change. */
    private static List<Entry> scaled() {
        List<Entry> all = new ArrayList<>(REPAIRED.size() + BARS.size());
        all.addAll(REPAIRED);
        all.addAll(BARS);
        return all;
    }

    /** Call once per frame before rendering the HUD elements. */
    public static void sync(Minecraft mc, ModConfig cfg) {
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        if (lastGuiWidth > 0 && lastGuiHeight > 0 && (w != lastGuiWidth || h != lastGuiHeight)) {
            float sx = (float) w / lastGuiWidth;
            float sy = (float) h / lastGuiHeight;
            for (Entry e : scaled()) {
                e.setX().accept(cfg, Math.round(e.x().applyAsInt(cfg) * sx));
                e.setY().accept(cfg, Math.round(e.y().applyAsInt(cfg) * sy));
            }
        }
        lastGuiWidth = w;
        lastGuiHeight = h;
        repairOffScreen(cfg, w, h);
    }

    /**
     * Resets an element whose stored position is entirely off-screen back to its visible default, so a
     * HUD element can never be lost off-screen.
     */
    private static void repairOffScreen(ModConfig cfg, int w, int h) {
        for (Entry e : REPAIRED) {
            if (offScreen(e.x().applyAsInt(cfg), e.y().applyAsInt(cfg), w, h)) {
                e.setX().accept(cfg, e.defaultX());
                e.setY().accept(cfg, e.defaultY());
            }
        }
        // One bar out of reach is repaired on its own. The set used to be reset as a whole, which also
        // wiped the other five bars' positions, scales and sizes over a single mistake; each reset still
        // lands the bar on its own row of the column, so the set does not come apart.
        for (StatsBarHudElement.Kind kind : StatsBarHudElement.Kind.values()) {
            if (offScreen(StatsBarHudElement.x(cfg, kind), StatsBarHudElement.y(cfg, kind), w, h)) {
                StatsBarHudElement.reset(cfg, kind);
            }
        }
    }

    private static boolean offScreen(int x, int y, int w, int h) {
        return x < 0 || x > w || y < 0 || y > h;
    }
}
