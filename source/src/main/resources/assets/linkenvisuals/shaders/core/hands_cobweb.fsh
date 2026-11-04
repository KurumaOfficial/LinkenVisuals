#version 330

in vec2 uv;
out vec4 outColor;

uniform sampler2D ColorTexture;
uniform sampler2D DepthTexture;
uniform sampler2D BeforeColorTexture;

layout(std140) uniform HandsData {
    vec4 colorA;
    vec4 colorB;
    vec4 params0;
    vec4 params1;
    vec4 screenSize;
    vec4 flags;
};

#define time (params0.x)
#define blurSize (params0.y)
#define quality (params0.z)
#define direction (params0.w)
#define refraction (params1.x)
#define brightness (params1.y)
#define backgroundBlur (params1.z)
#define resolution (screenSize.xy)
#define customColor1 (colorA.rgb)
#define customColor2 (colorB.rgb)
#define effectAlpha (colorA.w)
#define enableChromatic (flags.x > 0.5)
#define enableDistortion (flags.y > 0.5)
#define hideHand (flags.z > 0.5)
#define fillGlow (flags.w > 0.5)



const vec3 eps = vec3(0.001, 0., 0.);
const int iter = 64;
const float sq = 0.70710678;

float c(vec3 p) {
    vec3 q = abs(mod(p + vec3(cos(p.z*0.5), cos(p.x*0.5), cos(p.y*0.5)), 2.0) - 1.0);
    float a = q.x + q.y + q.z - min(min(q.x, q.y), q.z) - max(max(q.x, q.y), q.z);
    q = vec3(p.x+p.y, p.y+p.z, p.z+p.x) * sq;
    q = abs(mod(q, 2.0) - 1.0);
    float b = q.x + q.y + q.z - min(min(q.x, q.y), q.z) - max(max(q.x, q.y), q.z);
    return min(a, b);
}

vec3 n(vec3 p) {
    float o = c(p);
    return normalize(o - vec3(c(p - eps), c(p - eps.zxy), c(p - eps.yzx)));
}

void main() {
    vec2 texelSize = 1.0 / textureSize(DepthTexture, 0);

    float minDepth = 1.0;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 offset = vec2(x, y) * texelSize;
            float neighborDepth = texture(DepthTexture, uv + offset).r;
            minDepth = min(minDepth, neighborDepth);
        }
    }

    float depthMask = 1.0 - smoothstep(0.98, 0.995, minDepth);
    float colorMask = smoothstep(0.015, 0.08, length(texture(ColorTexture, uv).rgb - texture(BeforeColorTexture, uv).rgb));
    float mask = colorMask;
    if (mask < 0.01) {
        discard;
    }

    vec2 p = uv * 2.0 - 1.0;
    vec2 m = vec2(sin(time * 0.3) * 0.3, cos(time * 0.2) * 0.3);

    vec3 o = vec3(0., 0., time * 0.5);
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

    vec3 diffuse = mix(customColor1, customColor2, 0.5);
    t = (t + pow(x, 4.)) * (1. - t * .01) * diffuse;
    t *= b.z * 0.125;

    vec2 vig = p * 0.43;
    float vig_amount = 1.0 - length(vig) * 0.5;

    vec3 color = t * 3.0 * vig_amount;

    outColor = vec4(color, mask);
}

