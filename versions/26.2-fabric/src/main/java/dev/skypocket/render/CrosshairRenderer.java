package dev.skypocket.render;

import com.mojang.blaze3d.platform.NativeImage;
import dev.skypocket.SkyPocketMod;
import dev.skypocket.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.Arrays;
import java.util.Objects;

/**
 * Draws the 15x15 solid-color crosshair grid.
 *
 * <p>The crosshair is baked into a {@link NativeImage} texture at a fixed maximum
 * resolution ({@code 15 * 8}) so the texture never has to be resized, then blitted
 * with only the actually-used region and the pose-matrix scaling applied around the
 * centre. Scaling a single texture makes the GPU scale the whole image uniformly,
 * so the arms stay symmetric (unlike scaling discrete rectangles, whose fractional
 * edges rasterize differently on the X and Y axes).
 */
public final class CrosshairRenderer {
    /** Largest pixel size the config allows; matches {@code ModConfig.sanitize()} clamp [1, 8]. */
    /**
     * Largest cell edge, in design units, the bake can produce.
     *
     * <p>Kept equal to the config range ({@code ModConfig.sanitize} clamps
     * {@code crosshairPixelSize} to 1..3). It is the texture size basis, so raising it above what the
     * config allows would only waste atlas space; lowering it below would clip a legal setting.
     */
    private static final int MAX_PIXEL = 3;

    /**
     * Transparent margin baked around the grid, in texels.
     *
     * <p>The stroke of an edge cell extends up to {@code border} texels outward, and {@code border}
     * comes from {@code crosshairStrokeWidth}, whose config range is 0.5..4. Four is therefore the
     * largest overhang any legal setting can produce. Without this margin the outer stroke was drawn
     * at negative coordinates and silently dropped by {@code fillRect}'s bounds check.
     */
    private static final int MAX_PAD = 4;

    /** Full texture edge: the grid at the largest cell size, plus the margin on both sides. */
    private static final int MAX_SIZE = ModConfig.GRID * MAX_PIXEL + MAX_PAD * 2;

    private static DynamicTexture TEXTURE;
    private static Identifier TEXTURE_ID;
    private static int bakedPixel = -1;
    private static int bakedHash = -1;

    private CrosshairRenderer() {
    }

    /**
     * The on-screen size of a crosshair drawn with these settings, in design units.
     *
     * <p>Callers that have to fit the crosshair into a box - the settings-page preview - need this
     * before deciding whether to scale it down. It mirrors what {@link #render} does with the same
     * arguments: the grid edge times the cell size, then the scale.
     */
    public static int sizeFor(int pixel, float scale) {
        int clampedPixel = Math.max(1, Math.min(MAX_PIXEL, pixel));
        return Math.round(ModConfig.GRID * clampedPixel * scale);
    }

    public static void render(GuiGraphicsExtractor graphics, int centerX, int centerY, int pixel, float scale,
                              boolean[] grid, int color, boolean stroke, float strokeWidth) {
        int n = ModConfig.GRID;
        if (grid == null || grid.length != n * n) {
            return;
        }
        pixel = Math.max(1, Math.min(MAX_PIXEL, pixel));
        int border = Math.max(1, Math.round(strokeWidth));
        // The tint is per-target: keep the colour in the bake key so entity changes repaint the mask.
        ensureTexture(pixel, grid, color, stroke, border);

        // Draw only the grid's own region of the texture, offset by the transparent margin the bake
        // leaves for the stroke. The texture is always MAX_SIZE wide no matter what `pixel` is, so
        // the UV denominator is MAX_SIZE and not the sampled size - passing the smaller value made
        // the sampled rectangle land off-centre, which is what shifted the crosshair to the upper
        // left at pixel 1 and 2.
        int size = n * pixel;
        int originX = centerX - size / 2;
        int originY = centerY - size / 2;

        var pose = graphics.pose();
        pose.pushMatrix();
        if (Math.abs(scale - 1.0f) > 1e-4f) {
            pose.translate(centerX, centerY);
            pose.scale(scale, scale);
            pose.translate(-centerX, -centerY);
        }
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE_ID,
                originX, originY, MAX_PAD, MAX_PAD, size, size, MAX_SIZE, MAX_SIZE);
        pose.popMatrix();
    }

    private static void ensureTexture(int pixel, boolean[] grid, int color, boolean stroke, int border) {
        int hash = Objects.hash(pixel, color, stroke, border) * 31 + Arrays.hashCode(grid);
        Minecraft mc = Minecraft.getInstance();
        if (TEXTURE != null && mc.getTextureManager().getTexture(TEXTURE_ID) == TEXTURE
                && bakedPixel == pixel && bakedHash == hash) {
            return;
        }

        int n = ModConfig.GRID;
        // Bake at a fixed MAX_SIZE so the texture never has to be resized. Resizing a
        // live DynamicTexture (setPixels with a different-sized image + upload) is
        // unsafe in 26.2 and crashes the renderer. Unused texels stay transparent.
        NativeImage image = new NativeImage(MAX_SIZE, MAX_SIZE, false);
        for (int y = 0; y < MAX_SIZE; y++) {
            for (int x = 0; x < MAX_SIZE; x++) {
                image.setPixelABGR(x, y, 0);
            }
        }

        int colorAbgr = ColorUtil.abgr(ColorUtil.alpha(color), ColorUtil.red(color),
                ColorUtil.green(color), ColorUtil.blue(color));

        // Content starts at MAX_PAD so the stroke, which extends `border` texels outward, has room
        // instead of being clipped at the texture edge. Everything below is offset by this.
        if (stroke) {
            int strokeAbgr = ColorUtil.abgr(ColorUtil.alpha(color), 0, 0, 0);
            for (int gy = 0; gy < n; gy++) {
                for (int gx = 0; gx < n; gx++) {
                    if (grid[gy * n + gx]) {
                        fillRect(image, MAX_PAD + gx * pixel - border, MAX_PAD + gy * pixel - border,
                                pixel + 2 * border, pixel + 2 * border, strokeAbgr);
                    }
                }
            }
        }
        for (int gy = 0; gy < n; gy++) {
            for (int gx = 0; gx < n; gx++) {
                if (grid[gy * n + gx]) {
                    fillRect(image, MAX_PAD + gx * pixel, MAX_PAD + gy * pixel, pixel, pixel, colorAbgr);
                }
            }
        }

        if (TEXTURE == null || mc.getTextureManager().getTexture(TEXTURE_ID) == null) {
            TEXTURE = new DynamicTexture(() -> "skypocket/crosshair", image);
            TEXTURE_ID = Identifier.fromNamespaceAndPath(SkyPocketMod.MOD_ID, "crosshair_texture");
            mc.getTextureManager().register(TEXTURE_ID, TEXTURE);
        } else {
            TEXTURE.setPixels(image);
        }
        TEXTURE.upload();

        bakedPixel = pixel;
        bakedHash = hash;
    }

    private static void fillRect(NativeImage image, int x0, int y0, int w, int h, int abgr) {
        int maxX = image.getWidth();
        int maxY = image.getHeight();
        for (int y = y0; y < y0 + h; y++) {
            if (y < 0 || y >= maxY) {
                continue;
            }
            for (int x = x0; x < x0 + w; x++) {
                if (x < 0 || x >= maxX) {
                    continue;
                }
                image.setPixelABGR(x, y, abgr);
            }
        }
    }
}
