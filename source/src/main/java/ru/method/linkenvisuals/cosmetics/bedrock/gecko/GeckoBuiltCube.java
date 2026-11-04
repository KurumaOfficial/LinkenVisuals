package ru.method.linkenvisuals.cosmetics.bedrock.gecko;

import java.util.List;

public final class GeckoBuiltCube {
    public final float pivotX;
    public final float pivotY;
    public final float pivotZ;
    public final float rotationX;
    public final float rotationY;
    public final float rotationZ;
    public final float sizeX;
    public final float sizeY;
    public final float sizeZ;
    public final List<GeckoQuad> quads;

    public GeckoBuiltCube(
            float pivotX,
            float pivotY,
            float pivotZ,
            float rotationX,
            float rotationY,
            float rotationZ,
            float sizeX,
            float sizeY,
            float sizeZ,
            List<GeckoQuad> quads
    ) {
        this.pivotX = pivotX;
        this.pivotY = pivotY;
        this.pivotZ = pivotZ;
        this.rotationX = rotationX;
        this.rotationY = rotationY;
        this.rotationZ = rotationZ;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.quads = quads == null ? List.of() : List.copyOf(quads);
    }
}
