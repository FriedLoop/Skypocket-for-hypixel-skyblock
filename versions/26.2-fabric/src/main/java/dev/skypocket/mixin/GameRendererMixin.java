package dev.skypocket.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.skypocket.config.ConfigManager;
import dev.skypocket.render.AntialiasingRenderer;
import dev.skypocket.render.BloomRenderer;
import dev.skypocket.render.ColorGradeRenderer;
import dev.skypocket.render.MotionBlurRenderer;
import dev.skypocket.render.GuiPanelCache;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Disables the camera shake applied when the player takes damage. {@code bobHurt}
 * also handles the death camera tilt, so it is only cancelled while the camera
 * entity is alive — the death tilt is left intact.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void sky$disableHurtShake(CameraRenderState state, PoseStack poseStack, CallbackInfo ci) {
        if (ConfigManager.get().noHurtCameraEnabled && !state.entityRenderState.isDeadOrDying) {
            ci.cancel();
        }
    }

    /**
     * Frames start here, so this is the last point where no render pass is open: the Click
     * GUI panel cache can open its own pass and bake the panel it recorded last frame.
     * (See {@code GuiPanelCache}: the GUI pass itself cannot be nested inside.)
     */
    @Inject(method = "render", at = @At("HEAD"))
    private void sky$bakePanelCache(
//#if MC>=260300
            CallbackInfo ci) {
//#else
            DeltaTracker delta, boolean renderLevel, CallbackInfo ci) {
//#endif
        GuiPanelCache.bakePending();
    }

    /**
     * The world post-processing effects, at the only point where they can be correct: the level is
     * finished, vanilla's menu blur has not run yet and the HUD/GUI have not been drawn. Injecting
     * after the GUI instead would soften text, the crosshair and the Click GUI; injecting any earlier
     * (or into vanilla's own post-effect slot, which belongs to the creeper/spider vision effects)
     * would either miss the level or clash with it.
     *
     * <p>All of them live in one injection so their order is fixed: anti-aliasing first (its edge
     * detection wants the ungraded, unglowing image), then motion blur, then the colour grade, then bloom
     * - which is why it is last: its bright pass should see the finished image, so raising the grade's
     * exposure makes things glow.
     */
     @Inject(method = "renderLevel", at = @At("TAIL"))
     private void sky$worldPostEffects(
//#if MC>=260300
            CallbackInfo ci) {
//#else
            DeltaTracker delta, CallbackInfo ci) {
//#endif
        AntialiasingRenderer.process();
        MotionBlurRenderer.process();
        ColorGradeRenderer.process();
        BloomRenderer.process();
     }
}
