package dev.skypocket.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Custom block outline for the targeted block.
 *
 * <p>26.2 fires {@code LevelRenderEvents.BEFORE_BLOCK_OUTLINE} before the vanilla
 * outline submits; returning {@code false} cancels it. We replicate vanilla's
 * camera translate and then submit our own outline via {@code submitShapeOutline}
 * (which handles vertex elements, alpha and line width correctly) plus an optional
 * face tint via {@code submitCustomGeometry}.
 */
public final class BlockOutlineRenderer {

    private BlockOutlineRenderer() {
    }

    public static void init() {
        LevelRenderEvents.BEFORE_BLOCK_OUTLINE.register(BlockOutlineRenderer::beforeBlockOutline);
    }

    private static boolean beforeBlockOutline(LevelRenderContext context, BlockOutlineRenderState outline) {
        ModConfig config = ConfigManager.get();
        if (!config.blockOutlineBlock.blockOutlineEnabled) {
            return true; // keep vanilla behaviour
        }

        SubmitNodeCollector collector = context.submitNodeCollector();
        PoseStack poseStack = context.poseStack();
        Vec3 camera = context.levelState().cameraRenderState.pos;
        BlockPos pos = outline.pos();

        // The pose stack is already camera-relative; the outline shape is in
        // block-local coordinates. Vanilla translates by (blockPos - camera) so
        // the local shape lands on the targeted block.
        poseStack.pushPose();
        poseStack.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);

        if (config.blockOutlineBlock.blockOutlineFaceTint) {
            float[] faceColor = rgb(config.getBlockOutlineFaceColorARGB());
            for (AABB box : outline.shape().toAabbs()) {
                collector.submitCustomGeometry(poseStack, RenderTypes.debugFilledBox(),
                        (pose, vertexConsumer) -> renderBoxFaces(vertexConsumer, pose, box, faceColor));
            }
        }

        collector.submitShapeOutline(poseStack, outline.shape(), RenderTypes.secondaryBlockOutline(),
                config.getBlockOutlineColorARGB(), config.blockOutlineBlock.blockOutlineThickness, outline.isTranslucent());

        poseStack.popPose();
        return false; // cancel the vanilla outline
    }

    private static float[] rgb(int argb) {
        return new float[]{
                ColorUtil.red(argb) / 255f,
                ColorUtil.green(argb) / 255f,
                ColorUtil.blue(argb) / 255f,
                ColorUtil.alpha(argb) / 255f
        };
    }

    /** Emits the 6 faces of the box as untextured quads (debugFilledBox = position + color). */
    private static void renderBoxFaces(VertexConsumer vc, PoseStack.Pose pose, AABB box, float[] color) {
        double x0 = box.minX, x1 = box.maxX;
        double y0 = box.minY, y1 = box.maxY;
        double z0 = box.minZ, z1 = box.maxZ;

        // +X
        quad(vc, pose, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, color);
        // -X
        quad(vc, pose, x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0, color);
        // +Y
        quad(vc, pose, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, color);
        // -Y
        quad(vc, pose, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1, color);
        // +Z
        quad(vc, pose, x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1, color);
        // -Z
        quad(vc, pose, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, color);
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose pose,
                             double ax, double ay, double az,
                             double bx, double by, double bz,
                             double cx, double cy, double cz,
                             double dx, double dy, double dz, float[] color) {
        vc.addVertex(pose, (float) ax, (float) ay, (float) az).setColor(color[0], color[1], color[2], color[3]);
        vc.addVertex(pose, (float) bx, (float) by, (float) bz).setColor(color[0], color[1], color[2], color[3]);
        vc.addVertex(pose, (float) cx, (float) cy, (float) cz).setColor(color[0], color[1], color[2], color[3]);
        vc.addVertex(pose, (float) dx, (float) dy, (float) dz).setColor(color[0], color[1], color[2], color[3]);
    }
}
