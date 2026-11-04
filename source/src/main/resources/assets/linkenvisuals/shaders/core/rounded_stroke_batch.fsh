#version 150
#moj_import <linkenvisuals:common.glsl>

in vec2 FragCoord;
flat in vec2 StrokeSize;
in vec4 FragColor;
out vec4 OutColor;

void main() {
    float radius = min(StrokeSize.x, StrokeSize.y) * 0.5;
    float alpha = ralpha(StrokeSize, FragCoord, vec4(radius), 1.0);
    vec4 color = vec4(FragColor.rgb, FragColor.a * alpha);
    if (color.a == 0.0) discard;
    OutColor = color;
}
