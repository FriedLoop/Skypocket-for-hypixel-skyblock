package dev.skypocket.hud;

import java.util.ArrayList;
import java.util.List;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;
import dev.skypocket.net.PingMonitor;
import dev.skypocket.render.HudText;
import dev.skypocket.render.TpsEstimator;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

/**
 * On-screen FPS / TPS / ping readout, styled and positionable like the clock.
 */
public final class FpsTpsPingHudElement {
    private static final Identifier ID = Identifier.fromNamespaceAndPath("skypocket", "fps_tps_ping");

    private FpsTpsPingHudElement() {
    }

    public static void register() {
        HudElementRegistry.addLast(ID, FpsTpsPingHudElement::render);
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        ModConfig cfg = ConfigManager.get();
        HudPositionSync.sync(Minecraft.getInstance(), cfg);
        if (!cfg.fpsTpsPingBlock.fpsTpsPingEnabled) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();

        String[] lines = layoutLines(mc, cfg);

        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(cfg.fpsTpsPingBlock.fpsTpsPingX, cfg.fpsTpsPingBlock.fpsTpsPingY);
        pose.scale(cfg.fpsTpsPingBlock.fpsTpsPingScale, cfg.fpsTpsPingBlock.fpsTpsPingScale);
        int y = 0;
        int lineH = lineHeight(mc, cfg);
        for (String line : lines) {
            HudText.draw(graphics, cfg, line, 0, y, 0xFFFFFFFF, true, cfg.fpsTpsPingBlock.fpsTpsPingScale);
            y += lineH;
        }
        pose.popMatrix();
    }

    /** Lines as actually laid out: three stacked lines, or one joined horizontal line. */
    public static String[] layoutLines(Minecraft mc, ModConfig cfg) {
        String[] lines = lines(mc, cfg);
        if (cfg.fpsTpsPingBlock.fpsTpsPingVertical) {
            return lines;
        }
        return new String[]{String.join("   ", lines)};
    }

    /** Unscaled height of a single laid-out line (horizontal layout has no inter-line gap). */
    public static int lineHeight(Minecraft mc, ModConfig cfg) {
        // The parameter stays for the position editor's call shape; the font is chosen centrally.
        return cfg.fpsTpsPingBlock.fpsTpsPingVertical ? HudText.lineHeight(cfg) + 2 : HudText.lineHeight(cfg);
    }

    /** The display lines for the enabled readouts, built from live client data. */
    public static String[] lines(Minecraft mc, ModConfig cfg) {
        List<String> result = new ArrayList<>();
        if (cfg.fpsTpsPingBlock.fpsTpsPingShowFps) {
            result.add("FPS: " + mc.getFps());
        }
        if (cfg.fpsTpsPingBlock.fpsTpsPingShowTps) {
            result.add("TPS: " + String.format("%.1f", TpsEstimator.tps()));
        }
        if (cfg.fpsTpsPingBlock.fpsTpsPingShowPing) {
            int ping = ping(mc);
            result.add(ping < 0 ? "Ping: N/A" : "Ping: " + ping + "ms");
        }
        return result.toArray(new String[0]);
    }

    /**
     * Current ping in ms, or -1 when unknown. Prefers the real-time RTT measured via
     * the vanilla ping/pong protocol (works on servers like Hypixel that never update
     * the local player's tab latency), then falls back to tab latency and finally to
     * the server-list ping captured when the server was joined.
     */
    private static int ping(Minecraft mc) {
        if (mc.player == null) {
            return -1;
        }
        int measured = PingMonitor.latency();
        if (measured >= 0) {
            return measured;
        }
        if (mc.getConnection() != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
            if (info != null && info.getLatency() > 1) {
                return info.getLatency();
            }
        }
        ServerData server = mc.getCurrentServer();
        if (server != null && server.ping > 0) {
            return (int) server.ping;
        }
        return -1;
    }
}
