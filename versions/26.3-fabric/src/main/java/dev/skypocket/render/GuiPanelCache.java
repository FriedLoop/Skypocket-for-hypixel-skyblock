package dev.skypocket.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.OptionalDouble;

/**
 * Rasterises the Click GUI panel once into an offscreen texture and reuses that texture
 * while the panel's content stays the same.
 *
 * <p>Why: MC renders the GUI from a state tree, so every {@code fill} / {@code text} call
 * the panel makes is re-submitted, re-laid-out and re-meshed every frame — thousands of
 * elements for a card- or settings-heavy page, which is exactly the "more text, fewer
 * fps" cost. With a warm cache the whole panel costs one blit.
 *
 * <p>Everything here is built on MC's own primitives:
 * <ul>
 *   <li>the panel's draw code is recorded into a private {@link GuiRenderState} through
 *       its own {@link GuiGraphicsExtractor} (both have public constructors),</li>
 *   <li>before the GUI pass of a later frame, that tree is rendered into an offscreen
 *       target with our own {@link GuiRenderer},</li>
 *   <li>while baking, {@code GameRenderer.mainRenderTarget} is pointed at the target and
 *       {@link WindowRenderState} is told the "window" is exactly the panel. The GUI
 *       projection and the scissor rects are both derived from that state, so the panel's
 *       own coordinates land in texture space with no compensation — and each rendered
 *       pixel equals one on-screen pixel,</li>
 *   <li>later frames blit the texture 1:1.</li>
 * </ul>
 *
 * <p>Failure is contained: any exception disables the cache permanently (the panel then
 * draws live, exactly as before) and is logged once.
 */
public final class GuiPanelCache {
    private static final dev.skypocket.SkyPocketLog LOGGER =
            dev.skypocket.SkyPocketLog.of("panelCache");

    /** A cached panel is re-recorded after this many frames, as a staleness backstop. */
    /**
     * How many reused frames a baked texture is trusted for before it is refreshed.
     *
     * <p>Purely a safety net against a key term that was missed; the key already covers everything that
     * changes the pixels, so a refresh re-renders identical content and costs a full operation. Made
     * long on purpose: at 1200 fps the old 600 re-ran the whole panel twice a second, and a refresh is a
     * record + bake, not a cheap repair.
     */
    private static final int MAX_CACHED_FRAMES = 36_000;

    /** Draws the panel's (opaque) content in its own local coordinates; the caller sets the pose. */
    public interface PanelContent {
        void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta);
    }

    private static boolean disabled;
    private static final GuiRenderState recordedState = new GuiRenderState();
    private static GuiRenderer guiRenderer;
    private static RenderTarget target;
    private static GpuSampler sampler;
    private static int targetWidth;
    private static int targetHeight;
    private static int targetGuiScale = 1;
    private static long lastKey = Long.MIN_VALUE;
    private static long recordedKey = Long.MIN_VALUE;
    private static long bakedKey = Long.MIN_VALUE;
    private static boolean recordedValid;
    private static boolean pendingBake;
    private static int cachedFrames;

    private GuiPanelCache() {
    }

    /** True once the cache has failed and the panel is being drawn live for good. */
    public static boolean disabled() {
        return disabled;
    }

    // ---- Optional counters ----------------------------------------------------------------
    //
    // Off unless -Dskypocket.panelDebug=true. They answer the only question that matters when
    // optimising this cache: how many frames actually hit it, and how often a bake is paid for. GuiLib's
    // architecture notes (the reference for this work) admit it has no such instrumentation at all -
    // adding two counters is cheaper than guessing which change helped.

    /** Read live from the config so the switch in the panel settings takes effect immediately. */
    private static boolean statsOn() {
        return ConfigManager.get().panelCacheStats;
    }

    // Which gate a non-blitting frame stopped at. Without this, "0 blits, 0 bakes" says only that
    // something returned early - it does not say what.
    private static final int GATE_DISABLED = 0;
    private static final int GATE_SCALE = 1;
    private static final int GATE_NULL_STATE = 2;
    private static final int GATE_ANIMATING = 3;
    private static final int GATE_NO_TARGET = 4;
    private static final int GATE_KEY_CHANGED = 5;
    private static final int GATE_FRAME_CAP = 6;
    private static final String[] GATE_NAMES = {
            "disabled", "scale", "null-state", "animating", "no-target", "key-changed", "frame-cap"};
    private static final long[] statGates = new long[GATE_NAMES.length];

    private static void statGate(int gate) {
        if (statsOn()) {
            statGates[gate]++;
        }
    }

    private static long statCalls;
    private static long statBakes;
    private static long statSince;
    private static long statBlitNanos;
    private static long statBlitFrames;

    private static void statCall() {
        if (!statsOn()) {
            return;
        }
        statCalls++;
        long now = System.nanoTime();
        if (statSince == 0L) {
            statSince = now;
            return;
        }
        if (now - statSince < 5_000_000_000L) {
            return;
        }
        // A frame is one of three things, and they must be counted separately: a blit of the cached
        // texture, a live draw, or a live draw that also paid for a bake. Reporting "not baked" as
        // "reused" was wrong and made 98% look like a hit rate when it was not.
        long blits = statBlitFrames;
        long live = Math.max(0L, statCalls - blits);
        StringBuilder gates = new StringBuilder();
        for (int i = 0; i < statGates.length; i++) {
            if (statGates[i] > 0L) {
                gates.append(' ').append(GATE_NAMES[i]).append('=').append(statGates[i]);
            }
        }
        LOGGER.info("panel cache (5s): {} frames = {} blits ({}%) + {} live ({} of them baked),"
                        + " settled {} / already-recorded {}; stopped at:{}", statCalls, blits,
                100L * blits / Math.max(1L, statCalls), live, statBakes, statSettledFrames,
                statAlreadyRecorded, gates.length() == 0 ? " none" : gates.toString());
        StringBuilder bake = new StringBuilder();
        for (int i = 0; i < statBakeGates.length; i++) {
            if (statBakeGates[i] > 0L) {
                bake.append(' ').append(BAKE_GATES[i]).append('=').append(statBakeGates[i]);
            }
        }
        LOGGER.info("panel bake (5s):{}", bake.length() == 0 ? " never called" : bake.toString());
        java.util.Arrays.fill(statBakeGates, 0L);
        java.util.Arrays.fill(statGates, 0L);
        statSettledFrames = 0L;
        statAlreadyRecorded = 0L;
        statCalls = 0L;
        statBakes = 0L;
        statBlitNanos = 0L;
        statBlitFrames = 0L;
        statSince = now;
    }

    private static long statSettledFrames;
    private static long statAlreadyRecorded;

    // Why bakePending did not produce a bake, one counter per early return.
    private static final String[] BAKE_GATES = {
            "disabled", "not-pending", "invalid", "no-target", "cache-off", "no-renderer",
            "no-main", "no-state", "baked"};
    private static final long[] statBakeGates = new long[BAKE_GATES.length];

    private static void statBakeGate(int gate) {
        if (statsOn()) {
            statBakeGates[gate]++;
        }
    }

    private static long stateNextMs;

    /** Once a second: the flags that decide whether a blit or a bake is possible. */
    private static void statState(long key) {
        if (!statsOn()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < stateNextMs) {
            return;
        }
        stateNextMs = now + 1000L;
        LOGGER.info("panel state: bakedKey==key {} recordedKey==key {} pendingBake {} recordedValid {}"
                        + " target {} sampler {} cachedFrames {}",
                bakedKey == key, recordedKey == key, pendingBake, recordedValid, target != null,
                sampler != null, cachedFrames);
    }

    private static void statSettled(boolean settled, boolean alreadyRecorded) {
        if (!statsOn()) {
            return;
        }
        if (settled) {
            statSettledFrames++;
        }
        if (alreadyRecorded) {
            statAlreadyRecorded++;
        }
    }

    private static void statBlit(long nanos) {
        if (statsOn()) {
            statBlitNanos += nanos;
            statBlitFrames++;
        }
    }

    private static void statBake() {
        if (statsOn()) {
            statBakes++;
        }
    }

    /**
     * Draws the cached panel when it is up to date, otherwise tells the caller to draw it
     * live (and records it once the content has settled).
     *
     * @param key         hash of everything that affects the panel's pixels
     * @param panelWidth  panel width in its own local units
     * @param panelHeight panel height in its own local units
     * @param scaleX      scale of the pose the panel is drawn with (local units -> GUI), x
     * @param scaleY      same for y (equal in practice; the panel snaps both to whole device
     *                    pixels, see {@code OneClickScreen.layout})
     * @return true when a blit of the cache was emitted and nothing else needs drawing
     */
    public static boolean draw(GuiGraphicsExtractor graphics, long key, int panelWidth, int panelHeight,
                               float scaleX, float scaleY, boolean animating, PanelContent content,
                               int mouseX, int mouseY, float delta) {
        statCall();
        if (disabled) {
            statGate(GATE_DISABLED);
            return false;
        }
        // A fractional GUI scale cannot be baked pixel-exactly, so do not try.
        //
        // WindowRenderState.guiScale is an int (verified in the class file), while the live frame uses
        // Window.getGuiScale(), which is fractional under automatic scaling. The bake therefore renders
        // the panel at round(scale) and then stretches the result back by exactScale: the content is
        // laid out on a different grid than the live frame and then resampled, so labels and frames sit
        // a fraction of a pixel off - visible as "the cached panel is not quite where the live one is",
        // and worse for thin 1px details like text.
        float liveScale = Minecraft.getInstance().getWindow().getGuiScale();
        if (Math.abs(liveScale - Math.round(liveScale)) > 0.001f) {
            statGate(GATE_SCALE);
            return false;
        }
        // Note: there used to be a third gate here - "the outline or the vertical contrast is on, so draw
        // live instead of baking" - because those effects redrew each text run through scissor clips and
        // the offscreen path did not reproduce that. Both effects were removed (they could not be made
        // consistent across the live and cached paths), so the exemption went with them and the cache is
        // now governed only by the GUI-scale and animation checks above.
        // Never blit while the panel is being scaled (open/close): the cached texture is
        // rasterised 1:1, and a resampled copy would both look different from the live
        // drawing and pop when the animation ends.
        boolean usable = !animating && target != null && sampler != null && bakedKey == key;
        if (usable && cachedFrames >= MAX_CACHED_FRAMES) {
            // Periodic refresh, and it must go through a *new recording*.
            //
            // Two separate defects met here. The original condition simply stopped blitting at the cap,
            // and because the key still matched, no bake was requested again - the cache stayed off
            // until something invalidated it. Requesting a bake was not enough either: the recorded
            // command tree is consumed by the bake that produced the current texture, so replaying it
            // a second time draws nothing and the panel vanished a few seconds after settling.
            // Invalidating the record makes the next frame record and bake afresh.
            recordedKey = Long.MIN_VALUE;
        } else if (usable) {
            try {
                GuiRenderState live = ((GuiGraphicsExtractorAccessor) graphics).sky$getRenderState();
                if (live == null) {
                    statGate(GATE_NULL_STATE);
                    return false;
                }
                // Premultiplied blit, exactly the way MC blits its own offscreen GUI
                // textures (PictureInPictureRenderer.blitTexture). The panel is baked over
                // nothing, so the texture holds premultiplied colours and this is the only
                // pipeline that composites it back correctly - including every
                // antialiased pixel, which is why the whole panel (translucent shell and
                // all) can be cached and still look pixel-identical to a live frame.
                //
                // Note: this must be a BlitRenderState. Going through
                // graphics.fill(pipeline, textureSetup, ...) builds a
                // ColoredRectangleRenderState (position + colour only) which this
                // pipeline's vertex format rejects ("Missing elements in vertex:
                // UV2, Normal, LineWidth") and MC crashes while meshing it.
                // The blit rect is in whole pose units, so its device size would be
                // panelWidth * renderScale * guiScale — fractional — while the texture is
                // that size rounded. Left uncorrected the cached panel is stretched by
                // ~1/1000: the left edge lines up, the far edges drift by up to a pixel and
                // every component sits a fraction of a pixel off, which shows up as the UI
                // shifting whenever the cache turns on or off. Scaling the pose about the
                // panel's own origin (its top-left) makes the blit exactly 1:1.
                float guiScale = Math.max(1.0f, Minecraft.getInstance().getWindow().getGuiScale());
                Matrix3x2f blitPose = new Matrix3x2f(graphics.pose()).scale(
                        exactScale(targetWidth, panelWidth, scaleX, guiScale),
                        exactScale(targetHeight, panelHeight, scaleY, guiScale));
                // UVs are the ones PictureInPictureRenderer uses for its own render-target
                // texture (0, 1, 1, 0): the V axis of a render target is inverted relative
                // to the GUI's texture convention, so the straight (0, 0, 1, 1) ordering
                // renders the cached panel upside down.
                long blitStart = System.nanoTime();
                live.addBlitToCurrentLayer(new BlitRenderState(
                        RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA,
                        TextureSetup.singleTexture(target.getColorTextureView(), sampler),
                        blitPose,
                        0, 0, panelWidth, panelHeight,
                        0f, 1f, 1f, 0f,
                        -1, null));
                statBlit(System.nanoTime() - blitStart);
                cachedFrames++;
                return true;
            } catch (Throwable t) {
                fail("blitting the cached Click GUI panel", t);
                return false;
            }
        }
        // Live frame. Record only once the content has settled: a dragged slider or the
        // open/close animation changes the key every frame, and re-baking each of those
        // frames would cost more than drawing live.
        // Two facts that decide whether a bake can ever happen: has the key repeated since last frame
        // (it has to, or nothing is "settled"), and is the recorded key already this one (in which case
        // no new recording is made and the pending bake is what has to run).
        statSettled(key == lastKey, key == recordedKey);
        statState(key);
        // Self-heal. The state machine can arrive at "recorded for exactly this key, but no bake is
        // pending and the texture does not hold it" - observed in practice: every frame then draws
        // live forever, because the record condition is already satisfied and nothing re-requests the
        // bake. Re-requesting it here cannot loop: a successful bake sets bakedKey and clears the flag,
        // and a failing one disables the cache.
        if (ConfigManager.get().clickGuiPanelCache && recordedValid && key == recordedKey
                && bakedKey != key && !pendingBake) {
            pendingBake = true;
        }
        if (ConfigManager.get().clickGuiPanelCache && key == lastKey && key != recordedKey) {
            record(key, panelWidth, panelHeight, scaleX, scaleY, content, mouseX, mouseY, delta);
        }
        // Why this frame was drawn live instead of blitted.
        if (animating) {
            statGate(GATE_ANIMATING);
        } else if (target == null || sampler == null) {
            statGate(GATE_NO_TARGET);
        } else if (bakedKey == key) {
            // The texture matches; this frame simply asked for a refresh instead of blitting.
            statGate(GATE_FRAME_CAP);
        } else {
            statGate(GATE_KEY_CHANGED);
        }
        lastKey = key;
        return false;
    }

    /**
     * Correction factor that makes the blit land on exactly one device pixel per texel:
     * the texture is sized in rounded device pixels while the blit rect is sized in pose
     * units, so the two only agree after multiplying by this.
     */
    private static float exactScale(int targetSize, int panelSize, float renderScale, float guiScale) {
        float device = panelSize * renderScale * guiScale;
        return device > 0f ? targetSize / device : 1f;
    }

    /** Bakes the recorded panel into the offscreen texture; must run outside a render pass. */
    public static void bakePending() {
        if (disabled) {
            statBakeGate(0);
            return;
        }
        if (!pendingBake) {
            statBakeGate(1);
            return;
        }
        if (!recordedValid) {
            statBakeGate(2);
            return;
        }
        if (target == null) {
            statBakeGate(3);
            return;
        }
        if (!ConfigManager.get().clickGuiPanelCache) {
            statBakeGate(4);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        GameRenderer gameRenderer = mc.gameRenderer;
        if (gameRenderer == null) {
            statBakeGate(5);
            return;
        }
        RenderTarget main = gameRenderer.mainRenderTarget();
        WindowRenderState window = null;
        int savedWidth = 0;
        int savedHeight = 0;
        int savedScale = 1;
        try {
            GameRenderState gameState = gameRenderer.gameRenderState();
            if (main == null) {
                statBakeGate(6);
                return;
            }
            if (gameState == null || gameState.windowRenderState == null) {
                statBakeGate(7);
                return;
            }
            window = gameState.windowRenderState;
            savedWidth = window.width;
            savedHeight = window.height;
            savedScale = window.guiScale;
            renderer(gameRenderer);
            clearTarget();
            ((GameRendererAccessor) gameRenderer).sky$setMainRenderTarget(target);
            // Pretend the panel texture *is* the window: the GUI projection and the
            // scissor rects are both derived from this state, so the panel's local
            // coordinates then map exactly onto the texture's pixels.
            window.width = targetWidth;
            window.height = targetHeight;
            window.guiScale = targetGuiScale;
            guiRenderer.render();
            guiRenderer.endFrame();
            bakedKey = recordedKey;
            statBake();
            statBakeGate(8);
            cachedFrames = 0;
            pendingBake = false;
        } catch (Throwable t) {
            fail("baking the Click GUI panel", t);
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

    /** Drops everything (screen closed, GUI scale changed, or a failure). */
    public static void release() {
        releaseTarget();
        if (guiRenderer != null) {
            try {
                guiRenderer.close();
            } catch (Throwable ignored) {
                // Nothing to do: the GPU objects are gone either way.
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

    private static void record(long key, int panelWidth, int panelHeight, float scaleX, float scaleY,
                               PanelContent content, int mouseX, int mouseY, float delta) {
        try {
            float guiScale = Math.max(1.0f, Minecraft.getInstance().getWindow().getGuiScale());
            int width = Math.max(1, Math.round(panelWidth * scaleX * guiScale));
            int height = Math.max(1, Math.round(panelHeight * scaleY * guiScale));
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
            fail("recording the Click GUI panel", t);
        }
    }

    private static void renderer(GameRenderer gameRenderer) {
        if (guiRenderer == null) {
            // No picture-in-picture renderers: the panel draws no models.
            guiRenderer = new GuiRenderer(recordedState, gameRenderer.featureRenderDispatcher(), List.of());
        }
    }

    /** (Re)creates the offscreen target when the panel's pixel size changed. */
    private static void ensureTarget(int width, int height, int guiScale) {
        if (target != null && targetWidth == width && targetHeight == height && targetGuiScale == guiScale) {
            return;
        }
        releaseTarget();
        RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        // Same shape as the main target (depth included): the GUI pipelines expect a
        // depth attachment, and the 2D ones never write to it, so one clear is enough.
        target = new TextureTarget("SkyPocket panel cache", width, height, true,
                main.getColorTexture().getFormat());
        target.resize(width, height);
        if (target.getColorTexture() == null) {
            target.createBuffers(width, height);
        }
        if (target.getDepthTexture() != null) {
            RenderSystem.getDevice().createCommandEncoder()
                    .clearDepthTexture(target.getDepthTexture(), 1.0);
        }
        // 1:1 blit, so nearest sampling is exact; clamping keeps the panel's edge pixels
        // from picking up anything from outside the texture.
        sampler = RenderSystem.getDevice().createSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                FilterMode.NEAREST, FilterMode.NEAREST, 1, OptionalDouble.empty());
        targetWidth = width;
        targetHeight = height;
        targetGuiScale = guiScale;
    }

    /**
     * Clears the target to transparent, the way MC clears its own offscreen GUI textures
     * ({@code PictureInPictureRenderer.prepareTexturesAndProjection}): without this a
     * re-bake would composite over the previous bake.
     */
    private static void clearTarget() {
        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
                target.getColorTexture(), GuiRenderer.CLEAR_COLOR, target.getDepthTexture(), 1.0);
    }

    private static void releaseTarget() {
        if (target != null) {
            try {
                target.destroyBuffers();
            } catch (Throwable ignored) {
                // Best effort: the buffers may already be gone.
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

    private static void fail(String what, Throwable t) {
        if (!disabled) {
            disabled = true;
            LOGGER.warn("panel cache disabled after failing while {}; drawing live instead", what, t);
        }
        release();
    }
}
