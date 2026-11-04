#version 150
#moj_import <linkenvisuals:common.glsl>

in vec3 Position;
in vec2 UV0;
in vec4 Color;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
out vec2 FragCoord;
flat out vec2 StrokeSize;
out vec4 FragColor;

void main() {
    FragCoord = rvertexcoord(gl_VertexID);
    StrokeSize = UV0;
    FragColor = Color;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
