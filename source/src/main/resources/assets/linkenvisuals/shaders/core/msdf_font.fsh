#version 150

in vec2 TexCoord;
in vec4 FragColor;
in vec4 WorldPosition;

uniform sampler2D Sampler0;
uniform float Range; // distance field range of the msdf font texture
uniform float Thickness; // text thickness
uniform float Smoothness; // edge smoothness
uniform bool Outline; // if false, outline computation will be ignored
uniform float OutlineThickness;
uniform vec4 OutlineColor;

uniform bool Glow;
uniform vec4 GlowColor;
uniform float GlowRadius;

uniform bool Gradient;
uniform vec4 GradientColorStart;
uniform vec4 GradientColorEnd;
uniform vec2 GradientBounds;
uniform float GradientAngle;

out vec4 OutColor;

float median(vec3 color) {
    return max(min(color.r, color.g), min(max(color.r, color.g), color.b));
}

void main() {
    float dist = median(texture(Sampler0, TexCoord).rgb) - 0.5 + Thickness;
    vec2 h = vec2(dFdx(TexCoord.x), dFdy(TexCoord.y)) * textureSize(Sampler0, 0);
    float pixels = Range * inversesqrt(h.x * h.x + h.y * h.y);
    float alpha = smoothstep(-Smoothness, Smoothness, dist * pixels);
    vec4 color = vec4(FragColor.rgb, FragColor.a * alpha);
    
    if (Gradient) {
        float cosAngle = cos(GradientAngle);
        float sinAngle = sin(GradientAngle);
        float gradientPos = WorldPosition.x * sinAngle + WorldPosition.y * cosAngle;

        float t = clamp((gradientPos - GradientBounds.x) / (GradientBounds.y - GradientBounds.x), 0.0, 1.0);
        vec4 gradientColor = mix(GradientColorStart, GradientColorEnd, t);
        color = vec4(gradientColor.rgb, FragColor.a * alpha);
    }

    if (Outline) {
        color = mix(OutlineColor, FragColor, alpha);
        color.a *= smoothstep(-Smoothness, Smoothness, (dist + OutlineThickness) * pixels);
    }

    if (Glow) {
        float glowAlpha = smoothstep(0.0, GlowRadius, (0.5 - abs(dist))) * 0.7;
        vec4 glow = vec4(GlowColor.rgb, glowAlpha * GlowColor.a);
        color.rgb = mix(glow.rgb, color.rgb, color.a);
        color.a = max(color.a, glow.a);
    }
    
    OutColor = color;
}
