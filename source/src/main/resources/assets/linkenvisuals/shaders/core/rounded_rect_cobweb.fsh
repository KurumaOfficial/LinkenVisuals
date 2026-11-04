#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec2 quadCoord;
in vec4 vertexColor;
in float aspect;
in float radiusPx;

uniform sampler2D Sampler0;

out vec4 fragColor;

const vec3 eps = vec3(0.001, 0., 0.);
const int iter = 64;
const float sq = 0.70710678; // sqrt(2.0) * 0.5

float c(vec3 p) {
    vec3 q = abs(mod(p + vec3(cos(p.z * 0.5), cos(p.x * 0.5), cos(p.y * 0.5)), 2.0) - 1.0);
    float a = q.x + q.y + q.z - min(min(q.x, q.y), q.z) - max(max(q.x, q.y), q.z);
    q = vec3(p.x + p.y, p.y + p.z, p.z + p.x) * sq;
    q = abs(mod(q, 2.0) - 1.0);
    float b = q.x + q.y + q.z - min(min(q.x, q.y), q.z) - max(max(q.x, q.y), q.z);
    return min(a, b);
}

vec3 n(vec3 p) {
    float o = c(p);
    return normalize(o - vec3(c(p - eps), c(p - eps.zxy), c(p - eps.yzx)));
}

void main() {
    float time = texture(Sampler0, vec2(0.5, 0.5)).r * 12.0;

    // Rounded rect coverage, identical to rounded_rect.fsh.
    vec2 pixels = 1.0 / max(fwidth(quadCoord), vec2(0.0001));
    vec2 point = (quadCoord - 0.5) * pixels;
    vec2 half_size = pixels * 0.5;
    float radius = min(radiusPx, min(half_size.x, half_size.y));

    vec2 delta = abs(point) - half_size + radius;
    float dist = length(max(delta, 0.0)) + min(max(delta.x, delta.y), 0.0) - radius;

    float coverage = 1.0 - smoothstep(-1.0, 1.0, dist);
    if (coverage <= 0.001) {
        discard;
    }

    // The donor panel base is always rgb(20,23,30); the vertex colour carries the theme accent
    // used by the cobweb (the donor passed ColorUtils.getPrimaryColor() to the shader).
    float panelAlpha = vertexColor.a;
    vec3 accent = vertexColor.rgb;
    vec4 base = vec4(20.0 / 255.0, 23.0 / 255.0, 30.0 / 255.0, panelAlpha) * ColorModulator;

    vec2 p = quadCoord * 2. - 1.;
    vec2 m = vec2(sin(time * 0.45) * 0.3, cos(time * 0.32) * 0.3);
    p.x *= aspect;
    m.x *= aspect;

    vec3 o = vec3(0., 0., time * 0.7);
    vec3 s = vec3(m, 0.);
    vec3 b = vec3(0., 0., 0.);
    vec3 d = vec3(p, 1.) / 32.;
    vec3 t = vec3(.5);
    vec3 a;

    for (int i = 0; i < iter; ++i) {
        float h = c(b + s + o);
        b += h * 10.0 * d;
        t += h;
    }
    t /= float(iter);
    a = n(b + s + o);
    float x = dot(a, t);

    vec3 diffuse = accent;
    t = (t + pow(x, 4.)) * (1. - t * .01) * diffuse;
    t *= b.z * 0.125;

    vec2 vig = p * 0.43;
    vig.y *= aspect;
    float vig_amount = 1.0 - length(vig) * 0.5;

    vec3 web = t * 3.6 * vig_amount;
    float webAlpha = clamp(panelAlpha * 0.30, 0.0, 1.0);

    vec3 rgb = mix(base.rgb, web, webAlpha);
    fragColor = vec4(rgb, base.a * coverage);
}
