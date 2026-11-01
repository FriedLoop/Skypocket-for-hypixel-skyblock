#version 330
//#if MC>=260300
#extension GL_ARB_separate_shader_objects : require
//#endif
// Vertical half of the glow blur: the bright image from post/bloom_pre, blurred along y. No bright
// pass here - that already happened, and re-testing luma would darken the glow a second time.

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

//#if MC>=260300
layout(location = 0) in vec2 texCoord;
//#else
in vec2 texCoord;
//#endif
//#if MC>=260300
layout(location = 0) out vec4 fragColor;
//#else
out vec4 fragColor;
//#endif

// Same kernel as the horizontal pass (see post/bloom_pre.fsh).
const float WEIGHTS[5] = float[5](0.227027, 0.1945946, 0.1216216, 0.054054, 0.016216);

void main() {
    float step = max(BloomRadius, 1.0) * 0.25 * (1.0 / InSize.y);
    vec3 sum = vec3(0.0);
    float weightSum = 0.0;

    for (int i = -4; i <= 4; i++) {
        int index = i < 0 ? -i : i;
        float weight = WEIGHTS[index];
        vec3 tap = texture(InSampler, vec2(texCoord.x, texCoord.y + step * float(i))).rgb;
        sum += tap * weight;
        weightSum += weight;
    }

    fragColor = vec4(sum / weightSum, 1.0);
}
