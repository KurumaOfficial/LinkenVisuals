#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec2 quadCoord;
in vec2 params;
in vec4 vertexColor;

out vec4 fragColor;

float dstfn(vec2 p, vec2 b, float r) {
    p = abs(p) - b;
    vec2 q = p + vec2(r);
    float k = r;
    return length(max(q, 0.0)) - k;
}

void main() {
    // Donor dw_rounded_outline.fsh, expressed in screen pixels. The quad UVs
    // give the exact pixel size, so the soft stroke keeps its design width on
    // every GUI scale.
    vec2 pixels = 1.0 / max(fwidth(quadCoord), vec2(0.0001));
    vec2 pixel = quadCoord * pixels;
    vec2 centre = 0.5 * pixels;
    float outline = max(params.y, 0.01);
    float radius = params.x;
    vec2 halfSize = pixels / 2.0 - vec2(outline / 2.0);
    float d = dstfn(centre - pixel, halfSize - vec2(outline / 2.0), radius);

    vec4 color = vertexColor;
    float sa = smoothstep(0.0, outline / 2.0, abs(d) - outline / 2.0 / 2.0);
    vec4 c = mix(vec4(color.rgb, 1.0), vec4(color.rgb, 0.0), sa);
    vec4 outColor = vec4(c.rgb, color.a * c.a) * ColorModulator;
    if (outColor.a <= 0.001) {
        discard;
    }
    fragColor = outColor;
}
