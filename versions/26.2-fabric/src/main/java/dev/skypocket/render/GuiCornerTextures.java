package dev.skypocket.render;

import com.mojang.blaze3d.platform.NativeImage;
import dev.skypocket.SkyPocketMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Pre-baked, anti-aliased rounded-corner masks for the Click GUI.
 *
 * <p>The panel used to anti-alias its corners by filling one row per device pixel with
 * the exact coverage as alpha. That is correct, but it costs up to ~200 GUI elements per
 * rounded rect (rows x 4 corners x 2 fills), and a screen full of cards pushed the corner
 * geometry past the text geometry. One mask per device-pixel radius replaces a whole
 * corner with a single 1:1 blit: a rounded rect becomes 4 blits of one texture (batched
 * together) plus 3 straight fills, ~20x fewer elements, and the arc is smooth in both
 * directions instead of being approximated row by row.
 *
 * <p>All four corners live in one texture as quadrants, so the four blits share a
 * texture and batch. The mask is white with the coverage in alpha, so one texture
 * serves every colour (the blit's tint provides it) — including the panel background
 * while its opacity slider is being dragged.
 */
public final class GuiCornerTextures {
    private static final dev.skypocket.SkyPocketLog LOGGER =
            dev.skypocket.SkyPocketLog.of("cornerMask");

    /** Sub-samples per axis when rasterizing the arc (coverage steps of 1/16). */
    private static final int SUB = 4;
    /** Masks kept at once; dragging the panel-scale slider walks through radii. */
    private static final int MAX_CACHED = 12;

    /** Access-ordered so the oldest entry is the least recently used one. */
    private static final Map<Integer, Mask> CACHE = new LinkedHashMap<>(16, 0.75f, true);
    /** Radii first seen during a draw; baked on the next client tick, never mid-frame. */
    private static final LinkedHashSet<Integer> PENDING = new LinkedHashSet<>();
    private static boolean disabled;

    private GuiCornerTextures() {
    }

    /**
     * The mask for a corner radius measured in <em>device pixels</em>, baked on first use.
     *
     * @return the mask, or null when the caller must fall back to row-by-row corners
     */
    public static Mask get(int radius) {
        if (disabled || radius < 1) {
            return null;
        }
        synchronized (CACHE) {
            Mask cached = CACHE.get(radius);
            if (cached != null && isLive(cached)) {
                return cached;
            }
            // Do not bake here: this method is called while GUI geometry is being recorded.
            // Creating/registering a DynamicTexture mid-draw corrupts texture state and can
            // invalidate the glyph atlas. The caller falls back to row-by-row corners for
            // this frame; the next tick bakes the mask once.
            PENDING.add(radius);
            return null;
        }
    }

    /** Bakes masks requested during drawing at a safe point: the client tick. */
    public static void tick() {
        if (disabled) {
            return;
        }
        List<Integer> radii;
        synchronized (CACHE) {
            if (PENDING.isEmpty()) {
                return;
            }
            radii = new ArrayList<>(PENDING);
            PENDING.clear();
        }
        for (int radius : radii) {
            synchronized (CACHE) {
                Mask cached = CACHE.get(radius);
                if (cached != null && isLive(cached)) {
                    continue;
                }
                try {
                    CACHE.put(radius, bake(radius));
                    evictStale();
                } catch (Throwable t) {
                    // Never break the GUI over an optimisation: callers draw rows instead.
                    disabled = true;
                    LOGGER.warn("corner mask unavailable, drawing corners row by row", t);
                    return;
                }
            }
        }
    }

    /** True while our texture is still the one registered under the mask's id. */
    private static boolean isLive(Mask mask) {
        try {
            return Minecraft.getInstance().getTextureManager().getTexture(mask.id()) == mask.texture();
        } catch (Throwable t) {
            // Dropped by a resource reload: treat as stale so it gets re-baked.
            return false;
        }
    }

    private static Mask bake(int radius) {
        int quadrant = radius + 1;
        int size = quadrant * 2;
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, size, size, false);
        for (int j = 0; j < quadrant; j++) {
            for (int i = 0; i < quadrant; i++) {
                int alpha = coverage(radius, i, j);
                int mirrorX = size - 1 - i;
                int mirrorY = size - 1 - j;
                put(image, i, j, alpha);                                  // top-left
                put(image, mirrorX, j, alpha);                            // top-right
                put(image, i, mirrorY, alpha);                            // bottom-left
                put(image, mirrorX, mirrorY, alpha);                      // bottom-right
            }
        }
        Identifier id = Identifier.fromNamespaceAndPath(SkyPocketMod.MOD_ID, "gui_corner_" + radius);
        DynamicTexture texture = new DynamicTexture(() -> "SkyPocket GUI corner " + radius, image);
        TextureManager textures = Minecraft.getInstance().getTextureManager();
        try {
            // Re-baking an id that a resource reload dropped: clear the stale instance so
            // nothing keeps a closed texture view around.
            textures.release(id);
        } catch (Throwable ignored) {
            // Nothing registered under that id yet, which is the normal first bake.
        }
        textures.register(id, texture);
        return new Mask(id, quadrant, size, texture);
    }

    /**
     * Coverage of the pixel {@code (i, j)} inside a radius-{@code r} rounded rect's
     * top-left corner: the unit square minus the part outside the corner circle, so the
     * mask matches the shape the row-by-row path builds (circle centred at (r, r)).
     * The single pixel column/row at {@code r} is the straight edge just past the arc and
     * is fully opaque, which is where the straight fills butt against the mask.
     */
    private static int coverage(int r, int i, int j) {
        int hits = 0;
        for (int sy = 0; sy < SUB; sy++) {
            double y = j + (sy + 0.5) / SUB;
            for (int sx = 0; sx < SUB; sx++) {
                double x = i + (sx + 0.5) / SUB;
                if (x >= r || y >= r || (x - r) * (x - r) + (y - r) * (y - r) <= (double) r * r) {
                    hits++;
                }
            }
        }
        return Math.round(255.0f * hits / (SUB * SUB));
    }

    /** White RGB (byte order inside RGB is irrelevant) with the coverage as alpha. */
    private static void put(NativeImage image, int x, int y, int alpha) {
        image.setPixelABGR(x, y, (alpha << 24) | 0x00FFFFFF);
    }

    /** Keeps the cache small: radii that scrolled out of use are released at the end. */
    private static void evictStale() {
        Iterator<Map.Entry<Integer, Mask>> it = CACHE.entrySet().iterator();
        while (CACHE.size() > MAX_CACHED && it.hasNext()) {
            Map.Entry<Integer, Mask> entry = it.next();
            it.remove();
            try {
                Minecraft.getInstance().getTextureManager().release(entry.getValue().id());
            } catch (Throwable t) {
                // Best effort; the texture is dropped with the manager otherwise.
                LOGGER.debug("corner mask release failed", t);
            }
        }
    }

    /**
     * A baked corner mask.
     *
     * @param id          the registered texture
     * @param quadrant    side of one corner quadrant in texture pixels (radius + 1)
     * @param textureSize full texture side (two quadrants)
     * @param texture     the texture instance, used to detect a resource-reload drop
     */
    public record Mask(Identifier id, int quadrant, int textureSize, DynamicTexture texture) {
    }
}
