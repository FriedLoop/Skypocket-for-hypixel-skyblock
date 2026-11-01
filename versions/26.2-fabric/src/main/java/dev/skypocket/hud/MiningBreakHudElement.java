package dev.skypocket.hud;

import dev.skypocket.SkyPocketMod;
import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;
import dev.skypocket.net.PingMonitor;
import dev.skypocket.net.SkyBlockDetector;
import dev.skypocket.render.ColorUtil;
import dev.skypocket.render.HudText;
import dev.skypocket.skyblock.MiningBreakTracker;
import dev.skypocket.skyblock.MiningStrengthTable;
import dev.skypocket.skyblock.MiningSpeedTracker;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * Draws the break prediction around the crosshair: a circular progress ring whose
 * colour runs from the "start" colour to the "end" colour as the percentage grows.
 *
 * <p>It also drives {@link MiningBreakTracker}, because the block outline is rendered
 * from the same state — so the tracker ticks once per frame even when the ring itself
 * is toggled off.
 */
public final class MiningBreakHudElement {
    private static final Identifier ID = Identifier.fromNamespaceAndPath(SkyPocketMod.MOD_ID, "mining_break");
    /** Ring segments; enough for a smooth circle at any radius. */
    private static final int SEGMENTS = 72;

    private MiningBreakHudElement() {
    }

    public static void register() {
        HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, ID, MiningBreakHudElement::render);
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        ModConfig cfg = ConfigManager.get();
        Minecraft mc = Minecraft.getInstance();
        if (!cfg.miningBreakPredictEnabled || mc.player == null) {
            MiningBreakTracker.reset();
            MiningSpeedTracker.reset();
            return;
        }
        MiningSpeedTracker.update(mc, System.currentTimeMillis());
        if (!SkyBlockDetector.isActive()) {
            MiningBreakTracker.reset();
            return;
        }
        MiningBreakTracker.update(mc);

        float progress = MiningBreakTracker.progress();
        if (cfg.miningBreakRingEnabled && progress >= 0f) {
            drawRing(graphics, cfg, progress, MiningBreakTracker.alpha());
        }
        if (cfg.miningBreakDebugHud) {
            drawDebug(graphics, mc);
        }
    }

    private static void drawRing(GuiGraphicsExtractor graphics, ModConfig cfg, float progress, float alpha) {
        int cx = graphics.guiWidth() / 2;
        int cy = graphics.guiHeight() / 2;
        int radius = Math.max(4, Math.round(cfg.miningBreakRingRadius));
        int thickness = Math.max(1, Math.round(cfg.miningBreakRingWidth));
        int color = lerp(cfg.miningBreakRingColorStart, cfg.miningBreakRingColorEnd, progress, alpha);

        // Filled arc from the top, clockwise; small quads so the ring has no gaps.
        int last = Math.max(1, Math.round(SEGMENTS * Math.min(1f, progress)));
        for (int i = 0; i < last; i++) {
            double a0 = -Math.PI / 2 + 2 * Math.PI * i / SEGMENTS;
            double a1 = -Math.PI / 2 + 2 * Math.PI * (i + 1) / SEGMENTS;
            int x0 = cx + (int) Math.round(Math.cos(a0) * radius);
            int y0 = cy + (int) Math.round(Math.sin(a0) * radius);
            int x1 = cx + (int) Math.round(Math.cos(a1) * radius);
            int y1 = cy + (int) Math.round(Math.sin(a1) * radius);
            graphics.fill(Math.min(x0, x1), Math.min(y0, y1),
                    Math.max(x0, x1) + thickness, Math.max(y0, y1) + thickness, color);
        }
    }

    /** Progress colour: linearly between the two configured colours. */
    private static int lerp(int from, int to, float t, float alpha) {
        float k = Math.max(0f, Math.min(1f, t));
        int a = Math.round((ColorUtil.alpha(from) + (ColorUtil.alpha(to) - ColorUtil.alpha(from)) * k) * alpha);
        int r = Math.round(ColorUtil.red(from) + (ColorUtil.red(to) - ColorUtil.red(from)) * k);
        int g = Math.round(ColorUtil.green(from) + (ColorUtil.green(to) - ColorUtil.green(from)) * k);
        int b = Math.round(ColorUtil.blue(from) + (ColorUtil.blue(to) - ColorUtil.blue(from)) * k);
        return (Math.max(0, Math.min(255, a)) << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * Calibration readout: the learned duration must NOT drift with the ping (if it
     * does, the RTT term in the model is wrong), and the error should hover near zero.
     */
    private static void drawDebug(GuiGraphicsExtractor graphics, Minecraft mc) {
        String block = MiningBreakTracker.debugBlock();
        int x = 4;
        int y = 4;
        int step = 12;
        int color = 0xFFFFFF00;
        HudText.draw(graphics, ConfigManager.get(), "ping " + PingMonitor.latency() + "ms min "
                + PingMonitor.minLatency() + "ms", x, y, color, true);
        y += step;
        HudText.draw(graphics, ConfigManager.get(), "speed " + fmt0(MiningSpeedTracker.rawSpeed()) + " settled "
                + fmt0(MiningSpeedTracker.settledSpeed()), x, y, color, true);
        y += step;
        HudText.draw(graphics, ConfigManager.get(), "scale " + MiningBreakTracker.debugScales()
                + "  ph " + MiningBreakTracker.debugPhase()
                + "  rtt " + fmt0(MiningBreakTracker.debugRttMs()) + "ms"
                + "  eff " + fmt0(MiningBreakTracker.debugEffectiveSpeed()), x, y, color, true);
        y += step;
        HudText.draw(graphics, ConfigManager.get(), "speed the last break implies " + fmt0(MiningBreakTracker.debugImpliedSpeed()),
                x, y, color, true);
        y += step;
        // After the break the live fields are cleared; fall back to the cycle that just finished.
        String shownBlock = block != null ? block : MiningBreakTracker.debugLastBlock();
        HudText.draw(graphics, ConfigManager.get(), "block " + (shownBlock == null ? "-" : shownBlock)
                + "  t " + MiningBreakTracker.debugLastTicks(), x, y, color, true);
        y += step;
        HudText.draw(graphics, ConfigManager.get(), "model " + fmt(MiningBreakTracker.debugPredicted()) + "s  assert "
                + fmt0(MiningBreakTracker.debugAssertion()) + "ms", x, y, color, true);
        y += step;
        HudText.draw(graphics, ConfigManager.get(), "observed " + fmt(MiningBreakTracker.debugObserved()) + "s", x, y, color, true);
        y += step;
        HudText.draw(graphics, ConfigManager.get(), "error " + fmt(MiningBreakTracker.debugError()) + "s", x, y, color, true);
        y += step;
        HudText.draw(graphics, ConfigManager.get(), "strength " + MiningBreakTracker.debugStrength()
                + " (" + MiningBreakTracker.debugSource() + ")  implied "
                + fmt0(MiningBreakTracker.debugImpliedStrength()), x, y, color, true);
        y += step;
        HudText.draw(graphics, ConfigManager.get(), "resolved " + MiningStrengthTable.size()
                + "  breaks " + MiningBreakTracker.debugObservations()
                + "  capped " + MiningBreakTracker.debugCappedPredictions()
                + "  dropped " + MiningStrengthTable.droppedSamples(), x, y, color, true);
        y += step;
        // observed/model per duration bucket: falling = additive offset, flat = wrong speed.
        HudText.draw(graphics, ConfigManager.get(), "obs/model " + MiningBreakTracker.debugRatios()
                + "  (" + ConfigManager.get().miningTickRounding + ")", x, y, color, true);
    }

    private static String fmt(double v) {
        return v < 0 ? "-" : String.format("%.3f", v);
    }

    private static String fmt2(double v) {
        return v < 0 ? "-" : String.format("%.2f", v);
    }

    private static String fmt0(double v) {
        return v < 0 ? "-" : String.format("%.0f", v);
    }
}
