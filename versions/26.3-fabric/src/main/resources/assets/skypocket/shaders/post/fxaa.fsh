#version 330

// Directional anti-aliasing (FXAA 3.11) over the finished world image: one full-screen pass, nine
// texture lookups, no history buffer and no motion vectors.
//
// Because it only reads pixels and rewrites them it cannot drop anything that was already drawn -
// which is exactly what shader-pack TAA breaks (other mods' block highlights and world-space text
// have no place in a pack's gbuffer/motion-vector classification, so they get overwritten by the
// history buffer or discarded outright).
//
// Two things separate this from the 5-lookup variant it replaces:
//  * the neighbourhood is the full 3x3 (N/S/E/W as well as the corner diagonals), so the flat-region
//    test and the sub-pixel estimate see eight neighbours instead of four;
//  * **sub-pixel aliasing removal**. The edge filter cannot see a feature thinner than a pixel (a
//    fence wire, distant leaves, the sparkle on a specular highlight), because there is no gradient
//    to follow. Comparing the centre against the local average and blending some of that average in
//    is what removes those - and it is the single biggest visual difference against the cheap variant.
//
// **That second part is also what blurs small text**, and text is the one thin, high-contrast feature
// on screen that is meant to stay readable rather than shimmer: a glyph stroke triggers the edge
// filter *because* it is high contrast, and then gets pulled towards the 9-tap average *because* it is
// thinner than the filter can follow. Subpix caps how much of that average is mixed in and is the knob
// that trades legibility against shimmer; the thresholds decide how much of the image is treated at
// all. The stock FXAA quality presets are the aggressive end of both, which is why the values this
// chain ships with are noticeably more conservative.
//
// The chain runs main -> swap (this pass) -> main (post/sharpen), because a pass may not read and write
// the same target - and the sharpen deliberately comes *after*: it adds high frequencies, which is
// exactly what the edge filter removes, so running it first would mean the filter undoes it precisely
// on the thin strokes it is wanted for.

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

// Provided by the post-effect JSON (see post_effect/antialiasing.json) and patched live by
// AntialiasingRenderer (a lower threshold treats more edges, i.e. softens more).
layout(std140) uniform FxaaConfig {
    float EdgeThreshold;
    float EdgeThresholdMin;
    float Subpix;
    float Sharpen;
};

in vec2 texCoord;
out vec4 fragColor;

const vec3 LUMA = vec3(0.299, 0.587, 0.114);
const float REDUCE_MIN = 1.0 / 128.0;
const float REDUCE_MUL = 1.0 / 8.0;
const float SPAN_MAX = 8.0;

void main() {
    vec2 texel = 1.0 / InSize;

    vec3 rgbNW = texture(InSampler, texCoord + vec2(-1.0, -1.0) * texel).rgb;
    vec3 rgbNE = texture(InSampler, texCoord + vec2(1.0, -1.0) * texel).rgb;
    vec3 rgbSW = texture(InSampler, texCoord + vec2(-1.0, 1.0) * texel).rgb;
    vec3 rgbSE = texture(InSampler, texCoord + vec2(1.0, 1.0) * texel).rgb;
    vec3 rgbN = texture(InSampler, texCoord + vec2(0.0, -1.0) * texel).rgb;
    vec3 rgbS = texture(InSampler, texCoord + vec2(0.0, 1.0) * texel).rgb;
    vec3 rgbW = texture(InSampler, texCoord + vec2(-1.0, 0.0) * texel).rgb;
    vec3 rgbE = texture(InSampler, texCoord + vec2(1.0, 0.0) * texel).rgb;
    vec4 center = texture(InSampler, texCoord);

    float lumaCenter = dot(center.rgb, LUMA);
    float lumaNW = dot(rgbNW, LUMA);
    float lumaNE = dot(rgbNE, LUMA);
    float lumaSW = dot(rgbSW, LUMA);
    float lumaSE = dot(rgbSE, LUMA);
    float lumaN = dot(rgbN, LUMA);
    float lumaS = dot(rgbS, LUMA);
    float lumaW = dot(rgbW, LUMA);
    float lumaE = dot(rgbE, LUMA);

    float lumaMin = min(lumaCenter, min(min(lumaNW, lumaNE), min(lumaSW, lumaSE)));
    lumaMin = min(lumaMin, min(min(lumaN, lumaS), min(lumaW, lumaE)));
    float lumaMax = max(lumaCenter, max(max(lumaNW, lumaNE), max(lumaSW, lumaSE)));
    lumaMax = max(lumaMax, max(max(lumaN, lumaS), max(lumaW, lumaE)));

    // Flat neighbourhood: nothing to filter. Most of the screen lands here, which is what keeps FXAA
    // cheap. The threshold has a floor so that a dark, low-contrast area is not filtered into mush.
    if (lumaMax - lumaMin < max(EdgeThresholdMin, lumaMax * EdgeThreshold)) {
        fragColor = center;
        return;
    }

    // Edge direction from the luma gradient of the four diagonals, normalised so that a step along it
    // stays within the edge's own pixel (the reduce term keeps very short edges from stretching it).
    vec2 dir = vec2(-((lumaNW + lumaNE) - (lumaSW + lumaSE)),
            ((lumaNW + lumaSW) - (lumaNE + lumaSE)));
    float dirReduce = max((lumaNW + lumaNE + lumaSW + lumaSE) * 0.25 * REDUCE_MUL, REDUCE_MIN);
    float scale = 1.0 / (min(abs(dir.x), abs(dir.y)) + dirReduce);
    dir = clamp(dir * scale, vec2(-SPAN_MAX), vec2(SPAN_MAX)) * texel;

    // Two candidates: the 1/3-step blend, and a wider 1/2-step one that is only kept while it stays
    // within the neighbourhood's luma range (otherwise it would smear across a real edge).
    vec3 blendA = 0.5 * (texture(InSampler, texCoord + dir * (1.0 / 3.0 - 0.5)).rgb
            + texture(InSampler, texCoord + dir * (2.0 / 3.0 - 0.5)).rgb);
    vec3 blendB = blendA * 0.5 + 0.25 * (texture(InSampler, texCoord - dir * 0.5).rgb
            + texture(InSampler, texCoord + dir * 0.5).rgb);
    float lumaBlendB = dot(blendB, LUMA);
    vec3 filtered = (lumaBlendB < lumaMin || lumaBlendB > lumaMax) ? blendA : blendB;

    // Sub-pixel aliasing. A thin feature has a centre that stands out from its neighbourhood while
    // the neighbourhood as a whole is nearly flat; there the directional filter has nothing to follow,
    // so part of the local average is mixed in instead. blendL is 0 on a real, high-contrast edge
    // (where the directional result is the right answer) and grows as the centre pulls away from the
    // average, capped by the module's Subpix setting.
    vec3 lowpass = (rgbNW + rgbNE + rgbSW + rgbSE + rgbN + rgbS + rgbW + rgbE + center.rgb) / 9.0;
    float lumaAverage = dot(lowpass, LUMA);
    float lumaRange = max(lumaMax - lumaMin, 1.0 / 1024.0);
    float blendL = max(0.0, abs(lumaAverage - lumaCenter) / lumaRange - 0.25) * (1.0 / 0.75);
    blendL = min(Subpix, blendL);

    fragColor = vec4(mix(filtered, lowpass, blendL), center.a);
}
