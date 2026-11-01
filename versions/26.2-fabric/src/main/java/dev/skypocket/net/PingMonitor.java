package dev.skypocket.net;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.ping.ClientboundPongResponsePacket;
import net.minecraft.network.protocol.ping.ServerboundPingRequestPacket;
import net.minecraft.util.Util;

/**
 * Measures real round-trip latency using the vanilla 26.2 ping/pong protocol.
 *
 * <p>The client sends a {@link ServerboundPingRequestPacket} carrying a timestamp; the
 * server echoes it back in a {@link ClientboundPongResponsePacket}. Subtracting the
 * echoed timestamp from the receive time gives the RTT. This runs on the existing
 * gameplay connection (no extra connection), so it works — and is safe — on servers
 * like Hypixel that never update the local player's tab-list latency.
 */
public final class PingMonitor {
    /** Ping every 60 ticks (once every 3 seconds). */
    private static final int PING_INTERVAL_TICKS = 60;
    /**
     * EMA smoothing factor in (0, 1]. Lower = smoother but slower to react to real
     * latency changes; higher = more responsive but jitterier. 0.2 damps the per-second
     * RTT jitter and one-off spikes while still tracking genuine changes within a few
     * seconds.
     */
    private static final float EMA_ALPHA = 0.2f;

    /**
     * Rolling window of raw RTT samples used by {@link #minLatency()}. The minimum of
     * the last few seconds is a far better estimate of the pure round trip than the
     * average is: a pong is answered on a server tick, so any average carries server
     * load / TPS dips that have nothing to do with the network path.
     */
    private static final int MIN_WINDOW = 5;
    private static final long[] recentRtt = new long[MIN_WINDOW];
    private static int recentCount;
    private static int recentIndex;

    private static int tickCounter = 0;
    private static float smoothedLatency = -1f;

    private PingMonitor() {
    }

    /** Call once per client tick; sends a ping request on the configured interval. */
    public static void onClientTick(Minecraft mc) {
        if (mc.getConnection() == null || mc.player == null) {
            smoothedLatency = -1f;
            tickCounter = 0;
            return;
        }
        if (++tickCounter >= PING_INTERVAL_TICKS) {
            tickCounter = 0;
            mc.getConnection().send(new ServerboundPingRequestPacket(Util.getMillis()));
        }
    }

    /** Call when a pong response arrives. */
    public static void onPong(ClientboundPongResponsePacket packet) {
        long rtt = Util.getMillis() - packet.time();
        int raw = rtt < 0 ? 0 : (int) rtt;
        if (smoothedLatency < 0f) {
            smoothedLatency = raw;
        } else {
            smoothedLatency = smoothedLatency * (1f - EMA_ALPHA) + raw * EMA_ALPHA;
        }
        recentRtt[recentIndex] = raw;
        recentIndex = (recentIndex + 1) % MIN_WINDOW;
        if (recentCount < MIN_WINDOW) {
            recentCount++;
        }
    }

    /** Smoothed latency in ms, or -1 if none has been measured yet. */
    public static int latency() {
        return smoothedLatency < 0f ? -1 : Math.round(smoothedLatency);
    }

    /**
     * Lowest RTT of the last few samples, or {@link #latency()} when there are none.
     * Used for latency compensation, where an over-estimated round trip would make a
     * prediction fire too early: the minimum errs on the safe (late) side and is much
     * steadier than the average.
     */
    public static int minLatency() {
        if (recentCount == 0) {
            return latency();
        }
        long min = Long.MAX_VALUE;
        for (int i = 0; i < recentCount; i++) {
            min = Math.min(min, recentRtt[i]);
        }
        return (int) min;
    }
}
