#version 150

in vec2 TexCoord;

uniform sampler2D Sampler0;
uniform vec2 TexelSize;
uniform vec2 Direction;

out vec4 OutColor;

void main() {
    vec2 step = TexelSize * Direction;
    vec3 color = texture(Sampler0, TexCoord).rgb * 0.2270270270;
    color += texture(Sampler0, TexCoord + step).rgb * 0.1945945946;
    color += texture(Sampler0, TexCoord - step).rgb * 0.1945945946;
    color += texture(Sampler0, TexCoord + step * 2.0).rgb * 0.1216216216;
    color += texture(Sampler0, TexCoord - step * 2.0).rgb * 0.1216216216;
    OutColor = vec4(color, 1.0);
}
