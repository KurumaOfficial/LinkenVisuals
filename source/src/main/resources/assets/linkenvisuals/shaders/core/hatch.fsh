#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec2 quadCoord;
in vec3 params;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec2 pixels = 1.0 / max(fwidth(quadCoord), vec2(0.0001));
    vec2 p = quadCoord * pixels;

    float step = max(params.x, 1.0);
    float thickness = max(params.y, 0.5);

    float phase = mod(p.x + p.y, step);
    float dist = min(phase, step - phase);

    float alpha = 1.0 - smoothstep(0.0, thickness, dist);
    if (alpha <= 0.001) {
        discard;
    }

    vec4 color = vertexColor * ColorModulator;
    fragColor = vec4(color.rgb, color.a * alpha);
}
