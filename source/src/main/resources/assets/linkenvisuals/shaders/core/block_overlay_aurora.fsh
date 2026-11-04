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

float fbm(vec2 p) {
    float v = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 5; i++) {
        v += amp * noise(p);
        p *= 2.0;
        amp *= 0.5;
    }
    return v;
}

void main() {
    // Координаты грани блока 0..1, с компенсацией зума относительно центра.
    vec2 uv = (texCoord - 0.5) / zoomFactor + 0.5;
    float t = time;

    // Ночное небо.
    vec3 color = vec3(0.012, 0.02, 0.045);

    // Несколько колышущихся занавесей северного сияния.
    float aurora = 0.0;
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        float wave = fbm(vec2(uv.x * 3.0 + fi * 2.3, t * 0.25 + fi));
        float band = 0.30 + 0.22 * fi + 0.12 * sin(t * 0.4 + fi * 1.7) + 0.20 * (wave - 0.5);
        float d = abs(uv.y - band);
        // Вертикальные лучи внутри занавеси.
        float streak = 0.55 + 0.45 * noise(vec2(uv.x * 42.0 + fi * 10.0, t * 0.6));
        float intensity = exp(-d * d * 55.0) * streak;
        aurora += intensity * (0.75 - 0.18 * fi);
    }

    // Палитра: низ — выбранный цвет, верх — холодный фиолетовый оттенок.
    vec3 lo = baseColor.rgb;
    vec3 hi = mix(baseColor.rgb, vec3(0.40, 0.20, 0.90), 0.6);
    vec3 auroraColor = mix(lo, hi, clamp(uv.y, 0.0, 1.0));
    color += auroraColor * aurora * 1.7;

    // Редкие звёзды на фоне.
    vec2 sUV = uv * vec2(screenSize.x / max(1.0, screenSize.y), 1.0) * 20.0;
    vec2 sid = floor(sUV);
    float star = hash(sid);
    if (star > 0.95) {
        float tw = 0.5 + 0.5 * sin(t * 3.0 + star * 30.0);
        float dd = length(fract(sUV) - 0.5);
        color += vec3(0.9, 0.95, 1.0) * exp(-dd * dd * 120.0) * tw * 0.6;
    }

    color = clamp(color, 0.0, 2.0);
    float finalAlpha = alpha * (0.35 + length(color) * 0.25);
    fragColor = vec4(color, finalAlpha);
}
