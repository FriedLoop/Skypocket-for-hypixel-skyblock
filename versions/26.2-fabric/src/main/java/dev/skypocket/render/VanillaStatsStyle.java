package dev.skypocket.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.skypocket.config.ModConfig;
import dev.skypocket.skyblock.PlayerStatsTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

/**
 * The classic look: SkyBlock's numbers drawn with the vanilla HUD's own vocabulary.
 *
 * <p>Layout is the vanilla one, byte for byte: the rows are centred on the hotbar, the left row starts
 * at {@code guiWidth/2 - 91} and the right one ends at {@code guiWidth/2 + 91}, the bottom row sits at
 * {@code guiHeight - 39}, icons are 9&times;9 and slots are 8 apart - so neighbours overlap by a pixel
 * exactly like hearts and drumsticks do. Vanilla's constants for those are {@code NUM_HEARTS_PER_ROW},
 * {@code HEART_SIZE} and {@code HEART_SEPARATION}.
 *
 * <p>Health keeps the vanilla sprites and gains the behaviour SkyBlock wants: <b>ten icons per row,
 * two rows</b>, one icon per {@code maxHealth / 20}, with absorption hearts drawn <em>over</em> the red
 * ones (the vanilla {@code absorbing_*} sprites are transparent around the yellow heart, which is what
 * makes the overlap work). Mana is the same arrangement on the right, where the hunger bar was, using
 * the module's own art, with overflow mana overlaid the same way. The air row moves one row up, into
 * the space vanilla leaves above the food row - its left half is where the armour bar sits, so the two
 * do not collide.
 */
public final class VanillaStatsStyle {
    /** One slot is 9&times;9 and the next one starts 8 further along (vanilla's {@code HEART_*} trio). */
    private static final int SLOT = 9;
    private static final int STEP = 8;
    private static final int PER_ROW = 10;
    private static final int ROWS = 2;
    private static final int SIDE = 91;
    private static final int BASE_OFFSET = 39;
    private static final int ROW_STEP = 10;
    /** Air bubbles are 30 units each, ten of them (vanilla's {@code NUM_AIR_BUBBLES}). */
    private static final int AIR_PER_BUBBLE = 30;
    private static final int AIR_BUBBLES = 10;

    private static final Identifier HEART_CONTAINER =
            Identifier.withDefaultNamespace("hud/heart/container");
    private static final Identifier HEART_CONTAINER_BLINKING =
            Identifier.withDefaultNamespace("hud/heart/container_blinking");
    private static final Identifier HEART_FULL = Identifier.withDefaultNamespace("hud/heart/full");
    private static final Identifier HEART_HALF = Identifier.withDefaultNamespace("hud/heart/half");
    private static final Identifier HEART_ABSORBING_FULL =
            Identifier.withDefaultNamespace("hud/heart/absorbing_full");
    private static final Identifier HEART_ABSORBING_HALF =
            Identifier.withDefaultNamespace("hud/heart/absorbing_half");
    private static final Identifier AIR = Identifier.withDefaultNamespace("hud/air");
    private static final Identifier AIR_EMPTY = Identifier.withDefaultNamespace("hud/air_empty");
    private static final Identifier AIR_BURSTING = Identifier.withDefaultNamespace("hud/air_bursting");

    /** The module's own art; see docs/DEVELOPMENT.md for the size rules these follow. */
    private static final Identifier MANA_CONTAINER = sprite("mana/mana_container");
    private static final Identifier MANA_CONTAINER_BLINK = sprite("mana/mana_container_blink");
    private static final Identifier MANA_FULL = sprite("mana/mana_full");
    private static final Identifier MANA_HALF = sprite("mana/mana_half");
    private static final Identifier MANA_OVERFLOW_FULL = sprite("mana/mana_overflow_full");
    private static final Identifier MANA_OVERFLOW_HALF = sprite("mana/mana_overflow_half");

    private static final RenderPipeline PIPELINE = RenderPipelines.GUI_TEXTURED;

    /** Draws the classic display; called from the HUD mixin at the end of the vanilla health block. */
    public static void draw(GuiGraphicsExtractor graphics, ModConfig cfg) {
        if (!PlayerStatsTracker.hasData()) {
            return; // nothing to show until the server has sent an action bar line
        }
        int centre = graphics.guiWidth() / 2;
        int baseY = graphics.guiHeight() - BASE_OFFSET;
        drawHealth(graphics, centre - SIDE, baseY);
        drawMana(graphics, cfg, centre + SIDE, baseY);
        drawAir(graphics, centre + SIDE, baseY - ROW_STEP * ROWS);
    }

    /** Health and absorption, left of centre, filling from the left like the vanilla hearts. */
    private static void drawHealth(GuiGraphicsExtractor graphics, int leftX, int baseY) {
        float perSlot = PlayerStatsTracker.maxHealth() / (float) (PER_ROW * ROWS);
        if (perSlot <= 0f) {
            return;
        }
        // The captured health includes absorption, so the red icons only cover what the maximum can
        // hold; everything above it is what the overlay draws.
        int health = Math.min(PlayerStatsTracker.health(), PlayerStatsTracker.maxHealth());
        int absorption = PlayerStatsTracker.absorption();
        for (int slot = 0; slot < PER_ROW * ROWS; slot++) {
            int x = leftX + slot % PER_ROW * STEP;
            int y = baseY - slot / PER_ROW * ROW_STEP;
            blit(graphics, HEART_CONTAINER, x, y);
            blit(graphics, filled(health, slot, perSlot), x, y);
            blit(graphics, overlay(absorption, slot, perSlot, HEART_ABSORBING_FULL,
                    HEART_ABSORBING_HALF), x, y);
        }
    }

    /** Mana and its overflow, right of centre, filling from the right like the hunger bar. */
    private static void drawMana(GuiGraphicsExtractor graphics, ModConfig cfg, int rightX, int baseY) {
        float perSlot = PlayerStatsTracker.maxMana() / (float) (PER_ROW * ROWS);
        if (perSlot <= 0f) {
            return;
        }
        int mana = PlayerStatsTracker.mana();
        int overflow = PlayerStatsTracker.overflowMana();
        boolean blink = cfg.statsDisplayBlock.statsManaBlink && mana <= perSlot * 2f && blinks();
        Identifier container = blink ? MANA_CONTAINER_BLINK : MANA_CONTAINER;
        for (int slot = 0; slot < PER_ROW * ROWS; slot++) {
            int x = rightX - slot % PER_ROW * STEP - SLOT;
            int y = baseY - slot / PER_ROW * ROW_STEP;
            blit(graphics, container, x, y);
            blit(graphics, filledMana(mana, slot, perSlot), x, y);
            blit(graphics, overlay(overflow, slot, perSlot, MANA_OVERFLOW_FULL, MANA_OVERFLOW_HALF),
                    x, y);
        }
    }

    /**
     * The air row, one row above mana - where vanilla leaves room above the food row. Drawn here
     * rather than left to vanilla because the vanilla row would sit exactly where mana's second row
     * is.
     */
    private static void drawAir(GuiGraphicsExtractor graphics, int rightX, int y) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || player.getAirSupply() >= player.getMaxAirSupply()) {
            return;
        }
        int air = player.getAirSupply();
        int full = air / AIR_PER_BUBBLE;
        boolean bursting = air % AIR_PER_BUBBLE != 0;
        for (int slot = 0; slot < AIR_BUBBLES; slot++) {
            int x = rightX - slot * STEP - SLOT;
            Identifier sprite = slot < full ? AIR
                    : (slot == full && bursting ? AIR_BURSTING : AIR_EMPTY);
            blit(graphics, sprite, x, y);
        }
    }

    /** Full when the value reaches the slot's top, half from its middle, otherwise empty. */
    private static Identifier filled(int value, int slot, float perSlot) {
        if (reaches(value, slot + 1f, perSlot)) {
            return HEART_FULL;
        }
        if (reaches(value, slot + 0.5f, perSlot)) {
            return HEART_HALF;
        }
        return null;
    }

    private static Identifier filledMana(int value, int slot, float perSlot) {
        if (reaches(value, slot + 1f, perSlot)) {
            return MANA_FULL;
        }
        if (reaches(value, slot + 0.5f, perSlot)) {
            return MANA_HALF;
        }
        return null;
    }

    private static Identifier overlay(int value, int slot, float perSlot, Identifier full,
                                      Identifier half) {
        if (reaches(value, slot + 1f, perSlot)) {
            return full;
        }
        if (reaches(value, slot + 0.5f, perSlot)) {
            return half;
        }
        return null;
    }

    /**
     * Whether {@code value} reaches {@code slot} slots' worth, with a thousandth of a slot of slack.
     *
     * <p>Every step of this is float arithmetic - there is no integer division in the per-slot size -
     * but {@code (slot + 1) * (max / 20)} does not always land exactly on {@code max}: with a maximum
     * like 1127 the product rounds a hair above it, and the value that <em>is</em> the maximum then
     * compares as short of the last slot's top. That is the "full mana does not quite fill" report:
     * one half slot short at full. A thousandth of a slot is a few points of mana, far below anything
     * a slot can express, so it cannot hide a genuine half slot.
     */
    private static boolean reaches(float value, float slot, float perSlot) {
        return value + perSlot * 0.001f >= slot * perSlot;
    }

    /** Vanilla's blink: five ticks on, five off (see {@code Hud}'s heart rendering). */
    private static boolean blinks() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getGameTime() % 10 < 5;
    }

    private static void blit(GuiGraphicsExtractor graphics, Identifier sprite, int x, int y) {
        if (sprite != null) {
            graphics.blitSprite(PIPELINE, sprite, x, y, SLOT, SLOT);
        }
    }

    private static Identifier sprite(String path) {
        return Identifier.fromNamespaceAndPath("skypocket", path);
    }
}
