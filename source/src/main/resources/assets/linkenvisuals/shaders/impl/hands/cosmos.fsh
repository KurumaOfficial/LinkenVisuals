#version 330 core

in vec2 uv;
out vec4 outColor;

uniform sampler2D ColorTexture;
uniform sampler2D DepthTexture;
uniform sampler2D BeforeColorTexture;
uniform sampler2D BeforeDepthTexture;
uniform float time;
uniform vec2 resolution;
uniform vec3 color1;
uniform vec3 color2;
uniform vec3 color3;
uniform vec3 color4;
uniform int mode;

#define SPIN_ROTATION -2.0
#define SPIN_SPEED 7.0
#define OFFSET vec2(0.0)
#define SPIN_AMOUNT 0.25
#define PIXEL_FILTER 745.0
#define SPIN_EASE 1.0
#define PI 3.14159265359

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

vec3 stars(vec2 p) {
    vec2 grid = p * 32.0;
    vec2 id = floor(grid);
    vec2 local = fract(grid) - 0.5;
    float rnd = hash(id);
    float star = 0.0;
    if (rnd > 0.82) {
        vec2 starPos = vec2(hash(id + vec2(1.0, 0.0)), hash(id + vec2(0.0, 1.0))) - 0.5;
        float dist = length(local - starPos * 0.7);
        float twinkle = sin(time * 3.0 + rnd * 6.28318) * 0.35 + 0.75;
        star = exp(-dist * dist * 520.0) * twinkle;
    }
    return vec3(star);
}

vec4 cosmosEffect(vec2 screenSize, vec2 screenCoords, float contrast, float lighting) {
    float pixelSize = length(screenSize.xy) / PIXEL_FILTER;
    vec2 effectUv = (floor(screenCoords.xy * (1.0 / pixelSize)) * pixelSize - 0.5 * screenSize.xy) / length(screenSize.xy) - OFFSET;
    float uvLen = length(effectUv);

    float speed = SPIN_ROTATION * SPIN_EASE * 0.2 + 302.2;
    float angle = atan(effectUv.y, effectUv.x) + speed - SPIN_EASE * 20.0 * (SPIN_AMOUNT * uvLen + (1.0 - SPIN_AMOUNT));
    vec2 mid = (screenSize.xy / length(screenSize.xy)) / 2.0;
    effectUv = vec2(uvLen * cos(angle) + mid.x, uvLen * sin(angle) + mid.y) - mid;

    effectUv *= 30.0;
    speed = time * SPIN_SPEED;
    vec2 uv2 = vec2(effectUv.x + effectUv.y);

    for (int i = 0; i < 5; i++) {
        uv2 += sin(max(effectUv.x, effectUv.y)) + effectUv;
        effectUv += 0.5 * vec2(
            cos(5.1123314 + 0.353 * uv2.y + speed * 0.131121),
            sin(uv2.x - 0.113 * speed)
        );
        effectUv -= cos(effectUv.x + effectUv.y) - sin(effectUv.x * 0.711 - effectUv.y);
    }

    float contrastMod = 0.25 * contrast + 0.5 * SPIN_AMOUNT + 1.2;
    float paint = min(2.0, max(0.0, length(effectUv) * 0.035 * contrastMod));
    float c1p = max(0.0, 1.0 - contrastMod * abs(1.0 - paint));
    float c2p = max(0.0, 1.0 - contrastMod * abs(paint));
    float c3p = 1.0 - min(1.0, c1p + c2p);
    float light = (lighting - 0.2) * max(c1p * 5.0 - 4.0, 0.0) + lighting * max(c2p * 5.0 - 4.0, 0.0);

    vec4 colour1 = vec4(color1, 1.0);
    vec4 colour2 = vec4(color2, 1.0);
    vec4 colour3 = vec4(color3, 1.0);
    vec4 swirls = (0.3 / contrast) * colour1
            + (1.0 - 0.3 / contrast) * (colour1 * c1p + colour2 * c2p + vec4(c3p * colour3.rgb, c3p * colour1.a))
            + light;
    swirls.rgb += stars(screenCoords / screenSize) * 0.8;
    return swirls;
}

vec3 chromaEffect(vec2 p) {
    float hue = p.x * 0.55 + p.y * 0.22 + time * 0.12;
    vec3 k = vec3(1.0, 0.6666667, 0.3333333);
    vec3 rgb = abs(fract(hue + k) * 6.0 - 3.0) - 1.0;
    return clamp(rgb, 0.0, 1.0);
}

vec3 glassEffect(vec4 original, vec2 p) {
    float edge = smoothstep(0.15, 0.95, length(p * 2.0 - 1.0));
    vec3 tint = mix(vec3(0.55, 0.9, 1.0), vec3(1.0), 0.35);
    return mix(original.rgb * 0.55 + tint * 0.45, vec3(1.0), edge * 0.25);
}

vec3 glowEffect(vec2 p) {
    float pulse = 0.65 + 0.35 * sin(time * 3.2 + p.y * 10.0);
    float glow = exp(-length(p * 2.0 - 1.0) * 1.4);
    return color2 * (0.45 + pulse) + color3 * glow;
}

vec3 smokeEffect(vec2 p) {
    vec2 q = p * 4.0 + vec2(time * 0.08, -time * 0.05);
    float n = sin(q.x * 2.1 + sin(q.y + time)) * 0.5 + 0.5;
    n += sin((q.x + q.y) * 1.7 - time * 0.8) * 0.25;
    return mix(vec3(0.06, 0.06, 0.09), vec3(0.55, 0.58, 0.68), clamp(n, 0.0, 1.0));
}

vec3 snowEffect(vec2 p) {
    vec3 base = vec3(0.72, 0.88, 1.0);
    vec3 flakes = stars(p * 1.4 + vec2(time * 0.03, -time * 0.08)) * 1.4;
    return base + flakes;
}

vec3 stripesEffect(vec2 p) {
    float stripes = smoothstep(0.42, 0.5, abs(fract((p.x + p.y * 0.42 + time * 0.25) * 9.0) - 0.5));
    return mix(color1, color3, stripes);
}

void main() {
    vec4 originalColor = texture(ColorTexture, uv);
    vec4 beforeColor = texture(BeforeColorTexture, uv);
    float currentDepth = texture(DepthTexture, uv).r;
    float beforeDepth = texture(BeforeDepthTexture, uv).r;

    float colorDelta = length(originalColor.rgb - beforeColor.rgb);
    float depthDelta = abs(beforeDepth - currentDepth);
    float colorMask = smoothstep(0.015, 0.08, colorDelta);
    float depthMask = smoothstep(0.00001, 0.0005, depthDelta);
    float handMask = max(colorMask, depthMask);
    if (handMask <= 0.01) {
        discard;
    }

    float contrast = max(1.0, color4.r * 10.0);
    float lighting = color4.g;
    vec4 effectColor = cosmosEffect(resolution, uv * resolution, contrast, lighting);
    if (mode == 1) {
        effectColor = vec4(chromaEffect(uv), 1.0);
    } else if (mode == 2) {
        effectColor = vec4(glassEffect(originalColor, uv), 1.0);
    } else if (mode == 3) {
        effectColor = vec4(glowEffect(uv), 1.0);
    } else if (mode == 4) {
        effectColor = vec4(smokeEffect(uv), 1.0);
    } else if (mode == 5) {
        effectColor = vec4(snowEffect(uv), 1.0);
    } else if (mode == 6) {
        effectColor = vec4(stripesEffect(uv), 1.0);
    }
    vec3 finalColor = mix(originalColor.rgb, effectColor.rgb, 0.88);

    outColor = vec4(finalColor, handMask);
}
