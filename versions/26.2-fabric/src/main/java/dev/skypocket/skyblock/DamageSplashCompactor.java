package dev.skypocket.skyblock;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects SkyBlock damage splashes into one number per mob and damage type.
 *
 * <p>Every hit spawns its own invisible armor stand, so the vanilla numbers cannot be kept alive or
 * placed - the server owns both the lifetime and the position. The stand is therefore discarded as
 * soon as it is understood and the text is drawn by {@link DamageSplashRenderer} instead.
 *
 * <p>Types are not named anywhere in the game; only the number's colour tells them apart (a critical
 * hit additionally carries ✧/✯). Keying by the colour palette therefore separates every type without a
 * table, including types added by future updates.
 */
public final class DamageSplashCompactor {

    /** One merged number, owned by us from the moment its first splash arrives. */
    public static final class Run {
        public final int sourceId;
        public final boolean crit;
        public final String palette;
        public int slot;

        long sum;
        int count;
        long lastMs;

        /** Frozen once the mob dies: the death animation keeps moving the hitbox, and numbers sliding
         *  along with a corpse look wrong.
         */
        public Vec3 frozenAnchor;

        /** Cached text, rebuilt only when the number, the count or the fade changes. */
        Component text;
        long textSum = -1;
        int textCount = -1;
        int textAlpha = -1;

        Run(int sourceId, boolean crit, String palette, int slot) {
            this.sourceId = sourceId;
            this.crit = crit;
            this.palette = palette;
            this.slot = slot;
        }

        public long sum() {
            return sum;
        }

        public int count() {
            return count;
        }

        public long lastMs() {
            return lastMs;
        }
    }

    /** Key is {@code sourceEntityId|typeKey}. */
    private static final Map<String, Run> RUNS = new LinkedHashMap<>();

    /** Entity ids already handled, so a resent name packet is not counted twice. */
    private static final Map<Integer, Long> HANDLED = new HashMap<>();

    private DamageSplashCompactor() {
    }

    /** Called after vanilla applied an entity data packet. */
    public static void onEntityData(ClientboundSetEntityDataPacket packet) {
        ModConfig cfg = ConfigManager.get();
        if (!cfg.damageSplashCompactBlock.damageSplashEnabled) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        Entity entity = mc.level.getEntity(packet.id());
        if (!(entity instanceof ArmorStand stand)) {
            return;
        }
        Component name = stand.getCustomName();
        if (name == null) {
            return;
        }
        String plain = name.getString();
        if (!isDamage(plain)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (HANDLED.put(stand.getId(), now) != null) {
            return;
        }

        long value = digits(plain);
        boolean crit = plain.indexOf('✧') >= 0 || plain.indexOf('✯') >= 0;
        String palette = palette(name);

        // The vanilla stand is ours to remove either way: keeping it would show the unformatted number
        // and, worse, disappear when the server decides to clean it up.
        stand.discard();

        Entity source = source(mc, stand, cfg.damageSplashCompactBlock.damageSplashRadius);
        if (source == null) {
            return;
        }

        String typeKey = (crit ? "crit:" : "dmg:") + palette;
        // With compaction off every splash stays its own run, so nothing is added together; the id keeps
        // otherwise identical splashes from sharing a key.
        String key = cfg.damageSplashCompactBlock.damageSplashCompactEnabled
                ? source.getId() + "|" + typeKey
                : source.getId() + "|" + typeKey + "|#" + stand.getId();
        Run run = RUNS.get(key);
        if (run != null && now - run.lastMs > windowMs(cfg)) {
            RUNS.remove(key);
            run = null;
        }
        if (run == null) {
            RUNS.put(key, new Run(source.getId(), crit, palette, nextSlot(source.getId())));
            run = RUNS.get(key);
        }
        run.sum += value;
        run.count++;
        run.lastMs = now;
    }

    /** Drops runs whose number has faded out. Called every client tick. */
    public static void tick() {
        ModConfig cfg = ConfigManager.get();
        if (RUNS.isEmpty()) {
            return;
        }
        if (!cfg.damageSplashCompactBlock.damageSplashEnabled) {
            RUNS.clear();
            return;
        }
        long now = System.currentTimeMillis();
        long keep = lifetimeMs(cfg) + fadeMs(cfg);
        Iterator<Map.Entry<String, Run>> it = RUNS.entrySet().iterator();
        List<String> stale = new ArrayList<>();
        while (it.hasNext()) {
            Map.Entry<String, Run> entry = it.next();
            if (now - entry.getValue().lastMs > keep) {
                stale.add(entry.getKey());
            }
        }
        for (String key : stale) {
            RUNS.remove(key);
        }
        HANDLED.entrySet().removeIf(entry -> now - entry.getValue() > 10_000L);
    }

    public static void clear() {
        RUNS.clear();
        HANDLED.clear();
    }

    /** Live runs, for the renderer. */
    public static Collection<Run> runs() {
        return RUNS.values();
    }

    /**
     * World position of one row of a mob's numbers.
     *
     * <p>The anchor is the mob's own right side, not the world's +X: a hitbox is axis aligned and never
     * rotates, so "right" has to come from the yaw. The offset is pushed out by the box's extent along
     * that direction, otherwise a mob facing diagonally would have its numbers sink into the box.
     *
     * <p>Rows are centred on the middle of the hitbox: anchoring the first row there and stacking down
     * pushed the later types towards the mob's feet.
     */
    public static Vec3 anchor(Entity source, float partialTick, int index, int total) {
        ModConfig cfg = ConfigManager.get();
        AABB box = source.getBoundingBox();
        double centerX = (box.minX + box.maxX) / 2.0;
        double centerZ = (box.minZ + box.maxZ) / 2.0;
        double centerY = box.minY + box.getYsize() / 2.0;

        double yaw = Math.toRadians(source.getYRot(partialTick));
        double facingX = -Math.sin(yaw);
        double facingZ = Math.cos(yaw);
        double rightX = facingZ;
        double rightZ = -facingX;

        double half = Math.abs(rightX) * box.getXsize() / 2.0 + Math.abs(rightZ) * box.getZsize() / 2.0;
        double push = half + cfg.damageSplashCompactBlock.damageSplashRightOffset;

        double rowHeight = cfg.damageSplashCompactBlock.damageSplashRowHeight;
        double stack = (total - 1) * rowHeight / 2.0;
        double y = centerY + stack - index * rowHeight;

        return new Vec3(centerX + rightX * push, y, centerZ + rightZ * push);
    }

    /** 1 while the number is fresh, falling to 0 over the last {@code fadeMs}. */
    public static float alphaOf(Run run) {
        ModConfig cfg = ConfigManager.get();
        long age = System.currentTimeMillis() - run.lastMs;
        long fade = fadeMs(cfg);
        long lifetime = lifetimeMs(cfg);
        if (age <= lifetime) {
            return 1f;
        }
        float left = (lifetime + fade - age) / (float) fade;
        return Math.max(0f, Math.min(1f, left));
    }

    /**
     * Nearest entity the splash can belong to.
     *
     * <p>Real players are excluded: a bystander is usually the closest thing horizontally, and real
     * players never receive a splash of their own. Player-shaped NPCs are not real players by
     * {@link PlayerLookalike} and stay eligible, which matters on servers that dress mobs as players.
     */
    private static Entity source(Minecraft mc, Entity splash, double radius) {
        AABB area = splash.getBoundingBox().inflate(radius, 4.0, radius);
        List<Entity> candidates = mc.level.getEntities(splash, area, entity -> entity.isAlive()
                && entity instanceof LivingEntity
                && !(entity instanceof ArmorStand)
                && entity != mc.player
                && !PlayerLookalike.isRealPlayer(entity));
        Entity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity candidate : candidates) {
            if (Math.abs(candidate.getY() - splash.getY()) > 4.0) {
                continue;
            }
            double distance = Math.hypot(candidate.getX() - splash.getX(),
                    candidate.getZ() - splash.getZ());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    /** Row index of this type within its mob, in order of first appearance. */
    private static int nextSlot(int sourceId) {
        boolean[] used = new boolean[8];
        for (Map.Entry<String, Run> entry : RUNS.entrySet()) {
            if (entry.getKey().startsWith(sourceId + "|") && entry.getValue().slot < used.length) {
                used[entry.getValue().slot] = true;
            }
        }
        for (int i = 0; i < used.length; i++) {
            if (!used[i]) {
                return i;
            }
        }
        return 0;
    }

    /**
     * The Component to draw for a run, cached until the number, the count or the fade changes.
     */
    public static Component textFor(Run run) {
        if (run.text != null && run.textSum == run.sum && run.textCount == run.count) {
            return run.text;
        }
        ModConfig cfg = ConfigManager.get();
        String number = cfg.damageSplashCompactBlock.damageSplashUppercase ? format(run.sum)
                : String.format(java.util.Locale.ROOT, "%,d", run.sum);
        String suffix = cfg.damageSplashCompactBlock.damageSplashShowCount && run.count > 1 ? " x" + run.count : "";
        net.minecraft.network.chat.MutableComponent built;
        if (run.crit) {
            built = net.minecraft.network.chat.Component.literal("✧").withColor(0xFFFFFF);
            String marked = number + suffix;
            for (int i = 0; i < marked.length(); i++) {
                float position = marked.length() <= 1 ? 0f : i / (float) (marked.length() - 1);
                built.append(net.minecraft.network.chat.Component.literal(String.valueOf(marked.charAt(i)))
                        .withColor(gradient(position)));
            }
            built.append(net.minecraft.network.chat.Component.literal("✧").withColor(0xFFFFFF));
        } else {
            built = net.minecraft.network.chat.Component.literal(number + suffix)
                    .withStyle(style -> style.withColor(colorOf(run.palette)));
        }
        run.text = built;
        run.textSum = run.sum;
        run.textCount = run.count;
        return built;
    }

    /** White to red, the direction the game's own critical colours run in. */
    public static int gradient(float t) {
        int from = 0xFFFFFF;
        int to = 0xFF5555;
        int r = (int) (((from >> 16) & 0xFF) * (1 - t) + ((to >> 16) & 0xFF) * t);
        int g = (int) (((from >> 8) & 0xFF) * (1 - t) + ((to >> 8) & 0xFF) * t);
        int b = (int) ((from & 0xFF) * (1 - t) + (to & 0xFF) * t);
        return (r << 16) | (g << 8) | b;
    }

    static long windowMs(ModConfig cfg) {
        return Math.max(200, cfg.damageSplashCompactBlock.damageSplashWindowMs);
    }

    private static long lifetimeMs(ModConfig cfg) {
        return Math.max(200, cfg.damageSplashCompactBlock.damageSplashLifetimeMs);
    }

    private static long fadeMs(ModConfig cfg) {
        return Math.max(0, Math.min(cfg.damageSplashCompactBlock.damageSplashFadeMs, lifetimeMs(cfg)));
    }

    /** Only a name that is nothing but a damage number counts; name plates must not. */
    private static boolean isDamage(String plain) {
        return plain.matches("[✧✯]?[0-9][0-9,]*[✧✯]?[⚔+❤☄♞✷\uFB17]?");
    }

    private static long digits(String plain) {
        String only = plain.replaceAll("[^0-9]", "");
        if (only.isEmpty()) {
            return 0;
        }
        try {
            return Long.parseLong(only);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Sorted distinct colours, joined with {@code +}; this is the damage type. */
    private static String palette(Component name) {
        java.util.TreeSet<String> unique = new java.util.TreeSet<>();
        collect(name, unique);
        for (Component sibling : name.getSiblings()) {
            collect(sibling, unique);
        }
        return String.join("+", unique);
    }

    private static void collect(Component part, java.util.Set<String> out) {
        TextColor color = part.getStyle().getColor();
        if (color != null) {
            out.add(String.format("%06X", color.getValue() & 0xFFFFFF));
        }
    }

    /** The first palette colour that is not white (white belongs to the critical markers). */
    public static int colorOf(String palette) {
        if (palette != null && !palette.isEmpty()) {
            for (String part : palette.split("\\+")) {
                if (!"FFFFFF".equals(part)) {
                    return Integer.parseInt(part, 16);
                }
            }
        }
        return 0xFFFFFF;
    }

    /**
     * K/M/B/T with at most two decimals, the shape Skyblocker uses.
     *
     * <p>Truncating rather than rounding means a growing sum never appears to shrink.
     */
    public static String format(long damage) {
        if (damage < 1000) {
            return Long.toString(damage);
        }
        if (damage < 1_000_000) {
            return trim(damage / 1_000.0) + "k";
        }
        if (damage < 1_000_000_000L) {
            return trim(damage / 1_000_000.0) + "M";
        }
        if (damage < 1_000_000_000_000L) {
            return trim(damage / 1_000_000_000.0) + "B";
        }
        return trim(damage / 1_000_000_000_000.0) + "T";
    }

    private static String trim(double value) {
        String text = String.format(java.util.Locale.ROOT, "%.2f", value);
        while (text.endsWith("0")) {
            text = text.substring(0, text.length() - 1);
        }
        if (text.endsWith(".")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }
}
