#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec2 quadCoord;
in vec4 vertexColor;
in float radiusPx;
in vec3 borderParams;

out vec4 fragColor;

void main() {
    vec2 pixels = 1.0 / max(fwidth(quadCoord), vec2(0.0001));
    vec2 point = (quadCoord - 0.5) * pixels;
    vec2 halfSize = pixels * 0.5;
    float radius = min(radiusPx, min(halfSize.x, halfSize.y));

    vec2 delta = abs(point) - halfSize + radius;
    float dist = length(max(delta, 0.0)) + min(max(delta.x, delta.y), 0.0) - radius;

    float thickness = max(borderParams.x, 0.0);
    float internalSmooth = max(borderParams.y, 0.01);
    float externalSmooth = max(borderParams.z, 0.01);

    // Donor border.fsh: rising inner smoothstep * falling outer smoothstep (no hard hole).
    float inner = smoothstep(-thickness - internalSmooth - externalSmooth,
                             -thickness - externalSmooth, dist);
    float outer = 1.0 - smoothstep(-externalSmooth, 0.0, dist);
    float ring = clamp(inner * outer, 0.0, 1.0);
    if (ring <= 0.001) {
        discard;
    }

    vec4 color = vertexColor * ColorModulator;
    fragColor = vec4(color.rgb, color.a * ring);
}
