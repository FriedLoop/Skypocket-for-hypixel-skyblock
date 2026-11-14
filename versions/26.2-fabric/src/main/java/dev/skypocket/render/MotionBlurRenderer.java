package dev.skypocket.render;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.UniformValue;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Camera motion blur by reprojection against the previous frame (see the shader for the technique and
 * for what it deliberately cannot do: object motion).
 *
 * <p><b>Frame rate.</b> The amount is expressed as an exposure time, not as a per-frame factor:
 * {@code scale = exposure / frameTime} is handed to the shader, so one frame's blur covers the same
 * slice of <em>time</em> at 30 fps and at 240 fps - the reprojected velocity already grows with the
 * frame time, and this cancels it back out. The knob is one multiply on the CPU and one uniform, so
 * the cost of the compensation is nil. Two honest limits: the slice cannot exceed one frame's worth
 * of motion (a single reprojection has no more data than that - a longer exposure would need history
 * accumulation), and the scale is capped so a slow frame cannot request an absurd stretch.
 *
 * <p>The values change every frame, which is only affordable because {@link LivePostChain} swaps in a
 * writable uniform buffer: a per-frame chain reload would relink a pipeline every frame.
 *
 * <p>The shader builds the velocity in two parts - a depth-free rotation term and a depth-based
 * parallax on top - and this class supplies the numbers that keep the second honest: a far-field
 * depth to reproject for the first, and bounds on how large either part may plausibly be.
 */
public final class MotionBlurRenderer {
    private static final dev.skypocket.SkyPocketLog LOGGER =
            dev.skypocket.SkyPocketLog.of("motionBlur");

    /** How far the exposure window may outrun one frame, in frames. */
    /** Smoothed frame time in seconds, and the strength applied last frame. */
    private static float smoothedFrameTime;
    private static float lastScale;

    /** How strongly the raw frame time is pulled towards the smoothed one, per frame. */
    private static final float FRAME_TIME_SMOOTHING = 0.15f;
    /** Floor on the frame time used for the strength, so micro-frames cannot blow it up. */
    private static final float MIN_FRAME_SECONDS = 0.001f;
    /** Largest change of the strength per frame, as a fraction of the current value. */
    private static final float SCALE_STEP = 0.15f;
    /** Below this the screen does not move enough for the blur to show anything. */
    private static final float MIN_VELOCITY = 0.0015f;

    private static final float MAX_SCALE = 8.0f;
    /** A gap longer than this is a hitch or a load; start over rather than blur across it. */
    private static final float MAX_FRAME_SECONDS = 0.5f;
    /** A camera jump beyond this many blocks (teleport, dimension change) invalidates the history. */
    private static final double MAX_JUMP_SQUARED = 1024.0;
    /**
     * The shallowest depth a pixel can have and still be blurred (blocks). Used both for the shader's
     * near gate and for the velocity bound below.
     */
    private static final float MIN_PLAUSIBLE_DEPTH = 1.5f;
    /** How much slack the velocity bounds leave, for the perspective edge of the screen. */
    private static final float VELOCITY_MARGIN = 3.0f;
    /** The bound never drops below this (uv units), so slow motion is never rejected. */
    private static final float MIN_BOUND = 0.02f;
    /**
     * How far along the view axis the far-field probe is put (blocks). Everything that far away is
     * moved by the camera's translation by far less than a pixel, so reprojecting it isolates the
     * rotation - which is the part of the blur that must survive a missing or unusable depth buffer.
     */
    private static final float FAR_PROBE_BLOCKS = 500f;

    private static final LivePostChain CHAIN =
            new LivePostChain("post_effect/motion_blur.json", "BlurConfig");

    /** Scratch matrices; only ever touched on the client thread. */
    private static final Matrix4f VIEW = new Matrix4f();
    private static final Matrix4f VIEW_PROJ = new Matrix4f();
    private static final Matrix4f INVERSE = new Matrix4f();
    private static final Matrix4f INVERSE_VIEW = new Matrix4f();
    /** Last frame's view-projection: what the current frame's pixels are reprojected against. */
    private static final Matrix4f PREVIOUS = new Matrix4f();
    /** Scratch points for the far-field probe and its projection. */
    private static final Vector4f PROBE = new Vector4f();
    private static final Vector4f PROBE_CLIP = new Vector4f();

    private static boolean hasHistory;
    private static long lastFrameNanos;
    private static Vec3 lastCameraPos = Vec3.ZERO;
    private static final Quaternionf LAST_ORIENTATION = new Quaternionf();
    private static Boolean depthZeroToOne;

    /** Frames seen since the module was switched on, and the state that was last reported. */
    private static int enabledFrames;
    private static int reportedState = -1;

    private MotionBlurRenderer() {
    }

    /** Called once per frame, after the antialiasing pass and before the colour grade. */
    public static void process() {
        ModConfig cfg = ConfigManager.get();
        long now = System.nanoTime();
        Minecraft mc = Minecraft.getInstance();
        if (!cfg.postProcessBlock.postProcessEnabled || !cfg.postProcessBlock.motionBlurEnabled
                || (cfg.postProcessBlock.motionBlurSkipScreens && mc.gui.screen() != null)) {
            hasHistory = false;
            smoothedFrameTime = 0f;
            lastScale = 0f;
            lastFrameNanos = now;
            enabledFrames = 0;
            report(3, cfg, 0f, 0f, 0f, 0f);
            return;
        }
        enabledFrames++;
        CameraRenderState camera =
                mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
        if (camera == null || !camera.initialized) {
            hasHistory = false;
            smoothedFrameTime = 0f;
            lastScale = 0f;
            report(1, cfg, 0f, 0f, 0f, 0f);
            return;
        }
        float frameTime = lastFrameNanos == 0L ? 0f : (now - lastFrameNanos) / 1.0e9f;
        lastFrameNanos = now;

        buildMatrices(camera);
        boolean continuous = hasHistory && frameTime > 0f && frameTime <= MAX_FRAME_SECONDS
                && camera.pos.distanceToSqr(lastCameraPos) <= MAX_JUMP_SQUARED;
        // Frame time is smoothed before it is divided by the exposure: at a high frame rate the raw value
        // spikes (3-13 ms among 0.6 ms frames) and the strength is its inverse, so a single spike collapsed
        // the blur for one frame - visible as stutter that vanished with vsync on.
        smoothedFrameTime = smoothedFrameTime <= 0f
                ? frameTime
                : smoothedFrameTime + (frameTime - smoothedFrameTime) * FRAME_TIME_SMOOTHING;
        float effectiveFrameTime = Math.max(smoothedFrameTime, MIN_FRAME_SECONDS);
        float scale = continuous
                ? Math.min(cfg.postProcessBlock.motionBlurExposureMs / 1000.0f / effectiveFrameTime, MAX_SCALE)
                : 0f;
        // And the strength itself is rate-limited, so a leftover step cannot flash.
        if (scale > 0f && lastScale > 0f) {
            float step = lastScale * SCALE_STEP;
            scale = Math.max(lastScale - step, Math.min(lastScale + step, scale));
        }
        lastScale = scale;

        float maxVelocity = rotationBound(camera) + parallaxBound(camera);
        // Nothing moves, nothing to reproject: running the pass anyway cost a full-screen 10-tap shader
        // every frame at 1700 fps, which is where the idle stutter came from.
        boolean moving = maxVelocity >= MIN_VELOCITY;
        if (moving) {
            CHAIN.apply(parameters(cfg, scale, camera, maxVelocity));
            WorldPostChain.process(CHAIN.chain());
        }
        report(moving && scale > 0f ? 0 : 1, cfg, moving ? scale : 0f, frameTime, maxVelocity,
                parallaxBound(camera));

        PREVIOUS.set(VIEW_PROJ);
        hasHistory = true;
        lastCameraPos = camera.pos;
        LAST_ORIENTATION.set(camera.orientation);
    }

    /**
     * Builds the current view and view-projection, plus the inverse the shader reconstructs world
     * positions with.
     *
     * <p>The view matrix is exactly the one MC renders with: {@code Camera.getViewRotationMatrix}
     * returns {@code rotation(orientation.conjugate())} - the rotation half only, no translation - so
     * the view is that followed by a translation by the negated camera position.
     */
    private static void buildMatrices(CameraRenderState camera) {
        Vec3 pos = camera.pos;
        VIEW.set(camera.viewRotationMatrix).translate((float) -pos.x, (float) -pos.y, (float) -pos.z);
        VIEW_PROJ.set(camera.projectionMatrix).mul(VIEW);
        INVERSE.set(VIEW_PROJ).invert();
        INVERSE_VIEW.set(VIEW).invert();
    }

    /**
     * The largest screen motion (in uv units) this frame's camera could possibly have produced: the
     * rotation part (a rotation moves the image by about {@code angle / fovY} of the screen) plus the
     * translation part at the shallowest blurrable depth. Both are per-frame deltas, so no frame-time
     * scaling is needed; both get a margin, because the displacement grows towards the frame's edge.
     */
    private static float rotationBound(CameraRenderState camera) {
        float fovY = 2f * (float) Math.atan(tanHalfFovY(camera));
        float angle = angleBetween(LAST_ORIENTATION, camera.orientation);
        return Math.max(MIN_BOUND, angle / fovY * VELOCITY_MARGIN);
    }

    /**
     * How far the difference between the measured reprojection and the far-field one (i.e. the
     * parallax of this pixel) can plausibly be: the camera's movement across the shallowest blurrable
     * depth gives the largest legitimate value. Anything beyond it did not come from the world
     * projection - the first-person hand is the case that matters.
     */
    private static float parallaxBound(CameraRenderState camera) {
        float translation = (float) (lastCameraPos.distanceTo(camera.pos) / MIN_PLAUSIBLE_DEPTH)
                / (2f * tanHalfFovY(camera));
        return Math.max(MIN_BOUND, translation * VELOCITY_MARGIN);
    }

    /** {@code tan(fovY/2)}, straight out of the projection: for a perspective matrix m11 = 1/tan. */
    private static float tanHalfFovY(CameraRenderState camera) {
        float m11 = Math.abs(camera.projectionMatrix.m11());
        return m11 > 1.0e-6f ? 1.0f / m11 : 0.7f;
    }

    /** The angle between two orientations, in radians. */
    private static float angleBetween(Quaternionf from, Quaternionf to) {
        float dot = Math.abs(from.x * to.x + from.y * to.y + from.z * to.z + from.w * to.w);
        return 2f * (float) Math.acos(Math.min(1.0, dot));
    }

    /**
     * The clip-space depth of a point far down the view axis, which the shader reprojects to get the
     * rotation-only part of the velocity. Computed through the real matrices (rather than from a
     * formula) so no depth-range convention has to be assumed, and kept clear of the far plane where
     * the projection divides by ~0.
     */
    private static float farDepth(CameraRenderState camera) {
        float far = Math.min(FAR_PROBE_BLOCKS, Math.max(camera.depthFar * 0.5f, 32f));
        INVERSE_VIEW.transform(PROBE.set(0f, 0f, -far, 1f));
        PROBE_CLIP.set(PROBE).mul(VIEW_PROJ);
        if (Math.abs(PROBE_CLIP.w) < 1.0e-6f) {
            return depthZeroToOne() ? 1.0e-4f : 0.9999f;
        }
        return PROBE_CLIP.z / PROBE_CLIP.w;
    }

    /** The block values, in the order the shader and the template expect. */
    private static List<UniformValue> parameters(ModConfig cfg, float scale, CameraRenderState camera,
                                                 float maxVelocity) {
        Vec3 pos = camera.pos;
        List<UniformValue> values = new ArrayList<>(13);
        // Copies, not the scratch matrices: a record keeps the reference it is given, and the change
        // detection in LivePostChain compares by value - handing it a matrix we overwrite next frame
        // would make every frame look unchanged.
        values.add(new UniformValue.Matrix4x4Uniform(new Matrix4f(INVERSE)));
        values.add(new UniformValue.Matrix4x4Uniform(new Matrix4f(PREVIOUS)));
        values.add(new UniformValue.FloatUniform(cfg.postProcessBlock.motionBlurStrength));
        values.add(new UniformValue.FloatUniform(scale));
        values.add(new UniformValue.FloatUniform(cfg.postProcessBlock.motionBlurMaxRadius));
        values.add(new UniformValue.FloatUniform(depthZeroToOne() ? 1f : 0f));
        values.add(new UniformValue.FloatUniform(farDepth(camera)));
        values.add(new UniformValue.FloatUniform(maxVelocity));
        values.add(new UniformValue.FloatUniform(parallaxBound(camera)));
        values.add(new UniformValue.FloatUniform(cfg.postProcessBlock.motionBlurDebug ? 1f : 0f));
        values.add(new UniformValue.FloatUniform((float) pos.x));
        values.add(new UniformValue.FloatUniform((float) pos.y));
        values.add(new UniformValue.FloatUniform((float) pos.z));
        return values;
    }

    /**
     * Whether the depth range is [0,1] rather than [-1,1]. The device decides, and it cannot change
     * while the game runs, so it is read once.
     */
    private static boolean depthZeroToOne() {
        if (depthZeroToOne == null) {
            depthZeroToOne = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
        }
        return depthZeroToOne;
    }

    /**
     * Reports the state once per change, after the effect has had a moment to settle: whether the
     * effect is actually blurring, and if not, which of the two ways it can be idle is responsible
     * (no motion to integrate versus no chain to run). A silent no-op is the one failure this effect
     * cannot be debugged by looking at the screen - the shader is either blurring or returning the
     * pixel untouched, and both look like "the setting does nothing".
     *
     * @param state 0 blurring, 1 enabled but no exposure yet, 2 missing, 3 switched off.
     */
    private static void report(int state, ModConfig cfg, float scale, float frameTime,
                               float maxVelocity, float maxParallax) {
        if (state == 0 && CHAIN.chain() == null) {
            state = 2; // blurring parameters, but nothing to run them on
        }
        if (state == reportedState || (state == 0 && enabledFrames < 60) || enabledFrames < 2) {
            return;
        }
        reportedState = state;
        switch (state) {
            case 0 -> LOGGER.info("motion blur running: exposure={}ms frame={}ms scale={}"
                            + " maxVelocity={} maxParallax={} strength={} chain=live({})",
                    cfg.postProcessBlock.motionBlurExposureMs, frameTime * 1000f, scale, maxVelocity, maxParallax,
                    cfg.postProcessBlock.motionBlurStrength, CHAIN.liveUniforms() ? "writable" : "rebuilt");
            case 1 -> LOGGER.info("motion blur idle: no frame-to-frame exposure yet"
                            + " (camera or history just reset)");
            case 2 -> LOGGER.info("motion blur idle: the post chain is not loaded"
                            + " - see the warnings above for the reason");
            default -> LOGGER.info("motion blur off (module or post-processing disabled,"
                    + " or a screen is open while 'skip with screens' is on)");
        }
    }
}
