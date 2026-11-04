#version 330

in vec2 uv;
out vec4 fragColor;

layout(std140) uniform BlurData {
    vec4 params; // direction.xy, texelSize.xy
    vec4 gauss;  // g0, g1, g2, support
    vec4 flags;  // x = linear sampling, y = keep alpha
};

uniform sampler2D Sampler0;

void main() {
    vec3 gaussian = gauss.xyz;
    vec4 color = texture(Sampler0, uv) * gaussian.x;
    float sum = gaussian.x;
    vec2 step = params.zw * params.xy;
    int support = int(gauss.w);

    if (flags.x > 0.5) {
        // Donor linear-sampling path: pair two taps into one bilinear fetch.
        for (int i = 1; i <= support; i += 2) {
            gaussian.xy *= gaussian.yz;
            float w1 = gaussian.x;
            gaussian.xy *= gaussian.yz;
            float w2 = gaussian.x;
            float w = w1 + w2;
            if (w <= 0.0) {
                continue;
            }
            vec2 offset = step * ((i * w1 + (i + 1.0) * w2) / w);
            color += texture(Sampler0, uv + offset) * w;
            color += texture(Sampler0, uv - offset) * w;
            sum += w * 2.0;
        }
    } else {
        for (int i = 1; i <= support; i++) {
            gaussian.xy *= gaussian.yz;
            vec2 offset = step * float(i);
            color += texture(Sampler0, uv + offset) * gaussian.x;
            color += texture(Sampler0, uv - offset) * gaussian.x;
            sum += gaussian.x * 2.0;
        }
    }

    color /= max(sum, 0.00001);
    if (flags.y < 0.5) {
        color.a = 1.0;
    }
    fragColor = color;
}
