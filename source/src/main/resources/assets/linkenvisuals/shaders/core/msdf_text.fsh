#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

float median3(float red, float green, float blue) {
    return max(min(red, green), min(max(red, green), blue));
}

float screenPixelRange() {
    // Donor: DistanceRange = 10, atlas size from the texture itself.
    vec2 unitRange = vec2(10.0) / vec2(textureSize(Sampler0, 0));
    vec2 screenTextureSize = vec2(1.0) / fwidth(texCoord0);
    return max(0.5 * dot(unitRange, screenTextureSize), 1.0);
}

void main() {
    vec3 sampleValue = texture(Sampler0, texCoord0).rgb;
    float distanceValue = median3(sampleValue.r, sampleValue.g, sampleValue.b);
    float coverage = clamp(screenPixelRange() * (distanceValue - 0.5) + 0.5, 0.0, 1.0);
    vec4 color = vec4(vertexColor.rgb, vertexColor.a * coverage) * ColorModulator;
    if (color.a <= 0.0) {
        discard;
    }
    fragColor = color;
}
