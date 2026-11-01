package dev.skypocket.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hand-item tweaks:
 * <ul>
 *   <li>{@code swingArm}: vanilla applies a position thrust ("挥", a translate) and
 *       then the attack rotation ("rotate", via {@code applyItemArmAttackTransform}).
 *       When disabling the swing we cancel the whole method and re-apply ONLY the
 *       rotation, dropping the thrust - the same split ToolPose uses.</li>
 *   <li>{@code applyItemArmTransform}: optionally removes the item-switch "reequip" bob
 *       (via the dedicated toggle) and applies the position offset, static rotation and
 *       scale BEFORE the swing rotation, so the transform shares ToolPose's rotation
 *       pivot (offset/scale live in the pre-rotation frame instead of the rotated frame).</li>
 * </ul>
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {

    @Inject(method = "swingArm", at = @At("HEAD"), cancellable = true)
    private void sky$removeSwingThrust(float attack, PoseStack poseStack, int invert, HumanoidArm arm, CallbackInfo ci) {
        ModConfig config = ConfigManager.get();
        if (!config.handItemEnabled || !config.handItemDisableSwing) {
            return;
        }
        ci.cancel();
        // Re-apply ONLY the swing rotation - the exact 4 rotations of vanilla's
        // applyItemArmAttackTransform (identical to ToolPose with swingIntensity=1.0).
        // swingArm applies the position thrust ("挥") first, then delegates to
        // applyItemArmAttackTransform; cancelling the whole method drops the thrust
        // while we replay the full rotation, so the idle pose stays vanilla (the
        // 45°/-45° Y rotations cancel out at attack=0).
        float ySwing = Mth.sin(attack * attack * (float) Math.PI);
        float xzSwing = Mth.sin(Mth.sqrt(attack) * (float) Math.PI);
        rotate(poseStack, Axis.YP, invert * (45.0f + ySwing * -20.0f));
        rotate(poseStack, Axis.ZP, invert * xzSwing * -20.0f);
        rotate(poseStack, Axis.XP, xzSwing * -80.0f);
        rotate(poseStack, Axis.YP, invert * -45.0f);
    }

    /**
     * Mirrors ToolPose's {@code applyItemArmTransform} TAIL inject: offset + static
     * rotation + scale are applied here, BEFORE {@code swingArm}'s rotation, so they
     * share ToolPose's pivot (the swing rotates around the fixed hand point, and the
     * offset does not rotate with the swing).
     *
     * <p>Vanilla {@code applyItemArmTransform} translates Y by {@code -0.52 +
     * inverseArmHeight * -0.6}; the {@code inverseArmHeight} term is the item-switch
     * "reequip" bob ({@code mainHandHeight}/{@code offHandHeight}). Cancelling it is
     * driven by the dedicated {@code handItemDisableReequip} toggle, independent of the
     * swing toggle.
     */
    @Inject(method = "applyItemArmTransform", at = @At("TAIL"))
    private void sky$applyHandTransform(PoseStack poseStack, HumanoidArm arm, float inverseArmHeight, CallbackInfo ci) {
        ModConfig config = ConfigManager.get();
        if (!config.handItemEnabled) {
            return;
        }
        if (config.handItemDisableReequip) {
            poseStack.translate(0.0f, 0.6f * inverseArmHeight, 0.0f);
        }
        poseStack.translate(config.handItemOffsetX, config.handItemOffsetY, config.handItemOffsetZ);
        rotate(poseStack, Axis.XP, config.handItemRotateX);
        rotate(poseStack, Axis.YP, config.handItemRotateY);
        rotate(poseStack, Axis.ZP, config.handItemRotateZ);
        float s = Math.max(0.01f, config.handItemScale);
        poseStack.scale(s, s, s);
    }
    /**
     * Rotates the stack by an axis angle.
     *
     * <p>26.3 removed {@code mulPose(Quaternionf)} from PoseStack and kept {@code rotateDegrees(Axis,
     * float)}; older versions only had the quaternion form. One helper keeps the seven call sites from
     * each carrying a version branch.
     */
    private static void rotate(PoseStack poseStack, Axis axis, float degrees) {
//#if MC>=260300
        poseStack.rotateDegrees(axis, degrees);
//#else
        poseStack.mulPose(axis.rotationDegrees(degrees));
//#endif
    }
}
