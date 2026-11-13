package dev.skypocket.mixin;

import dev.skypocket.render.ThirdPersonCamera;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets the third-person camera pass through blocks: vanilla's {@code getMaxZoom}
 * samples 8 points behind the camera and shortens the distance when a block is
 * hit; we skip that entirely and always return the requested distance.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {

    @Inject(method = "getMaxZoom", at = @At("HEAD"), cancellable = true, require = 0)
    private void sky$thirdPersonNoClip(float distance, CallbackInfoReturnable<Float> cir) {
        if (ThirdPersonCamera.isActive()) {
            cir.setReturnValue(distance);
        }
    }
}
