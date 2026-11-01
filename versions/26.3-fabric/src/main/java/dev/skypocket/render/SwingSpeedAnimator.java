package dev.skypocket.render;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;

/**
 * Client-only swing-animation clock, fully decoupled from the game's
 * {@code swingTime}/{@code swinging} lifecycle.
 *
 * <p>At 100% speed with "ignore haste" off it is inactive and the vanilla
 * {@code getAttackAnim} is used (perfectly synced with game logic). Otherwise the
 * animation advances at its own rate: slower speeds play to completion instead of
 * being cut off, and faster speeds can restart immediately without waiting for the
 * game's swing to finish.
 */
public final class SwingSpeedAnimator {
    /**
     * Re-swing is allowed once the animation reaches this progress. Vanilla uses
     * {@code swingTime >= duration / 2} (progress 0.5); 0.6 keeps rapid swings feeling
     * connected while ignoring clicks that are too early in the swing.
     */
    private static final float RESET_THRESHOLD = 0.75f;

    /** Animation progress in [0, 1]; 1 = done (idle pose). */
    private static float progress = 1.0f;
    private static float prevProgress = 1.0f;

    private SwingSpeedAnimator() {}

    /** Whether the custom clock should drive the swing (otherwise vanilla is used). */
    public static boolean isCustomActive() {
        ModConfig config = ConfigManager.get();
        if (!config.handItemEnabled) {
            return false;
        }
        return config.swingIgnoreHaste || config.swingSpeed != 100;
    }

    /**
     * Starts a new swing once the current animation has passed the reset threshold.
     * Clicks that arrive too early are ignored, so rapid clicking can't reset the
     * clock mid-swing and make the animation jitter.
     */
    public static void restart() {
        if (progress < RESET_THRESHOLD) {
            return;
        }
        // Restart the swing immediately from the current position (no queue, no chain).
        progress = 0.0f;
        prevProgress = 0.0f;
    }

    /** Advances the clock by one client tick. */
    public static void tick() {
        if (!isCustomActive()) {
            return;
        }
        prevProgress = progress;
        if (progress >= 1.0f) {
            return;
        }
        float duration = currentDuration();
        if (duration <= 0.0f) {
            progress = 1.0f;
            return;
        }
        float speed = ConfigManager.get().swingSpeed / 100.0f;
        progress = Math.min(1.0f, progress + speed / duration);
    }

    /** Interpolated progress for the renderer's partial tick. */
    public static float getProgress(float partialTick) {
        return prevProgress + (progress - prevProgress) * partialTick;
    }

    /**
     * Effective swing duration in ticks, mirroring vanilla
     * {@code LivingEntity.getCurrentSwingDuration()} (Haste / Mining Fatigue included
     * unless ignored).
     */
    private static float currentDuration() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return 1.0f;
        }
        InteractionHand hand = player.swingingArm != null ? player.swingingArm : InteractionHand.MAIN_HAND;
        ItemStack stack = player.getItemInHand(hand);
        int base = stack.getSwingAnimation().duration();
        if (ConfigManager.get().swingIgnoreHaste) {
            return base;
        }
        if (MobEffectUtil.hasDigSpeed(player)) {
            return base - 1 - MobEffectUtil.getDigSpeedAmplification(player);
        }
        if (player.hasEffect(MobEffects.MINING_FATIGUE)) {
            return base + 1 + player.getEffect(MobEffects.MINING_FATIGUE).getAmplifier() * 2;
        }
        return base;
    }
}
