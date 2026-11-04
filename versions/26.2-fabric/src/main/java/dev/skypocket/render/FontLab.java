package dev.skypocket.render;

import java.util.List;

/**
 * Temporary knobs for the UI v2 font comparison.
 *
 * <p>These options are deliberately not in {@code ModConfig}: this is a development experiment, not a
 * player setting, and adding persisted fields would require a config migration that will be thrown away
 * once the final raster policy is chosen. The values reset on every launch; the debug group exposes them
 * so the real ClickGUI can be compared under each combination.</p>
 */
public final class FontLab {
    public enum Origin {
        DEVICE("Device", 1.0f),
        HALF("Half", 0.5f),
        QUARTER("Quarter", 0.25f),
        NONE("None", 0.0f);

        public final String id;
        /** Snap step in device pixels; 0 disables origin snapping. */
        public final float step;

        Origin(String id, float step) {
            this.id = id;
            this.step = step;
        }
    }

    public enum Hinting {
        LIGHT("Light"),
        NORMAL("Normal"),
        NONE("None");

        public final String id;

        Hinting(String id) {
            this.id = id;
        }
    }

    public enum Weight {
        // The original inter.ttf shipped as OS/2 weight 700 (Bold), medium as 500 and semibold as
        // 600. Map by actual weight so REGULAR is never heavier than MEDIUM/SEMIBOLD. A true 400
        // Regular can replace the first asset later without changing call sites.
        REGULAR("Regular", "assets/skypocket/font/inter-medium.ttf"),
        MEDIUM("Medium", "assets/skypocket/font/inter-semibold.ttf"),
        SEMIBOLD("SemiBold", "assets/skypocket/font/inter.ttf");

        public final String id;
        public final String asset;

        Weight(String id, String asset) {
            this.id = id;
            this.asset = asset;
        }
    }

    private static final List<String> ORIGIN_IDS =
            List.of(Origin.DEVICE.id, Origin.HALF.id, Origin.QUARTER.id, Origin.NONE.id);
    private static final List<String> HINTING_IDS =
            List.of(Hinting.LIGHT.id, Hinting.NORMAL.id, Hinting.NONE.id);
    private static final List<String> WEIGHT_IDS =
            List.of(Weight.REGULAR.id, Weight.MEDIUM.id, Weight.SEMIBOLD.id);
    private static final List<String> OVERSAMPLE_IDS = List.of("1x", "2x");

    private static boolean integerAdvance = true;
    private static Origin origin = Origin.DEVICE;
    private static Hinting hinting = Hinting.NONE;
    private static Weight weight = Weight.REGULAR;
    private static int oversample = 1;
    private static float gamma = 1.0f;
    private static boolean linearFilter;
    private static boolean atlasText = true;
    private static final byte[] GAMMA_LUT = new byte[256];

    static {
        rebuildGamma();
    }

    private FontLab() {
    }

    public static boolean integerAdvance() {
        return integerAdvance;
    }

    public static void setIntegerAdvance(boolean value) {
        if (integerAdvance != value) {
            integerAdvance = value;
            ClickGuiFonts.reloadExperimentFonts();
        }
    }

    public static Origin origin() {
        return origin;
    }

    public static String originId() {
        return origin.id;
    }

    public static List<String> originIds() {
        return ORIGIN_IDS;
    }

    public static void setOrigin(String id) {
        for (Origin value : Origin.values()) {
            if (value.id.equals(id)) {
                // This only changes the draw origin, not the glyph atlas, so no rebuild is needed.
                origin = value;
                return;
            }
        }
    }

    public static Hinting hinting() {
        return hinting;
    }

    public static String hintingId() {
        return hinting.id;
    }

    public static List<String> hintingIds() {
        return HINTING_IDS;
    }

    public static void setHinting(String id) {
        for (Hinting value : Hinting.values()) {
            if (value.id.equals(id) && hinting != value) {
                hinting = value;
                ClickGuiFonts.reloadExperimentFonts();
                return;
            }
        }
    }

    public static Weight weight() {
        return weight;
    }

    public static String weightId() {
        return weight.id;
    }

    public static List<String> weightIds() {
        return WEIGHT_IDS;
    }

    public static void setWeight(String id) {
        for (Weight value : Weight.values()) {
            if (value.id.equals(id) && weight != value) {
                weight = value;
                ClickGuiFonts.reloadExperimentFonts();
                return;
            }
        }
    }

    public static int oversample() {
        return oversample;
    }

    public static String oversampleId() {
        return oversample + "x";
    }

    public static List<String> oversampleIds() {
        return OVERSAMPLE_IDS;
    }

    public static void setOversample(String id) {
        int value = "2x".equals(id) ? 2 : 1;
        if (oversample != value) {
            oversample = value;
            ClickGuiFonts.reloadExperimentFonts();
        }
    }

    public static boolean linearFilter() {
        return linearFilter;
    }

    public static void setLinearFilter(boolean value) {
        // The sampler is chosen when the glyph render state is built; no atlas rebuild is needed.
        linearFilter = value;
    }

    /** The configuration selected after the in-game comparison. */
    public static void applyBestPreset() {
        integerAdvance = true;
        origin = Origin.DEVICE;
        hinting = Hinting.NONE;
        weight = Weight.REGULAR;
        oversample = 1;
        gamma = 1.0f;
        linearFilter = false;
        atlasText = true;
        rebuildGamma();
        ClickGuiFonts.reloadExperimentFonts();
    }

    /** A deliberately different 2x/fractional profile, kept for comparison. */
    public static void applySmoothPreset() {
        integerAdvance = false;
        origin = Origin.NONE;
        hinting = Hinting.NORMAL;
        weight = Weight.REGULAR;
        oversample = 2;
        gamma = 1.0f;
        // The custom atlas supplies its own linear sampler; leaving this on would also make vanilla
        // HUD glyphs use the bleeding MC atlas.
        linearFilter = false;
        atlasText = true;
        rebuildGamma();
        ClickGuiFonts.reloadExperimentFonts();
    }

    /** The current crisp policy, so the comparison can always get back to a known baseline. */
    public static void applyCrispPreset() {
        integerAdvance = true;
        origin = Origin.DEVICE;
        hinting = Hinting.LIGHT;
        weight = Weight.REGULAR;
        oversample = 1;
        gamma = 1.0f;
        linearFilter = false;
        atlasText = false;
        rebuildGamma();
        ClickGuiFonts.reloadExperimentFonts();
    }

    public static boolean atlasText() {
        return atlasText;
    }

    public static void setAtlasText(boolean value) {
        if (atlasText != value) {
            atlasText = value;
            ClickGuiFonts.reloadExperimentFonts();
        }
    }

    public static float gamma() {
        return gamma;
    }

    public static void setGamma(double value) {
        float next = (float) Math.max(0.8, Math.min(1.4, value));
        if (Math.abs(next - gamma) > 0.001f) {
            gamma = next;
            rebuildGamma();
            ClickGuiFonts.reloadExperimentFonts();
        }
    }

    /** Coverage after gamma correction; 1.0 is the identity table. */
    public static int coverage(int value) {
        return GAMMA_LUT[value & 0xFF] & 0xFF;
    }

    private static void rebuildGamma() {
        for (int i = 0; i < 256; i++) {
            double corrected = Math.pow(i / 255.0, gamma) * 255.0;
            GAMMA_LUT[i] = (byte) Math.max(0, Math.min(255, Math.round(corrected)));
        }
    }
}
