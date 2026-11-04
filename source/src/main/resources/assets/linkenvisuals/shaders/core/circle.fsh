#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec2 p = texCoord0 - 0.5;
    float dist = length(p) - 0.49;
    // 1 px of coverage in *physical* framebuffer pixels. The previous 0.55
    // produced a sub-pixel edge on HiDPI/GUI-scale-2 displays, which read as a
    // hard, pixelated circle outline.
    float aa = max(fwidth(dist), 0.0008) * 1.0;
    float coverage = 1.0 - smoothstep(-aa, aa, dist);
    if (coverage <= 0.001) {
        discard;
    }

    vec4 base = texture(Sampler0, texCoord0) * vertexColor;
    fragColor = vec4(base.rgb, base.a * coverage) * ColorModulator;
}
