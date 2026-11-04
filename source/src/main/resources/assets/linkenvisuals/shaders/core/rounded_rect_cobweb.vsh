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

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec3 Normal;

out vec2 quadCoord;
out vec4 vertexColor;
out float time;
out float aspect;
out float radiusPx;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position.xy, 0.0, 1.0);
    quadCoord = UV0;
    vertexColor = Color;
    time = Normal.x;
    aspect = max(0.001, Normal.y);
    radiusPx = mod(Position.z, 1000.0);
}
