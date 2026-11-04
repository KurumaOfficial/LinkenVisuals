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
out float radiusPx;
out float spreadPx;
out vec4 vertexColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position.xy, 0.0, 1.0);
    quadCoord = UV0;
    // Position.z carries the exact pixel radius (a full float, ignored as
    // depth by this 2D pass). The normal only carries the spread ratio: 8 bits
    // is plenty for the soft halo width, but not for the corner radius on wide
    // panels (1/127 quantisation used to shift the halo off the corners).
    radiusPx = Position.z;
    spreadPx = Normal.x * 96.0;
    vertexColor = Color;
}
