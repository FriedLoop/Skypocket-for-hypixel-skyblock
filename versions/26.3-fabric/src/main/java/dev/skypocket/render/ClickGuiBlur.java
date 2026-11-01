package dev.skypocket.render;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;
import net.minecraft.client.Minecraft;

/**
 * The vanilla 26.2 renderer already blurs the background behind a screen via the
 * {@code Options.menuBackgroundBlurriness} option (0..10), applied by
 * {@code Screen.extractBackground}. Doing our own blur on top of that conflicts and
 * throws "Can only blur once per frame".
 *
 * <p>So instead of blurring ourselves, we keep our config's toggle/strength in sync
 * with that vanilla option.
 */
public final class ClickGuiBlur {
    private ClickGuiBlur() {
    }

    /** Maps our blur config to the vanilla menu-background blurriness option. */
    public static void apply() {
        ModConfig cfg = ConfigManager.get();
        int blurriness = cfg.clickGuiBlur ? Math.round(cfg.clickGuiBlurIntensity / 10.0f) : 0;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null) {
            mc.options.menuBackgroundBlurriness().set(blurriness);
        }
    }
}
