package dev.skypocket.feature.config;


import dev.skypocket.render.AntialiasingRenderer;
import dev.skypocket.render.ColorGradeRenderer;
import dev.skypocket.render.ColorUtil;

/**
 * Persisted state for the <code>post_process</code> feature.
 *
 * <p>Fields keep the names they had on ModConfig so the rewrite of every read site is an
 * insertion rather than a rename. Defaults are the ones the flat fields carried.
 */
public final class PostProcessConfig {
    public boolean antialiasingEnabled = false;
    public float antialiasingSharpen = 0.0f;
    public boolean antialiasingSkipScreens = true;
    public String antialiasingStrength = AntialiasingRenderer.STANDARD;
    public boolean antialiasingWithShaders = true;
    public boolean bloomEnabled = false;
    public float bloomRadius = 28f;
    public float bloomStrength = 1.0f;
    public float bloomThreshold = 0.70f;
    public float colorGradeContrast = 1.12f;
    public boolean colorGradeEnabled = false;
    public float colorGradeExposure = 1.05f;
    public float colorGradeGamma = 0.98f;
    public float colorGradeLift = 0.005f;
    public String colorGradePreset = ColorGradeRenderer.STANDARD;
    public float colorGradeSaturation = 1.10f;
    public boolean colorGradeSkipScreens = false;
    public float colorGradeTemperature = 0.50f;
    public float colorGradeVignette = 0.30f;
    public boolean motionBlurDebug = false;
    public boolean motionBlurEnabled = false;
    public float motionBlurExposureMs = 16f;
    public float motionBlurMaxRadius = 32f;
    public boolean motionBlurSkipScreens = true;
    public float motionBlurStrength = 0.65f;
    public boolean postProcessEnabled = true;
    /**

     * Clamps and repairs this block's fields after load.
     *
     * <p>Moved out of ModConfig.sanitize() when the fields moved here: a range belongs with the
     * field it constrains, and this class is the only one that knows it. Called from
     * ModConfig.sanitize(), which stays the single entry point.
     */
    public void sanitize() {
        if (!AntialiasingRenderer.LIGHT.equals(antialiasingStrength)
                && !AntialiasingRenderer.STANDARD.equals(antialiasingStrength)
                && !AntialiasingRenderer.STRONG.equals(antialiasingStrength)) {
            antialiasingStrength = AntialiasingRenderer.STANDARD;
        }
        colorGradeExposure = ColorUtil.clamp(colorGradeExposure, 0.5f, 2f);
        colorGradeContrast = ColorUtil.clamp(colorGradeContrast, 0.5f, 2f);
        colorGradeSaturation = ColorUtil.clamp(colorGradeSaturation, 0f, 2f);
        colorGradeGamma = ColorUtil.clamp(colorGradeGamma, 0.5f, 2f);
        colorGradeLift = ColorUtil.clamp(colorGradeLift, -0.2f, 0.3f);
        colorGradeTemperature = ColorUtil.clamp(colorGradeTemperature, 0f, 1f);
        colorGradeVignette = ColorUtil.clamp(colorGradeVignette, 0f, 1f);
        antialiasingSharpen = ColorUtil.clamp(antialiasingSharpen, 0f, 1.5f);
        if (!ColorGradeRenderer.presets().contains(colorGradePreset)) {
            colorGradePreset = ColorGradeRenderer.CUSTOM;
        }
        motionBlurStrength = ColorUtil.clamp(motionBlurStrength, 0f, 1f);
        motionBlurExposureMs = ColorUtil.clamp(motionBlurExposureMs, 1f, 50f);
        motionBlurMaxRadius = ColorUtil.clamp(motionBlurMaxRadius, 4f, 64f);
        bloomStrength = ColorUtil.clamp(bloomStrength, 0f, 2f);
        bloomThreshold = ColorUtil.clamp(bloomThreshold, 0f, 1f);
        bloomRadius = ColorUtil.clamp(bloomRadius, 1f, 64f);
    }
}
