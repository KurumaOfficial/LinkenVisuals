#version 150

in vec2 FragCoord;

uniform vec2 Size;
uniform vec4 Round;
uniform vec4 Color1;
uniform vec4 Color2;
uniform vec4 Color3;
uniform vec4 Color4;
uniform float OutlineWidth;

out vec4 OutColor;

float dstfn(vec2 p, vec2 b, vec4 r) {
    p = abs(p) - b;
    vec2 q = p + r.xy;
    float k = max(r.x, r.y);
    return length(max(q, 0.0)) - k;
}

void main() {
    vec2 pixel = FragCoord * Size;
    vec2 centre = 0.5 * Size;
    vec2 halfSize = Size / 2.0 - vec2(OutlineWidth / 2.0);
    float d = dstfn(centre - pixel, halfSize - vec2(OutlineWidth / 2.0), Round);

    vec4 color = mix(mix(Color1, Color2, FragCoord.x),
                     mix(Color3, Color4, FragCoord.x),
                     FragCoord.y);

    float sa = smoothstep(0.0, OutlineWidth / 2.0, abs(d) - OutlineWidth / 2.0 / 2.0);
    vec4 c = mix(vec4(color.rgb, 1.0), vec4(color.rgb, 0.0), sa);
    OutColor = vec4(c.rgb, color.a * c.a);

    if (OutColor.a <= 0.001) {
        discard;
    }
}
