package dev.skypocket.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;

/**
 * Server TPS for the HUD.
 *
 * <p>In singleplayer the real tick time is read live from the integrated server.
 * In multiplayer the server does not broadcast TPS, so it is estimated from the
 * {@code ClientboundSetTimePacket} game-time delta (each packet carries the server's
 * authoritative game time; its rate of advance over wall-clock time is the TPS).
 */
public final class TpsEstimator {
    private static float estimatedTps = 20.0f;
    private static long lastServerGameTime = -1L;
    private static long lastNanos = -1L;

    private TpsEstimator() {
    }

    /** Called on every {@code ClientboundSetTimePacket} (client packet listener). */
    public static void onServerTime(long gameTime) {
        long now = System.nanoTime();
        if (lastServerGameTime >= 0 && now > lastNanos) {
            double seconds = (now - lastNanos) / 1_000_000_000.0;
            if (seconds >= 0.1) {
                long delta = gameTime - lastServerGameTime;
                estimatedTps = (float) (delta / seconds);
            }
        }
        lastServerGameTime = gameTime;
        lastNanos = now;
    }

    /** Current TPS: real value in singleplayer, estimate in multiplayer. */
    public static float tps() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.hasSingleplayerServer()) {
            IntegratedServer server = mc.getSingleplayerServer();
            if (server != null) {
                long nanos = server.getAverageTickTimeNanos();
                if (nanos > 0) {
                    return Math.max(0.0f, Math.min(20.0f, (float) (1_000_000_000L / (double) nanos)));
                }
            }
        }
        return Math.max(0.0f, Math.min(20.0f, estimatedTps));
    }
}
