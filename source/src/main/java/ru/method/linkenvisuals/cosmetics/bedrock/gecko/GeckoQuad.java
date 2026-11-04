package ru.method.linkenvisuals.cosmetics.bedrock.gecko;

public final class GeckoQuad {
    public final GeckoVertex[] vertices;
    public final float normalX;
    public final float normalY;
    public final float normalZ;

    public GeckoQuad(GeckoVertex[] vertices, float normalX, float normalY, float normalZ) {
        this.vertices = vertices;
        this.normalX = normalX;
        this.normalY = normalY;
        this.normalZ = normalZ;
    }
}
