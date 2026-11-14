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
            entry(c -> c.clockBlock.clockX, (c, v) -> c.clockBlock.clockX = v,
                    c -> c.clockBlock.clockY, (c, v) -> c.clockBlock.clockY = v, 10, 10),
            entry(c -> c.fpsTpsPingBlock.fpsTpsPingX, (c, v) -> c.fpsTpsPingBlock.fpsTpsPingX = v,
                    c -> c.fpsTpsPingBlock.fpsTpsPingY, (c, v) -> c.fpsTpsPingBlock.fpsTpsPingY = v, 4, 4),
            entry(c -> c.dayCountBlock.dayCountX, (c, v) -> c.dayCountBlock.dayCountX = v,
                    c -> c.dayCountBlock.dayCountY, (c, v) -> c.dayCountBlock.dayCountY = v, 10, 40),
            entry(c -> c.miningAbilityReminderBlock.miningAbilityX, (c, v) -> c.miningAbilityReminderBlock.miningAbilityX = v,
                    c -> c.miningAbilityReminderBlock.miningAbilityY, (c, v) -> c.miningAbilityReminderBlock.miningAbilityY = v, 10, 70),
            entry(c -> c.miningAbilityReminderBlock.miningAbilitySubtitleX, (c, v) -> c.miningAbilityReminderBlock.miningAbilitySubtitleX = v,
                    c -> c.miningAbilityReminderBlock.miningAbilitySubtitleY, (c, v) -> c.miningAbilityReminderBlock.miningAbilitySubtitleY = v, 120, 90),
            entry(c -> c.commissionsBlock.commissionsX, (c, v) -> c.commissionsBlock.commissionsX = v,
                    c -> c.commissionsBlock.commissionsY, (c, v) -> c.commissionsBlock.commissionsY = v, 10, 120),
            entry(c -> c.skillDisplayBlock.skillDisplayX, (c, v) -> c.skillDisplayBlock.skillDisplayX = v,
                    c -> c.skillDisplayBlock.skillDisplayY, (c, v) -> c.skillDisplayBlock.skillDisplayY = v, 10, 130),
            entry(c -> c.drillFuelBarBlock.drillFuelBarX, (c, v) -> c.drillFuelBarBlock.drillFuelBarX = v,
                    c -> c.drillFuelBarBlock.drillFuelBarY, (c, v) -> c.drillFuelBarBlock.drillFuelBarY = v, 10, 150),
            entry(c -> c.thunderBottleBlock.thunderBottleX, (c, v) -> c.thunderBottleBlock.thunderBottleX = v,
                    c -> c.thunderBottleBlock.thunderBottleY, (c, v) -> c.thunderBottleBlock.thunderBottleY = v, 10, 198),
            entry(c -> c.itemPickupLogBlock.pickupLogX, (c, v) -> c.itemPickupLogBlock.pickupLogX = v,
                    c -> c.itemPickupLogBlock.pickupLogY, (c, v) -> c.itemPickupLogBlock.pickupLogY = v, 10, 220),
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
