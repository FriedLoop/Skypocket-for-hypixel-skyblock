package dev.skypocket.render;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.mixin.TextDisplayAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Detects fishing hotspots and estimates their size.
 *
 * <p>The hotspot centre is the position of the "HOTSPOT" marker the server spawns
 * above the liquid — on Hypixel this is an armour stand with a custom name (with a
 * text-display fallback), and it works for water and lava alike. The radius is
 * estimated from the ring of particles the server spawns around the hotspot: every
 * received particle is attributed to the nearest centre and its horizontal distance
 * is sampled into a rolling window. Ring particles all sit on one circle, so their
 * distances cluster at a single radius; the radius and surface height are taken from
 * the sharp peak of the distance histogram (scattered interference such as spawn
 * bursts or bobber splashes never forms a peak), with an optional hard whitelist of
 * the two ring particle types.
 *
 * <p>All timing uses wall-clock milliseconds, not {@code ClientLevel#getGameTime()}
 * (which on a multiplayer client only advances when the server sends time packets).
 */
public final class HotspotTracker {
    /** Ring samples outside this horizontal distance range are ignored. */
    static final double MIN_RADIUS = 1.0;
    static final double MAX_RADIUS = 12.0;
    /** Marker re-scan interval, and how long a hotspot lives without its marker. */
    private static final long SCAN_INTERVAL_MS = 200;
    private static final long HOTSPOT_TTL_MS = 3000;
    /** Rolling sample window, and the minimum sample count before a radius is trusted. */
    private static final long SAMPLE_WINDOW_MS = 3000;
    private static final int MIN_SAMPLES = 10;
    /** The two particle types that form hotspot rings: dust (water) and smoke (lava). */
    private static final Set<String> RING_PARTICLE_TYPES = new HashSet<>(Arrays.asList(
            "minecraft:dust",  // water-surface ring
            "minecraft:smoke"  // lava-surface ring
    ));
    /** Histogram bin width (blocks) used to locate the ring's distance peak. */
    private static final double BIN_WIDTH = 0.25;
    /** The ring peak (peak bin ± one neighbour) must cover this share of samples. */
    private static final double PEAK_SHARE = 0.7;
    /** Minimum samples inside the peak (±1 bin) before a radius is trusted. */
    private static final int MIN_PEAK_SAMPLES = 8;
    /** Radius re-estimation is throttled to this interval (ms) to avoid per-particle work. */
    private static final long RECOMPUTE_INTERVAL_MS = 100;
    /** A stat-line marker further than this many blocks from a HOTSPOT marker is ignored. */
    private static final double TYPE_MATCH_RANGE = 3.0;

    /** One detected hotspot: centre XZ + sampled base height and ring radius. */
    public static final class Hotspot {
        public final double x;
        public final double z;
        /** Y of the "HOTSPOT" marker (the liquid surface sits slightly below). */
        public final double anchorY;
        /** Estimated ring radius; negative until enough samples exist. */
        public float radius = -1.0f;
        /** Liquid-surface height estimated from the ring particles' Y (median). */
        public float baseY = Float.NaN;
        /** Hotspot kind from the stat line next to the marker; null when undetected. */
        public HotspotType type;

        private long lastSeen;
        private boolean dirty = true;
        private long lastRecomputeMs;
        /** Once a trusted radius is found it is frozen (a hotspot's size never changes). */
        private boolean locked;
        private final List<Sample> samples = new ArrayList<>();

        Hotspot(double x, double anchorY, double z) {
            this.x = x;
            this.anchorY = anchorY;
            this.z = z;
        }

        void refresh(long now) {
            if (locked) {
                return; // radius is frozen; nothing to do until the marker disappears
            }
            boolean pruned = samples.removeIf(s -> now - s.ms > SAMPLE_WINDOW_MS);
            if (pruned) {
                dirty = true;
            }
            // Throttle re-estimation while still collecting (the lock freezes it soon).
            if (dirty && now - lastRecomputeMs >= RECOMPUTE_INTERVAL_MS) {
                dirty = false;
                lastRecomputeMs = now;
                recompute();
            }
        }

        private void recompute() {
            if (samples.size() < MIN_SAMPLES) {
                clearEstimate();
                return;
            }
            // Ring particles all sit on one circle, so their horizontal distance from
            // the marker centre clusters tightly at the radius. Interference (spawn
            // bursts, splashes, neighbour hotspots) is scattered and never forms a
            // single sharp peak. Find the peak bin, require it to dominate, and take
            // the median of the in-peak samples for both radius and surface height.
            int bins = (int) Math.ceil((MAX_RADIUS - MIN_RADIUS) / BIN_WIDTH) + 1;
            int[] hist = new int[bins];
            for (Sample s : samples) {
                int b = (int) Math.floor((s.distance - MIN_RADIUS) / BIN_WIDTH);
                if (b >= 0 && b < bins) {
                    hist[b]++;
                }
            }
            int peak = 0;
            for (int b = 1; b < bins; b++) {
                if (hist[b] > hist[peak]) {
                    peak = b;
                }
            }
            int peakCount = hist[peak]
                    + (peak > 0 ? hist[peak - 1] : 0)
                    + (peak < bins - 1 ? hist[peak + 1] : 0);
            if (peakCount < MIN_PEAK_SAMPLES || (double) peakCount / samples.size() < PEAK_SHARE) {
                clearEstimate();
                return;
            }

            double lo = MIN_RADIUS + (peak - 1) * BIN_WIDTH;
            double hi = MIN_RADIUS + (peak + 2) * BIN_WIDTH;
            List<Sample> ring = new ArrayList<>();
            for (Sample s : samples) {
                if (s.distance >= lo && s.distance <= hi) {
                    ring.add(s);
                }
            }
            double[] ds = new double[ring.size()];
            double[] ys = new double[ring.size()];
            for (int i = 0; i < ring.size(); i++) {
                ds[i] = ring.get(i).distance;
                ys[i] = ring.get(i).y;
            }
            Arrays.sort(ds);
            Arrays.sort(ys);
            radius = (float) Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, ds[ds.length / 2]));
            baseY = (float) (ys[ys.length / 2] - 0.1);
            // A hotspot's size never changes over its lifetime: freeze the estimate
            // and drop the samples so we never recompute or sample again.
            locked = true;
            samples.clear();
        }

        private void clearEstimate() {
            radius = -1.0f;
            baseY = Float.NaN;
        }
    }

    private static final class Sample {
        final String type;
        final double distance;
        final double y;
        final long ms;

        Sample(String type, double distance, double y, long ms) {
            this.type = type;
            this.distance = distance;
            this.y = y;
            this.ms = ms;
        }
    }

    private static final Map<Long, Hotspot> HOTSPOTS = new HashMap<>();
    private static ClientLevel scanLevel;
    private static long lastScanMs;

    private HotspotTracker() {
    }

    /** Per-frame update from the renderer: refresh centres, prune and re-estimate. */
    public static void update(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null) {
            clear();
            return;
        }
        if (level != scanLevel) {
            HOTSPOTS.clear();
            scanLevel = level;
            lastScanMs = 0;
        }
        long now = System.currentTimeMillis();
        if (now - lastScanMs >= SCAN_INTERVAL_MS) {
            lastScanMs = now;
            scanMarkers(level, now);
        }
        Iterator<Map.Entry<Long, Hotspot>> it = HOTSPOTS.entrySet().iterator();
        while (it.hasNext()) {
            Hotspot h = it.next().getValue();
            if (now - h.lastSeen > HOTSPOT_TTL_MS) {
                it.remove();
                continue;
            }
            h.refresh(now);
        }
    }

    /** Clears all state (feature disabled / not on SkyBlock / level changed). */
    public static void clear() {
        if (!HOTSPOTS.isEmpty()) {
            HOTSPOTS.clear();
        }
        scanLevel = null;
        lastScanMs = 0;
    }

    /**
     * Called for every received particle (main thread: injected at the packet
     * handler's TAIL, after the same-thread check). Attributes the particle to the
     * nearest hotspot centre and records its horizontal distance.
     */
    public static void onParticle(double x, double y, double z, String type) {
        if (HOTSPOTS.isEmpty() || !ConfigManager.get().hotspotOverlayEnabled) {
            return;
        }
        // Only the two ring particle types ever form a circle; reject everything else
        // (bobber splashes, spawn bursts, other mods' particles). Empty until the two
        // IDs are identified — then all types pass and the histogram peak below still
        // rejects scattered interference.
        if (!RING_PARTICLE_TYPES.isEmpty() && !RING_PARTICLE_TYPES.contains(type)) {
            return;
        }
        Hotspot best = null;
        double bestD2 = Double.MAX_VALUE;
        for (Hotspot h : HOTSPOTS.values()) {
            // Locked hotspots must still participate in nearest-centre attribution. Otherwise
            // their ring particles are handed to a nearby unlocked hotspot and pollute its
            // distance histogram, so two close hotspots can leave one with no estimated radius.
            double dx = x - h.x;
            double dz = z - h.z;
            double d2 = dx * dx + dz * dz;
            if (d2 < bestD2) {
                bestD2 = d2;
                best = h;
            }
        }
        if (best == null || best.locked) {
            return;
        }
        double d = Math.sqrt(bestD2);
        if (d < MIN_RADIUS || d > MAX_RADIUS) {
            return;
        }
        // Only surface-level particles define the ring's height.
        if (Math.abs(y - best.anchorY) > 5.0) {
            return;
        }
        best.samples.add(new Sample(type, d, y, System.currentTimeMillis()));
        best.dirty = true;
    }

    /** Hotspots with an estimated radius, for rendering. */
    public static List<Hotspot> active() {
        List<Hotspot> out = new ArrayList<>(HOTSPOTS.size());
        for (Hotspot h : HOTSPOTS.values()) {
            if (h.radius > 0) {
                out.add(h);
            }
        }
        return out;
    }

    /** Scans for "HOTSPOT" markers (an armour stand with a custom name, or a text
     *  display fallback), and assigns each one its kind from the stat line rendered
     *  on a stacked marker (e.g. "+5 Sea Creature Chance"). Uses a player-centred
     *  bounding-box query rather than {@code entitiesForRendering()} so markers are
     *  not missed when the renderer culls them. */
    private static void scanMarkers(ClientLevel level, long now) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        AABB area = mc.player.getBoundingBox().inflate(ConfigManager.get().hotspotOverlayRange);
        List<Entity> entities = level.getEntities(mc.player, area,
                e -> e instanceof ArmorStand || e instanceof Display.TextDisplay);
        List<Entity> hotspotMarkers = new ArrayList<>();
        List<Entity> statMarkers = new ArrayList<>();
        List<HotspotType> statTypes = new ArrayList<>();
        for (Entity e : entities) {
            String text = null;
            if (e instanceof Display.TextDisplay display) {
                Component c = ((TextDisplayAccessor) display).sky$getText();
                text = c == null ? null : c.getString();
            } else if (e instanceof ArmorStand stand) {
                Component name = stand.getCustomName();
                text = name == null ? null : name.getString();
            } else {
                continue;
            }
            if (text == null) {
                continue;
            }
            if (text.toUpperCase().contains("HOTSPOT")) {
                hotspotMarkers.add(e);
            } else {
                HotspotType t = HotspotType.fromText(text);
                if (t != null) {
                    statMarkers.add(e);
                    statTypes.add(t);
                }
            }
        }
        for (Entity e : hotspotMarkers) {
            long key = key(e.getX(), e.getZ());
            Hotspot h = HOTSPOTS.get(key);
            if (h == null) {
                h = new Hotspot(e.getX(), e.getY(), e.getZ());
                HOTSPOTS.put(key, h);
            }
            h.lastSeen = now;
            h.type = nearestType(statMarkers, statTypes, e.getX(), e.getZ());
        }
    }

    /** Nearest stat-line marker within {@code TYPE_MATCH_RANGE} blocks of the
     *  "HOTSPOT" marker (the stat line is stacked at the same spot). */
    private static HotspotType nearestType(List<Entity> markers, List<HotspotType> types, double x, double z) {
        HotspotType best = null;
        double bestD2 = TYPE_MATCH_RANGE * TYPE_MATCH_RANGE;
        for (int i = 0; i < markers.size(); i++) {
            Entity e = markers.get(i);
            double dx = e.getX() - x;
            double dz = e.getZ() - z;
            double d2 = dx * dx + dz * dz;
            if (d2 <= bestD2) {
                bestD2 = d2;
                best = types.get(i);
            }
        }
        return best;
    }

    /** Quarter-block quantised centre key, so re-scans find the same hotspot. */
    private static long key(double x, double z) {
        return (Math.round(x * 4.0) << 32) | (Math.round(z * 4.0) & 0xFFFFFFFFL);
    }
}
