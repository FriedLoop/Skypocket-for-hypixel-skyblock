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
    private static final int MAX_PIXEL = 8;
    /** Fixed bake resolution, so the texture never needs resizing (which crashes 26.2). */
    private static final int MAX_SIZE = ModConfig.GRID * MAX_PIXEL;

    private static DynamicTexture TEXTURE;
    private static Identifier TEXTURE_ID;
    private static int bakedPixel = -1;
    private static int bakedHash = -1;

    private CrosshairRenderer() {
    }

    public static void render(GuiGraphicsExtractor graphics, int centerX, int centerY, int pixel, float scale,
                              boolean[] grid, int color, boolean stroke, float strokeWidth) {
        int n = ModConfig.GRID;
        if (grid == null || grid.length != n * n) {
            return;
        }
        pixel = Math.max(1, Math.min(MAX_PIXEL, pixel));
        int border = Math.max(1, Math.round(strokeWidth));
        ensureTexture(pixel, grid, color, stroke, border);

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
                originX, originY, 0f, 0f, size, size, MAX_SIZE, MAX_SIZE);
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

        if (stroke) {
            int strokeAbgr = ColorUtil.abgr(ColorUtil.alpha(color), 0, 0, 0);
            for (int gy = 0; gy < n; gy++) {
                for (int gx = 0; gx < n; gx++) {
                    if (grid[gy * n + gx]) {
                        fillRect(image, gx * pixel - border, gy * pixel - border,
                                pixel + 2 * border, pixel + 2 * border, strokeAbgr);
                    }
                }
            }
        }
        for (int gy = 0; gy < n; gy++) {
            for (int gx = 0; gx < n; gx++) {
                if (grid[gy * n + gx]) {
                    fillRect(image, gx * pixel, gy * pixel, pixel, pixel, colorAbgr);
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
