package dev.skypocket.mixin;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Auto-sprint: forces the sprint key "held" in the client input when enabled.
 *
 * <p>Vanilla's {@code LocalPlayer.canStartSprinting()} already enforces every
 * legality rule - forward movement, not crouching, hunger, on-ground, not using an
 * item - and the sprint key is only honored inside that check. So faking the key
 * press gives exactly vanilla-legit sprinting with no re-implemented conditions.
 */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin {

    @Inject(method = "tick", at = @At("RETURN"), require = 0)
    private void sky$forceSprint(CallbackInfo ci) {
        ModConfig config = ConfigManager.get();
        if (!config.autoSprintBlock.autoSprintEnabled) {
            return;
        }
        ClientInput self = (ClientInput) (Object) this;
        Input old = self.keyPresses;
        if (old == null) {
            return;
        }
        // Rebuild the input record with sprint=true; everything else unchanged.
        self.keyPresses = new Input(old.forward(), old.backward(), old.left(),
                old.right(), old.jump(), old.shift(), true);
    }
}
