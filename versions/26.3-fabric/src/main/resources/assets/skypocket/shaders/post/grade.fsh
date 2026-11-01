#version 330

// One-pass colour grading over the finished world image: exposure, temperature, contrast, lift/gamma,
// saturation and a vignette. Same shape as the FXAA pass - a single full-screen read/write, five or
// fewer taps, no geometry pass and no shadow map, so its cost tracks the resolution and nothing else.
//
// Deliberately *no* HDR tonemap operator (Reinhard/ACES): the input is the finished, already
// tonemapped LDR image, so an operator built for linear HDR input would simply darken and desaturate
// it. On display-referred data a pivot contrast curve, lift/gamma and a luma-preserving saturation
// are what actually reads as a "look".
//
// All parameters are neutral at exposure 1, contrast 1, saturation 1, gamma 1, lift 0,
// temperature 0.5, vignette 0, so every preset is a small deviation from the vanilla image.
//
// Depth-driven effects (sharpen, distance fog, depth AO) live in their own chain and module - see
// post_effect/image_detail.json - so that anything to do with the depth input stays out of this one.

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

// Provided by the post-effect JSON (see post_effect/grade.json); order must match the JSON list.
layout(std140) uniform GradeConfig {
    float Exposure;
    float Contrast;
    float Saturation;
    float Gamma;
    float Lift;
    float Temperature;
    float Vignette;
};

in vec2 texCoord;
out vec4 fragColor;

const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);

void main() {
    vec4 src = texture(InSampler, texCoord);
    vec3 c = src.rgb;

    // 1. Exposure.
    c *= Exposure;

    // 2. Temperature: warm lifts red and cuts blue, cool the opposite (0.5 = neutral).
    c *= mix(vec3(0.94, 1.0, 1.06), vec3(1.06, 1.0, 0.94), Temperature);

    // 3. Contrast around mid grey.
    c = (c - 0.5) * Contrast + 0.5;

    // 4. Lift (black point) then gamma (midtones).
    c = pow(max(c + Lift, 0.0), vec3(1.0 / max(Gamma, 0.01)));

    // 5. Saturation, luma preserving.
    c = mix(vec3(dot(c, LUMA)), c, Saturation);

    // 6. Vignette, aspect corrected so the corners darken evenly on any resolution.
    float aspect = OutSize.x / max(OutSize.y, 1.0);
    vec2 d = (texCoord - 0.5) * vec2(aspect, 1.0);
    c *= 1.0 - Vignette * smoothstep(0.35, 0.85, length(d));

    fragColor = vec4(clamp(c, 0.0, 1.0), src.a);
}
