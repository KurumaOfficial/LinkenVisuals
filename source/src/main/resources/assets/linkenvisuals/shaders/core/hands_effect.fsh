#version 330

in vec2 uv;
out vec4 fragColor;

layout(std140) uniform HandsData {
    vec4 colorA;      // own colour #1 / plasma base, alpha
    vec4 colorB;      // own colour #2 / secondary, alpha
    vec4 shaderColor; // theme shader colour, alpha
    vec4 params;      // time, mode, blurStrength, gradientSpeed
    vec4 resolution;  // width, height, farPlane, nearPlane
};

uniform sampler2D ColorTexture;
uniform sampler2D DepthTexture;

vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

void main() {
    vec2 texel = 1.0 / max(resolution.xy, vec2(1.0));

    // The hand was rendered into the main depth buffer just before this pass.
    // Background depth is 1.0, hand depth is well below 0.99. The donor used a
    // reversed smoothstep(0.99, 0.98) here, whose result is undefined by the
    // GLSL spec; some drivers returned 1.0 everywhere and tinted the whole
    // frame black. Use the equivalent, well-defined ascending form.
    float minDepth = 1.0;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            minDepth = min(minDepth, texture(DepthTexture, uv + vec2(x, y) * texel).r);
        }
    }
    float mask = 1.0 - smoothstep(0.98, 0.995, minDepth);
    if (mask <= 0.01) {
        discard;
    }

    float time = params.x;
    int mode = int(params.y + 0.5);
    float speed = max(0.05, params.w);
    vec3 color = colorA.rgb;
    float alpha = max(colorA.a, colorB.a);

    if (mode == 0) {
        // Own colour: vertical animated gradient.
        float g = sin((uv.y * 1.4 + time * speed * 2.0) * 6.28318) * 0.5 + 0.5;
        color = mix(colorA.rgb, colorB.rgb, g);
        float brightness = 0.85 + sin(time * 6.28318) * 0.15;
        color *= brightness;
        alpha = max(colorA.a, colorB.a);
    } else if (mode == 1) {
        // Transparent glass: blurred scene + a soft theme tint.
        vec3 blurred = vec3(0.0);
        float total = 0.0;
        for (int y = -3; y <= 3; y++) {
            for (int x = -3; x <= 3; x++) {
                float weight = 1.0 / (1.0 + float(x * x + y * y));
                blurred += texture(ColorTexture, uv + vec2(x, y) * texel * 2.0).rgb * weight;
                total += weight;
            }
        }
        blurred /= max(total, 0.0001);
        color = mix(blurred, shaderColor.rgb, 0.14) * 1.06 + 0.02;
        alpha = 1.0;
    } else if (mode == 2) {
        // Plasma.
        float v = sin(uv.x * 10.0 + time * 2.0)
            + sin(uv.y * 8.0 + time * 1.3)
            + sin((uv.x + uv.y) * 12.0 + time * 1.8);
        color = mix(shaderColor.rgb, colorA.rgb, 0.5 + 0.5 * sin(v * 3.14159));
        alpha = 1.0;
    } else if (mode == 3) {
        // Nebula: soft layered interference.
        float n = sin(uv.x * 6.0 + time * 0.7) * cos(uv.y * 5.0 - time * 0.9)
            + sin((uv.x - uv.y) * 9.0 + time * 1.2) * 0.5;
        color = mix(shaderColor.rgb, colorB.rgb, 0.5 + 0.5 * n);
        color = mix(color, hsv2rgb(vec3(fract(time * 0.03 + uv.y * 0.2), 0.7, 1.0)), 0.25);
        alpha = 1.0;
    } else {
        // Cobweb: radial web lines.
        vec2 p = uv - 0.5;
        float angle = atan(p.y, p.x);
        float radius = length(p);
        float web = abs(sin(angle * 8.0 + time * 0.8)) * smoothstep(0.5, 0.05, radius);
        color = mix(shaderColor.rgb, colorA.rgb, clamp(web * 0.7, 0.0, 1.0));
        alpha = 1.0;
    }

    fragColor = vec4(color, mask * alpha);
}
