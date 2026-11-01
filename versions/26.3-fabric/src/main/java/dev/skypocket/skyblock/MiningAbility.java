package dev.skypocket.skyblock;

/**
 * The six Hypixel pickaxe ("mining") abilities. {@link #matchName} is the exact
 * English name the server uses in tab list / chat; it is also shown verbatim in the
 * HUD and subtitle (ability names are not localized).
 */
public enum MiningAbility {
    MINING_SPEED_BOOST("Mining Speed Boost"),
    PICKOBULUS("Pickobulus"),
    MANIAC_MINER("Maniac Miner"),
    TUNNEL_VISION("Tunnel Vision"),
    SHEER_FORCE("Sheer Force"),
    GEMSTONE_INFUSION("Gemstone Infusion");

    /** Exact English name as it appears in the tab list and chat messages. */
    public final String matchName;

    MiningAbility(String matchName) {
        this.matchName = matchName;
    }
}
