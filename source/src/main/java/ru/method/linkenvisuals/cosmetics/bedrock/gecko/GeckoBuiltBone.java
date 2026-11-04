package ru.method.linkenvisuals.cosmetics.bedrock.gecko;

import java.util.List;

public final class GeckoBuiltBone {
    public final String name;
    public final float rotationPointX;
    public final float rotationPointY;
    public final float rotationPointZ;
    public final float rotationX;
    public final float rotationY;
    public final float rotationZ;
    public final List<GeckoBuiltCube> cubes;
    public final List<GeckoBuiltBone> children;

    public GeckoBuiltBone(
            String name,
            float rotationPointX,
            float rotationPointY,
            float rotationPointZ,
            float rotationX,
            float rotationY,
            float rotationZ,
            List<GeckoBuiltCube> cubes,
            List<GeckoBuiltBone> children
    ) {
        this.name = name;
        this.rotationPointX = rotationPointX;
        this.rotationPointY = rotationPointY;
        this.rotationPointZ = rotationPointZ;
        this.rotationX = rotationX;
        this.rotationY = rotationY;
        this.rotationZ = rotationZ;
        this.cubes = cubes == null ? List.of() : List.copyOf(cubes);
        this.children = children == null ? List.of() : List.copyOf(children);
    }
}
