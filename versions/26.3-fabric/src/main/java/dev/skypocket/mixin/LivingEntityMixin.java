package dev.skypocket.mixin;

import dev.skypocket.render.SwingSpeedAnimator;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hooks the local player's swing for the {@link SwingSpeedAnimator}:
 * <ul>
 *   <li>{@code swing}: restarts the decoupled animation clock on every new swing.</li>
 *   <li>{@code getAttackAnim}: returns the custom clock's progress instead of the
 *       game's, but only when the custom clock is active (at 100% speed with haste
 *       ignored off, the vanilla value is returned for a perfect sync).</li>
 * </ul>
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Z)V", at = @At("HEAD"))
    private void sky$onSwing(InteractionHand hand, boolean updateSelf, CallbackInfo ci) {
        if ((Object) this != Minecraft.getInstance().player) {
            return;
        }
        if (SwingSpeedAnimator.isCustomActive()) {
            SwingSpeedAnimator.restart();
        }
    }

    @Inject(method = "getAttackAnim", at = @At("RETURN"), cancellable = true)
    private void sky$adjustSwingSpeed(float partialTick, CallbackInfoReturnable<Float> cir) {
        if ((Object) this != Minecraft.getInstance().player) {
            return;
        }
        if (SwingSpeedAnimator.isCustomActive()) {
            cir.setReturnValue(SwingSpeedAnimator.getProgress(partialTick));
        }
    }
}
