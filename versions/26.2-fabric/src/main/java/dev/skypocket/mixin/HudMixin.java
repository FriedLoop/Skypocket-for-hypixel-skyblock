package dev.skypocket.mixin;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;
import dev.skypocket.render.StatsOverlayRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides the vanilla HUD pieces the SkyBlock stats display replaces, and hands the health area over to
 * {@link StatsOverlayRenderer}.
 *
 * <p>Each piece has its own switch, because which of them should go depends on the style: the classic
 * style draws its icons where the vanilla ones were, so leaving them on would draw both on top of each
 * other, while the modern style puts bars somewhere else and a player may well want the vanilla hearts
 * kept. The three that the module takes over (hearts, food, air) default to hidden, the armour row -
 * the one piece nothing here collides with - defaults to visible.
 *
 * <p>Cancelling at {@code HEAD} beats editing the methods: the values behind these are wrong on
 * SkyBlock, not merely styled differently (the hearts come from the client entity, and the food row is
 * replaced by mana outright). The air row is cancelled rather than moved because it would land exactly
 * on mana's second row; both styles draw it themselves.
 *
 * <p>The replacement is drawn at the {@code TAIL} of {@code extractPlayerHealth}: by then vanilla has
 * drawn the armour bar, so our rows land on top of the finished block instead of being painted over.
 *
 * <p>Two behaviours are lost with the pieces being cancelled: vanilla plays the "bubble popped" sound
 * from inside its air row method, and the action bar's fade timing goes with its drawing.
 */
@Mixin(Hud.class)
public abstract class HudMixin {

    @Inject(method = "extractHearts", at = @At("HEAD"), cancellable = true, require = 0)
    private void sky$replaceHearts(GuiGraphicsExtractor graphics, Player player, int i, int j, int k,
                                   int l, float f, int m, int n, int o, boolean bl, CallbackInfo ci) {
        if (hide(cfg -> cfg.statsHideVanillaHealth)) {
            ci.cancel();
        }
    }

    @Inject(method = "extractFood", at = @At("HEAD"), cancellable = true, require = 0)
    private void sky$replaceFood(GuiGraphicsExtractor graphics, Player player, int i, int j,
                                 CallbackInfo ci) {
        if (hide(cfg -> cfg.statsHideVanillaFood)) {
            ci.cancel();
        }
    }

    @Inject(method = "extractAirBubbles", at = @At("HEAD"), cancellable = true, require = 0)
    private void sky$replaceAir(GuiGraphicsExtractor graphics, Player player, int i, int j, int k,
                                CallbackInfo ci) {
        if (hide(cfg -> cfg.statsHideVanillaAir)) {
            ci.cancel();
        }
    }

    /** Static in the target, so the handler has to be static too. */
    @Inject(method = "extractArmor", at = @At("HEAD"), cancellable = true, require = 0)
    private static void sky$replaceArmor(GuiGraphicsExtractor graphics, Player player, int i, int j,
                                         int k, int l, CallbackInfo ci) {
        if (hide(cfg -> cfg.statsHideVanillaArmor)) {
            ci.cancel();
        }
    }

    /**
     * The action bar text. Hiding it does not stop the display from working: the numbers come from the
     * message event, which is untouched by whether the text is drawn.
     */
    @Inject(method = "extractOverlayMessage", at = @At("HEAD"), cancellable = true, require = 0)
    private void sky$hideActionBar(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker delta,
                                   CallbackInfo ci) {
        if (hide(cfg -> cfg.statsHideVanillaActionBar)) {
            ci.cancel();
        }
    }

    /** Shared gate: the module has to be active on SkyBlock *and* the piece switched off. */
    private static boolean hide(java.util.function.Predicate<ModConfig> toggle) {
        return StatsOverlayRenderer.active() && toggle.test(ConfigManager.get());
    }

    @Inject(method = "extractPlayerHealth", at = @At("TAIL"), require = 0)
    private void sky$drawStats(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        StatsOverlayRenderer.draw(graphics);
    }
}
