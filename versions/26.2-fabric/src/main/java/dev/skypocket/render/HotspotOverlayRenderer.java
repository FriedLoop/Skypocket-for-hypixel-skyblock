package dev.skypocket.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;
import dev.skypocket.net.SkyBlockDetector;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Draws a translucent, cap-less cylinder (a vertical wall, no top/bottom faces)
 * around each fishing hotspot. The centre is the "HOTSPOT" marker (an armour stand
 * with a custom name, with a text-display fallback) and the radius is estimated from
 * the ring of particles the server spawns around the hotspot (see
 * {@link HotspotTracker}); the wall extends upward by a configurable height from the
 * liquid surface.
 */
public final class HotspotOverlayRenderer {
    private static final int SEGMENTS = 48;

    private HotspotOverlayRenderer() {
    }

    public static void init() {
        LevelRenderEvents.COLLECT_SUBMITS.register(HotspotOverlayRenderer::collectSubmits);
    }

    private static void collectSubmits(LevelRenderContext context) {
        ModConfig cfg = ConfigManager.get();
        if (!cfg.hotspotOverlayEnabled) {
            HotspotTracker.clear();
            return;
        }
        if (!SkyBlockDetector.isActive()) {
            HotspotTracker.clear();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        HotspotTracker.update(mc);
        List<HotspotTracker.Hotspot> hotspots = HotspotTracker.active();
        if (hotspots.isEmpty()) {
            return;
        }

        SubmitNodeCollector collector = context.submitNodeCollector();
        PoseStack poseStack = context.poseStack();
        Vec3 camera = context.levelState().cameraRenderState.pos;

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        for (HotspotTracker.Hotspot h : hotspots) {
            int argb = h.type != null ? h.type.color(cfg) : cfg.hotspotOverlayColor;
            renderCylinder(collector, poseStack, h, rgba(argb), cfg);
        }
        poseStack.popPose();
    }

    private static void renderCylinder(SubmitNodeCollector collector, PoseStack poseStack,
                                       HotspotTracker.Hotspot h, float[] c, ModConfig cfg) {
        double baseY = h.baseY;
        double topY = baseY + cfg.hotspotOverlayHeight;
        double r = h.radius;
        double step = 2 * Math.PI / SEGMENTS;

        // Translucent side wall (cap-less cylinder).
        collector.submitCustomGeometry(poseStack, RenderTypes.debugQuads(), (pose, vc) -> {
            for (int i = 0; i < SEGMENTS; i++) {
                double a0 = i * step;
                double a1 = (i + 1) * step;
                double x0 = h.x + Math.cos(a0) * r;
                double z0 = h.z + Math.sin(a0) * r;
                double x1 = h.x + Math.cos(a1) * r;
                double z1 = h.z + Math.sin(a1) * r;
                wallQuad(vc, pose, x0, z0, x1, z1, baseY, topY, c);
            }
        });

        // Crisp edge rings at the base and the top (full alpha regardless of fill alpha).
        float[] lc = {c[0], c[1], c[2], 1.0f};
        float width = cfg.hotspotOverlayLineWidth;
        collector.submitCustomGeometry(poseStack, RenderTypes.lines(), (pose, vc) -> {
            for (int i = 0; i < SEGMENTS; i++) {
                double a0 = i * step;
                double a1 = (i + 1) * step;
                double x0 = h.x + Math.cos(a0) * r;
                double z0 = h.z + Math.sin(a0) * r;
                double x1 = h.x + Math.cos(a1) * r;
                double z1 = h.z + Math.sin(a1) * r;
                line(vc, pose, x0, baseY, z0, x1, baseY, z1, lc, width);
                line(vc, pose, x0, topY, z0, x1, topY, z1, lc, width);
            }
        });
    }

    private static void wallQuad(VertexConsumer vc, PoseStack.Pose pose,
                                 double x0, double z0, double x1, double z1,
                                 double y0, double y1, float[] c) {
        vc.addVertex(pose, (float) x0, (float) y0, (float) z0).setColor(c[0], c[1], c[2], c[3]);
        vc.addVertex(pose, (float) x1, (float) y0, (float) z1).setColor(c[0], c[1], c[2], c[3]);
        vc.addVertex(pose, (float) x1, (float) y1, (float) z1).setColor(c[0], c[1], c[2], c[3]);
        vc.addVertex(pose, (float) x0, (float) y1, (float) z0).setColor(c[0], c[1], c[2], c[3]);
    }

    private static void line(VertexConsumer vc, PoseStack.Pose pose,
                             double x0, double y0, double z0, double x1, double y1, double z1,
                             float[] c, float width) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double dz = z1 - z0;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        float nx = len == 0 ? 0f : (float) (dx / len);
        float ny = len == 0 ? 0f : (float) (dy / len);
        float nz = len == 0 ? 1f : (float) (dz / len);
        vc.addVertex(pose, (float) x0, (float) y0, (float) z0)
                .setColor(c[0], c[1], c[2], c[3]).setNormal(nx, ny, nz).setLineWidth(width);
        vc.addVertex(pose, (float) x1, (float) y1, (float) z1)
                .setColor(c[0], c[1], c[2], c[3]).setNormal(nx, ny, nz).setLineWidth(width);
    }

    private static float[] rgba(int argb) {
        return new float[]{
                ColorUtil.red(argb) / 255f,
                ColorUtil.green(argb) / 255f,
                ColorUtil.blue(argb) / 255f,
                ColorUtil.alpha(argb) / 255f
        };
    }
}
