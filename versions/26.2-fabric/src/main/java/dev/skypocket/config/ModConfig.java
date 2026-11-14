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
    /**
     * Crosshair grid edge, in cells. Must stay even.
     *
     * <p>Every cell is {@code crosshairPixelSize} units wide, so the drawn crosshair is
     * {@code GRID * pixel} units across. With an odd edge and an odd pixel size that total is odd,
     * and an odd-width square cannot be centred on an integer pixel: half a pixel of error always
     * lands somewhere, and it showed up as the crosshair sitting up and to the left. Even edges keep
     * the total even for every integer pixel size, so the centre is exact.
     */
    public static final int GRID = 16;

    /** Edge of the grid before 16x16, used only by the config migration that reshapes old arrays. */
    public static final int LEGACY_GRID_15 = 15;

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

    // ---- Feature toggles ----

    // ---- Player glow ----

    // ---- Dropped item glow ----

    // ---- Dragon glow ----

    // ---- Hide nearby players ----
    /** Hide other players closer than this many blocks (0..10). */

    /** Hide real players standing at the Hub spawn point. */
    /** Radius around the Hub spawn point treated as the spawn point. */
    /** Hide real players standing near a SkyBlock NPC. */
    /** Radius around an NPC treated as "near the NPC". */
    /** Hide Jerry presents that are neither addressed to nor sent by the local player. */

    // ---- Hide arrows ----
    /**
     * The {@code hide_landed_arrows} feature's own state.
     *
     * <p>Replaced a flat {@code hideLandedArrows} boolean; the existing file is updated by
     * tools/migrate-config.ps1 rather than by a shipped migration step.
     */
    public dev.skypocket.feature.config.HideLandedArrowsConfig hideLandedArrowsBlock =
            new dev.skypocket.feature.config.HideLandedArrowsConfig();
    /**
     * The {@code hide_stuck_arrows} feature's own state.
     *
     * <p>Nested so the feature owns its fields rather than adding another entry to this class. Gson
     * writes it as a nested object.
     *
     * <p>This replaced a flat {@code hideStuckArrows} boolean. There is no migration step for it:
     * the mod has no released users yet, so an existing file is updated by a one-off script on the
     * developer's own instances instead of by code that would have to ship forever.
     */
    public dev.skypocket.feature.config.HideStuckArrowsConfig hideStuckArrowsBlock =
            new dev.skypocket.feature.config.HideStuckArrowsConfig();

    // ---- Fishing hotspot overlay ----
    /** Hotspot drawing style: bottomless cylinder, filled circle or hollow circle. */

    // ---- Legendary sea creature warning ----

    /** Overlay colour for hotspots whose kind could not be detected. */
    // Per-kind colours (defaults match the stat text colours).
    /** Cylinder wall height extending upward from the liquid surface. */
    /** Edge ring line width. */
    /** How far (blocks) the player can be from a hotspot and still see its overlay. */

    // ---- Third-person camera ----
    /** Let the third-person camera clip through blocks and keep the player visible. */

    // ---- Hurt camera ----
    /** Disable the camera shake when the player takes damage. */

    // ---- Chat mention alert ----

    /**
     * Collapses repeated chat messages into one line with a counter.
     */

    /**
     * Master switch of the damage splash module.
     *
     * <p>Separate from {@link #damageSplashCompactEnabled}: the module can be on with only the
     * formatting applied, which is how long the feature behaved before compaction existed.
     */

    /**
     * Merges splashes of the same type that belong to the same mob into one number.
     */

    /**
     * Horizontal radius used to decide which mob a splash belongs to. Measured: splashes sit 0.1-1.25
     * blocks away horizontally, and their vertical offset (about 2.3 blocks) is ignored.
     */

    /**
     * How long a run keeps accumulating, in milliseconds. Every new splash of the run restarts it.
     */

    /** Formats merged numbers as K/M/B/T instead of 1,234,567. */

    /** Appends the hit count to a merged number.
     */

    /** How long a number stays on screen after the last hit, in milliseconds. */

    /** Fade-out at the end of the lifetime, in milliseconds. */

    /** Horizontal gap between the hitbox and the number. */

    /** Vertical gap between two damage types of the same mob. */


    /**
     * How long a counter keeps accumulating, in seconds. Past it the message starts a new run.
     */
    /** Sound id: a vanilla "minecraft:..." key or an mp3 file name in skypocket/sound/. */
    /** Alert volume [0..2] (200% amplifies past the vanilla gain) and pitch [0.5..2]; vanilla sounds only. */
    /** Custom search text; empty falls back to the game ID (matched in full). */
    /** Number of leading characters of the game ID to match (1..16). */

    // ---- Mining ability reminder ----
    /** Subtitle colors: the ability name and the "available!" part. */
    /** Countdown HUD position/scale (draggable in the HUD position editor). */
    /** Reminder subtitle position/scale (draggable in the HUD position editor). */

    // ---- Commissions HUD (tab list, mining areas) ----
    /** Hide finished commissions; with all of them done the whole block disappears. */
    /** Move the unfinished ones to the top (tab order otherwise). */
    /** Header colour ("Commissions:"), then the pending -> done gradient of the task lines. */
    /** HUD position/scale (draggable in the HUD position editor). */

    // ---- Post-processing (the module that owns every full-screen pass; see ModuleDefinitions) ----
    /**
     * Master switch of the post-processing module. The four effects keep their own flags (they are the
     * module's group switches), so this only says "the whole stack is off" - and it is on by default so
     * that a config written before the merge keeps doing exactly what it did.
     */

    // ---- Anti-aliasing (FXAA post pass over the finished world image) ----
    /** One of {@code AntialiasingRenderer.LIGHT / STANDARD / STRONG}. */
    /**
     * Unsharp-mask amount, run as the chain's last pass - i.e. <em>after</em> FXAA (see the chain's
     * JSON). That order is the point: it puts contrast back into the thin detail the anti-aliasing
     * smoothed away, which is what small world text is. Off by default; the input is an LDR image with
     * no oversampling, so anything past about 1 just rings around high-contrast edges.
     */
    /** Keep the pass running while a shader pack is loaded (the pack's own AA must be off then). */
    /** Skip the pass while a screen is open: the world is behind the GUI/blur and invisible anyway. */

    // ---- Colour grading (post pass over the finished world image) ----
    /**
     * Skip the pass while a screen is open. Off by default: the Click GUI is translucent, so the
     * world behind it is exactly where a parameter change has to be visible - and the pass is skipped
     * only, the chain still tracks the sliders either way.
     */
    /** The applied preset, or {@code ColorGradeRenderer.CUSTOM} once a slider has moved. */
    /**
     * The grading parameters, in the order the shader's {@code GradeConfig} block expects them (see
     * {@code ColorGradeRenderer}). All of them are neutral at exposure/contrast/saturation/gamma 1,
     * lift 0, temperature 0.5 and vignette 0 - so any change on its own is a small deviation from the
     * vanilla image. The defaults are the "standard" preset.
     */
    // ---- Motion blur (camera reprojection; its own chain because its values change every frame) ----
    /** How far the reprojected velocity may be stretched. 0 disables the effect without unloading it. */
    /**
     * How much motion one frame's blur integrates, in milliseconds. This is the frame-rate independent
     * knob: the renderer divides it by the frame time, so the same number gives the same look at 30
     * and at 240 fps. The default is about one frame at 60 fps, i.e. the whole of each frame's motion.
     */
    /** Cap on the blur length in screen pixels, so a fast turn does not turn the screen into soup. */
    /**
     * Paint the per-pixel blur velocity instead of the blur: red is horizontal, green vertical, blue
     * the magnitude, black means no motion was found. Turning the camera should fill the screen.
     */
    /** Skip while a screen is open: the camera is still then anyway, and the pass costs nothing. */

    // ---- Bloom (glow around bright areas, its own chain) ----
    /**
     * How much of the blurred bright pass is blended back in. The composite is a screen blend, so this
     * can run higher than an additive one without blowing out what is already white.
     */
    /** Luma above which a pixel counts as bright; the glow fades in over a soft knee. */
    /**
     * Radius of the glow, in screen pixels. This is what makes a halo a halo: at a handful of pixels
     * the effect just brightens the highlights instead of spreading around them.
     */

    // ---- Mining break prediction (high-ping compensation) ----
    /** Block outline growing out of the eight corners. */
    /**
     * When the cue fires relative to the predicted break, in ticks (-6..3): 0 = exactly
     * at the break, positive = a safety margin after it, negative = earlier (a head
     * start for moving on, at the cost of finishing the block later than the server).
     * The physical break is {@code T0 + rtt/2 + duration}; the default 1 tick keeps the
     * cue from ever preceding it.
     */
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

    /**
     * Whether the fitted speed scale is applied at all.
     *
     * <p>Off leaves the model at {@code ceil(30·S / speed)} with the speed as read from the tab, which is
     * the only way to see whether the scale is correcting something real or just drifting.
     */

    /**
     * Fitted speed scale per block class ({@code BLOCK}, {@code ORE}, {@code GEMSTONE}, {@code DWARVEN}).
     *
     * <p>One global number mixed blocks whose server-side speed clearly differs: measured samples put
     * dwarven metals within a few percent while gemstones run about 18% faster than the reads suggest.
     * A single scale then wanders to whatever the last block type was.
     */

    /**
     * Whether the assertion starts from the next server tick boundary instead of the estimated arrival
     * of the start packet. Off makes it comparable against the old behaviour.
     */

    /**
     * Which moment the progress should reach 100% at: {@code zero} (as if the ping were 0, i.e.
     * {@code ticks×50} after the press), {@code server} (when the server performs the break) or
     * {@code client} (when the block visibly disappears here).
     *
     * <p>They differ by one one-way delay each, which at 200ms ping is two whole ticks - the same amount
     * the tick-offset slider used to be tuned by hand to cancel.
     */

    /**
     * Server-side minimum break time in ticks.
     *
     * <p>4 is what the reference notes claim; the measured capped bucket points at 5. Configurable so the
     * two can be compared without a rebuild.
     */

    /** Bumped when the fitted timing model changes, so older values are discarded. */
    public int miningTimingVersion = 0;
    /** Resolved Block Strength per disguised vanilla block (see MiningBlocks). */

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
    public static final float CLICK_GUI_SCALE_MIN = 0.5f;
    public static final float CLICK_GUI_SCALE_MAX = 1.5f;
    public static final float CLICK_GUI_SCALE_STEP = 0.1f;
    public float clickGuiScale = 1.0f;
    /** Panel background opacity [0, 255]; 255 = fully opaque. */
    public int clickGuiBackgroundAlpha = 255;
    /** Open/close scale-animation duration in ms (0 = no animation). */
    public int clickGuiAnimDuration = 200;
    /** Easing curve id for the open/close animation (see dev.skypocket.gui.Easing). */
    public String clickGuiEasing = "ease";
    /** Draw text shadows in the Click GUI (off by default: it doubles text geometry). */
    public boolean clickGuiTextShadow = false;
    /** Draw HUD text with the SkyPocket atlas font instead of the vanilla font. */
    public boolean hudSkyPocketFont = false;

    // ---- Drill fuel ----

    // ---- Item pickup log ----

    // ---- Thunder bottle ----

    // ---- Skill display ----
    /** Show the server percentage instead of current/total XP. */
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
    /** When true, modules marked skyBlockOnly only run while on Hypixel SkyBlock. */
    public boolean skyBlockModulesOnly = true;

    // ---- Custom crosshair ----

    // ---- Crosshair per-target color ----

    // ---- Hand-held item ----
    /** Swing speed in percent: 0 = frozen, 100 = normal, 200 = double speed. */
    /** When true, ignore Haste/Mining Fatigue effect on swing speed. */

    // ---- Block outline ----

    // ---- Clock ----

    // ---- Held item name (the vanilla tooltip that fades in above the hotbar) ----
    /**
     * The offsets are relative to vanilla's own position rather than absolute, so the name keeps
     * following the hotbar through window and GUI-scale changes - see {@code HeldItemNameHudElement}.
     * Defaults are all "untouched": the element then draws exactly like vanilla.
     */

    // ---- FPS / TPS / Ping HUD ----
    /** True = stacked vertically (one per line); false = single horizontal row. */

    // ---- Day count ----

    // ---- SkyBlock stats display (health / mana / defence / speed / vitality) ----
    /**
     * Hide the vanilla HUD pieces this module replaces, so the two cannot draw on top of each other.
     * The armour row is off by default: it is the one piece nothing here collides with.
     */
    /**
     * Hide the vanilla action bar text. It is only the <em>drawing</em> that is skipped - the numbers
     * are read from the message event, so hiding it does not stop the display from updating.
     */
    /**
     * Modern (bars) instead of classic (the vanilla HUD's own icons, plus mana in the hunger row). The
     * module's setting renders this as one button that flips it.
     */
    /** Classic style: blink the mana container when mana is down to its last two slots. */
    /**
     * Classic style: the health and mana readouts beside the icon rows, as HUD elements of their own.
     * Twenty slots cannot show a single point of health or mana, and the absorption and overflow
     * totals do not show up in the icons at all - and they are elements rather than part of the icon
     * drawing so they can be placed and sized like every other row (see {@code StatTextHudElement}).
     */
    /** Modern style: the one geometry every bar shares, so a row of them looks like a set. */
    /**
     * Bar height in GUI units. The artwork (bars/bar_frame.png, bar_fill.png) is drawn for 20 px: its
     * border is 3 px and its inner band 11 px, so anything much shorter squashes the design into a dark
     * line. 20 is the height it was authored at.
     */
    /**
     * Per-bar size overrides, indexed by {@code StatsBarHudElement.Kind}; 0 means "follow the shared
     * statsBarWidth / statsBarHeight". This is what makes a bar freely resizable: the position editor
     * writes here when an edge is dragged, while the two shared sliders below stay the default for bars
     * that were never resized.
     */
    /**
     * Modern style: one HUD element per bar, so each has its own position, size and switch - the same
     * arrangement the clock and the FPS readout use, and the reason they can be placed individually in
     * the position editor. The defaults put them in a column just right of centre, low on the screen.
     */
    /** Colours (ARGB); the defaults are the colours the action bar itself uses. */
    /** Defence / speed / vitality as plain HUD text, positioned like the clock. */

    // ---- Entity hitbox ----
    /** Render distance in blocks; ignored when {@link #hitboxUnlimitedRange} is on. */
    /** Wireframe line width in pixels, applied to hitbox outlines and the look ray. */
    /** Look-ray color (ARGB), independent of the hitbox outline color. */
    /** Look-ray length in blocks. */

    /**
     * A classic plus-shaped crosshair filling the grid.
     *
     * <p>An even grid has no single centre cell, so the cross is laid out around the boundary between
     * the two middle rows: the arms are {@code (GRID - 1) / 2} cells long from the edge, which puts
     * the intersection between cells {@code GRID/2 - 1} and {@code GRID/2}. Drawing one row instead
     * would bias the cross half a cell off centre - the same off-by-one this grid size exists to fix.
     */
    public static boolean[] defaultCrosshair() {
        boolean[] grid = new boolean[GRID * GRID];
        int lo = GRID / 2 - 1;
        int hi = GRID / 2;
        for (int i = 0; i < GRID; i++) {
            // Horizontal arm through the two middle rows, vertical arm through the two middle columns.
            grid[lo * GRID + i] = true;
            grid[hi * GRID + i] = true;
            grid[i * GRID + lo] = true;
            grid[i * GRID + hi] = true;
        }
        return grid;
    }

    public int getCrosshairColorARGB() {
        var b = crosshairBlock;
        return ColorUtil.argb(b.crosshairAlpha, b.crosshairRed, b.crosshairGreen, b.crosshairBlue);
    }

    public int getBlockOutlineColorARGB() {
        var b = blockOutlineBlock;
        return ColorUtil.argb(b.blockOutlineAlpha, b.blockOutlineRed, b.blockOutlineGreen, b.blockOutlineBlue);
    }

    public int getBlockOutlineFaceColorARGB() {
        var b = blockOutlineBlock;
        return ColorUtil.argb(b.blockOutlineFaceAlpha, b.blockOutlineFaceRed, b.blockOutlineFaceGreen, b.blockOutlineFaceBlue);
    }

    /** Clamps all values into valid ranges and repairs missing/invalid data. */
    public void sanitize() {
        clickGuiBlurIntensity = ColorUtil.clamp(clickGuiBlurIntensity, 0, 100);
        clickGuiScale = ColorUtil.clamp(clickGuiScale, CLICK_GUI_SCALE_MIN, CLICK_GUI_SCALE_MAX);
        clickGuiBackgroundAlpha = ColorUtil.clamp(clickGuiBackgroundAlpha, 0, 255);
        clickGuiAnimDuration = ColorUtil.clamp(clickGuiAnimDuration, 0, 2000);

        // Each feature's block clamps and repairs its own fields. This method stays the single entry
        // point so callers do not have to know which blocks exist, and a new block only has to be
        // added here.
        blockOutlineBlock.sanitize();
        clockBlock.sanitize();
        commissionsBlock.sanitize();
        crosshairBlock.sanitize();
        dayCountBlock.sanitize();
        drillFuelBarBlock.sanitize();
        fishingHotspotOverlayBlock.sanitize();
        fpsTpsPingBlock.sanitize();
        handItemBlock.sanitize();
        heldItemNameBlock.sanitize();
        hidePlayersBlock.sanitize();
        hidePlayersNearNpcsBlock.sanitize();
        hideSpawnPointPlayersBlock.sanitize();
        hitboxBlock.sanitize();
        itemPickupLogBlock.sanitize();
        legendarySeaCreatureWarningBlock.sanitize();
        mentionAlertBlock.sanitize();
        miningAbilityReminderBlock.sanitize();
        miningBreakPredictBlock.sanitize();
        postProcessBlock.sanitize();
        scrollableTooltipsBlock.sanitize();
        skillDisplayBlock.sanitize();
        statsDisplayBlock.sanitize();
        thunderBottleBlock.sanitize();
    }

    // ---- Per-feature config blocks (Phase 3) ----
    //
    // Each feature owns its own state object; Gson writes them as nested objects under the
    // field name. FeatureModule.config() returns the instance so the framework can find it.
    public dev.skypocket.feature.config.AutoSprintConfig autoSprintBlock = new dev.skypocket.feature.config.AutoSprintConfig();
    public dev.skypocket.feature.config.NoHurtCameraConfig noHurtCameraBlock = new dev.skypocket.feature.config.NoHurtCameraConfig();

    // ---- Per-feature config blocks (Phase 3) ----
    //
    // Each feature owns its own state object; Gson writes them as nested objects under the
    // field name. FeatureModule.config() returns the instance so the framework can find it.
    public dev.skypocket.feature.config.BlockOutlineConfig blockOutlineBlock = new dev.skypocket.feature.config.BlockOutlineConfig();
    public dev.skypocket.feature.config.ChatCompactConfig chatCompactBlock = new dev.skypocket.feature.config.ChatCompactConfig();
    public dev.skypocket.feature.config.ClockConfig clockBlock = new dev.skypocket.feature.config.ClockConfig();
    public dev.skypocket.feature.config.CommissionsConfig commissionsBlock = new dev.skypocket.feature.config.CommissionsConfig();
    public dev.skypocket.feature.config.CrosshairConfig crosshairBlock = new dev.skypocket.feature.config.CrosshairConfig();
    public dev.skypocket.feature.config.DamageSplashCompactConfig damageSplashCompactBlock = new dev.skypocket.feature.config.DamageSplashCompactConfig();
    public dev.skypocket.feature.config.DayCountConfig dayCountBlock = new dev.skypocket.feature.config.DayCountConfig();
    public dev.skypocket.feature.config.DragonGlowConfig dragonGlowBlock = new dev.skypocket.feature.config.DragonGlowConfig();
    public dev.skypocket.feature.config.DrillFuelBarConfig drillFuelBarBlock = new dev.skypocket.feature.config.DrillFuelBarConfig();
    public dev.skypocket.feature.config.FishingHotspotOverlayConfig fishingHotspotOverlayBlock = new dev.skypocket.feature.config.FishingHotspotOverlayConfig();
    public dev.skypocket.feature.config.FpsTpsPingConfig fpsTpsPingBlock = new dev.skypocket.feature.config.FpsTpsPingConfig();
    public dev.skypocket.feature.config.HandItemConfig handItemBlock = new dev.skypocket.feature.config.HandItemConfig();
    public dev.skypocket.feature.config.HeldItemNameConfig heldItemNameBlock = new dev.skypocket.feature.config.HeldItemNameConfig();
    public dev.skypocket.feature.config.HideOtherPlayersPresentsConfig hideOtherPlayersPresentsBlock = new dev.skypocket.feature.config.HideOtherPlayersPresentsConfig();
    public dev.skypocket.feature.config.HidePlayersConfig hidePlayersBlock = new dev.skypocket.feature.config.HidePlayersConfig();
    public dev.skypocket.feature.config.HidePlayersNearNpcsConfig hidePlayersNearNpcsBlock = new dev.skypocket.feature.config.HidePlayersNearNpcsConfig();
    public dev.skypocket.feature.config.HideSpawnPointPlayersConfig hideSpawnPointPlayersBlock = new dev.skypocket.feature.config.HideSpawnPointPlayersConfig();
    public dev.skypocket.feature.config.HitboxConfig hitboxBlock = new dev.skypocket.feature.config.HitboxConfig();
    public dev.skypocket.feature.config.ItemGlowConfig itemGlowBlock = new dev.skypocket.feature.config.ItemGlowConfig();
    public dev.skypocket.feature.config.ItemPickupLogConfig itemPickupLogBlock = new dev.skypocket.feature.config.ItemPickupLogConfig();
    public dev.skypocket.feature.config.LegendarySeaCreatureWarningConfig legendarySeaCreatureWarningBlock = new dev.skypocket.feature.config.LegendarySeaCreatureWarningConfig();
    public dev.skypocket.feature.config.MentionAlertConfig mentionAlertBlock = new dev.skypocket.feature.config.MentionAlertConfig();
    public dev.skypocket.feature.config.MiningAbilityReminderConfig miningAbilityReminderBlock = new dev.skypocket.feature.config.MiningAbilityReminderConfig();
    public dev.skypocket.feature.config.MiningBreakPredictConfig miningBreakPredictBlock = new dev.skypocket.feature.config.MiningBreakPredictConfig();
    public dev.skypocket.feature.config.PlayerGlowConfig playerGlowBlock = new dev.skypocket.feature.config.PlayerGlowConfig();
    public dev.skypocket.feature.config.PostProcessConfig postProcessBlock = new dev.skypocket.feature.config.PostProcessConfig();
    public dev.skypocket.feature.config.ScrollableTooltipsConfig scrollableTooltipsBlock = new dev.skypocket.feature.config.ScrollableTooltipsConfig();
    public dev.skypocket.feature.config.SkillDisplayConfig skillDisplayBlock = new dev.skypocket.feature.config.SkillDisplayConfig();
    public dev.skypocket.feature.config.StatsDisplayConfig statsDisplayBlock = new dev.skypocket.feature.config.StatsDisplayConfig();
    public dev.skypocket.feature.config.ThirdPersonConfig thirdPersonBlock = new dev.skypocket.feature.config.ThirdPersonConfig();
    public dev.skypocket.feature.config.ThunderBottleConfig thunderBottleBlock = new dev.skypocket.feature.config.ThunderBottleConfig();
}


