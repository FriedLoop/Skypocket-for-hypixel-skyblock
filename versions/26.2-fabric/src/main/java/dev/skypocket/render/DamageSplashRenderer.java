package dev.skypocket.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;
import dev.skypocket.skyblock.DamageSplashCompactor;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws the merged damage numbers.
 *
 * <p>The vanilla numbers are armor stands, which the server positions and removes on its own schedule,
 * so they cannot hold a merged value: the collect pass throws them away and this draws the text. That
 * also means the lifetime, the fade and the position are ours, and nothing fights the server over the
 * entity's position (which used to show up as the text drifting away).
 *
 * <p>Text is submitted as a camera-facing billboard in the gizmo phase, with normal depth testing, so a
 * number behind a wall stays hidden without any distance cutoff.
 */
public final class DamageSplashRenderer {

    /** Matches the scale vanilla nametags use. */
    private static final float SCALE = 0.025f;

    /** Packed light: full block light, full sky light - 26.x no longer ships a named constant. */
    private static final int FULL_BRIGHT = 0xF000F0;

    private DamageSplashRenderer() {
    }

    public static void init() {
        LevelRenderEvents.BEFORE_GIZMOS.register(DamageSplashRenderer::render);
    }

    private static void render(LevelRenderContext context) {
        ModConfig cfg = ConfigManager.get();
        if (!cfg.damageSplashCompactBlock.damageSplashEnabled) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || DamageSplashCompactor.runs().isEmpty()) {
            return;
        }

        SubmitNodeCollector collector = context.submitNodeCollector();
        PoseStack poseStack = context.poseStack();
        Vec3 camera = context.levelState().cameraRenderState.pos;
        org.joml.Quaternionf rotation = context.levelState().cameraRenderState.orientation;
        Font font = mc.font;

        // Entity positions only advance on the game tick; interpolating by the frame's partial tick is
        // what keeps the anchor from stepping once per tick.
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);

        // Rows are laid out per mob, because the group is centred on the hitbox and that depends on how
        // many types that mob currently shows.
        Map<Integer, List<DamageSplashCompactor.Run>> bySource = new HashMap<>();
        for (DamageSplashCompactor.Run run : DamageSplashCompactor.runs()) {
            bySource.computeIfAbsent(run.sourceId, ignored -> new ArrayList<>()).add(run);
        }

        for (Map.Entry<Integer, List<DamageSplashCompactor.Run>> entry : bySource.entrySet()) {
            Entity source = mc.level.getEntity(entry.getKey());
            if (source == null) {
                continue;
            }
            List<DamageSplashCompactor.Run> group = entry.getValue();
            group.sort((a, b) -> Integer.compare(a.slot, b.slot));

            for (int index = 0; index < group.size(); index++) {
                DamageSplashCompactor.Run run = group.get(index);
                float alpha = DamageSplashCompactor.alphaOf(run);
                if (alpha <= 0.02f) {
                    continue;
                }
                Vec3 anchor = anchorFor(run, source, partialTick, index, group.size());

                Component text = DamageSplashCompactor.textFor(run);
                FormattedCharSequence sequence = text.getVisualOrderText();

                poseStack.pushPose();
                poseStack.translate(anchor.x - camera.x, anchor.y - camera.y, anchor.z - camera.z);
//#if MC>=260300
                // 26.3 moved the quaternion helper from mulPose to rotate.
                poseStack.rotate(rotation);
//#else
                poseStack.mulPose(rotation);
//#endif
                poseStack.scale(SCALE, -SCALE, SCALE);
                float x = -font.width(text) / 2f;
                // Alpha rides on the colour argument: the per-character styles carry the damage type's
                // colour, so the fade has to come from somewhere else.
                int argb = ((int) (alpha * 255f) << 24) | 0xFFFFFF;
                collector.submitText(poseStack, x, 0f, sequence, true, Font.DisplayMode.NORMAL,
                        FULL_BRIGHT, argb, 0, 0);
                poseStack.popPose();
            }
        }
    }

    /**
     * Where one row goes, frozen at the moment the mob dies.
     *
     * <p>A dying mob keeps moving through its death animation, and numbers sliding along with the corpse
     * read as a glitch; the last live position is kept instead.
     */
    private static Vec3 anchorFor(DamageSplashCompactor.Run run, Entity source, float partialTick,
                                  int index, int total) {
        if (run.frozenAnchor != null) {
            return run.frozenAnchor;
        }
        Vec3 anchor = DamageSplashCompactor.anchor(source, partialTick, index, total);
        // 26.x has no isDeadOrDying on Entity; a dead mob simply stops being alive.
        if (!source.isAlive() || source.isRemoved()) {
            run.frozenAnchor = anchor;
        }
        return anchor;
    }
}
