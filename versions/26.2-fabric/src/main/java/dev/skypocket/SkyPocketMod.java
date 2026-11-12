package dev.skypocket;

import com.mojang.blaze3d.platform.InputConstants;
import dev.skypocket.chat.MentionAlert;
import dev.skypocket.config.ConfigManager;
import dev.skypocket.render.GuiCornerTextures;
import dev.skypocket.gui.OneClickScreen;
import dev.skypocket.sound.MentionSoundManager;
import dev.skypocket.hud.ClockHudElement;
import dev.skypocket.hud.CommissionHudElement;
import dev.skypocket.hud.CrosshairHudElement;
import dev.skypocket.hud.DrillFuelBarHudElement;
import dev.skypocket.hud.DayCountHudElement;
import dev.skypocket.hud.FpsTpsPingHudElement;
import dev.skypocket.hud.HeldItemNameHudElement;
import dev.skypocket.hud.ItemPickupLogHudElement;
import dev.skypocket.hud.LegendarySeaCreatureWarningHudElement;
import dev.skypocket.hud.MiningAbilityHudElement;
import dev.skypocket.hud.MiningBreakHudElement;
import dev.skypocket.hud.SkillDisplayHudElement;
import dev.skypocket.hud.StatTextHudElement;
import dev.skypocket.hud.StatsBarHudElement;
import dev.skypocket.hud.ThunderBottleHudElement;
import dev.skypocket.skyblock.MiningAbilityTracker;
import dev.skypocket.skyblock.MiningSpeedBoostTracker;
import dev.skypocket.skyblock.PlayerStatsTracker;
import dev.skypocket.skyblock.ItemPickupTracker;
import dev.skypocket.skyblock.SkillDisplayTracker;
import dev.skypocket.skyblock.SkyBlockNpcTracker;
import dev.skypocket.skyblock.ThunderBottleTracker;
import dev.skypocket.net.PingMonitor;
import dev.skypocket.render.BlockOutlineRenderer;
import dev.skypocket.render.ClickGuiFonts;
import dev.skypocket.render.LivePostChain;
import dev.skypocket.render.HitboxRenderer;
import dev.skypocket.render.HotspotOverlayRenderer;
import dev.skypocket.render.DamageSplashRenderer;
import dev.skypocket.render.MiningBreakRenderer;
import dev.skypocket.render.StatsBarTexture;
import dev.skypocket.render.SwingSpeedAnimator;
import dev.skypocket.skyblock.DamageSplashCompactor;
import dev.skypocket.skyblock.DrillFuelTracker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Client-only entrypoint (fabric.mod.json has "environment": "client").
 *
 * <p>All rendering happens on the render thread only:
 * <ul>
 *   <li>Crosshair HUD element registered via Fabric's {@code HudElementRegistry}
 *       (render callback receives a {@code GuiGraphicsExtractor} + {@code DeltaTracker}).</li>
 *   <li>Block outline drawn via {@code LevelRenderEvents} on the render thread.</li>
 *   <li>Hand-item scale/position/swing applied via mixin inside the renderers.</li>
 * </ul>
 */
public final class SkyPocketMod implements ClientModInitializer {
    public static final String MOD_ID = "skypocket";

    private static KeyMapping clickGuiKey;

    @Override
    public void onInitializeClient() {
        ConfigManager.load();
        // Pick up the stored theme before anything is drawn. The defaults are written to disk once, so
        // from then on the palette lives in config/skypocket/themes/*.json and can be edited by hand.
        dev.skypocket.gui.widget.ThemeStore.seedDefaults();
        dev.skypocket.gui.widget.ThemeStore.apply(ConfigManager.get().clickGuiTheme);
        // Applies the "debug log" setting before anything else can log, so startup is quiet unless asked.
        SkyPocketLog.apply();
        CrosshairHudElement.register();
        HeldItemNameHudElement.register();
        ClockHudElement.register();
        DayCountHudElement.register();
        FpsTpsPingHudElement.register();
        StatTextHudElement.register();
        StatsBarHudElement.register();
        SkillDisplayHudElement.register();
        BlockOutlineRenderer.init();
        HitboxRenderer.init();
        HotspotOverlayRenderer.init();
        MiningBreakRenderer.init();
        DamageSplashRenderer.init();
        MiningAbilityHudElement.register();
        MiningBreakHudElement.register();
        DrillFuelBarHudElement.register();
        ThunderBottleHudElement.register();
        ItemPickupLogHudElement.register();
        CommissionHudElement.register();
        LegendarySeaCreatureWarningHudElement.register();
        // The live post chains (colour grade, bloom) hold objects the device rebuilds on a resource
        // reload, so they have to be dropped then - see LivePostChain.
        LivePostChain.installReloadHook();
        MiningAbilityTracker.init();
        MiningSpeedBoostTracker.init();
        PlayerStatsTracker.init();
        DrillFuelTracker.init();
        SkillDisplayTracker.init();
        MentionSoundManager.init();
        MentionAlert.init();
        registerCommands();
        registerClickGuiKey();
        ClientTickEvents.END_CLIENT_TICK.register(client -> StatsBarTexture.ensureRegistered());
        ClientTickEvents.END_CLIENT_TICK.register(client -> SwingSpeedAnimator.tick());
        ClientTickEvents.END_CLIENT_TICK.register(client -> DamageSplashCompactor.tick());
        ClientTickEvents.END_CLIENT_TICK.register(PingMonitor::onClientTick);
        // Pre-build Click GUI fonts in the background so opening the GUI never stalls.
        ClientTickEvents.END_CLIENT_TICK.register(client -> ClickGuiFonts.tick());
        ClientTickEvents.END_CLIENT_TICK.register(client -> GuiCornerTextures.tick());
        ClientTickEvents.END_CLIENT_TICK.register(SkyBlockNpcTracker::tick);
        ClientTickEvents.END_CLIENT_TICK.register(ThunderBottleTracker::tick);
        ClientTickEvents.END_CLIENT_TICK.register(ItemPickupTracker::tick);
    }

    public static KeyMapping getClickGuiKey() {
        return clickGuiKey;
    }

    /** Right Shift toggles the one-click (ClickGUI) screen. */
    private static void registerClickGuiKey() {
        KeyMapping.Category category = new KeyMapping.Category(Identifier.fromNamespaceAndPath(MOD_ID, "category"));
        clickGuiKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.skypocket.clickgui",
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_RSHIFT,
                category
        ));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (clickGuiKey.consumeClick()) {
                if (client.gui.screen() instanceof OneClickScreen screen) {
                    // Route through onClose so the close animation plays.
                    screen.onClose();
                } else if (client.gui.screen() == null) {
                    client.gui.setScreen(new OneClickScreen());
                }
            }
        });
    }

    /** Registers {@code /skypocket} to open the config screen quickly. */
    private static void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommands.literal("skypocket")
                        .executes(context -> {
                            // Defer to the next client tick: opening a screen directly
                            // from the command executor gets overridden by the chat
                            // screen closing right after the command runs.
                            Minecraft client = context.getSource().getClient();
                            client.schedule(() -> client.gui.setScreen(new OneClickScreen()));
                            return 1;
                        })));
    }}
