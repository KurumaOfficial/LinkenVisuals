package ru.method.linkenvisuals.cosmetics.bedrock;

public record BedrockCube(
        float originX,
        float originY,
        float originZ,
        float sizeX,
        float sizeY,
        float sizeZ,
        float pivotX,
        float pivotY,
        float pivotZ,
        float rotationX,
        float rotationY,
        float rotationZ,
        boolean mirror,
        BedrockFaceUv north,
        BedrockFaceUv east,
        BedrockFaceUv south,
        BedrockFaceUv west,
        BedrockFaceUv up,
        BedrockFaceUv down
) {
}
