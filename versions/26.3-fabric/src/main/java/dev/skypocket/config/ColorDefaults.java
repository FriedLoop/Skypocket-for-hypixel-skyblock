package dev.skypocket.config;

/**
 * Default values for the colours a player can edit.
 *
 * <p>They used to be written twice - once as the field initialisers in {@link ModConfig}, once again in
 * the settings page's "reset" button - so the two had to be kept in step by hand, and a reset could
 * quietly land on a different colour than a fresh install. Both sides read this class now.
 *
 * <p>These are <b>user data</b>, not theme colours: changing one is a change to what a feature draws,
 * not to how the interface is painted.
 */
public final class ColorDefaults {
    public static final int PLAYER_GLOW = 0xFFFFFFFF;
    public static final int DRAGON_GLOW = 0xFFFFFFFF;
    public static final int HITBOX = 0xFFFFFFFF;
    public static final int LOOK_RAY = 0xFFFF5555;

    public static final int STATS_HEALTH = 0xFFFF5555;
    public static final int STATS_ABSORPTION = 0xFFFFFF55;
    public static final int STATS_MANA = 0xFF5555FF;
    public static final int STATS_OVERFLOW = 0xFF00AAAA;
    public static final int STATS_AIR = 0xFF55FFFF;
    public static final int STATS_DEFENSE = 0xFF55FF55;
    public static final int STATS_SPEED = 0xFFFFFFFF;
    public static final int STATS_VITALITY = 0xFFAA0000;

    private ColorDefaults() {
    }
}
