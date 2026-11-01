#version 330

// Camera motion blur: per-pixel reprojection against the previous frame.
//
// This is the same family as the motion blur in shader packs (BSL / Complementary / Photon keep the
// previous frame's view-projection and reproject against it, and expose an intensity plus a
// max-velocity clamp) and as the client-side effect in Lunar / Badlion (which build a screen-space
// velocity from the camera's rotation and movement deltas, usually with a depth term so translation
// parallaxes while rotation smears everything). None of them have per-object motion vectors either -
// a running player does not blur in those either - so the whole-screen smear this produces when you
// walk or turn is the real thing, not an approximation of it.
//
// The velocity is built in two parts, and that split is what keeps this from failing to nothing:
//
//  * **Rotation**, which needs no depth at all. A point far enough away cannot be moved measurably by
//    the camera's translation, so reprojecting one gives exactly the rotation part of the velocity -
//    and a rotation smears near and far pixels alike, which is the effect people actually notice.
//  * **Parallax**, from the depth buffer on top: the difference between reprojecting the pixel's real
//    world position and the far-field one. Walking makes near geometry slide more than distant
//    geometry, and that is this term.
//
// So a depth buffer that turns out to be unusable, or a pixel whose depth did not come from the world
// projection (the first-person hand is drawn with a separate HUD projection), degrades the blur to
// rotation-only instead of to nothing - and the Java side's bounds catch the difference.
//
// **Coordinate convention (worth not guessing).** minecraft's `core/screenquad` vertex shader maps
// `texCoord` linearly onto clip space on *both* axes (`uv = 0` is NDC -1, `uv = 1` is NDC +1), so
// texCoord and clip space agree and neither axis is flipped - unlike a D3D-style texture coordinate.
// Getting that wrong mirrors the whole reconstruction, which turns every velocity into garbage.
//
// The chain runs main -> swap (this pass) -> main (vanilla's post/blit), and **the colour input must
// be bilinear** (see post_effect/motion_blur.json): with nearest sampling the taps snap to whole
// texels, so a few pixels of genuine motion average the same one or two texels and the effect is
// invisible while a large (wrong) velocity still smears visibly.

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

// Provided by the post-effect JSON (see post_effect/motion_blur.json) and patched live by
// MotionBlurRenderer. The matrices come first so every member sits on its natural std140 boundary
// without padding.
layout(std140) uniform BlurConfig {
    mat4 InvViewProj;
    mat4 PrevViewProj;
    float Strength;
    float Scale;
    float MaxRadius;
    float ZZeroToOne;
    float FarDepth;
    float MaxVelocity;
    float MaxParallax;
    float Debug;
    float CamX;
    float CamY;
    float CamZ;
};

in vec2 texCoord;
out vec4 fragColor;

const float DEPTH_EPSILON = 0.000001;
// Nothing to smear this close, and that band is where the first-person hand lives: it is drawn with
// the separate HUD projection, so its depth reprojects to a wrong place entirely.
const float MIN_DISTANCE = 1.5;
// How far away "the sky" counts as being: it has no depth to reconstruct (cleared), but it is a
// surface like any other and has to blur into itself when the camera turns.
const float SKY_DISTANCE = 1.0e6;
const int TAPS = 10;

float rawDepth(vec2 uv) {
    return texture(DepthSampler, uv).r;
}

bool emptyDepth(float depth) {
    return depth <= DEPTH_EPSILON || depth >= 1.0 - DEPTH_EPSILON;
}

/** Screen position (the space texCoord is in) to clip space - no flips, see the note above. */
vec2 toClip(vec2 uv) {
    return uv * 2.0 - 1.0;
}

/** World position of a pixel, from a depth value. */
vec3 worldPos(vec2 uv, float depth) {
    // The depth range is the device's - MC builds its matrix for [0,1] or for [-1,1] - and
    // ZZeroToOne says which, so the clip-space z fed to the inverse matrix always matches it.
    float clipZ = mix(depth * 2.0 - 1.0, depth, ZZeroToOne);
    vec4 world = InvViewProj * vec4(toClip(uv), clipZ, 1.0);
    return world.xyz / world.w;
}

/** Where a world point was on screen last frame, in screen space. False when it was behind the camera. */
bool previousScreenPos(vec3 world, out vec2 prevUV) {
    vec4 clip = PrevViewProj * vec4(world, 1.0);
    if (clip.w <= 0.0001) {
        return false;
    }
    prevUV = clip.xy / clip.w * 0.5 + 0.5;
    return true;
}

void main() {
    vec4 src = texture(InSampler, texCoord);
    bool debug = Debug > 0.5;

    // The rotation part: reproject the far-field point along this pixel's own ray.
    vec2 rotationVelocity = vec2(0.0);
    vec2 farUV;
    bool haveRotation = previousScreenPos(worldPos(texCoord, FarDepth), farUV);
    if (haveRotation) {
        rotationVelocity = farUV - texCoord;
    }

    // ... plus the parallax of this pixel's measured world position, when that depth is usable.
    vec2 velocity = rotationVelocity;
    float depth = rawDepth(texCoord);
    if (!emptyDepth(depth)) {
        vec3 here = worldPos(texCoord, depth);
        if (distance(here, vec3(CamX, CamY, CamZ)) >= MIN_DISTANCE) {
            vec2 objectUV;
            if (previousScreenPos(here, objectUV)) {
                vec2 parallax = objectUV - texCoord - rotationVelocity;
                // A usable parallax stays below what this frame's camera movement could produce at the
                // shallowest blurrable depth. Anything larger did not come from the world projection -
                // the hand, or a stale buffer after a teleport - and the rotation part stands.
                if (length(parallax) <= MaxParallax) {
                    velocity = objectUV - texCoord;
                }
            }
        }
    }

    if (debug) {
        // Deliberately exempt from every guard below: the point of this view is to answer "is a
        // velocity being computed here at all?", which a guard could otherwise hide.
        vec2 shown = abs(velocity * max(Strength * Scale, 1.0) * InSize.y) / max(MaxRadius, 1.0);
        fragColor = vec4(shown.x, shown.y, max(shown.x, shown.y), 1.0);
        return;
    }

    if (Strength * Scale <= 0.0 || !haveRotation) {
        fragColor = src;
        return;
    }
    // More screen motion than the camera could possibly have produced this frame: trust the rotation
    // part (which is depth-free) rather than smearing whatever came out.
    if (length(velocity) > MaxVelocity) {
        velocity = rotationVelocity;
    }

    velocity *= Strength * Scale;
    float travel = length(velocity * InSize.y);
    if (travel < 0.5) {
        fragColor = src;
        return;
    }
    if (travel > MaxRadius) {
        velocity *= MaxRadius / travel;
    }

    vec3 camera = vec3(CamX, CamY, CamZ);
    float hereDistance = emptyDepth(depth) ? SKY_DISTANCE
            : distance(worldPos(texCoord, depth), camera);
    vec3 sum = src.rgb;
    float weightSum = 1.0;
    for (int i = 1; i <= TAPS; i++) {
        float t = float(i) / float(TAPS);
        vec2 uv = texCoord + velocity * t;
        float tapDepth = rawDepth(uv);
        // Only average taps that live on the same piece of world, or a foreground object gets smeared
        // over the background it passes in front of. The test is on the *distance from the camera*.
        //
        // It used to be on the distance between the two reconstructed world positions, which looks
        // equivalent and is not: along the blur segment a pixel legitimately sweeps across the scene,
        // so at any real distance the reconstructed point moves much further than the object is thick
        // (a 32 px sweep at 100 blocks is a ~4 block world displacement locally). Every tap was
        // therefore rejected, the weighted sum fell back to the untouched centre pixel, and the result
        // was a pass that ran, computed correct velocities, and changed nothing at all on anything far
        // away - while the first-person hand, a couple of blocks from the camera, passed the test and
        // was the only thing that visibly blurred.
        float tapDistance = emptyDepth(tapDepth) ? SKY_DISTANCE
                : distance(worldPos(uv, tapDepth), camera);
        if (abs(tapDistance - hereDistance) > max(1.0, hereDistance * 0.15)) {
            continue;
        }
        sum += texture(InSampler, uv).rgb * (1.0 - 0.5 * t);
        weightSum += 1.0 - 0.5 * t;
    }

    fragColor = vec4(sum / weightSum, src.a);
}
