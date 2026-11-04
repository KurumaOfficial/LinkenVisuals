#version 330

in vec2 texCoord;
out vec4 fragColor;

layout(std140) uniform BlockOverlay {
    vec4 uParams;
    vec4 uScreen;
    vec4 uColor;
};

#define time (uParams.x)
#define alpha (uParams.y)
#define zoomFactor (max(0.001, uParams.z))
#define screenSize (uScreen.xy)
#define baseColor (uColor)

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float hash13(vec3 p3) {
    p3 = fract(p3 * 0.1031);
    p3 += dot(p3, p3.zyx + 31.32);
    return fract((p3.x + p3.y) * p3.z);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(vec2 p, float t) {
    float v = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 5; i++) {
        v += amp * noise(p + t * 0.05);
        p *= 2.0;
        amp *= 0.5;
    }
    return v;
}

// Слой звёзд с мерцанием.
vec3 starLayer(vec2 uv, float t, float density, float bright) {
    vec3 col = vec3(0.0);
    vec2 g = uv * density;
    vec2 id = floor(g);
    vec2 lo = fract(g) - 0.5;
    for (float y = -1.0; y <= 1.0; y++) {
        for (float x = -1.0; x <= 1.0; x++) {
            vec2 nb = vec2(x, y);
            vec2 cid = id + nb;
            float r = hash13(vec3(cid, 1.0));
            if (r < 0.55) continue;
            vec2 sp = nb + vec2(hash(cid + vec2(1.0, 0.0)) - 0.5, hash(cid + vec2(0.0, 1.0)) - 0.5) * 0.85;
            float d = length(lo - sp);
            float sz = 0.012 + hash(cid + vec2(2.0, 3.0)) * 0.02;
            float tw = 0.6 + 0.4 * sin(t * 2.5 + r * 6.28318);
            float s = exp(-d * d / (sz * sz)) * tw;
            vec3 sc = mix(vec3(1.0, 0.92, 0.82), vec3(0.8, 0.9, 1.0), r);
            col += sc * s * bright;
        }
    }
    return col;
}

void main() {
    vec2 uv = texCoord * 2.0 - 1.0;
    uv /= zoomFactor;
    float t = time;

    // Глубокий космос.
    vec3 color = vec3(0.01, 0.01, 0.025);

    // Туманные облака, окрашенные выбранным цветом.
    float n1 = fbm(uv * 2.2, t * 0.3);
    float n2 = fbm(uv * 4.0 + vec2(7.0, 3.0), t * 0.2);
    float clouds = pow(n1 * 0.6 + n2 * 0.4, 1.6);
    vec3 neb = mix(baseColor.rgb, vec3(0.25, 0.35, 0.90), 0.5);
    neb = mix(neb, vec3(0.90, 0.30, 0.60), pow(clouds, 2.0));
    color += neb * clouds * 0.9;

    // Параллакс-звёзды (два слоя).
    color += starLayer(uv + vec2(t * 0.01, 0.0), t, 8.0, 0.7);
    color += starLayer(uv * 1.7 + vec2(t * 0.02, t * 0.005), t, 16.0, 0.5);

    // Лёгкое свечение к центру от выбранного цвета.
    float dist = length(uv);
    color += baseColor.rgb * exp(-dist * 1.2) * 0.12;

    color = clamp(color, 0.0, 2.0);
    float finalAlpha = alpha * (0.4 + length(color) * 0.22);
    fragColor = vec4(color, finalAlpha);
}
