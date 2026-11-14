package dev.skypocket.gui;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;
import dev.skypocket.gui.setting.Setting;
import dev.skypocket.ui.Style;
import dev.skypocket.ui.widget.CrosshairGrid;
import dev.skypocket.ui.widget.CrosshairPreview;
import dev.skypocket.hud.StatsBarHudElement;
import dev.skypocket.i18n.Localization;
import dev.skypocket.render.AntialiasingRenderer;
import dev.skypocket.render.ColorGradeRenderer;
import dev.skypocket.render.ColorUtil;
import dev.skypocket.render.HotspotOverlayStyle;
import dev.skypocket.render.HotspotType;
import dev.skypocket.skyblock.MiningStrengthTable;
import dev.skypocket.sound.MentionSoundManager;
import dev.skypocket.sound.SoundManager;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/**
 * Defines the built-in Click GUI modules as declarative {@link Setting} lists. Each
 * feature's settings live here instead of inside the panel, and are registered into
 * {@link ModuleRegistry} on client init.
 */
final class ModuleDefinitions {

    private ModuleDefinitions() {
    }

    static void registerAll() {
        ModConfig cfg = ConfigManager.get();
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.CrosshairFeature());
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.BlockOutlineFeature());
        // Phase 1 of the feature SPI: these three migrated off the hand-written registrations.
        // They still register into the same ModuleRegistry the panel reads, so nothing visible
        // changes - see dev.skypocket.feature.ModuleAdapter.
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.ThirdPersonFeature());
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.NoHurtCameraFeature());
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.MentionAlertFeature());
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.ChatCompactFeature());
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.DamageSplashFeature());
        // Phase 4 batch 1: the ESP family, all toggle + zero-or-one setting.
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.PlayerGlowFeature());
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.ItemGlowFeature());
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.DragonGlowFeature());
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.HidePlayersFeature());
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.HideSpawnPointPlayersFeature());
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.HidePlayersNearNpcsFeature());
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.HideOtherPlayersPresentsFeature());
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.HitboxFeature());
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.HideLandedArrowsFeature());
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.HideStuckArrowsFeature());
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.FishingHotspotOverlayFeature());
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.LegendarySeaCreatureWarningFeature());
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.DrillFuelFeature());
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.MiningAbilityReminderFeature());
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.MiningBreakPredictFeature());
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.CommissionsFeature());
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.PostProcessFeature());
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.HandItemFeature());
        // Phase 2: these two also own a HUD element, so their HudSpec replaces the editor's
        // hand-written entry for that target.
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.ClockFeature());
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.DayCountFeature());
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.FpsTpsPingFeature());
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.HeldItemNameFeature());
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.StatsDisplayFeature());
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.SkillDisplayFeature());
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.ThunderBottleFeature());
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.ItemPickupLogFeature());
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.AutoSprintFeature());
        dev.skypocket.feature.ModuleAdapter.register(new dev.skypocket.feature.modules.ScrollableTooltipsFeature());
    }

}
