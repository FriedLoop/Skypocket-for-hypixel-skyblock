package dev.skypocket.render;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.skypocket.SkyPocketMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * StatsBar's PNGs are small, authored for a 20 px bar, and frequently minified. A plain
 * NEAREST sampler makes them blocky, while bilinear alone produces a dark fringe where
 * transparent pixels interpolate against RGB zero. This texture:
 *
 * <ul>
 *   <li>bleeds the RGB of nearby opaque pixels into fully transparent pixels, so linear
 *       filtering cannot blend toward black;</li>
 *   <li>builds a real mip chain, so minification uses a stable prefiltered level instead of
 *       aliasing the 20 px source;</li>
 *   <li>uses clamped linear filtering for both magnification and minification.</li>
 * </ul>
 *
 * It is registered only for the StatsBar assets and leaves every other GUI/world texture alone.
 */
public final class StatsBarTexture extends SimpleTexture {
    private static final Identifier BAR_FRAME =
            Identifier.fromNamespaceAndPath(SkyPocketMod.MOD_ID, "textures/gui/sprites/bars/bar_frame.png");
    private static final Identifier BAR_FILL =
            Identifier.fromNamespaceAndPath(SkyPocketMod.MOD_ID, "textures/gui/sprites/bars/bar_fill.png");
    private static final Identifier[] ICONS = {
            Identifier.fromNamespaceAndPath(SkyPocketMod.MOD_ID, "textures/gui/sprites/icons/health.png"),
            Identifier.fromNamespaceAndPath(SkyPocketMod.MOD_ID, "textures/gui/sprites/icons/mana.png"),
            Identifier.fromNamespaceAndPath(SkyPocketMod.MOD_ID, "textures/gui/sprites/icons/defense.png"),
            Identifier.fromNamespaceAndPath(SkyPocketMod.MOD_ID, "textures/gui/sprites/icons/speed.png"),
            Identifier.fromNamespaceAndPath(SkyPocketMod.MOD_ID, "textures/gui/sprites/icons/vitality.png"),
            Identifier.fromNamespaceAndPath(SkyPocketMod.MOD_ID, "textures/gui/sprites/icons/air.png")
    };

    private final boolean mipmapped;

    private static boolean registered;

    private StatsBarTexture(Identifier id, boolean mipmapped) {
        super(id);
        this.mipmapped = mipmapped;
    }

    /**
     * Registers the specialised loader on the first HUD draw. Client initializers run before
     * the client's ResourceManager exists, so registering there crashes at startup.
     */
    public static void ensureRegistered() {
        if (registered) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ResourceManager resources = mc.getResourceManager();
        if (resources == null) {
            return;
        }
        TextureManager textures = mc.getTextureManager();
        // Nine-sliced bars must stay on one mip level: their middle slice is minified
        // while the caps can be magnified, and per-primitive LOD selection made the
        // middle look like a translucent layer. Icons keep the mip chain.
        for (Identifier id : new Identifier[]{BAR_FRAME, BAR_FILL}) {
            if (resources.getResource(id).isPresent()) {
                textures.registerAndLoad(id, new StatsBarTexture(id, false));
            }
        }
        for (Identifier id : ICONS) {
            if (resources.getResource(id).isPresent()) {
                textures.registerAndLoad(id, new StatsBarTexture(id, true));
            }
        }
        registered = true;
    }

    @Override
    protected void doLoad(NativeImage image) {
        bleedTransparentPixels(image);
        int levels = mipmapped ? mipLevels(image.getWidth(), image.getHeight()) : 1;
        var device = RenderSystem.getDevice();
        var nextTexture = device.createTexture(
                () -> "skypocket statsbar",
                GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST,
                GpuFormat.RGBA8_UNORM,
                image.getWidth(), image.getHeight(), 1, levels);
        var encoder = device.createCommandEncoder();
        encoder.writeToTexture(nextTexture, image);

        NativeImage source = image;
        for (int i = 1; i < levels; i++) {
            NativeImage mip = downsample(source);
            encoder.writeToTexture(nextTexture, mip, i, 0, 0, 0);
            // Keep the source alive until the next level has been sampled from it.
            if (source != image) {
                source.close();
            }
            source = mip;
        }
        if (source != image) {
            source.close();
        }

        var oldTexture = texture;
        var oldView = textureView;
        texture = nextTexture;
        textureView = device.createTextureView(nextTexture);
        sampler = RenderSystem.getSamplerCache()
                .getClampToEdge(FilterMode.LINEAR, mipmapped);
        if (oldTexture != null) {
            oldTexture.close();
        }
        if (oldView != null) {
            oldView.close();
        }
    }

    /**
     * Fills the RGB of fully transparent pixels from opaque neighbours. Alpha stays zero, so
     * the silhouette is unchanged; only the hidden colour used by interpolation changes.
     */
    private static void bleedTransparentPixels(NativeImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        for (int pass = 0; pass < 4; pass++) {
            int[] next = new int[width * height];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int argb = image.getPixel(x, y);
                    if ((argb >>> 24) == 0) {
                        int r = 0;
                        int g = 0;
                        int b = 0;
                        int count = 0;
                        for (int dy = -1; dy <= 1; dy++) {
                            for (int dx = -1; dx <= 1; dx++) {
                                if (dx == 0 && dy == 0) {
                                    continue;
                                }
                                int nx = x + dx;
                                int ny = y + dy;
                                if (nx < 0 || ny < 0 || nx >= width || ny >= height) {
                                    continue;
                                }
                                int sample = image.getPixel(nx, ny);
                                if ((sample >>> 24) > 0) {
                                    r += (sample >> 16) & 0xFF;
                                    g += (sample >> 8) & 0xFF;
                                    b += sample & 0xFF;
                                    count++;
                                }
                            }
                        }
                        if (count > 0) {
                            argb = ((r / count) << 16) | ((g / count) << 8) | (b / count);
                        }
                    }
                    next[y * width + x] = argb;
                }
            }
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    image.setPixel(x, y, next[y * width + x]);
                }
            }
        }
    }

    private static NativeImage downsample(NativeImage source) {
        int width = Math.max(1, source.getWidth() / 2);
        int height = Math.max(1, source.getHeight() / 2);
        NativeImage out = new NativeImage(source.format(), width, height, false);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int r = 0;
                int g = 0;
                int b = 0;
                int a = 0;
                int count = 0;
                for (int dy = 0; dy < 2; dy++) {
                    for (int dx = 0; dx < 2; dx++) {
                        int sx = Math.min(source.getWidth() - 1, x * 2 + dx);
                        int sy = Math.min(source.getHeight() - 1, y * 2 + dy);
                        int p = source.getPixel(sx, sy);
                        r += (p >> 16) & 0xFF;
                        g += (p >> 8) & 0xFF;
                        b += p & 0xFF;
                        a += (p >>> 24) & 0xFF;
                        count++;
                    }
                }
                out.setPixel(x, y, ((a / count) << 24) | ((r / count) << 16)
                        | ((g / count) << 8) | (b / count));
            }
        }
        return out;
    }

    private static int mipLevels(int width, int height) {
        // GpuTexture.getWidth/getHeight use a plain right shift, so a non-square texture
        // runs out of mip levels when its shortest side reaches 1. 728x20 must stop at
        // level 4 (45x1); level 5 would be 22x0 and cannot be written.
        int shortest = Math.max(1, Math.min(width, height));
        int levels = 1;
        while (shortest > 1) {
            shortest >>= 1;
            levels++;
        }
        return levels;
    }
}
