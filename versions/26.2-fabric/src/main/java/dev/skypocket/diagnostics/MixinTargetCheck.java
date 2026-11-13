package dev.skypocket.diagnostics;

import dev.skypocket.SkyPocketLog;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reports at startup which of the methods our {@code require = 0} injections hook are still present.
 *
 * <p>Most of this mod's injections are {@code require = 0} (see the note on
 * {@code EntityMixin}): when a target method is renamed or removed, the affected cosmetic feature
 * silently stops working instead of aborting mixin application. The cost of that choice is silence -
 * a player whose glow filter no longer applies cannot tell whether the toggle is broken or the hook
 * never landed. This check turns that silence into one WARN line naming the missing targets.
 *
 * <p>The two <em>core</em> injections stay at {@code require = 1} and are deliberately not listed
 * here: {@code GuiScreenScissorMixin} (the scissor stack must stay balanced) and
 * {@code GameRendererMixin#sky$bakePanelCache}. If those fail, mixin application fails loudly on its
 * own.
 *
 * <p>Diagnostic only. Every probe is wrapped, and a failure just skips the report: a broken
 * diagnostic must not become a new crash source.
 */
public final class MixinTargetCheck {
    private static final SkyPocketLog LOGGER = SkyPocketLog.of("mixin");

    /** Never instantiated. */
    private MixinTargetCheck() {
    }

    /**
     * Probe list: {@code label -> (class, method)}. Kept as class names rather than {@code Class}
     * references so that a class missing from this Minecraft build produces a report line instead of
     * a {@code NoClassDefFoundError} during our own class initialisation.
     */
    private static final String[][] TARGETS = {
            {"player/dragon/item glow", "net.minecraft.world.entity.Entity", "isCurrentlyGlowing"},
            {"hide players / presets / arrows", "net.minecraft.client.renderer.entity.EntityRenderer", "shouldRender"},
            {"vanilla HUD replacement", "net.minecraft.client.gui.Hud", "extractPlayerHealth"},
            {"hurt camera", "net.minecraft.client.renderer.GameRenderer", "bobHurt"},
            {"world post-processing", "net.minecraft.client.renderer.GameRenderer", "renderLevel"},
            {"third-person zoom", "net.minecraft.client.Camera", "getMaxZoom"},
            {"hand swing / attack animation", "net.minecraft.world.entity.LivingEntity", "getAttackAnim"},
            {"hand item transform", "net.minecraft.client.renderer.ItemInHandRenderer", "swingArm"},
            {"chat compression", "net.minecraft.client.gui.components.ChatComponent", "addMessageToQueue"},
            {"mouse scroll", "net.minecraft.client.MouseHandler", "onScroll"},
            {"keyboard input", "net.minecraft.client.player.KeyboardInput", "tick"},
            {"block break tracking", "net.minecraft.client.multiplayer.MultiPlayerGameMode", "startDestroyBlock"},
            {"stuck arrows", "net.minecraft.client.renderer.entity.layers.StuckInBodyLayer", "submit"},
            {"glyph texture", "net.minecraft.client.renderer.state.gui.GlyphRenderState", "textureSetup"},
    };

    /**
     * Logs one WARN naming every target that could not be found, or one INFO when all are present.
     *
     * <p>Called once from the client initialiser.
     */
    public static void report() {
        Map<String, String> missing = new LinkedHashMap<>();
        try {
            for (String[] target : TARGETS) {
                if (!hasMethod(target[1], target[2])) {
                    missing.put(target[0], target[1] + "#" + target[2]);
                }
            }
        } catch (Throwable t) {
            LOGGER.debug("mixin probe aborted: {}", t.toString());
            return;
        }
        if (missing.isEmpty()) {
            LOGGER.debug("all {} mixin targets present", TARGETS.length);
            return;
        }
        // One line, because a broken build would otherwise produce sixteen warnings at every start.
        LOGGER.warn("{} mixin target(s) not found in this Minecraft version; those features are disabled: {}",
                missing.size(), missing);
    }

    /** Whether {@code className} declares a method with this name. Never throws. */
    private static boolean hasMethod(String className, String methodName) {
        try {
            for (Method method : Class.forName(className).getDeclaredMethods()) {
                if (method.getName().equals(methodName)) {
                    return true;
                }
            }
            // Some targets are inherited (e.g. shouldRender on a subclass chain); walk up too.
            Class<?> current = Class.forName(className).getSuperclass();
            while (current != null) {
                for (Method method : current.getDeclaredMethods()) {
                    if (method.getName().equals(methodName)) {
                        return true;
                    }
                }
                current = current.getSuperclass();
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }
}
