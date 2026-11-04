#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};

in vec2 texCoord;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec2 p = (texCoord - 0.5) * 2.0;
    float dist = clamp(length(p), 0.0, 1.0);
    // smoothstep requires edge0 < edge1; the inverted call was undefined on AMD
    // and collapsed the centre of the halo into a ring.
    float glow = 1.0 - smoothstep(0.0, 1.0, dist);
    glow = glow * glow;
    vec4 color = vec4(vertexColor.rgb, vertexColor.a * glow) * ColorModulator;
    if (color.a <= 0.001) {
        discard;
    }
    fragColor = color;
}
