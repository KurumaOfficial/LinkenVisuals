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




vec3 createVerticalGradient(vec2 coord, vec3 color1, vec3 color2, float t) {
    float factor = coord.y + t;

    factor = sin(factor * 3.14159 * 2.0) * 0.5 + 0.5;

    return mix(color1, color2, factor);
}

void main() {
    vec4 originalColor = texture(ColorTexture, uv);
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

    vec3 gradientColor = createVerticalGradient(uv, customColor1, customColor2, time);

    float pulse = 0.85 + sin(time * 6.28318) * 0.15;
    gradientColor *= pulse;

    vec4 effectColor = vec4(gradientColor, 1.0);

    vec4 finalHandColor = mix(originalColor, effectColor, effectAlpha);

    outColor = vec4(finalHandColor.rgb, mask);
}

