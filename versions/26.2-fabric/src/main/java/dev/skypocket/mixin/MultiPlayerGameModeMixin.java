package dev.skypocket.mixin;

import dev.skypocket.skyblock.MiningBreakTracker;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Feeds the client's destroy actions into {@link MiningBreakTracker} so it knows when
 * a break cycle starts (and on which block) — the {@code T0} of the break timing.
 *
 * <p>Both {@code startDestroyBlock} and {@code continueDestroyBlock} report their
 * target: the former fires when the attack button goes down, the latter on every tick
 * the player keeps mining, which is how a target switch while holding is caught.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {

    @Inject(method = "startDestroyBlock", at = @At("RETURN"), require = 0)
    private void sky$onStartDestroy(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            MiningBreakTracker.onTarget(pos);
        }
    }

    @Inject(method = "continueDestroyBlock", at = @At("HEAD"), require = 0)
    private void sky$onContinueDestroy(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        MiningBreakTracker.onTarget(pos);
    }

    @Inject(method = "stopDestroyBlock", at = @At("HEAD"), require = 0)
    private void sky$onStopDestroy(CallbackInfo ci) {
        MiningBreakTracker.onStopDestroy();
    }
}
