package dev.skypocket.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import dev.skypocket.config.ConfigManager;
import dev.skypocket.mixin.GameRendererAccessor;
import dev.skypocket.mixin.GuiGraphicsExtractorAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.WindowRenderState;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.joml.Matrix3x2f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Offscreen page cache. Each caller owns a {@link Slot}, so a frame can blit a chrome slot, a page
 * slot and a popup slot without their render targets competing for one global texture.
 */
public final class GuiPanelCache {
    private static final dev.skypocket.SkyPocketLog LOGGER =
            dev.skypocket.SkyPocketLog.of("panelCache");
    private static final int MAX_CACHED_FRAMES = 36_000;
    private static final Set<Slot> SLOTS =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static int nextSlotId;

    /** Draws the page's content in its own local coordinates; the caller sets the pose. */
    public interface PanelContent {
        void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta);
    }

    private GuiPanelCache() {
    }

    /** One independently baked page/chrome/popup cache. */
    public static final class Slot {
        private final int id = nextSlotId++;
        private final GuiRenderState recordedState = new GuiRenderState();
        private GuiRenderer guiRenderer;
        private RenderTarget target;
        private GpuSampler sampler;
        private int targetWidth;
        private int targetHeight;
        private int targetGuiScale = 1;
        private long lastKey = Long.MIN_VALUE;
        private float lastBlitScaleX = 1f;
        private float lastBlitScaleY = 1f;
        private long recordedKey = Long.MIN_VALUE;
        private long bakedKey = Long.MIN_VALUE;
        private boolean recordedValid;
        private boolean pendingBake;
        private boolean disabled;
        private int cachedFrames;

        private void register() {
            if (!SLOTS.contains(this)) {
                SLOTS.add(this);
            }
        }

        /**
         * Draws the cached page when it is up to date, otherwise draws live and records it.
         *
         * @return true when a blit was emitted and nothing else needs drawing
         */
        public boolean draw(GuiGraphicsExtractor graphics, long key, int panelWidth, int panelHeight,
                            float scaleX, float scaleY, boolean animating, PanelContent content,
                            int mouseX, int mouseY, float delta) {
            register();
            if (disabled) {
                return false;
            }
            lastBlitScaleX = 1f;
            lastBlitScaleY = 1f;
            float liveScale = Minecraft.getInstance().getWindow().getGuiScale();
            if (Math.abs(liveScale - Math.round(liveScale)) > 0.001f) {
                return false;
            }
            boolean usable = !animating && target != null && sampler != null && bakedKey == key;
            if (usable && cachedFrames >= MAX_CACHED_FRAMES) {
                recordedKey = Long.MIN_VALUE;
            } else if (usable) {
                try {
                    GuiRenderState live = ((GuiGraphicsExtractorAccessor) graphics).sky$getRenderState();
                    if (live == null) {
                        return false;
                    }
                    float guiScale = Math.max(1.0f, Minecraft.getInstance().getWindow().getGuiScale());
                    float blitScaleX = exactScale(targetWidth, panelWidth, scaleX, guiScale);
                    float blitScaleY = exactScale(targetHeight, panelHeight, scaleY, guiScale);
                    Matrix3x2f blitPose = new Matrix3x2f(graphics.pose()).scale(blitScaleX, blitScaleY);
                    lastBlitScaleX = blitScaleX;
                    lastBlitScaleY = blitScaleY;
                    live.addBlitToCurrentLayer(new BlitRenderState(
                            RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA,
                            TextureSetup.singleTexture(target.getColorTextureView(), sampler),
                            blitPose,
                            0, 0, panelWidth, panelHeight,
                            0f, 1f, 1f, 0f,
                            -1, null));
                    cachedFrames++;
                    return true;
                } catch (Throwable t) {
                    fail("blitting the cached page", t);
                    return false;
                }
            }
            if (ConfigManager.get().clickGuiPanelCache && recordedValid && key == recordedKey
                    && bakedKey != key && !pendingBake) {
                pendingBake = true;
            }
            if (ConfigManager.get().clickGuiPanelCache && key == lastKey && key != recordedKey) {
                record(key, panelWidth, panelHeight, scaleX, scaleY, content, mouseX, mouseY, delta);
            }
            lastKey = key;
            return false;
        }

        /** Bakes this slot; must run outside a render pass. */
        public void bakePending() {
            if (disabled || !pendingBake || !recordedValid || target == null
                    || !ConfigManager.get().clickGuiPanelCache) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            GameRenderer gameRenderer = mc.gameRenderer;
            if (gameRenderer == null) {
                return;
            }
            RenderTarget main = gameRenderer.mainRenderTarget();
            WindowRenderState window = null;
            int savedWidth = 0;
            int savedHeight = 0;
            int savedScale = 1;
            try {
                GameRenderState gameState = gameRenderer.gameRenderState();
                if (main == null || gameState == null || gameState.windowRenderState == null) {
                    return;
                }
                window = gameState.windowRenderState;
                savedWidth = window.width;
                savedHeight = window.height;
                savedScale = window.guiScale;
                renderer(gameRenderer);
                clearTarget();
                ((GameRendererAccessor) gameRenderer).sky$setMainRenderTarget(target);
                window.width = targetWidth;
                window.height = targetHeight;
                window.guiScale = targetGuiScale;
                guiRenderer.render();
                guiRenderer.endFrame();
                bakedKey = recordedKey;
                cachedFrames = 0;
                pendingBake = false;
            } catch (Throwable t) {
                fail("baking the cached page", t);
            } finally {
                if (window != null) {
                    window.width = savedWidth;
                    window.height = savedHeight;
                    window.guiScale = savedScale;
                }
                if (main != null) {
                    try {
                        ((GameRendererAccessor) gameRenderer).sky$setMainRenderTarget(main);
                    } catch (Throwable t) {
                        fail("restoring the main render target", t);
                    }
                }
            }
        }

        /** Drops the texture and renderer for this slot. */
        public void release() {
            releaseTarget();
            if (guiRenderer != null) {
                try {
                    guiRenderer.close();
                } catch (Throwable ignored) {
                    // The GPU objects are gone either way.
                }
                guiRenderer = null;
            }
            recordedState.reset();
            recordedValid = false;
            pendingBake = false;
            recordedKey = Long.MIN_VALUE;
            bakedKey = Long.MIN_VALUE;
            lastKey = Long.MIN_VALUE;
            cachedFrames = 0;
        }

        public float lastBlitScaleX() {
            return lastBlitScaleX;
        }

        public float lastBlitScaleY() {
            return lastBlitScaleY;
        }

        private void record(long key, int panelWidth, int panelHeight, float scaleX, float scaleY,
                            PanelContent content, int mouseX, int mouseY, float delta) {
            try {
                float guiScale = Math.max(1.0f, Minecraft.getInstance().getWindow().getGuiScale());
                // Floor the target so a fractional device edge cannot leave an unpainted last
                // column that CLAMP_TO_EDGE would smear into a black line. Padding the texture
                // changed the content origin and made live overlays jitter against the cache.
                int width = Math.max(1, (int) Math.floor(panelWidth * scaleX * guiScale));
                int height = Math.max(1, (int) Math.floor(panelHeight * scaleY * guiScale));
                GuiGraphicsExtractor recorder =
                        new GuiGraphicsExtractor(Minecraft.getInstance(), recordedState, mouseX, mouseY);
                recordedState.reset();
                recorder.pose().pushMatrix();
                recorder.pose().scale(scaleX, scaleY);
                content.drawContent(recorder, mouseX, mouseY, delta);
                recorder.pose().popMatrix();
                ensureTarget(width, height, Math.round(guiScale));
                recordedKey = key;
                recordedValid = true;
                pendingBake = true;
                cachedFrames = 0;
            } catch (Throwable t) {
                fail("recording the cached page", t);
            }
        }

        private void renderer(GameRenderer gameRenderer) {
            if (guiRenderer == null) {
                guiRenderer = new GuiRenderer(recordedState, gameRenderer.featureRenderDispatcher(),
                        List.of());
            }
        }

        private void ensureTarget(int width, int height, int guiScale) {
            if (target != null && targetWidth == width && targetHeight == height
                    && targetGuiScale == guiScale) {
                return;
            }
            releaseTarget();
            RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
            //#if MC>=260300
            target = new TextureTarget("SkyPocket page cache " + id, width, height,
                    main.getColorTexture().getFormat(), main.getDepthTexture().getFormat());
            //#else
            target = new TextureTarget("SkyPocket page cache " + id, width, height, true,
                    main.getColorTexture().getFormat());
            //#endif
            target.resize(width, height);
            if (target.getColorTexture() == null) {
                target.createBuffers(width, height);
            }
            if (target.getDepthTexture() != null) {
                RenderSystem.getDevice().createCommandEncoder()
                        .clearDepthTexture(target.getDepthTexture(), 1.0);
            }
            sampler = RenderSystem.getDevice().createSampler(AddressMode.CLAMP_TO_EDGE,
                    AddressMode.CLAMP_TO_EDGE, FilterMode.NEAREST, FilterMode.NEAREST, 1,
                    OptionalDouble.empty());
            targetWidth = width;
            targetHeight = height;
            targetGuiScale = guiScale;
        }

        private void clearTarget() {
            RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
                    target.getColorTexture(), GuiRenderer.CLEAR_COLOR,
                    target.getDepthTexture(), 1.0);
        }

        private void releaseTarget() {
            if (target != null) {
                try {
                    target.destroyBuffers();
                } catch (Throwable ignored) {
                    // Best effort.
                }
                target = null;
            }
            if (sampler != null) {
                try {
                    sampler.close();
                } catch (Throwable ignored) {
                    // Best effort.
                }
                sampler = null;
            }
            targetWidth = 0;
            targetHeight = 0;
        }

        private void fail(String what, Throwable t) {
            if (!disabled) {
                disabled = true;
                LOGGER.warn("page cache slot {} disabled after failing while {}; drawing live instead",
                        id, what, t);
            }
            release();
        }
    }

    /** Bakes every active slot; called before the GUI pass. */
    public static void bakePending() {
        for (Slot slot : new ArrayList<>(SLOTS)) {
            slot.bakePending();
        }
    }

    /** Releases every active slot (screen close, cache switch off). */
    public static void release() {
        for (Slot slot : new ArrayList<>(SLOTS)) {
            slot.release();
        }
        SLOTS.clear();
    }

    /** Correction factor for an exact 1:1 blit. */
    private static float exactScale(int targetSize, int panelSize, float renderScale, float guiScale) {
        float device = panelSize * renderScale * guiScale;
        return device > 0f ? targetSize / device : 1f;
    }
}
