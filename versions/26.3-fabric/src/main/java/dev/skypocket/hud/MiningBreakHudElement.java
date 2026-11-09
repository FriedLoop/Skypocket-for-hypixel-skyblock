package dev.skypocket.hud;

import dev.skypocket.SkyPocketMod;
import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;
import dev.skypocket.net.PingMonitor;
import dev.skypocket.net.SkyBlockDetector;
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
 * Drives {@link MiningBreakTracker} once per frame and draws its optional debug readout.
 */
public final class MiningBreakHudElement {
    private static final Identifier ID = Identifier.fromNamespaceAndPath(SkyPocketMod.MOD_ID, "mining_break");

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

        if (cfg.miningBreakDebugHud) {
            drawDebug(graphics, mc);
        }
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
