#version 150

in vec2 TexCoord;
in vec4 VertColor;

uniform float Time;
uniform vec2 Resolution;
uniform int Mode;
uniform float Alpha;

out vec4 OutColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 5; i++) {
        v += a * noise(p);
        p = mat2(0.8, 0.6, -0.6, 0.8) * p * 2.0 + vec2(4.2, 7.1);
        a *= 0.5;
    }
    return v;
}

vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 0.6666667, 0.3333333, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

vec3 cosmos(vec2 uv) {
    vec2 p = uv * 2.0 - 1.0;
    float r = length(p);
    float a = atan(p.y, p.x);
    float arms = sin(a * 3.0 + r * 10.0 - Time * 1.15) * 0.5 + 0.5;
    float dust = fbm(p * 3.2 + vec2(Time * 0.08, -Time * 0.04));
    vec3 deep = vec3(0.025, 0.01, 0.11);
    vec3 glow = mix(vec3(0.22, 0.04, 0.8), vec3(0.0, 0.8, 1.0), arms);
    vec3 pink = vec3(1.0, 0.22, 0.75) * pow(dust, 2.0);
    return deep + glow * dust * (1.25 - r * 0.35) + pink * 0.55;
}

vec3 stars(vec2 uv) {
    vec2 grid = uv * 16.0;
    vec2 id = floor(grid);
    vec2 local = fract(grid) - 0.5;
    float rnd = hash(id);
    vec3 col = vec3(0.02, 0.03, 0.12);
    if (rnd > 0.62) {
        vec2 pos = vec2(hash(id + vec2(1.0, 0.0)), hash(id + vec2(0.0, 1.0))) - 0.5;
        float d = length(local - pos * 0.75);
        float twinkle = sin(Time * 3.0 + rnd * 6.28318) * 0.35 + 0.75;
        col += vec3(1.0, 0.94, 0.72) * exp(-d * d * 440.0) * twinkle;
    }
    return col;
}

vec3 effect(vec2 uv) {
    if (Mode == 1) {
        return hsv2rgb(vec3(uv.x * 0.55 + uv.y * 0.25 + Time * 0.12, 0.85, 1.0));
    }
    if (Mode == 2) {
        return mix(vec3(0.45, 0.9, 1.0), vec3(1.0), smoothstep(0.2, 1.25, length(uv * 2.0 - 1.0)));
    }
    if (Mode == 3) {
        return vec3(0.45, 0.16, 0.95) * (1.0 + sin(Time * 3.0 + uv.y * 12.0) * 0.35) + vec3(0.0, 0.78, 1.0) * 0.5;
    }
    if (Mode == 4) {
        float n = fbm(uv * 5.0 + vec2(Time * 0.1, -Time * 0.07));
        return mix(vec3(0.05, 0.05, 0.08), vec3(0.58, 0.6, 0.72), n);
    }
    if (Mode == 5) {
        return vec3(0.65, 0.82, 1.0) + stars(uv + vec2(Time * 0.02, -Time * 0.07)) * 1.4;
    }
    if (Mode == 6) {
        float stripe = smoothstep(0.42, 0.5, abs(fract((uv.x + uv.y * 0.45 + Time * 0.22) * 9.0) - 0.5));
        return mix(vec3(0.08, 0.03, 0.24), vec3(0.0, 0.8, 1.0), stripe);
    }
    return cosmos(uv) + stars(uv) * 0.9;
}

void main() {
    vec2 p = TexCoord * 2.0 - 1.0;
    float mask = smoothstep(1.25, 0.55, length(p));
    vec3 color = clamp(effect(TexCoord), 0.0, 1.0);
    OutColor = vec4(color, Alpha * VertColor.a * mask);
}
