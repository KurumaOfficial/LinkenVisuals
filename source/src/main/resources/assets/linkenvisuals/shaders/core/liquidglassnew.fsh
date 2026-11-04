#version 150

#moj_import <linkenvisuals:common.glsl>

in vec2 FragCoord;
in vec2 TexCoord;
in vec4 FragColor;

uniform sampler2D Sampler0;
uniform vec2 Size;
uniform vec4 Radius;
uniform float Smoothness;
uniform float CornerSmoothness;
uniform float GlobalAlpha;
uniform vec2 FramebufferSize;
uniform float BlurRadius;

uniform float FresnelPower;
uniform vec3 FresnelColor;
uniform float FresnelAlpha;
uniform float BaseAlpha;
uniform int FresnelInvert;
uniform float FresnelMix;
uniform float DistortStrength;

out vec4 OutColor;

const float TAU = 6.28318530718;
const float RING_STEP = TAU / 10.0;
const float STEP_SIZE = 1.0 / 3.0;

float roundedBoxSDF(vec2 p, vec2 b, vec4 r, float smoothness) {
    r.xy = (p.x > 0.0) ? r.xy : r.zw;
    r.x = (p.y > 0.0) ? r.x : r.y;

    vec2 q = abs(p) - b + r.x;
    vec2 q_clamped = max(q, 0.0);
    float len = pow(pow(q_clamped.x, smoothness) + pow(q_clamped.y, smoothness), 1.0 / smoothness);
    return min(max(q.x, q.y), 0.0) + len - r.x;
}

vec4 sampleScene(vec2 coord) {
    vec2 uv = clamp(coord, vec2(0.0), vec2(1.0));
    return texture(Sampler0, uv) * FragColor;
}

vec4 blurScene(vec2 coord) {
    float radius = max(BlurRadius, 0.0);
    if (radius < 0.001 || FramebufferSize.x <= 0.0 || FramebufferSize.y <= 0.0) {
        return sampleScene(coord);
    }

    vec2 invSize = vec2(1.0) / max(FramebufferSize, vec2(1.0));
    vec4 accum = sampleScene(coord);
    float weightSum = 1.0;

    for (float angle = 0.0; angle < TAU; angle += RING_STEP) {
        vec2 direction = vec2(cos(angle), sin(angle));
        for (float step = STEP_SIZE; step <= 1.001; step += STEP_SIZE) {
            float ringWeight = 1.0 - 0.28 * step;
            vec2 offset = direction * (radius * step) * invSize;
            accum += sampleScene(coord + offset) * ringWeight;
            weightSum += ringWeight;
        }
    }

    return accum / weightSum;
}

void main() {
    vec2 center = Size * 0.5;
    vec2 box_half_size = center - 1.0;
    vec2 pos = (FragCoord * Size) - center;

    float distance = roundedBoxSDF(-pos, box_half_size, Radius, CornerSmoothness);
    float gradient = fwidth(distance);
    if (!(gradient > 0.0)) {
        gradient = 0.0;
    }
    float aa = max(gradient * 0.5, 0.0001);
    float softness = max(Smoothness * 0.5, 0.0);
    float alpha = smoothstep(-softness - aa, softness + aa, -distance);

    float distToEdge = abs(distance);

    float max_dist_norm = min(box_half_size.x, box_half_size.y);
    float edge_gradient = 1.0 - clamp(distToEdge / max_dist_norm, 0.0, 1.0);

    float base = FresnelInvert == 1 ? edge_gradient : (1.0 - edge_gradient);
    float fresnel;

    if (FresnelPower > 20.0) {
        fresnel = exp(FresnelPower * log(clamp(base, 0.001, 1.0)));
    } else {
        fresnel = pow(base, FresnelPower);
    }
    fresnel = clamp(fresnel, 0.0, 1.0);

    vec2 dir = vec2(0.0);
    float lenPos = length(pos);
    if (lenPos > 0.0001) {
        dir = pos / lenPos;
    }

    vec2 distortedTexCoord = TexCoord + dir * fresnel * DistortStrength;

    vec4 texColor = blurScene(distortedTexCoord);

    vec3 finalColor = mix(texColor.rgb, FresnelColor, fresnel * FresnelMix);
    float finalAlpha = mix(BaseAlpha, FresnelAlpha, fresnel) * alpha;

    if (finalAlpha < 0.001) {
        discard;
    }

    OutColor = vec4(finalColor, finalAlpha * GlobalAlpha);
}
