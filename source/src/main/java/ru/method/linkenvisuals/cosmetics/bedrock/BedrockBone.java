package ru.method.linkenvisuals.cosmetics.bedrock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class BedrockBone {
    private final String name;
    private final float pivotX;
    private final float pivotY;
    private final float pivotZ;
    private final float rotationX;
    private final float rotationY;
    private final float rotationZ;
    private final List<BedrockCube> cubes;
    private final List<BedrockBone> children;

    public BedrockBone(
            String name,
            float pivotX,
            float pivotY,
            float pivotZ,
            float rotationX,
            float rotationY,
            float rotationZ,
            List<BedrockCube> cubes
    ) {
        this.name = name;
        this.pivotX = pivotX;
        this.pivotY = pivotY;
        this.pivotZ = pivotZ;
        this.rotationX = rotationX;
        this.rotationY = rotationY;
        this.rotationZ = rotationZ;
        this.cubes = cubes == null ? List.of() : List.copyOf(cubes);
        this.children = new ArrayList<>();
    }

    public String name() {
        return name;
    }

    public float pivotX() {
        return pivotX;
    }

    public float pivotY() {
        return pivotY;
    }

    public float pivotZ() {
        return pivotZ;
    }

    public float rotationX() {
        return rotationX;
    }

    public float rotationY() {
        return rotationY;
    }

    public float rotationZ() {
        return rotationZ;
    }

    public List<BedrockCube> cubes() {
        return cubes;
    }

    public List<BedrockBone> children() {
        return Collections.unmodifiableList(children);
    }

    void addChild(BedrockBone child) {
        if (child != null) {
            children.add(child);
        }
    }
}
