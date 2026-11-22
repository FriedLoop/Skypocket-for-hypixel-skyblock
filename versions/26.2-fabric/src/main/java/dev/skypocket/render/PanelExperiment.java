package dev.skypocket.render;

/**
 * Runtime switches for experiments on the panel's draw layering.
 *
 * <p>Deliberately not persisted configuration, following {@link FontLab}: these exist to answer a
 * question and are removed once it is answered.
 *
 * <h2>The question</h2>
 *
 * <p>{@code drawPanel} calls {@code GuiGraphicsExtractor.nextStratum()} after painting the
 * translucent body and before drawing the cached tabs/footer strip. The comment there says GUI blits
 * can be reordered by their bounds, so the background needs its own lower stratum or page content can
 * be composited underneath it. That is a claim about Minecraft's GUI batching, and it decides how the
 * shell can be structured: if the stratum is still required, the shell cannot simply be painted as a
 * plain back-to-front sequence of layers.
 *
 * <p>The claim was never tested. {@code nextStratum} allocates a new node in {@code GuiRenderState}
 * and appends it to the strata list, which is a real ordering mechanism - but whether the specific
 * reordering it guards against still happens in 26.2/26.3 is a separate question from whether the
 * mechanism exists.
 */
public final class PanelExperiment {
    private PanelExperiment() {
    }

    private static volatile boolean panelStratum = true;

    /** Whether the panel body is put in its own stratum below the shell strip. */
    public static boolean panelStratum() {
        return panelStratum;
    }

    public static void setPanelStratum(boolean value) {
        panelStratum = value;
    }

    private static volatile boolean shellCache = true;

    /** Whether the tabs/footer strip is drawn from the cached texture. */
    public static boolean shellCache() {
        return shellCache;
    }

    public static void setShellCache(boolean value) {
        shellCache = value;
    }
}
