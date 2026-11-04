package ru.method.linkenvisuals.cosmetics.bedrock;

public record BedrockFaceUv(float u, float v, float width, float height) {
    public static final BedrockFaceUv EMPTY = new BedrockFaceUv(0f, 0f, 0f, 0f);

    public boolean isEmpty() {
        return width == 0f && height == 0f;
    }
}
