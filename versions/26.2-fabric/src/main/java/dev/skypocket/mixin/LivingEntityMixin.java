package dev.skypocket.mixin;

import dev.skypocket.render.SwingSpeedAnimator;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
//#if MC>=260300
import net.minecraft.world.item.component.SwingAnimation;
//#endif
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Swing-speed hook for {@link SwingSpeedAnimator}.
 *
 * <p>26.2 only exposed {@code getAttackAnim}, so the mod had to run its own clock and replace the
 * progress every frame. 26.3's {@code swing(hand, SwingAnimation, updateSelf)} takes the animation as
 * an argument and counts against it, so the length can be set before the state exists and vanilla's
 * own counter does the rest: one timeline, no second clock to fight.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

//#if MC>=260300
    private InteractionHand sky$swingHand = InteractionHand.MAIN_HAND;
    private SwingAnimation sky$swingAnimation = SwingAnimation.DEFAULT;

    /** Mirrors 26.2: record the hand, restart only when the custom 75% gate allows it. */
    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;"
            + "Lnet/minecraft/world/item/component/SwingAnimation;Z)Z", at = @At("HEAD"), require = 0)
    private void sky$onSwing(InteractionHand hand, SwingAnimation animation, boolean updateSelf,
                             CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this != Minecraft.getInstance().player) {
            return;
        }
        SwingSpeedAnimator.setSwingHand(hand);
        if (SwingSpeedAnimator.isCustomActive()
                && SwingSpeedAnimator.restartIfReady()) {
            sky$swingHand = hand;
            sky$swingAnimation = animation;
        }
    }

    /** 26.3 render path: same custom progress as 26.2's getAttackAnim. */
    @Inject(method = "getSwingAnimation", at = @At("RETURN"), cancellable = true, require = 0)
    private void sky$overrideSwingAnimation(float partialTick,
                                            CallbackInfoReturnable<Float> cir) {
        if ((Object) this != Minecraft.getInstance().player) {
            return;
        }
        if (SwingSpeedAnimator.isCustomActive()) {
            cir.setReturnValue(SwingSpeedAnimator.getProgress(partialTick));
        }
    }

    /**
     * Vanilla clears its SwingState at the vanilla half-duration, so the custom clock also needs a
     * synthetic render descriptor while it is still playing. This is a read-only render-state
     * shim: no swing cancellation, packets, or attack logic are involved.
     */
    @Inject(method = "getCurrentSwing", at = @At("RETURN"), cancellable = true, require = 0)
    private void sky$overrideCurrentSwing(CallbackInfoReturnable<LivingEntity.SwingDescription> cir) {
        if ((Object) this != Minecraft.getInstance().player) {
            return;
        }
        if (SwingSpeedAnimator.isCustomActive() && SwingSpeedAnimator.rawProgress() < 1.0f) {
            cir.setReturnValue(new LivingEntity.SwingDescription(sky$swingHand, sky$swingAnimation,
                    Math.max(1, SwingSpeedAnimator.customDurationTicks())));
        }
    }
//#else
    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Z)V", at = @At("HEAD"), require = 0)
    private void sky$onSwing(InteractionHand hand, boolean updateSelf, CallbackInfo ci) {
        if ((Object) this != Minecraft.getInstance().player) {
            return;
        }
        SwingSpeedAnimator.setSwingHand(hand);
        if (SwingSpeedAnimator.isCustomActive()) {
            // Never cancel vanilla's swing: that changes the client swing state. The 75% rule
            // belongs to the custom clock, and getAttackAnim keeps rendering the custom progress
            // even when vanilla has started an early swing.
            SwingSpeedAnimator.restart();
        }
    }

    /** 26.2 has no animation argument, so the progress itself has to be replaced. */
    @Inject(method = "getAttackAnim", at = @At("RETURN"), cancellable = true, require = 0)
    private void sky$adjustSwingSpeed(float partialTick, CallbackInfoReturnable<Float> cir) {
        if ((Object) this != Minecraft.getInstance().player) {
            return;
        }
        if (SwingSpeedAnimator.isCustomActive()) {
            // Always keep rendering on the custom clock while held-item animation is active.
            // Handing back to vanilla when the custom clock reaches 1 made an early vanilla
            // swing (which the 75% rule intentionally ignored) suddenly appear as a jump.
            cir.setReturnValue(SwingSpeedAnimator.getProgress(partialTick));
        }
    }
//#endif
}
