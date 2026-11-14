package dev.skypocket.hud;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;
import dev.skypocket.i18n.Localization;
import dev.skypocket.render.HudText;
import dev.skypocket.render.StatsOverlayRenderer;
import dev.skypocket.skyblock.PlayerStatsTracker;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.ObjDoubleConsumer;
import java.util.function.ObjIntConsumer;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;

/**
 * Defence, speed and vitality as plain HUD text - the "everything else" half of the stats display,
 * built exactly like the clock and the FPS readout: registered as HUD elements, positioned and scaled
 * by the HUD position editor (see {@code HudPositionEditorScreen}, one target each).
 *
 * <p>These three are the values that do not become icons or bars in the classic style, and they are
 * deliberately independent of the style setting: in the modern style the same numbers appear in bars
 * as well, so each row here can simply be switched off from its own module settings.
 *
 * <p>Colour comes from the action bar's own palette by default (defence green, vitality dark red),
 * which is what "show them in their original colours" means - the settings expose one colour per row
 * and it is shared with the matching bar.
 */
public final class StatTextHudElement {
    /**
     * One row per value; the enum is what the editor and the settings are keyed on.
     *
     * <p>The first two are the classic style's own readout - the icons cannot express a single point,
     * so the numbers are printed beside them. They draw only while the classic style is selected
     * (the modern bars already print their own); the other three are the values that never became
     * icons or bars and so belong to both styles.
     */
    public enum Kind {
        HEALTH, MANA, DEFENSE, SPEED, VITALITY
    }

    private StatTextHudElement() {
    }

    /** Registers all three rows. Called once from the client initializer. */
    public static void register() {
        for (Kind kind : Kind.values()) {
            HudElementRegistry.addLast(id(kind), (graphics, delta) -> render(graphics, kind));
        }
    }

    private static void render(GuiGraphicsExtractor graphics, Kind kind) {
        ModConfig cfg = ConfigManager.get();
        if (!enabled(cfg, kind) || !StatsOverlayRenderer.active()) {
            return;
        }
        if ((kind == Kind.HEALTH || kind == Kind.MANA) && cfg.statsDisplayBlock.statsModernStyle) {
            return; // the modern bars print these two themselves; the rows here are the classic readout
        }
        String line = line(kind, cfg);
        if (line == null) {
            return;
        }
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x(cfg, kind), y(cfg, kind));
        float scale = scale(cfg, kind);
        pose.scale(scale, scale);
        HudText.draw(graphics, cfg, line, 0, 0, color(cfg, kind), true, scale);
        pose.popMatrix();
    }

    /**
     * The text for one row, or null when there is nothing to show: no captured values yet, or a value
     * the server is not publishing (vitality disappears from the action bar when it is not in use).
     */
    public static String line(Kind kind, ModConfig cfg) {
        if (!PlayerStatsTracker.hasData()) {
            return null;
        }
        return switch (kind) {
            // The current value is the whole of it: the captured health already includes absorption,
            // and mana is shown with its overflow added in, so the two read the same way. The "+xxx"
            // form this used to print was a second, redundant telling of the same thing.
            case HEALTH ->
                    PlayerStatsTracker.health() + "/" + PlayerStatsTracker.maxHealth();
            case MANA -> PlayerStatsTracker.mana() + PlayerStatsTracker.overflowMana() + "/"
                    + PlayerStatsTracker.maxMana();
            case DEFENSE -> PlayerStatsTracker.defense() + "❈ " + Localization.tr("防御");
            case SPEED -> PlayerStatsTracker.speed() + " " + Localization.tr("速度");
            case VITALITY -> {
                if (!PlayerStatsTracker.vitalityShown()) {
                    yield null;
                }
                // Integers, like the server publishes them - the row showed "12.0/25.0" before, which
                // was this mod inventing precision.
                String value = Integer.toString(PlayerStatsTracker.vitality());
                if (PlayerStatsTracker.maxVitality() > 0) {
                    value = value + "/" + PlayerStatsTracker.maxVitality();
                }
                yield value + "♨ " + Localization.tr("活力");
            }
        };
    }

    /** The row's name, for the HUD position editor's click targets. */
    public static String label(Kind kind) {
        return switch (kind) {
            case HEALTH -> Localization.tr("生命数值");
            case MANA -> Localization.tr("法力数值");
            case DEFENSE -> Localization.tr("防御");
            case SPEED -> Localization.tr("速度");
            case VITALITY -> Localization.tr("活力");
        };
    }

    /** One stat row's config fields - see {@link #FIELDS}. */
    private record Fields(ToIntFunction<ModConfig> x, ObjIntConsumer<ModConfig> setX,
                          ToIntFunction<ModConfig> y, ObjIntConsumer<ModConfig> setY,
                          ToDoubleFunction<ModConfig> scale, ObjDoubleConsumer<ModConfig> setScale,
                          Predicate<ModConfig> enabled) {
    }

    /**
     * Per-kind config access, in one place.
     *
     * <p>These used to be four switches here (enabled, x, y, scale) and five more lambdas in the HUD
     * position editor, all spelling out the same field names. Adding a stat meant editing all of that
     * and hoping nothing was missed - and a missed {@code case} in a writer is silent. With the fields
     * in a table, a new kind is one row, and the editor reuses these accessors instead of repeating
     * them.
     */
    private static final Map<Kind, Fields> FIELDS = new EnumMap<>(Kind.class);

    static {
        put(Kind.HEALTH, c -> c.statsDisplayBlock.statsHealthTextX, (c, v) -> c.statsDisplayBlock.statsHealthTextX = v,
                c -> c.statsDisplayBlock.statsHealthTextY, (c, v) -> c.statsDisplayBlock.statsHealthTextY = v,
                c -> c.statsDisplayBlock.statsHealthTextScale, (c, v) -> c.statsDisplayBlock.statsHealthTextScale = (float) v,
                c -> c.statsDisplayBlock.statsHealthTextEnabled);
        put(Kind.MANA, c -> c.statsDisplayBlock.statsManaTextX, (c, v) -> c.statsDisplayBlock.statsManaTextX = v,
                c -> c.statsDisplayBlock.statsManaTextY, (c, v) -> c.statsDisplayBlock.statsManaTextY = v,
                c -> c.statsDisplayBlock.statsManaTextScale, (c, v) -> c.statsDisplayBlock.statsManaTextScale = (float) v,
                c -> c.statsDisplayBlock.statsManaTextEnabled);
        put(Kind.DEFENSE, c -> c.statsDisplayBlock.statsDefenseX, (c, v) -> c.statsDisplayBlock.statsDefenseX = v,
                c -> c.statsDisplayBlock.statsDefenseY, (c, v) -> c.statsDisplayBlock.statsDefenseY = v,
                c -> c.statsDisplayBlock.statsDefenseScale, (c, v) -> c.statsDisplayBlock.statsDefenseScale = (float) v,
                c -> c.statsDisplayBlock.statsDefenseEnabled);
        put(Kind.SPEED, c -> c.statsDisplayBlock.statsSpeedX, (c, v) -> c.statsDisplayBlock.statsSpeedX = v,
                c -> c.statsDisplayBlock.statsSpeedY, (c, v) -> c.statsDisplayBlock.statsSpeedY = v,
                c -> c.statsDisplayBlock.statsSpeedScale, (c, v) -> c.statsDisplayBlock.statsSpeedScale = (float) v,
                c -> c.statsDisplayBlock.statsSpeedEnabled);
        put(Kind.VITALITY, c -> c.statsDisplayBlock.statsVitalityX, (c, v) -> c.statsDisplayBlock.statsVitalityX = v,
                c -> c.statsDisplayBlock.statsVitalityY, (c, v) -> c.statsDisplayBlock.statsVitalityY = v,
                c -> c.statsDisplayBlock.statsVitalityScale, (c, v) -> c.statsDisplayBlock.statsVitalityScale = (float) v,
                c -> c.statsDisplayBlock.statsVitalityEnabled);
    }

    private static void put(Kind kind, ToIntFunction<ModConfig> x, ObjIntConsumer<ModConfig> sx,
                            ToIntFunction<ModConfig> y, ObjIntConsumer<ModConfig> sy,
                            ToDoubleFunction<ModConfig> s, ObjDoubleConsumer<ModConfig> ss,
                            Predicate<ModConfig> enabled) {
        FIELDS.put(kind, new Fields(x, sx, y, sy, s, ss, enabled));
    }

    public static boolean enabled(ModConfig cfg, Kind kind) {
        return FIELDS.get(kind).enabled().test(cfg);
    }

    public static int x(ModConfig cfg, Kind kind) {
        return FIELDS.get(kind).x().applyAsInt(cfg);
    }

    public static void setX(ModConfig cfg, Kind kind, int value) {
        FIELDS.get(kind).setX().accept(cfg, value);
    }

    public static int y(ModConfig cfg, Kind kind) {
        return FIELDS.get(kind).y().applyAsInt(cfg);
    }

    public static void setY(ModConfig cfg, Kind kind, int value) {
        FIELDS.get(kind).setY().accept(cfg, value);
    }

    public static float scale(ModConfig cfg, Kind kind) {
        return (float) FIELDS.get(kind).scale().applyAsDouble(cfg);
    }

    public static void setScale(ModConfig cfg, Kind kind, float value) {
        FIELDS.get(kind).setScale().accept(cfg, value);
    }

    /** The row's own colour - the position editor previews it instead of assuming white. */
    public static int color(ModConfig cfg, Kind kind) {
        return switch (kind) {
            case HEALTH -> cfg.statsDisplayBlock.statsHealthColor;
            case MANA -> cfg.statsDisplayBlock.statsManaColor;
            case DEFENSE -> cfg.statsDisplayBlock.statsDefenseColor;
            case SPEED -> cfg.statsDisplayBlock.statsSpeedColor;
            case VITALITY -> cfg.statsDisplayBlock.statsVitalityColor;
        };
    }

    private static Identifier id(Kind kind) {
        return Identifier.fromNamespaceAndPath("skypocket", "stat_" + kind.name().toLowerCase(Locale.ROOT));
    }
}
