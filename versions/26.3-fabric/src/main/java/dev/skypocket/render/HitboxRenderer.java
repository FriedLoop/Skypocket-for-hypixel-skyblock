package dev.skypocket.render;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Renders entity hitboxes (AABB wireframes) and an optional eye look-ray for the
 * configured entity categories, plus container-block hitboxes.
 *
 * <p>Boxes are drawn with the same {@code submitShapeOutline} path vanilla uses for
 * block outlines (which is also what F3+B hitboxes share), so the wireframe lines up
 * exactly with the entity's {@link Entity#getBoundingBox()}.
 */
public final class HitboxRenderer {

    /** Hard cap for the container block scan so "unlimited" never scans the world. */
    private static final double CONTAINER_SCAN_CAP = 32.0;
    /** Container re-scan interval in ticks (20 = once per second). */
    private static final int CONTAINER_SCAN_INTERVAL = 20;

    /** Cached container hitboxes; rescanned only on movement or periodically. */
    private static final List<AABB> containerCache = new ArrayList<>();
    private static ClientLevel containerScanLevel = null;
    private static BlockPos containerScanPos = null;
    private static long containerScanTick = Long.MIN_VALUE;

    private HitboxRenderer() {
    }

    public static void init() {
        // COLLECT_SUBMITS fires during the submit phase (before the frame graph runs),
        // which is where geometry must be handed to the SubmitNodeCollector. Submitting
        // from the later AFTER_TRANSLUCENT_FEATURES hook would target a phase that is
        // already executing, lagging the overlay a frame behind the camera/entities.
        LevelRenderEvents.COLLECT_SUBMITS.register(HitboxRenderer::collectSubmits);
    }

    private static void collectSubmits(LevelRenderContext context) {
        ModConfig cfg = ConfigManager.get();
        if (!cfg.hitboxEnabled) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }

        SubmitNodeCollector collector = context.submitNodeCollector();
        PoseStack poseStack = context.poseStack();
        Vec3 camera = context.levelState().cameraRenderState.pos;

        Iterable<Entity> entities;
        if (cfg.hitboxUnlimitedRange) {
            entities = mc.level.entitiesForRendering();
        } else {
            AABB area = mc.player.getBoundingBox().inflate(cfg.hitboxRange);
            entities = mc.level.getEntities(mc.player, area, e -> e != mc.player);
        }

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);

        for (Entity e : entities) {
            if (e == mc.player || !isCategoryEnabled(cfg, HitboxClassifier.classify(e))) {
                continue;
            }
            collector.submitShapeOutline(poseStack, Shapes.create(e.getBoundingBox()), RenderTypes.lines(),
                    cfg.hitboxColor, cfg.hitboxLineWidth, false);
            if (cfg.hitboxShowLineOfSight && e instanceof LivingEntity living) {
                renderLookRay(collector, poseStack, living, cfg);
            }
        }

        if (cfg.hitboxShowContainer) {
            refreshContainerCache(mc, cfg);
            for (AABB box : containerCache) {
                collector.submitShapeOutline(poseStack, Shapes.create(box), RenderTypes.lines(),
                        cfg.hitboxColor, cfg.hitboxLineWidth, false);
            }
        }

        poseStack.popPose();
    }

    /**
     * Refreshes the cached container boxes. Container blocks are static, so scanning
     * the surrounding area (tens of thousands of block lookups) every frame is the
     * dominant hitbox cost. Only rescan when the player crosses a block boundary or at
     * most every {@link #CONTAINER_SCAN_INTERVAL} ticks, and reuse the cache otherwise.
     */
    private static void refreshContainerCache(Minecraft mc, ModConfig cfg) {
        if (containerScanLevel != mc.level) {
            containerCache.clear();
            containerScanLevel = mc.level;
            containerScanPos = null;
        }
        long now = mc.level.getGameTime();
        BlockPos playerPos = mc.player.blockPosition();
        if (containerScanPos != null && containerScanPos.equals(playerPos)
                && now - containerScanTick < CONTAINER_SCAN_INTERVAL) {
            return;
        }
        containerCache.clear();
        double scanRange = Math.min(cfg.hitboxUnlimitedRange ? CONTAINER_SCAN_CAP : cfg.hitboxRange, CONTAINER_SCAN_CAP);
        AABB area = mc.player.getBoundingBox().inflate(scanRange);
        BlockPos min = BlockPos.containing(area.minX, area.minY, area.minZ);
        BlockPos max = BlockPos.containing(area.maxX, area.maxY, area.maxZ);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockEntity be = mc.level.getBlockEntity(pos);
            if (be != null && CrosshairTargetClassifier.isContainer(be)) {
                // Use the block's actual collision shape (e.g. a chest is 14/16 of a
                // block), not a full cube, so the outline hugs the real hitbox.
                VoxelShape shape = mc.level.getBlockState(pos).getCollisionShape(mc.level, pos);
                containerCache.add(shape.isEmpty() ? new AABB(pos)
                        : shape.bounds().move(pos.getX(), pos.getY(), pos.getZ()));
            }
        }
        containerScanPos = playerPos;
        containerScanTick = now;
    }

    private static boolean isCategoryEnabled(ModConfig cfg, HitboxClassifier.Category cat) {
        return switch (cat) {
            case PLAYER -> cfg.hitboxShowPlayer;
            case PROJECTILE -> cfg.hitboxShowProjectile;
            case HOSTILE -> cfg.hitboxShowHostile;
            case NEUTRAL -> cfg.hitboxShowNeutral;
            case OTHER -> cfg.hitboxShowOther;
        };
    }

    private static void renderLookRay(SubmitNodeCollector collector, PoseStack poseStack, LivingEntity e, ModConfig cfg) {
        Vec3 start = e.getEyePosition();
        Vec3 end = start.add(e.getViewVector(1.0f).scale(cfg.lookRayLength));
        float[] color = rgb(cfg.lookRayColor);
        float width = cfg.hitboxLineWidth;
        collector.submitCustomGeometry(poseStack, RenderTypes.lines(), (pose, vc) -> {
            vc.addVertex(pose, (float) start.x, (float) start.y, (float) start.z)
                    .setColor(color[0], color[1], color[2], color[3]).setNormal(0f, 0f, 1f).setLineWidth(width);
            vc.addVertex(pose, (float) end.x, (float) end.y, (float) end.z)
                    .setColor(color[0], color[1], color[2], color[3]).setNormal(0f, 0f, 1f).setLineWidth(width);
        });
    }

    private static float[] rgb(int argb) {
        return new float[]{
                ColorUtil.red(argb) / 255f,
                ColorUtil.green(argb) / 255f,
                ColorUtil.blue(argb) / 255f,
                ColorUtil.alpha(argb) / 255f
        };
    }
}
