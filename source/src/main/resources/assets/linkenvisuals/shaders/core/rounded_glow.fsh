#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec2 quadCoord;
in float radiusPx;
in float spreadPx;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec2 pixels = 1.0 / max(fwidth(quadCoord), vec2(0.0001));
    vec2 point = (quadCoord - 0.5) * pixels;
    vec2 halfSize = pixels * 0.5;

    // Radius/spread come from the vertex shader in physical pixels, so the
    // halo keeps the panel's exact rounded-rectangle silhouette at any
    // resolution/GUI scale and never drifts away from the corners.
    float spread = max(spreadPx, 1.0);
    vec2 innerHalf = max(halfSize - spread, vec2(1.0));
    float radius = clamp(radiusPx, 0.0, min(innerHalf.x, innerHalf.y));

    vec2 delta = abs(point) - innerHalf + radius;
    float dist = length(max(delta, 0.0)) + min(max(delta.x, delta.y), 0.0) - radius;

    // Outer halo only: the card/panel fill must not be tinted by the glow.
    if (dist <= 0.0) {
        discard;
    }
    float glow = 1.0 - smoothstep(0.0, spread, dist);
    if (glow <= 0.001) {
        discard;
    }

    vec4 color = vertexColor * ColorModulator;
    fragColor = vec4(color.rgb, color.a * glow);
}
