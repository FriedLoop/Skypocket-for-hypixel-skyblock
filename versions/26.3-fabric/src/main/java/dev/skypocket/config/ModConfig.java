package dev.skypocket.config;

import dev.skypocket.render.AntialiasingRenderer;
import dev.skypocket.render.ColorGradeRenderer;
import dev.skypocket.render.ColorUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Plain data class serialized to JSON via Gson.
 * All fields are public so Gson can round-trip them directly.
 * Field initializers act as defaults for keys missing from the JSON file.
 */
public class ModConfig {
    public static final int GRID = 15;

    /**
     * The grid every scale setting snaps to. Shared on purpose: the ClickGUI sliders and the position
     * editor's wheel/handles must land on the same values, or the editor writes 1.96 while the slider
     * shows 1.95 and the two disagree about what the size is.
     */
    public static final float SCALE_GRID = 0.05f;
    /** Smallest and largest scale any element may have (the sliders' own bounds). */
    public static final float SCALE_MIN = 0.25f;
    public static final float SCALE_MAX = 6f;

    /** Shared limits for bar sizes: the sliders and the per-bar overrides use the same numbers. */
    public static final int BAR_MIN_W = 40;
    public static final int BAR_MAX_W = 400;
    public static final int BAR_MIN_H = 8;
    public static final int BAR_MAX_H = 48;

    /** Copies a per-bar size array, clamping values and leaving 0 ("use the shared size") alone. */
    private static int[] perBarSizes(int[] values, int min, int max) {
        int[] out = new int[6];
        if (values == null) {
            return out;
        }
        for (int i = 0; i < Math.min(out.length, values.length); i++) {
            out[i] = values[i] <= 0 ? 0 : Math.max(min, Math.min(max, values[i]));
        }
        return out;
    }

    // ---- Feature toggles ----
    public boolean crosshairEnabled = true;
    public boolean handItemEnabled = false;
    public boolean blockOutlineEnabled = true;
    public boolean autoSprintEnabled = false;
    public boolean scrollableTooltipsEnabled = true;
    public boolean scrollableTooltipsAlwaysScroll = false;
    public float scrollableTooltipsSensitivity = 1.0f;

    // ---- Player glow ----
    public boolean playerGlowEnabled = false;
    public int playerGlowColor = ColorDefaults.PLAYER_GLOW;

    // ---- Dragon glow ----
    public boolean dragonGlowEnabled = false;
    public int dragonGlowColor = ColorDefaults.DRAGON_GLOW;

    // ---- Hide nearby players ----
    public boolean hideNearbyPlayersEnabled = false;
    /** Hide other players closer than this many blocks (0..10). */
    public float hideNearbyPlayersRange = 5.0f;

    // ---- Hide arrows ----
    /** Hide arrows that have landed and are stuck in the ground. */
    public boolean hideLandedArrows = false;
    /** Hide arrows stuck in the player's body. */
    public boolean hideStuckArrows = false;

    // ---- Fishing hotspot overlay ----
    public boolean hotspotOverlayEnabled = false;
    /** Overlay colour for hotspots whose kind could not be detected. */
    public int hotspotOverlayColor = ColorUtil.argb(60, 0, 220, 255);
    // Per-kind colours (defaults match the stat text colours).
    public int hotspotColorFishingSpeed = ColorUtil.argb(70, 0x55, 0xFF, 0xFF);
    public int hotspotColorSeaCreature = ColorUtil.argb(70, 0x00, 0xAA, 0xAA);
    public int hotspotColorDoubleHook = ColorUtil.argb(70, 0x55, 0x55, 0xFF);
    public int hotspotColorTrophy = ColorUtil.argb(70, 0xFF, 0xAA, 0x00);
    public int hotspotColorTreasure = ColorUtil.argb(70, 0xFF, 0x8C, 0x00);
    /** Cylinder wall height extending upward from the liquid surface. */
    public float hotspotOverlayHeight = 1.0f;
    /** Edge ring line width. */
    public float hotspotOverlayLineWidth = 2.0f;
    /** How far (blocks) the player can be from a hotspot and still see its overlay. */
    public float hotspotOverlayRange = 64.0f;

    // ---- Third-person camera ----
    /** Let the third-person camera clip through blocks and keep the player visible. */
    public boolean thirdPersonThroughWalls = false;

    // ---- Hurt camera ----
    /** Disable the camera shake when the player takes damage. */
    public boolean noHurtCameraEnabled = false;

    // ---- Chat mention alert ----
    public boolean mentionAlertEnabled = false;
    /** Sound id: a vanilla "minecraft:..." key or an mp3 file name in skypocket/sound/. */
    public String mentionAlertSound = "minecraft:entity.experience_orb.pickup";
    /** Alert volume [0..2] (200% amplifies past the vanilla gain) and pitch [0.5..2]; vanilla sounds only. */
    public float mentionAlertVolume = 1.0f;
    public float mentionAlertPitch = 1.0f;
    /** Custom search text; empty falls back to the game ID (matched in full). */
    public String mentionAlertCustomText = "";
    /** Number of leading characters of the game ID to match (1..16). */
    public int mentionAlertNameLength = 16;

    // ---- Mining ability reminder ----
    public boolean miningAbilityReminderEnabled = false;
    /** Subtitle colors: the ability name and the "available!" part. */
    public int miningAbilityNameColor = ColorUtil.argb(255, 255, 255, 0);
    public int miningAbilityAvailableColor = ColorUtil.argb(255, 0, 255, 0);
    public String miningAbilityReminderSound = "minecraft:entity.experience_orb.pickup";
    public float miningAbilityReminderVolume = 1.0f;
    public float miningAbilityReminderPitch = 1.0f;
    /** Countdown HUD position/scale (draggable in the HUD position editor). */
    public int miningAbilityX = 10;
    public int miningAbilityY = 70;
    public float miningAbilityScale = 1.0f;
    /** Reminder subtitle position/scale (draggable in the HUD position editor). */
    public int miningAbilitySubtitleX = 120;
    public int miningAbilitySubtitleY = 90;
    public float miningAbilitySubtitleScale = 1.0f;

    // ---- Commissions HUD (tab list, mining areas) ----
    public boolean commissionsEnabled = false;
    /** Hide finished commissions; with all of them done the whole block disappears. */
    public boolean commissionsHideDone = false;
    /** Move the unfinished ones to the top (tab order otherwise). */
    public boolean commissionsPendingFirst = false;
    /** Header colour ("Commissions:"), then the pending -> done gradient of the task lines. */
    public int commissionsHeaderColor = ColorUtil.argb(255, 85, 85, 255);
    public int commissionsStartColor = ColorUtil.argb(255, 255, 85, 85);
    public int commissionsDoneColor = ColorUtil.argb(255, 85, 255, 85);
    /** HUD position/scale (draggable in the HUD position editor). */
    public int commissionsX = 10;
    public int commissionsY = 120;
    public float commissionsScale = 1.0f;

    // ---- Post-processing (the module that owns every full-screen pass; see ModuleDefinitions) ----
    /**
     * Master switch of the post-processing module. The four effects keep their own flags (they are the
     * module's group switches), so this only says "the whole stack is off" - and it is on by default so
     * that a config written before the merge keeps doing exactly what it did.
     */
    public boolean postProcessEnabled = true;

    // ---- Anti-aliasing (FXAA post pass over the finished world image) ----
    public boolean antialiasingEnabled = false;
    /** One of {@code AntialiasingRenderer.LIGHT / STANDARD / STRONG}. */
    public String antialiasingStrength = AntialiasingRenderer.STANDARD;
    /**
     * Unsharp-mask amount, run as the chain's last pass - i.e. <em>after</em> FXAA (see the chain's
     * JSON). That order is the point: it puts contrast back into the thin detail the anti-aliasing
     * smoothed away, which is what small world text is. Off by default; the input is an LDR image with
     * no oversampling, so anything past about 1 just rings around high-contrast edges.
     */
    public float antialiasingSharpen = 0.0f;
    /** Keep the pass running while a shader pack is loaded (the pack's own AA must be off then). */
    public boolean antialiasingWithShaders = true;
    /** Skip the pass while a screen is open: the world is behind the GUI/blur and invisible anyway. */
    public boolean antialiasingSkipScreens = true;

    // ---- Colour grading (post pass over the finished world image) ----
    public boolean colorGradeEnabled = false;
    /**
     * Skip the pass while a screen is open. Off by default: the Click GUI is translucent, so the
     * world behind it is exactly where a parameter change has to be visible - and the pass is skipped
     * only, the chain still tracks the sliders either way.
     */
    public boolean colorGradeSkipScreens = false;
    /** The applied preset, or {@code ColorGradeRenderer.CUSTOM} once a slider has moved. */
    public String colorGradePreset = ColorGradeRenderer.STANDARD;
    /**
     * The grading parameters, in the order the shader's {@code GradeConfig} block expects them (see
     * {@code ColorGradeRenderer}). All of them are neutral at exposure/contrast/saturation/gamma 1,
     * lift 0, temperature 0.5 and vignette 0 - so any change on its own is a small deviation from the
     * vanilla image. The defaults are the "standard" preset.
     */
    public float colorGradeExposure = 1.05f;
    public float colorGradeContrast = 1.12f;
    public float colorGradeSaturation = 1.10f;
    public float colorGradeGamma = 0.98f;
    public float colorGradeLift = 0.005f;
    public float colorGradeTemperature = 0.50f;
    public float colorGradeVignette = 0.30f;
    // ---- Motion blur (camera reprojection; its own chain because its values change every frame) ----
    public boolean motionBlurEnabled = false;
    /** How far the reprojected velocity may be stretched. 0 disables the effect without unloading it. */
    public float motionBlurStrength = 0.65f;
    /**
     * How much motion one frame's blur integrates, in milliseconds. This is the frame-rate independent
     * knob: the renderer divides it by the frame time, so the same number gives the same look at 30
     * and at 240 fps. The default is about one frame at 60 fps, i.e. the whole of each frame's motion.
     */
    public float motionBlurExposureMs = 16f;
    /** Cap on the blur length in screen pixels, so a fast turn does not turn the screen into soup. */
    public float motionBlurMaxRadius = 32f;
    /**
     * Paint the per-pixel blur velocity instead of the blur: red is horizontal, green vertical, blue
     * the magnitude, black means no motion was found. Turning the camera should fill the screen.
     */
    public boolean motionBlurDebug = false;
    /** Skip while a screen is open: the camera is still then anyway, and the pass costs nothing. */
    public boolean motionBlurSkipScreens = true;

    // ---- Bloom (glow around bright areas, its own chain) ----
    public boolean bloomEnabled = false;
    /**
     * How much of the blurred bright pass is blended back in. The composite is a screen blend, so this
     * can run higher than an additive one without blowing out what is already white.
     */
    public float bloomStrength = 1.0f;
    /** Luma above which a pixel counts as bright; the glow fades in over a soft knee. */
    public float bloomThreshold = 0.70f;
    /**
     * Radius of the glow, in screen pixels. This is what makes a halo a halo: at a handful of pixels
     * the effect just brightens the highlights instead of spreading around them.
     */
    public float bloomRadius = 28f;

    // ---- Mining break prediction (high-ping compensation) ----
    public boolean miningBreakPredictEnabled = false;
    /** Crosshair progress ring. */
    public boolean miningBreakRingEnabled = true;
    public float miningBreakRingRadius = 22.0f;
    public float miningBreakRingWidth = 2.5f;
    /** Ring colour at 0% and at 100% (the colour is interpolated with progress). */
    public int miningBreakRingColorStart = ColorUtil.argb(255, 255, 60, 60);
    public int miningBreakRingColorEnd = ColorUtil.argb(255, 80, 255, 80);
    /** Block outline growing out of the eight corners. */
    public boolean miningBreakOutlineEnabled = true;
    public int miningBreakOutlineColor = ColorUtil.argb(210, 255, 255, 255);
    /**
     * When the cue fires relative to the predicted break, in ticks (-6..3): 0 = exactly
     * at the break, positive = a safety margin after it, negative = earlier (a head
     * start for moving on, at the cost of finishing the block later than the server).
     * The physical break is {@code T0 + rtt/2 + duration}; the default 1 tick keeps the
     * cue from ever preceding it.
     */
    public int miningBreakBiasTicks = 1;
    /**
     * Fitted speed scale from confirmed breaks: the real duration is
     * {@code scale × ceil(strength × 30 / parsedSpeed) / 20} seconds. Only the scale is
     * fitted; the arrival delay is a physical quantity taken from the ping, because
     * fitting both at once is collinear for a player who mines one kind of block.
     */
    /**
     * How the tick count is rounded from {@code 30·S / speed}.
     *
     * <p>{@code ceil} is the default because it is what the server formula reads like; {@code floor} and
     * {@code round} exist so the difference can be measured instead of argued about - a one-tick
     * difference only shows up on breaks longer than the 4-tick floor.
     */
    public String miningTickRounding = "ceil";

    /**
     * Whether the fitted speed scale is applied at all.
     *
     * <p>Off leaves the model at {@code ceil(30·S / speed)} with the speed as read from the tab, which is
     * the only way to see whether the scale is correcting something real or just drifting.
     */
    public boolean miningAutoScale = true;

    /**
     * Fitted speed scale per block class ({@code BLOCK}, {@code ORE}, {@code GEMSTONE}, {@code DWARVEN}).
     *
     * <p>One global number mixed blocks whose server-side speed clearly differs: measured samples put
     * dwarven metals within a few percent while gemstones run about 18% faster than the reads suggest.
     * A single scale then wanders to whatever the last block type was.
     */
    public Map<String, Double> miningCategoryScale = new HashMap<>();

    /**
     * Whether the assertion starts from the next server tick boundary instead of the estimated arrival
     * of the start packet. Off makes it comparable against the old behaviour.
     */
    public boolean miningPhaseAlign = true;

    /**
     * Which moment the progress should reach 100% at: {@code zero} (as if the ping were 0, i.e.
     * {@code ticks×50} after the press), {@code server} (when the server performs the break) or
     * {@code client} (when the block visibly disappears here).
     *
     * <p>They differ by one one-way delay each, which at 200ms ping is two whole ticks - the same amount
     * the tick-offset slider used to be tuned by hand to cancel.
     */
    public String miningAlignTarget = "zero";

    /**
     * Server-side minimum break time in ticks.
     *
     * <p>4 is what the reference notes claim; the measured capped bucket points at 5. Configurable so the
     * two can be compared without a rebuild.
     */
    public int miningMinBreakTicks = 4;

    /** Bumped when the fitted timing model changes, so older values are discarded. */
    public int miningTimingVersion = 0;
    public String miningBreakSound = "minecraft:entity.experience_orb.pickup";
    public float miningBreakSoundVolume = 1.0f;
    public float miningBreakSoundPitch = 1.0f;
    /** Resolved Block Strength per disguised vanilla block (see MiningBlocks). */
    public Map<String, Integer> miningResolvedStrength = new HashMap<>();
    public boolean miningBreakDebugHud = false;

    // ---- Language ----
    /** "system" (follow the game), "zh_cn", or "en_us". */
    public String language = "system";

    /**
     * Shape version of this file: bumped whenever a field is added, renamed or retyped, with a matching
     * step in {@code ConfigManager.MIGRATIONS}. 0 means "written before versioning existed".
     */
    public int configVersion = 0;

    // ---- Click GUI ----
    public boolean clickGuiBlur = true;
    /** Blur strength in percent [0, 100]. */
    public int clickGuiBlurIntensity = 50;
    /**
     * Interface palette id: {@code "Dark"} or {@code "Light"} (see {@code GuiTheme}). Not a colour the
     * player edits - it selects which table the interface reads its colours from.
     */
    public String clickGuiTheme = "Dark";
    /** ClickGUI's own scale (real-pixel based, independent of the game GUI Scale). */
    public float clickGuiScale = 1.0f;
    /** One-time migration marker for the ClickGUI scale baseline change (see sanitize). */
    public int clickGuiScaleVersion = 0;
    /** Panel background opacity [0, 255]; 255 = fully opaque. */
    public int clickGuiBackgroundAlpha = 255;
    /** Panel width in px (draggable via the right edge). */
    public int clickGuiWidth = 300;
    /** Open/close scale-animation duration in ms (0 = no animation). */
    public int clickGuiAnimDuration = 200;
    /** Easing curve id for the open/close animation (see dev.skypocket.gui.Easing). */
    public String clickGuiEasing = "ease";
    /** Draw text shadows in the Click GUI (off by default: it doubles text geometry). */
    public boolean clickGuiTextShadow = false;
    /**
     * Log panel-cache statistics to the game log every five seconds: frames, bakes, reuse percentage
     * and the average time spent submitting the cached blit. Diagnostic only, off by default.
     */
    public boolean panelCacheStats = false;

    /**
     * Print the mod's information logs (INFO and DEBUG). Off by default; warnings and errors are
     * printed either way, so a bug report still contains the failures (see {@code SkyPocketLog}).
     */
    public boolean debugLog = false;

    /**
     * Rasterise the Click GUI panel into an offscreen texture and reuse it while nothing
     * changes (see {@code GuiPanelCache}). Turn this off in {@code config/skypocket.json}
     * if opening the panel ever misbehaves: the panel then draws live as before.
     */
    public boolean clickGuiPanelCache = true;
    /** When true, SkyBlock-category modules only run while on Hypixel SkyBlock. */
    public boolean skyBlockModulesOnly = true;

    // ---- Custom crosshair ----
    public boolean[] crosshairGrid = defaultCrosshair();
    public boolean crosshairStroke = false;
    public float crosshairStrokeWidth = 1.0f;
    public int crosshairRed = 255;
    public int crosshairGreen = 255;
    public int crosshairBlue = 255;
    public int crosshairAlpha = 255;
    public float crosshairPixelSize = 3.0f;
    public float crosshairScale = 1.0f;

    // ---- Crosshair per-target color ----
    public boolean crosshairEntityColorEnabled = false;
    public int crosshairFriendlyColor = ColorUtil.argb(255, 0, 255, 0);
    public int crosshairNeutralColor = ColorUtil.argb(255, 255, 255, 0);
    public int crosshairHostileColor = ColorUtil.argb(255, 255, 0, 0);
    public int crosshairContainerColor = ColorUtil.argb(255, 0, 255, 255);

    // ---- Hand-held item ----
    public float handItemScale = 1.0f;
    public boolean handItemDisableSwing = false;
    public boolean handItemDisableReequip = false;
    public float handItemOffsetX = 0.0f;
    public float handItemOffsetY = 0.0f;
    public float handItemOffsetZ = 0.0f;
    public float handItemRotateX = 0.0f;
    public float handItemRotateY = 0.0f;
    public float handItemRotateZ = 0.0f;
    /** Swing speed in percent: 0 = frozen, 100 = normal, 200 = double speed. */
    public int swingSpeed = 100;
    /** When true, ignore Haste/Mining Fatigue effect on swing speed. */
    public boolean swingIgnoreHaste = false;

    // ---- Block outline ----
    public int blockOutlineRed = 0;
    public int blockOutlineGreen = 0;
    public int blockOutlineBlue = 0;
    public int blockOutlineAlpha = 160;
    public float blockOutlineThickness = 1.0f;
    public boolean blockOutlineFaceTint = false;
    public int blockOutlineFaceRed = 0;
    public int blockOutlineFaceGreen = 160;
    public int blockOutlineFaceBlue = 255;
    public int blockOutlineFaceAlpha = 60;

    // ---- Clock ----
    public boolean clockEnabled = true;
    public int clockX = 10;
    public int clockY = 10;
    public float clockScale = 1.0f;
    public boolean clockFormat24h = true;

    // ---- Held item name (the vanilla tooltip that fades in above the hotbar) ----
    /**
     * The offsets are relative to vanilla's own position rather than absolute, so the name keeps
     * following the hotbar through window and GUI-scale changes - see {@code HeldItemNameHudElement}.
     * Defaults are all "untouched": the element then draws exactly like vanilla.
     */
    public boolean heldItemNameEnabled = true;
    public int heldItemNameOffsetX = 0;
    public int heldItemNameOffsetY = 0;
    public float heldItemNameScale = 1.0f;

    // ---- FPS / TPS / Ping HUD ----
    public boolean fpsTpsPingEnabled = false;
    public float fpsTpsPingScale = 1.0f;
    public int fpsTpsPingX = 4;
    public int fpsTpsPingY = 4;
    /** True = stacked vertically (one per line); false = single horizontal row. */
    public boolean fpsTpsPingVertical = true;
    public boolean fpsTpsPingShowFps = true;
    public boolean fpsTpsPingShowTps = true;
    public boolean fpsTpsPingShowPing = true;

    // ---- Day count ----
    public boolean dayCountEnabled = false;
    public int dayCountX = 10;
    public int dayCountY = 40;
    public float dayCountScale = 1.0f;

    // ---- SkyBlock stats display (health / mana / defence / speed / vitality) ----
    public boolean statsEnabled = false;
    /**
     * Hide the vanilla HUD pieces this module replaces, so the two cannot draw on top of each other.
     * The armour row is off by default: it is the one piece nothing here collides with.
     */
    public boolean statsHideVanillaHealth = true;
    public boolean statsHideVanillaFood = true;
    public boolean statsHideVanillaAir = true;
    public boolean statsHideVanillaArmor = false;
    /**
     * Hide the vanilla action bar text. It is only the <em>drawing</em> that is skipped - the numbers
     * are read from the message event, so hiding it does not stop the display from updating.
     */
    public boolean statsHideVanillaActionBar = true;
    /**
     * Modern (bars) instead of classic (the vanilla HUD's own icons, plus mana in the hunger row). The
     * module's setting renders this as one button that flips it.
     */
    public boolean statsModernStyle = false;
    /** Classic style: blink the mana container when mana is down to its last two slots. */
    public boolean statsManaBlink = true;
    /**
     * Classic style: the health and mana readouts beside the icon rows, as HUD elements of their own.
     * Twenty slots cannot show a single point of health or mana, and the absorption and overflow
     * totals do not show up in the icons at all - and they are elements rather than part of the icon
     * drawing so they can be placed and sized like every other row (see {@code StatTextHudElement}).
     */
    public boolean statsHealthTextEnabled = true;
    public int statsHealthTextX = 100;
    public int statsHealthTextY = 221;
    public float statsHealthTextScale = 1.0f;
    public boolean statsManaTextEnabled = true;
    public int statsManaTextX = 335;
    public int statsManaTextY = 221;
    public float statsManaTextScale = 1.0f;
    /** Modern style: the one geometry every bar shares, so a row of them looks like a set. */
    public int statsBarWidth = 160;
    /**
     * Bar height in GUI units. The artwork (bars/bar_frame.png, bar_fill.png) is drawn for 20 px: its
     * border is 3 px and its inner band 11 px, so anything much shorter squashes the design into a dark
     * line. 20 is the height it was authored at.
     */
    public int statsBarHeight = 20;
    /**
     * Per-bar size overrides, indexed by {@code StatsBarHudElement.Kind}; 0 means "follow the shared
     * statsBarWidth / statsBarHeight". This is what makes a bar freely resizable: the position editor
     * writes here when an edge is dragged, while the two shared sliders below stay the default for bars
     * that were never resized.
     */
    public int[] statsBarWidths = new int[6];
    public int[] statsBarHeights = new int[6];
    public boolean statsBarShowValues = true;
    /**
     * Modern style: one HUD element per bar, so each has its own position, size and switch - the same
     * arrangement the clock and the FPS readout use, and the reason they can be placed individually in
     * the position editor. The defaults put them in a column just right of centre, low on the screen.
     */
    public boolean statsHealthBarEnabled = true;
    public int statsHealthBarX = 171;
    public int statsHealthBarY = 231;
    public float statsHealthBarScale = 1.0f;
    public boolean statsManaBarEnabled = true;
    public int statsManaBarX = 171;
    public int statsManaBarY = 222;
    public float statsManaBarScale = 1.0f;
    public boolean statsDefenseBarEnabled = true;
    public int statsDefenseBarX = 171;
    public int statsDefenseBarY = 213;
    public float statsDefenseBarScale = 1.0f;
    public boolean statsSpeedBarEnabled = true;
    public int statsSpeedBarX = 171;
    public int statsSpeedBarY = 204;
    public float statsSpeedBarScale = 1.0f;
    public boolean statsVitalityBarEnabled = true;
    public int statsVitalityBarX = 171;
    public int statsVitalityBarY = 195;
    public float statsVitalityBarScale = 1.0f;
    public boolean statsAirBarEnabled = true;
    public int statsAirBarX = 171;
    public int statsAirBarY = 186;
    public float statsAirBarScale = 1.0f;
    /** Colours (ARGB); the defaults are the colours the action bar itself uses. */
    public int statsHealthColor = ColorDefaults.STATS_HEALTH;
    public int statsAbsorptionColor = ColorDefaults.STATS_ABSORPTION;
    public int statsManaColor = ColorDefaults.STATS_MANA;
    public int statsOverflowColor = ColorDefaults.STATS_OVERFLOW;
    public int statsAirColor = ColorDefaults.STATS_AIR;
    public int statsDefenseColor = ColorDefaults.STATS_DEFENSE;
    public int statsSpeedColor = ColorDefaults.STATS_SPEED;
    public int statsVitalityColor = ColorDefaults.STATS_VITALITY;
    /** Defence / speed / vitality as plain HUD text, positioned like the clock. */
    public boolean statsDefenseEnabled = true;
    public int statsDefenseX = 10;
    public int statsDefenseY = 70;
    public float statsDefenseScale = 1.0f;
    public boolean statsSpeedEnabled = true;
    public int statsSpeedX = 10;
    public int statsSpeedY = 90;
    public float statsSpeedScale = 1.0f;
    public boolean statsVitalityEnabled = true;
    public int statsVitalityX = 10;
    public int statsVitalityY = 110;
    public float statsVitalityScale = 1.0f;

    // ---- Entity hitbox ----
    public boolean hitboxEnabled = false;
    public int hitboxColor = ColorDefaults.HITBOX;
    /** Render distance in blocks; ignored when {@link #hitboxUnlimitedRange} is on. */
    public float hitboxRange = 16.0f;
    public boolean hitboxUnlimitedRange = false;
    public boolean hitboxShowPlayer = true;
    public boolean hitboxShowProjectile = true;
    public boolean hitboxShowHostile = true;
    public boolean hitboxShowNeutral = true;
    public boolean hitboxShowContainer = true;
    public boolean hitboxShowOther = true;
    public boolean hitboxShowLineOfSight = false;
    /** Wireframe line width in pixels, applied to hitbox outlines and the look ray. */
    public float hitboxLineWidth = 2.0f;
    /** Look-ray color (ARGB), independent of the hitbox outline color. */
    public int lookRayColor = ColorDefaults.LOOK_RAY;
    /** Look-ray length in blocks. */
    public float lookRayLength = 8.0f;

    /** A classic plus-shaped crosshair in the 15x15 grid. */
    public static boolean[] defaultCrosshair() {
        boolean[] grid = new boolean[GRID * GRID];
        int c = GRID / 2;
        for (int i = 0; i < GRID; i++) {
            grid[i * GRID + c] = true;
            grid[c * GRID + i] = true;
        }
        return grid;
    }

    public int getCrosshairColorARGB() {
        return ColorUtil.argb(crosshairAlpha, crosshairRed, crosshairGreen, crosshairBlue);
    }

    public int getBlockOutlineColorARGB() {
        return ColorUtil.argb(blockOutlineAlpha, blockOutlineRed, blockOutlineGreen, blockOutlineBlue);
    }

    public int getBlockOutlineFaceColorARGB() {
        return ColorUtil.argb(blockOutlineFaceAlpha, blockOutlineFaceRed, blockOutlineFaceGreen, blockOutlineFaceBlue);
    }

    /** Clamps all values into valid ranges and repairs missing/invalid data. */
    public void sanitize() {
        if (crosshairGrid == null || crosshairGrid.length != GRID * GRID) {
            crosshairGrid = defaultCrosshair();
        }
        crosshairRed = ColorUtil.clamp(crosshairRed, 0, 255);
        crosshairGreen = ColorUtil.clamp(crosshairGreen, 0, 255);
        crosshairBlue = ColorUtil.clamp(crosshairBlue, 0, 255);
        crosshairAlpha = ColorUtil.clamp(crosshairAlpha, 0, 255);
        crosshairPixelSize = ColorUtil.clamp(crosshairPixelSize, 1f, 8f);
        crosshairScale = ColorUtil.clamp(crosshairScale, 0.25f, 4f);
        crosshairStrokeWidth = ColorUtil.clamp(crosshairStrokeWidth, 0.5f, 4f);

        handItemScale = ColorUtil.clamp(handItemScale, 0.1f, 4.0f);
        handItemOffsetX = ColorUtil.clamp(handItemOffsetX, -1f, 1f);
        handItemOffsetY = ColorUtil.clamp(handItemOffsetY, -1f, 1f);
        handItemOffsetZ = ColorUtil.clamp(handItemOffsetZ, -1f, 1f);
        handItemRotateX = ColorUtil.clamp(handItemRotateX, -180f, 180f);
        handItemRotateY = ColorUtil.clamp(handItemRotateY, -180f, 180f);
        handItemRotateZ = ColorUtil.clamp(handItemRotateZ, -180f, 180f);
        swingSpeed = ColorUtil.clamp(swingSpeed, 0, 200);

        blockOutlineRed = ColorUtil.clamp(blockOutlineRed, 0, 255);
        blockOutlineGreen = ColorUtil.clamp(blockOutlineGreen, 0, 255);
        blockOutlineBlue = ColorUtil.clamp(blockOutlineBlue, 0, 255);
        blockOutlineAlpha = ColorUtil.clamp(blockOutlineAlpha, 0, 255);
        blockOutlineThickness = ColorUtil.clamp(blockOutlineThickness, 0.5f, 6.0f);
        blockOutlineFaceRed = ColorUtil.clamp(blockOutlineFaceRed, 0, 255);
        blockOutlineFaceGreen = ColorUtil.clamp(blockOutlineFaceGreen, 0, 255);
        blockOutlineFaceBlue = ColorUtil.clamp(blockOutlineFaceBlue, 0, 255);
        blockOutlineFaceAlpha = ColorUtil.clamp(blockOutlineFaceAlpha, 0, 255);

        clockX = ColorUtil.clamp(clockX, -100000, 100000);
        clockY = ColorUtil.clamp(clockY, -100000, 100000);
        clockScale = ColorUtil.clamp(clockScale, 0.25f, 6f);

        heldItemNameOffsetX = ColorUtil.clamp(heldItemNameOffsetX, -100000, 100000);
        heldItemNameOffsetY = ColorUtil.clamp(heldItemNameOffsetY, -100000, 100000);
        heldItemNameScale = ColorUtil.clamp(heldItemNameScale, 0.25f, 6f);

        fpsTpsPingX = ColorUtil.clamp(fpsTpsPingX, -100000, 100000);
        fpsTpsPingY = ColorUtil.clamp(fpsTpsPingY, -100000, 100000);
        fpsTpsPingScale = ColorUtil.clamp(fpsTpsPingScale, 0.25f, 6f);

        dayCountX = ColorUtil.clamp(dayCountX, -100000, 100000);
        dayCountY = ColorUtil.clamp(dayCountY, -100000, 100000);
        dayCountScale = ColorUtil.clamp(dayCountScale, 0.25f, 6f);

        hitboxRange = ColorUtil.clamp(hitboxRange, 1f, 128f);
        hitboxLineWidth = ColorUtil.clamp(hitboxLineWidth, 0.5f, 8f);
        lookRayLength = ColorUtil.clamp(lookRayLength, 1f, 64f);

        clickGuiBlurIntensity = ColorUtil.clamp(clickGuiBlurIntensity, 0, 100);
        // One-time migration: the render baseline was multiplied by 1.75 (so scale = 1
        // now matches the old scale = 1.75). Rescale existing values once so the
        // on-screen size is unchanged.
        if (clickGuiScaleVersion < 1) {
            clickGuiScale = clickGuiScale / 1.75f;
            clickGuiScaleVersion = 1;
        }
        clickGuiScale = ColorUtil.clamp(clickGuiScale, 0.5f, 1.5f);
        clickGuiBackgroundAlpha = ColorUtil.clamp(clickGuiBackgroundAlpha, 0, 255);
        clickGuiWidth = ColorUtil.clamp(clickGuiWidth, 300, 520);
        clickGuiAnimDuration = ColorUtil.clamp(clickGuiAnimDuration, 0, 2000);
        hideNearbyPlayersRange = ColorUtil.clamp(hideNearbyPlayersRange, 0f, 10f);
        scrollableTooltipsSensitivity = ColorUtil.clamp(scrollableTooltipsSensitivity, 0.25f, 4.0f);

        hotspotOverlayHeight = ColorUtil.clamp(hotspotOverlayHeight, 0.25f, 256f);
        hotspotOverlayLineWidth = ColorUtil.clamp(hotspotOverlayLineWidth, 0.5f, 4f);
        hotspotOverlayRange = ColorUtil.clamp(hotspotOverlayRange, 8f, 256f);

        mentionAlertNameLength = ColorUtil.clamp(mentionAlertNameLength, 1, 16);
        mentionAlertVolume = ColorUtil.clamp(mentionAlertVolume, 0f, 2f);
        mentionAlertPitch = ColorUtil.clamp(mentionAlertPitch, 0.5f, 2f);
        miningAbilityReminderVolume = ColorUtil.clamp(miningAbilityReminderVolume, 0f, 2f);
        miningAbilityReminderPitch = ColorUtil.clamp(miningAbilityReminderPitch, 0.5f, 2f);
        miningAbilityX = ColorUtil.clamp(miningAbilityX, -100000, 100000);
        miningAbilityY = ColorUtil.clamp(miningAbilityY, -100000, 100000);
        miningAbilityScale = ColorUtil.clamp(miningAbilityScale, 0.25f, 6f);
        miningAbilitySubtitleX = ColorUtil.clamp(miningAbilitySubtitleX, -100000, 100000);
        miningAbilitySubtitleY = ColorUtil.clamp(miningAbilitySubtitleY, -100000, 100000);
        miningAbilitySubtitleScale = ColorUtil.clamp(miningAbilitySubtitleScale, 0.25f, 6f);
        commissionsX = ColorUtil.clamp(commissionsX, -100000, 100000);
        commissionsY = ColorUtil.clamp(commissionsY, -100000, 100000);
        commissionsScale = ColorUtil.clamp(commissionsScale, 0.25f, 6f);
        statsBarWidth = ColorUtil.clamp(statsBarWidth, 40, 400);
        statsBarHeight = ColorUtil.clamp(statsBarHeight, 8, 20);
        // 0 keeps meaning "follow the shared size"; anything else is clamped like the sliders.
        statsBarWidths = perBarSizes(statsBarWidths, BAR_MIN_W, BAR_MAX_W);
        statsBarHeights = perBarSizes(statsBarHeights, BAR_MIN_H, BAR_MAX_H);
        statsHealthTextX = ColorUtil.clamp(statsHealthTextX, -100000, 100000);
        statsHealthTextY = ColorUtil.clamp(statsHealthTextY, -100000, 100000);
        statsHealthTextScale = ColorUtil.clamp(statsHealthTextScale, 0.25f, 6f);
        statsManaTextX = ColorUtil.clamp(statsManaTextX, -100000, 100000);
        statsManaTextY = ColorUtil.clamp(statsManaTextY, -100000, 100000);
        statsManaTextScale = ColorUtil.clamp(statsManaTextScale, 0.25f, 6f);
        statsHealthBarX = ColorUtil.clamp(statsHealthBarX, -100000, 100000);
        statsHealthBarY = ColorUtil.clamp(statsHealthBarY, -100000, 100000);
        statsHealthBarScale = ColorUtil.clamp(statsHealthBarScale, 0.25f, 6f);
        statsManaBarX = ColorUtil.clamp(statsManaBarX, -100000, 100000);
        statsManaBarY = ColorUtil.clamp(statsManaBarY, -100000, 100000);
        statsManaBarScale = ColorUtil.clamp(statsManaBarScale, 0.25f, 6f);
        statsDefenseBarX = ColorUtil.clamp(statsDefenseBarX, -100000, 100000);
        statsDefenseBarY = ColorUtil.clamp(statsDefenseBarY, -100000, 100000);
        statsDefenseBarScale = ColorUtil.clamp(statsDefenseBarScale, 0.25f, 6f);
        statsSpeedBarX = ColorUtil.clamp(statsSpeedBarX, -100000, 100000);
        statsSpeedBarY = ColorUtil.clamp(statsSpeedBarY, -100000, 100000);
        statsSpeedBarScale = ColorUtil.clamp(statsSpeedBarScale, 0.25f, 6f);
        statsVitalityBarX = ColorUtil.clamp(statsVitalityBarX, -100000, 100000);
        statsVitalityBarY = ColorUtil.clamp(statsVitalityBarY, -100000, 100000);
        statsVitalityBarScale = ColorUtil.clamp(statsVitalityBarScale, 0.25f, 6f);
        statsAirBarX = ColorUtil.clamp(statsAirBarX, -100000, 100000);
        statsAirBarY = ColorUtil.clamp(statsAirBarY, -100000, 100000);
        statsAirBarScale = ColorUtil.clamp(statsAirBarScale, 0.25f, 6f);
        statsDefenseX = ColorUtil.clamp(statsDefenseX, -100000, 100000);
        statsDefenseY = ColorUtil.clamp(statsDefenseY, -100000, 100000);
        statsDefenseScale = ColorUtil.clamp(statsDefenseScale, 0.25f, 6f);
        statsSpeedX = ColorUtil.clamp(statsSpeedX, -100000, 100000);
        statsSpeedY = ColorUtil.clamp(statsSpeedY, -100000, 100000);
        statsSpeedScale = ColorUtil.clamp(statsSpeedScale, 0.25f, 6f);
        statsVitalityX = ColorUtil.clamp(statsVitalityX, -100000, 100000);
        statsVitalityY = ColorUtil.clamp(statsVitalityY, -100000, 100000);
        statsVitalityScale = ColorUtil.clamp(statsVitalityScale, 0.25f, 6f);
        if (!AntialiasingRenderer.LIGHT.equals(antialiasingStrength)
                && !AntialiasingRenderer.STANDARD.equals(antialiasingStrength)
                && !AntialiasingRenderer.STRONG.equals(antialiasingStrength)) {
            antialiasingStrength = AntialiasingRenderer.STANDARD;
        }
        colorGradeExposure = ColorUtil.clamp(colorGradeExposure, 0.5f, 2f);
        colorGradeContrast = ColorUtil.clamp(colorGradeContrast, 0.5f, 2f);
        colorGradeSaturation = ColorUtil.clamp(colorGradeSaturation, 0f, 2f);
        colorGradeGamma = ColorUtil.clamp(colorGradeGamma, 0.5f, 2f);
        colorGradeLift = ColorUtil.clamp(colorGradeLift, -0.2f, 0.3f);
        colorGradeTemperature = ColorUtil.clamp(colorGradeTemperature, 0f, 1f);
        colorGradeVignette = ColorUtil.clamp(colorGradeVignette, 0f, 1f);
        antialiasingSharpen = ColorUtil.clamp(antialiasingSharpen, 0f, 1.5f);
        if (!ColorGradeRenderer.presets().contains(colorGradePreset)) {
            colorGradePreset = ColorGradeRenderer.CUSTOM;
        }
        motionBlurStrength = ColorUtil.clamp(motionBlurStrength, 0f, 1f);
        motionBlurExposureMs = ColorUtil.clamp(motionBlurExposureMs, 1f, 50f);
        motionBlurMaxRadius = ColorUtil.clamp(motionBlurMaxRadius, 4f, 64f);
        bloomStrength = ColorUtil.clamp(bloomStrength, 0f, 2f);
        bloomThreshold = ColorUtil.clamp(bloomThreshold, 0f, 1f);
        bloomRadius = ColorUtil.clamp(bloomRadius, 1f, 64f);
        miningBreakRingRadius = ColorUtil.clamp(miningBreakRingRadius, 6f, 120f);
        miningBreakRingWidth = ColorUtil.clamp(miningBreakRingWidth, 1f, 8f);
        miningBreakBiasTicks = ColorUtil.clamp(miningBreakBiasTicks, -6, 3);
        miningMinBreakTicks = ColorUtil.clamp(miningMinBreakTicks, 1, 20);
        if (miningCategoryScale == null) {
            miningCategoryScale = new HashMap<>();
        }
        for (Map.Entry<String, Double> entry : miningCategoryScale.entrySet()) {
            if (entry.getValue() == null || !Double.isFinite(entry.getValue())) {
                entry.setValue(1.0);
            } else {
                entry.setValue(Math.max(0.4, Math.min(2.5, entry.getValue())));
            }
        }
        if (miningAlignTarget == null || !(miningAlignTarget.equals("zero")
                || miningAlignTarget.equals("server") || miningAlignTarget.equals("client"))) {
            miningAlignTarget = "zero";
        }
        if (miningTickRounding == null || !(miningTickRounding.equals("ceil")
                || miningTickRounding.equals("floor") || miningTickRounding.equals("round"))) {
            miningTickRounding = "ceil";
        }
        if (miningTimingVersion < 4) {
            // v0 fitted the arrival delay and the scale together (collinear for a single
            // block type, produced nonsense); v1 subtracted only half the round trip when
            // learning, which inflated the scale and left every prediction a tick long.
            // Start clean; the scale is re-fitted from real breaks.
            // v2: the Mithril strengths were wrong, so the scale that had been fitted against them is
            // meaningless too - start clean and re-fit.
            miningTimingVersion = 4;
            // Per-category scales replaced the single number; start them empty so each class fits its own.
            if (miningCategoryScale != null) {
                miningCategoryScale.clear();
            }
        }
        miningBreakSoundVolume = ColorUtil.clamp(miningBreakSoundVolume, 0f, 2f);
        miningBreakSoundPitch = ColorUtil.clamp(miningBreakSoundPitch, 0.5f, 2f);
        if (miningBreakSound == null || miningBreakSound.isEmpty()) {
            miningBreakSound = "minecraft:entity.experience_orb.pickup";
        }
        if (miningResolvedStrength == null) {
            miningResolvedStrength = new HashMap<>();
        }

        // "ding" was the old synthesized built-in; migrate to the default vanilla sound.
        if (mentionAlertSound == null || mentionAlertSound.isEmpty() || mentionAlertSound.equals("ding")) {
            mentionAlertSound = "minecraft:entity.experience_orb.pickup";
        }
        if (miningAbilityReminderSound == null || miningAbilityReminderSound.isEmpty()) {
            miningAbilityReminderSound = "minecraft:entity.experience_orb.pickup";
        }
    }
}
