#version 150

#moj_import <linkenvisuals:common.glsl>

in vec3 Position; // POSITION_TEXTURE_COLOR
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 FragCoord; // 0..1 внутри карточки — для скруглённых углов
out vec2 TexCoord;  // 0..1 внутри грани плаща
out vec4 FragColor;

void main() {
    FragCoord = rvertexcoord(gl_VertexID);
    TexCoord = UV0;
    FragColor = Color;

    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
