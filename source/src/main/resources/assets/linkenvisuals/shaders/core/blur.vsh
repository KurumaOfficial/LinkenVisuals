#version 150

#moj_import <linkenvisuals:common.glsl>

in vec3 Position; // POSITION_COLOR vertex attributes
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 FragCoord;
out vec2 TexCoord;
out vec4 FragColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    FragCoord = rvertexcoord(gl_VertexID);
    TexCoord = (gl_Position.xy / gl_Position.w) * 0.5 + 0.5;
    FragColor = Color;
}
