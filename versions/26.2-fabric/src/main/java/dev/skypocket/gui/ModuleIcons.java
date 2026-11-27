package dev.skypocket.gui;

import dev.skypocket.SkyPocketMod;
import dev.skypocket.ui.Rect;
import dev.skypocket.ui.UiPainter;
import dev.skypocket.ui.mc.McUiPainter;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * The module-card icons, drawn from {@code icons-large.png}.
 *
 * <p>Separate from {@link IconAtlas} because the two sets differ in more than size: this one is
 * indexed by module id and drawn at 62px on the cards, that one by enum constant at 24px on the
 * footer and sidebar.
 *
 * <p>The atlas is a grid, and the cell for each id comes from {@link ModuleIconIds}, which
 * {@code tools/render-icons.mjs} writes. Nothing here decides where an icon lives - if an id is not
 * in the generated table, this class does not guess, it reports "no icon" and the card falls back to
 * its letter tile. That is the important property: a module added without an icon degrades visibly
 * rather than silently drawing someone else's glyph.
 */
final class ModuleIcons {
    private static final int CELL = AtlasSize.LARGE_CELL;
    private static final int COLUMNS = AtlasSize.LARGE_COLUMNS;
    private static final int ATLAS_W = AtlasSize.largeWidth();
    private static final int ATLAS_H = AtlasSize.largeHeight();

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
            SkyPocketMod.MOD_ID, "textures/gui/sprites/ui/icons-large.png");

    /** id -> cell index. Built once from the generated table. */
    private static final Map<String, Integer> CELL_OF = new HashMap<>();

    static {
        for (int i = 0; i < ModuleIconIds.CELLS.length; i++) {
            CELL_OF.put(ModuleIconIds.CELLS[i], i);
        }
    }

    private ModuleIcons() {
    }

    /** Whether this module has an icon in the atlas. */
    static boolean has(String moduleId) {
        return CELL_OF.containsKey(moduleId);
    }

    /**
     * Draws the module's icon centred in {@code box}, or returns false if it has none.
     *
     * <p>Returning a boolean rather than drawing a placeholder keeps the fallback decision with the
     * caller: the card already knows how to draw its letter tile, and it should not have to ask twice
     * whether an icon was drawn.
     *
     * @param color ARGB tint; white leaves the icon as drawn
     */
    static boolean paint(UiPainter painter, String moduleId, Rect box, int color) {
        Integer cell = CELL_OF.get(moduleId);
        if (cell == null || !(painter instanceof McUiPainter mc)) {
            return false;
        }
        float side = Math.min(box.width(), box.height());
        if (side <= 0f) {
            return false;
        }
        float x = box.x() + (box.width() - side) / 2f;
        float y = box.y() + (box.height() - side) / 2f;

        int u = (cell % COLUMNS) * CELL;
        int v = (cell / COLUMNS) * CELL;
        int w = Math.max(1, Math.round(side));
        int h = Math.max(1, Math.round(side));

        // The last two size arguments are the atlas dimensions, not the cell's. Passing CELL for both
        // normalises u/v against a cell-sized texture and samples the whole atlas into the
        // destination - the icon comes out as scrambled pixels.
        mc.graphics().blit(
                net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, TEXTURE,
                Math.round(x), Math.round(y), u, v, w, h, CELL, CELL, ATLAS_W, ATLAS_H,
                color);
        return true;
    }
}
