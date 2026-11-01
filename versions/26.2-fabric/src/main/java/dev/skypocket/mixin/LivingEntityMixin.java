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
import org.spongepowered.asm.mixin.injection.ModifyVariable;
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
    /** Remembers the hand: the duration is computed from the held stack. */
    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;"
            + "Lnet/minecraft/world/item/component/SwingAnimation;Z)Z", at = @At("HEAD"))
    private void sky$onSwing(InteractionHand hand, SwingAnimation animation, boolean updateSelf,
                             CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this != Minecraft.getInstance().player) {
            return;
        }
        SwingSpeedAnimator.setSwingHand(hand);
    }

    /**
     * Hands the swing a longer animation before it starts.
     *
     * <p>Vanilla reads the duration when {@code swing} is entered, so this is the only place where the
     * speed can be applied without a second clock overriding the progress afterwards - which is what
     * made the hand jitter between two slightly different positions.
     */
    @ModifyVariable(method = "swing", at = @At("HEAD"), argsOnly = true, index = 2)
    private SwingAnimation sky$lengthenSwing(SwingAnimation animation) {
        if ((Object) this != Minecraft.getInstance().player) {
            return animation;
        }
        if (!SwingSpeedAnimator.isCustomActive()) {
            return animation;
        }
        int ticks = SwingSpeedAnimator.customDurationTicks();
        if (ticks <= animation.duration()) {
            return animation;
        }
        return new SwingAnimation(animation.type(), ticks);
    }
//#else
    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Z)V", at = @At("HEAD"))
    private void sky$onSwing(InteractionHand hand, boolean updateSelf, CallbackInfo ci) {
        if ((Object) this != Minecraft.getInstance().player) {
            return;
        }
        SwingSpeedAnimator.setSwingHand(hand);
        if (SwingSpeedAnimator.isCustomActive()) {
            SwingSpeedAnimator.restart();
        }
    }

    /** 26.2 has no animation argument, so the progress itself has to be replaced. */
    @Inject(method = "getAttackAnim", at = @At("RETURN"), cancellable = true)
    private void sky$adjustSwingSpeed(float partialTick, CallbackInfoReturnable<Float> cir) {
        if ((Object) this != Minecraft.getInstance().player) {
            return;
        }
        if (SwingSpeedAnimator.isCustomActive() && SwingSpeedAnimator.rawProgress() < 1.0f) {
            cir.setReturnValue(SwingSpeedAnimator.getProgress(partialTick));
        }
    }
//#endif
}
