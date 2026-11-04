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
    float inset = params.y;

    vec2 grid = floor((p - inset) / step + 0.5) * step + inset;
    float dist = length(p - grid);

    float alpha = 1.0 - smoothstep(0.55, 1.65, dist);
    if (alpha <= 0.001) {
        discard;
    }

    vec4 color = vertexColor * ColorModulator;
    fragColor = vec4(color.rgb, color.a * alpha);
}
