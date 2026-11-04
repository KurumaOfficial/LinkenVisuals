#version 150

#moj_import <linkenvisuals:common.glsl>

in vec2 FragCoord; // normalized fragment coord relative to the primitive
in vec2 TexCoord;
in vec4 FragColor;

uniform sampler2D Sampler0;
uniform vec2 Size; // rectangle size
uniform vec4 Radius; // radius for each vertex
uniform float Smoothness; // edge smoothness
uniform float BlurRadius; // base blur radius in pixels (step)
uniform int Iterations; // number of blur rings to accumulate
uniform vec2 Resolution; // размер текстуры-снапшота в пикселях (замена textureSize — его нет в GLES2/gl4es)

out vec4 OutColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    // textureSize() отсутствует в GLES2 (gl4es) — размер снапшота приходит uniform'ом Resolution.
    vec2 resolution = max(Resolution, vec2(1.0));
    vec2 invResolution = 1.0 / resolution;

    const int MAX_ITERATIONS = 16;
    const int SAMPLES_PER_RING = 8;

    vec3 accum = texture(Sampler0, TexCoord).rgb;
    float weightSum = 1.0;

    float iterations = float(max(1, Iterations));
    float totalRadius = max(BlurRadius * iterations, 0.0001);
    float sigma = max(totalRadius * 0.57735, 0.0001);
    float baseRotation = hash(TexCoord * resolution) * 6.28318530718;

    for (int i = 0; i < MAX_ITERATIONS; ++i) {
        if (i >= Iterations) {
            break;
        }

        float ringRadius = (float(i) + 1.0) * BlurRadius;
        float ringWeight = exp(-0.5 * pow(ringRadius / sigma, 2.0));
        float rotation = baseRotation + (float(i) + 0.5) * 0.78539816339;

        float cosR = cos(rotation);
        float sinR = sin(rotation);
        mat2 rot = mat2(cosR, -sinR, sinR, cosR);

        vec3 ringAccum = vec3(0.0);
        for (int j = 0; j < SAMPLES_PER_RING; ++j) {
            float angle = (float(j) / float(SAMPLES_PER_RING)) * 6.28318530718;
            vec2 dir = vec2(cos(angle), sin(angle));
            vec2 offset = rot * dir * ringRadius * invResolution;
            vec2 sampleCoord = clamp(TexCoord + offset, invResolution * 0.5, vec2(1.0) - invResolution * 0.5);
            ringAccum += texture(Sampler0, sampleCoord).rgb;
        }

        accum += ringAccum * ringWeight;
        weightSum += float(SAMPLES_PER_RING) * ringWeight;
    }

    // Frosted glass: тинт КОМПОЗИТИТСЯ поверх размытой сцены (mix по альфе тинта), маска — SDF.
    // Прежнее умножение vec4(blur,1)*FragColor чернило панель любым тёмным тинтом (rgb ~ 4..30):
    // blur × ~0 → чёрный, «стекло» было невозможно в принципе.
    vec4 color = vec4(mix(accum / weightSum, FragColor.rgb, FragColor.a), 1.0);
    color.a = ralpha(Size, FragCoord, Radius, Smoothness);

    if (color.a <= 0.0) {
        discard;
    }

    OutColor = color;
}
