#version 150

in vec2 TexCoord;
in vec3 LocalPosition;
in vec4 VertColor;

uniform float Time;
uniform float Alpha;
uniform int Mode;
uniform vec3 BaseColor;

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
        p = mat2(0.8, 0.6, -0.6, 0.8) * p * 2.0 + vec2(7.2, 3.4);
        a *= 0.5;
    }
    return v;
}

vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 0.6666667, 0.3333333, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

vec3 plasma(vec2 uv) {
    vec2 p = uv * 10.0;
    float v = 0.0;
    v += sin(p.x + Time);
    v += sin((p.y + Time) * 0.5);
    v += sin((p.x + p.y + Time) * 0.5);
    vec2 c = p - vec2(5.0 + sin(Time * 0.5) * 3.0, 5.0 + cos(Time * 0.7) * 3.0);
    v += sin(length(c) + Time * 1.5);
    float hue = v * 0.08 + Time * 0.04;
    return hsv2rgb(vec3(hue, 0.85, 1.0));
}

vec3 cobweb(vec2 uv) {
    vec2 p = uv * 2.0 - 1.0;
    float angle = atan(p.y, p.x);
    float radius = length(p);
    float radial = smoothstep(0.025, 0.0, abs(fract(radius * 7.0 - Time * 0.12) - 0.5));
    float spokes = smoothstep(0.035, 0.0, abs(fract((angle + 3.14159) / 6.28318 * 14.0) - 0.5));
    float web = clamp(radial + spokes * (1.0 - radius * 0.35), 0.0, 1.0);
    return mix(vec3(0.1, 0.14, 0.18), vec3(0.9, 0.96, 1.0), web);
}

vec3 fireworks(vec2 uv) {
    vec2 p = uv * 2.0 - 1.0;
    vec3 color = vec3(0.02, 0.01, 0.05);
    for (int i = 0; i < 5; i++) {
        float fi = float(i);
        vec2 center = vec2(sin(fi * 4.1 + Time * 0.6), cos(fi * 3.3 + Time * 0.5)) * 0.45;
        vec2 d = p - center;
        float ray = pow(max(0.0, cos(atan(d.y, d.x) * 14.0 + Time * (1.5 + fi))), 18.0);
        float burst = exp(-length(d) * 5.0) * ray;
        color += hsv2rgb(vec3(fi * 0.17 + Time * 0.03, 0.8, 1.0)) * burst;
    }
    return clamp(color, 0.0, 1.4);
}

vec3 galaxy(vec2 uv) {
    vec2 p = uv * 2.0 - 1.0;
    float r = length(p);
    float a = atan(p.y, p.x);
    float arms = sin(a * 3.0 + r * 10.0 - Time * 0.7) * 0.5 + 0.5;
    float dust = fbm(p * 3.0 + Time * 0.08);
    vec3 deep = vec3(0.03, 0.02, 0.16);
    vec3 glow = mix(vec3(0.25, 0.08, 0.75), vec3(1.0, 0.42, 0.9), arms);
    float core = exp(-r * 2.5);
    return deep + glow * dust * (1.0 - r * 0.35) + vec3(1.0, 0.78, 0.55) * core * 0.8;
}

vec3 nebula(vec2 uv) {
    vec2 p = (uv * 2.0 - 1.0) * 2.8;
    float f = fbm(p + vec2(Time * 0.08, -Time * 0.05));
    vec3 c1 = BaseColor * 1.25;
    vec3 c2 = vec3(0.06, 0.18, 0.95);
    vec3 c3 = vec3(0.9, 0.12, 0.8);
    return mix(c2, c3, f) * (0.55 + f) + c1 * pow(f, 2.0) * 0.45;
}

vec3 stars(vec2 uv, bool field) {
    vec2 p = uv * (field ? 12.0 : 8.0);
    vec2 id = floor(p);
    vec2 local = fract(p) - 0.5;
    float rnd = hash(id);
    vec3 color = field ? vec3(0.01, 0.025, 0.09) : vec3(0.04, 0.03, 0.12);
    if (rnd > (field ? 0.58 : 0.68)) {
        vec2 starPos = vec2(hash(id + vec2(1.0, 0.0)), hash(id + vec2(0.0, 1.0))) - 0.5;
        float dist = length(local - starPos * 0.7);
        float twinkle = sin(Time * 2.0 + rnd * 6.28318) * 0.35 + 0.75;
        float star = exp(-dist * dist * (field ? 520.0 : 320.0)) * twinkle;
        color += mix(vec3(1.0), BaseColor, field ? 0.25 : 0.55) * star;
    }
    color += BaseColor * exp(-length(uv * 2.0 - 1.0) * 1.2) * 0.12;
    return color;
}

void main() {
    vec2 uv = TexCoord;
    vec3 color;

    if (Mode == 1) {
        color = cobweb(uv);
    } else if (Mode == 2) {
        color = fireworks(uv);
    } else if (Mode == 3) {
        color = galaxy(uv);
    } else if (Mode == 4) {
        color = nebula(uv);
    } else if (Mode == 5) {
        color = stars(uv, true);
    } else if (Mode == 6) {
        color = stars(uv, false);
    } else {
        color = plasma(uv);
    }

    float edge = smoothstep(0.0, 0.06, uv.x) * smoothstep(0.0, 0.06, uv.y)
            * smoothstep(0.0, 0.06, 1.0 - uv.x) * smoothstep(0.0, 0.06, 1.0 - uv.y);
    float alpha = Alpha * VertColor.a * (0.35 + edge * 0.65);
    OutColor = vec4(clamp(color, 0.0, 1.0), alpha);
}
