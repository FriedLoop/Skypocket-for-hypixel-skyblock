package dev.skypocket.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.skypocket.config.ConfigManager;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.StuckInBodyLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides arrows stuck in the player's body. When a non-piercing arrow hits a living
 * entity, the arrow entity is discarded and the stuck arrow is rendered by
 * {@link StuckInBodyLayer} from the entity's arrow count; cancelling this layer's
 * submit skips that rendering (client-side only).
 */
@Mixin(StuckInBodyLayer.class)
public abstract class StuckInBodyLayerMixin {

    @Inject(
            method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/AvatarRenderState;FF)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0)
    private void sky$hideStuckArrows(PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
                                     AvatarRenderState state, float yRot, float xRot, CallbackInfo ci) {
        if (ConfigManager.get().hideStuckArrows) {
            ci.cancel();
        }
    }
}
