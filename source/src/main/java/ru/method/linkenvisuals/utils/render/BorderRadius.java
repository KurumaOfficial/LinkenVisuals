package ru.method.linkenvisuals.utils.render;

public record BorderRadius(float topLeftRadius, float topRightRadius, float bottomRightRadius, float bottomLeftRadius) {

    public BorderRadius(double radius) {
        this((float) radius);
    }

    public BorderRadius(float radius) {
        this(radius, radius, radius, radius);
    }

    public BorderRadius(double topLeft, double topRight, double bottomRight, double bottomLeft) {
        this((float) topLeft, (float) topRight, (float) bottomRight, (float) bottomLeft);
    }

    public float[] asArray() {
        return new float[] { topLeftRadius, topRightRadius, bottomRightRadius, bottomLeftRadius };
    }
}
