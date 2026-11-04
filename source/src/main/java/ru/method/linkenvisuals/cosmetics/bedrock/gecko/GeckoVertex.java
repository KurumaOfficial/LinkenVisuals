package ru.method.linkenvisuals.cosmetics.bedrock.gecko;

public final class GeckoVertex {
    public final float x;
    public final float y;
    public final float z;
    public float u;
    public float v;

    public GeckoVertex(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public GeckoVertex withUv(float u, float v) {
        this.u = u;
        this.v = v;
        return this;
    }
}
