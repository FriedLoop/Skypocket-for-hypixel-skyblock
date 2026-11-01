#version 330

// A small unsharp mask on a 5-tap cross - the detail half of the antialiasing module, run as its own
// pass *after* FXAA (see post_effect/antialiasing.json).
//
// **After, not before.** An unsharp mask adds high frequencies and FXAA's edge filter spends its time
// removing them, so sharpening first would have the second pass undo it along exactly the edges the
// mask just restored - which on small text (thin, high contrast, and the reason this order matters)
// meant the sharpening could not help at all. This is the standard arrangement (anti-alias, then
// sharpen/CAS), and it is what makes the module's sharpening slider visibly affect text.
//
// It gets its own pass instead of sharing FXAA's because the two want different inputs: FXAA has to
// blend along an edge through samples it has not already altered, while the mask wants the filtered
// image as its base. The chain is still two full-screen passes (FXAA into an internal target, then
// this straight into the main target) instead of "FXAA then blit", so the cost is unchanged.

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

// The same block as FXAA's pass - both read it, and LivePostChain patches a block as a whole.
layout(std140) uniform FxaaConfig {
    float EdgeThreshold;
    float EdgeThresholdMin;
    float Subpix;
    float Sharpen;
};

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 texel = 1.0 / InSize;
    vec4 src = texture(InSampler, texCoord);

    // Deliberately small: the input is an LDR image with no oversampling, so a stronger mask just
    // rings around high-contrast edges - and the first-person hand, full of them, is where that shows.
    if (Sharpen > 0.0) {
        vec3 blur = (texture(InSampler, texCoord + vec2(texel.x, 0.0)).rgb
                + texture(InSampler, texCoord - vec2(texel.x, 0.0)).rgb
                + texture(InSampler, texCoord + vec2(0.0, texel.y)).rgb
                + texture(InSampler, texCoord - vec2(0.0, texel.y)).rgb) * 0.25;
        src.rgb += Sharpen * (src.rgb - blur);
    }

    fragColor = vec4(clamp(src.rgb, 0.0, 1.0), src.a);
}
