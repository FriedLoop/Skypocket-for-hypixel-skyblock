package dev.skypocket.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;
import dev.skypocket.net.SkyBlockDetector;
import dev.skypocket.skyblock.MiningBreakTracker;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Draws the predicted break progress as a block outline growing out of the eight
 * corners: each of the twelve edges is split in half and every half grows from its
 * corner toward the middle of that edge, so at 100% the segments meet and the block
 * is completely enclosed.
 *
 * <p>The block's own shape is outlined while it still exists (slabs/stairs work), but
 * once the server has removed it — while the client still draws it, which is exactly
 * the case this feature is for — a full cube is outlined in its place, marking the
 * block as already broken.
 */
public final class MiningBreakRenderer {
    private static final float LINE_WIDTH = 2.0f;
    private static final AABB FULL_CUBE = new AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);
    /**
     * Depth nudge towards the camera, in blocks (see {@link #collectSubmits}). A translation
     * along the view direction changes depth without moving the silhouette, so this is invisible.
     */
    private static final double DEPTH_BIAS = 0.005;

    private MiningBreakRenderer() {
    }

    public static void init() {
        // Submitted in the gizmo phase, not in COLLECT_SUBMITS. The render-category block overlay
        // is submitted at BEFORE_BLOCK_OUTLINE, which MC runs *later* in the frame (COLLECT_SUBMITS
        // hooks the feature-submit step, the outline is submitted after the solid/translucent
        // features), so both overlays ended up drawing over the very same block edges, at the very
        // same depth, with the outline last - and once transparency sorting is involved the two
        // trade places as the camera moves, i.e. the flicker. Submitting in a later phase puts the
        // prediction overlay on top; the depth bias below makes that independent of layer sorting.
        LevelRenderEvents.BEFORE_GIZMOS.register(MiningBreakRenderer::collectSubmits);
    }

    private static void collectSubmits(LevelRenderContext context) {
        ModConfig cfg = ConfigManager.get();
        if (!cfg.miningBreakPredictBlock.miningBreakPredictEnabled || !cfg.miningBreakPredictBlock.miningBreakOutlineEnabled) {
            return;
        }
        if (!SkyBlockDetector.isActive()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        BlockPos pos = MiningBreakTracker.outlinePos();
        if (pos == null || mc.level == null) {
            return;
        }
        float progress = MiningBreakTracker.progress();
        float alpha = MiningBreakTracker.alpha() * (ColorUtil.alpha(cfg.miningBreakPredictBlock.miningBreakOutlineColor) / 255f);
        if (progress < 0f || alpha <= 0.01f) {
            return;
        }

        BlockState state = mc.level.getBlockState(pos);
        List<AABB> boxes = state.getShape(mc.level, pos).toAabbs();
        float[] color = {
                ColorUtil.red(cfg.miningBreakPredictBlock.miningBreakOutlineColor) / 255f,
                ColorUtil.green(cfg.miningBreakPredictBlock.miningBreakOutlineColor) / 255f,
                ColorUtil.blue(cfg.miningBreakPredictBlock.miningBreakOutlineColor) / 255f,
                alpha
        };

        SubmitNodeCollector collector = context.submitNodeCollector();
        PoseStack poseStack = context.poseStack();
        Vec3 camera = context.levelState().cameraRenderState.pos;
        poseStack.pushPose();
        poseStack.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
        // Nudge the geometry a hair towards the camera. The render-category outline draws the same
        // edges at the same depth, and on equal depth the two can swap places frame to frame; being
        // slightly nearer makes this overlay win the depth test unconditionally. Translating along
        // the view direction only changes depth and leaves the silhouette where it is.
        Vec3 toCamera = camera.subtract(new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
        if (toCamera.lengthSqr() > 1.0e-6) {
            toCamera = toCamera.normalize().scale(DEPTH_BIAS);
            poseStack.translate(toCamera.x, toCamera.y, toCamera.z);
        }
        collector.submitCustomGeometry(poseStack, RenderTypes.lines(), (pose, vc) -> {
            if (boxes.isEmpty()) {
                drawCorners(vc, pose, FULL_CUBE, progress, color);
            } else {
                for (AABB box : boxes) {
                    drawCorners(vc, pose, box, progress, color);
                }
            }
        });
        poseStack.popPose();
    }

    /** Grows the twelve edges of one box out of its corners. */
    private static void drawCorners(VertexConsumer vc, PoseStack.Pose pose, AABB box,
                                    float progress, float[] c) {
        double[] xs = {box.minX, box.maxX};
        double[] ys = {box.minY, box.maxY};
        double[] zs = {box.minZ, box.maxZ};
        for (int corner = 0; corner < 8; corner++) {
            int bx = corner & 1;
            int by = (corner >> 1) & 1;
            int bz = (corner >> 2) & 1;
            double cx = xs[bx];
            double cy = ys[by];
            double cz = zs[bz];
            for (int axis = 0; axis < 3; axis++) {
                double ex = axis == 0 ? xs[1 - bx] : cx;
                double ey = axis == 1 ? ys[1 - by] : cy;
                double ez = axis == 2 ? zs[1 - bz] : cz;
                // Walk half of the edge, from this corner toward its midpoint.
                double tx = cx + (ex - cx) * 0.5 * progress;
                double ty = cy + (ey - cy) * 0.5 * progress;
                double tz = cz + (ez - cz) * 0.5 * progress;
                line(vc, pose, cx, cy, cz, tx, ty, tz, c);
            }
        }
    }

    private static void line(VertexConsumer vc, PoseStack.Pose pose,
                             double x0, double y0, double z0, double x1, double y1, double z1,
                             float[] c) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double dz = z1 - z0;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        float nx = len == 0 ? 0f : (float) (dx / len);
        float ny = len == 0 ? 0f : (float) (dy / len);
        float nz = len == 0 ? 1f : (float) (dz / len);
        vc.addVertex(pose, (float) x0, (float) y0, (float) z0)
                .setColor(c[0], c[1], c[2], c[3]).setNormal(nx, ny, nz).setLineWidth(LINE_WIDTH);
        vc.addVertex(pose, (float) x1, (float) y1, (float) z1)
                .setColor(c[0], c[1], c[2], c[3]).setNormal(nx, ny, nz).setLineWidth(LINE_WIDTH);
    }
}
