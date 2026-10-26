#version 330

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in vec2 texCoord;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 tex = texture(Sampler0, texCoord);
    float luma = max(tex.r, max(tex.g, tex.b));
    if (luma < 0.02 || tex.a < 0.01) {
        discard;
    }
    vec4 color = tex * vertexColor * ColorModulator;
    if (color.a < 0.01) {
        discard;
    }
    fragColor = color;
}
