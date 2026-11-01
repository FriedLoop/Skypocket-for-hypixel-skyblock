#version 330

// Bright pass, horizontally blurred.
//
// The textbook chain is bright pass -> horizontal blur -> vertical blur. The bright pass is a per-texel
// operation, and blurring it is identical to weighting every source tap by its own brightness, so the
// first two collapse into this one pass; the vertical half is the chain's second pass.
//
// The radius is a real one: nine gaussian taps spread over BloomRadius pixels, with bilinear sampling
// filling the gaps between them. That matters because a glow narrower than a few dozen pixels reads as
// "brighter image" rather than as a halo - which is exactly what a 13-tap disc crammed into 6 pixels
// looked like.

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform BloomConfig {
    float BloomStrength;
    float BloomThreshold;
    float BloomRadius;
};

in vec2 texCoord;
out vec4 fragColor;

const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);
// Gaussian weights, sigma ~ 2 taps: index 0..4 is the offset distance |i|.
const float WEIGHTS[5] = float[5](0.227027, 0.1945946, 0.1216216, 0.054054, 0.016216);
// Knee of the bright pass: dim pixels contribute little, genuinely bright ones glow, and nothing pops
// in at the threshold.
const float KNEE = 0.30;

void main() {
    // The outermost pair of taps reaches BloomRadius pixels; the inner ones sit proportionally closer.
    float step = max(BloomRadius, 1.0) * 0.25 * (1.0 / InSize.x);
    vec3 sum = vec3(0.0);
    float weightSum = 0.0;

    for (int i = -4; i <= 4; i++) {
        int index = i < 0 ? -i : i;
        float weight = WEIGHTS[index];
        vec3 tap = texture(InSampler, vec2(texCoord.x + step * float(i), texCoord.y)).rgb;
        float excess = max(dot(tap, LUMA) - BloomThreshold, 0.0);
        sum += tap * (weight * excess / (excess + KNEE));
        weightSum += weight;
    }

    fragColor = vec4(sum / weightSum, 1.0);
}
