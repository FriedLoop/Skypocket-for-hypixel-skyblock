package dev.skypocket.gui;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.gui.widget.TextFieldWidget;
import dev.skypocket.render.ClickGuiFonts;
import net.minecraft.client.Minecraft;

import static dev.skypocket.gui.PanelGeometry.FAR_AWAY;
import static dev.skypocket.gui.PanelGeometry.hoveredCardKey;
import static dev.skypocket.gui.PanelGeometry.hoveredFooterButton;
import static dev.skypocket.gui.PanelGeometry.hoveredSidebarKey;
import static dev.skypocket.gui.PanelGeometry.hoveredTabKey;
import static dev.skypocket.gui.PanelGeometry.inBody;
import static dev.skypocket.gui.PanelGeometry.insideSearchBox;

/**
 * Hash of everything that changes the panel's pixels; {@link dev.skypocket.render.GuiPanelCache}
 * re-bakes the panel whenever it changes and blits while it is stable.
 *
 * <p>Hovers are folded into a per-element signature of their own rather than the raw cursor, so
 * moving the mouse inside one card does not invalidate the cache. Anything that is not covered here
 * means a stale frame, so when in doubt add it here (a term that is hashed too eagerly only costs a
 * re-bake, a missing one shows a frozen panel).
 *
 * <p>Split out of {@code OneClickScreen}: the key reads the panel state and the layout, which are
 * {@link PanelView}'s and {@link PanelGeometry}'s business, not the screen's - and keeping it in one
 * place makes it obvious which state a re-bake depends on.
 */
public final class PanelCacheKey {
    private PanelCacheKey() {
    }

    /**
     * The frame's key.
     *
     * @param sidebarHighlightY the animated sidebar highlight position (it moves over several
     *                          frames, so it is part of the pixels)
     * @param contentRevision   bumped whenever the widgets are rebuilt from scratch
     * @param settingsAnimation how far the on/off switches in the settings page have moved; 0 when
     *                          they are at rest - which is why it is a term of its own rather than
     *                          "any animation is running" (see {@code ToggleAnimation#signature()})
     * @param animFinished      whether the open/close animation has settled
     */
    public static long of(PanelView view, int lmx, int lmy, float sidebarHighlightY,
                          int contentRevision, int settingsAnimation, boolean animFinished) {
        long[] parts = {
                viewHash(view),
                hoverKey(view, lmx, lmy),
                Math.round(sidebarHighlightY),
                ConfigManager.revision(),
                contentRevision,
                settingsAnimation,
                Math.round(ClickGuiFonts.deviceFactor() * 1000f),
                animFinished ? 1 : 0,
        };
        trace(parts);
        long h = 1125899906842597L;
        for (long part : parts) {
            h = h * 31 + part;
        }
        return h;
    }

    private static long viewHash(PanelView view) {
        long h = 1125899906842597L;
        h = h * 31 + (view.settingsTab() ? 1 : 0);
        h = h * 31 + (view.viewingFeature() == null ? 0
                : System.identityHashCode(view.viewingFeature()));
        h = h * 31 + (view.selected() == null ? 0 : System.identityHashCode(view.selected()));
        h = h * 31 + view.scrollY();
        h = h * 31 + view.search().hashCode();
        return h;
    }

    // ---- Which component moved (diagnostic) ----------------------------------------------
    //
    // The panel stopped caching while the mouse was still, and the gate counters said only
    // "key-changed" for every frame - i.e. one of these eight numbers moves every frame, but not
    // which. Naming it here is cheaper than bisecting by hand.

    private static final String[] TRACE_NAMES = {
            "view", "hover", "sidebar", "config-revision", "content-revision", "toggle-animation",
            "device-factor", "anim-finished"};
    private static final long[] TRACE_LAST = new long[TRACE_NAMES.length];
    private static long traceNextMs;

    private static void trace(long[] parts) {
        if (!ConfigManager.get().panelCacheStats) {
            return;
        }
        StringBuilder changed = null;
        for (int i = 0; i < parts.length && i < TRACE_LAST.length; i++) {
            if (parts[i] != TRACE_LAST[i]) {
                if (changed == null) {
                    changed = new StringBuilder();
                }
                changed.append(' ').append(TRACE_NAMES[i]).append('=').append(parts[i]);
            }
        }
        System.arraycopy(parts, 0, TRACE_LAST, 0, Math.min(parts.length, TRACE_LAST.length));
        long now = System.currentTimeMillis();
        if (changed != null && now >= traceNextMs) {
            traceNextMs = now + 1000L;
            LOGGER.info("panel key changed:{}", changed);
        }
    }

    /** Which elements sit under the cursor — see {@link #of}. */
    private static final dev.skypocket.SkyPocketLog LOGGER =
            dev.skypocket.SkyPocketLog.of("panelKey");

    /** Hover bucket size for widget pages, in GUI pixels - see {@link #hoverKey}. */
    private static final int HOVER_GRID_X = 32;
    private static final int HOVER_GRID_Y = 16;

    /** True while the left button is held, in which case a drag has to be hashed exactly. */
    private static boolean leftDown() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.mouseHandler != null && mc.mouseHandler.isLeftPressed();
    }

    private static long hoverKey(PanelView view, int lmx, int lmy) {
        long h = 17;
        h = h * 31 + hoveredTabKey(lmx, lmy);
        h = h * 31 + (hoveredFooterButton(lmx, lmy) ? 1 : 0);
        if (!view.settingsTab() && view.viewingFeature() == null) {
            h = h * 31 + hoveredSidebarKey(view, lmx, lmy);
            h = h * 31 + hoveredCardKey(view, lmx, lmy);
            h = h * 31 + (insideSearchBox(lmx, lmy) ? 1 : 0);
        } else {
            // Widget pages own their hover/drag states. Hashing the raw cursor meant the panel was
            // re-baked on every pixel of mouse movement - the cache was only ever reused while the
            // mouse was perfectly still.
            //
            // Hashing *which control* the cursor is over instead is the same information for a stacked
            // settings list, and it keeps the cache alive while the mouse moves within one control. The
            // grid is a coarse stand-in for that (32 x 16 px): coarse enough that ordinary movement does
            // not cross a boundary, fine enough that moving from a row's label to its toggle lands in a
            // different bucket and repaints.
            //
            // While the left button is down the cursor is hashed exactly: dragging a slider or a colour
            // picker reads the continuous position, and quantising it would freeze the visuals mid-drag.
            boolean dragging = leftDown();
            long hmx = inBody(lmx, lmy) ? (dragging ? lmx : lmx / HOVER_GRID_X) : FAR_AWAY;
            long hmy = inBody(lmx, lmy) ? (dragging ? lmy : lmy / HOVER_GRID_Y) : FAR_AWAY;
            h = h * 31 + hmx * 31L + hmy;
            h = h * 31 + (dragging ? 1 : 0);
            h = h * 31 + (TextFieldWidget.focusedField() == null ? 0 : 1);
        }
        return h;
    }
}
