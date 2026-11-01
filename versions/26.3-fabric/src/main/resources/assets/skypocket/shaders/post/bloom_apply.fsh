#version 330

// Adds the blurred bright pass back over the world image. Reads main and the glow, writes to an
// internal target - not to minecraft:main, because a pass must not read and write the same target.

uniform sampler2D InSampler;
uniform sampler2D BloomSampler;

// Same block as the bright pass (see post_effect/bloom.json); only the strength is used here, but a
// block is patched as a whole (see LivePostChain).
layout(std140) uniform BloomConfig {
    float BloomStrength;
    float BloomThreshold;
    float BloomRadius;
};

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 src = texture(InSampler, texCoord);
    vec3 glow = texture(BloomSampler, texCoord).rgb * BloomStrength;

    // Screen blend rather than addition. Additive bloom brightens the *interior* of bright areas -
    // measured complaint: "raising the strength makes anything white too bright" - because a glow that
    // covers an already-white pixel gets clipped and flattens it. With 1-(1-a)(1-b) a white pixel is
    // unchanged no matter the strength, while the halo over darker surroundings still shows at full
    // strength, which is the part that reads as glow.
    fragColor = vec4(clamp(1.0 - (1.0 - src.rgb) * (1.0 - glow), 0.0, 1.0), src.a);
}
