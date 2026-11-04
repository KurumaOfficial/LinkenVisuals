package ru.method.linkenvisuals.cosmetics.bedrock;

import net.minecraft.util.math.MathHelper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class BedrockBonePose {
    private final Map<String, float[]> rotations = new HashMap<>();
    private final Map<String, float[]> positions = new HashMap<>();
    private final Map<String, float[]> scales = new HashMap<>();

    public void setRotation(String bone, float x, float y, float z) {
        rotations.put(bone, new float[]{x, y, z});
    }

    public void setPosition(String bone, float x, float y, float z) {
        positions.put(bone, new float[]{x, y, z});
    }

    public void setScale(String bone, float x, float y, float z) {
        scales.put(bone, new float[]{x, y, z});
    }

    public float rotationX(String bone) {
        return component(rotations, bone, 0);
    }

    public float rotationY(String bone) {
        return component(rotations, bone, 1);
    }

    public float rotationZ(String bone) {
        return component(rotations, bone, 2);
    }

    public boolean hasPosition(String bone) {
        return positions.containsKey(bone);
    }

    public float positionX(String bone) {
        return component(positions, bone, 0);
    }

    public float positionY(String bone) {
        return component(positions, bone, 1);
    }

    public float positionZ(String bone) {
        return component(positions, bone, 2);
    }

    public boolean hasScale(String bone) {
        return scales.containsKey(bone);
    }

    public float scaleX(String bone) {
        float[] value = scales.get(bone);
        return value == null ? 1f : value[0];
    }

    public float scaleY(String bone) {
        float[] value = scales.get(bone);
        return value == null ? 1f : value[1];
    }

    public float scaleZ(String bone) {
        float[] value = scales.get(bone);
        return value == null ? 1f : value[2];
    }

    public void copyFrom(BedrockBonePose other) {
        rotations.clear();
        positions.clear();
        scales.clear();
        if (other == null) {
            return;
        }
        copyMap(other.rotations, rotations);
        copyMap(other.positions, positions);
        copyMap(other.scales, scales);
    }

    public static BedrockBonePose blend(BedrockBonePose first, BedrockBonePose second, float blend) {
        BedrockBonePose result = new BedrockBonePose();
        if (first == null && second == null) {
            return result;
        }
        if (first == null) {
            result.copyFrom(second);
            return result;
        }
        if (second == null) {
            result.copyFrom(first);
            return result;
        }

        float t = MathHelper.clamp(blend, 0f, 1f);
        Set<String> boneNames = new HashSet<>();
        boneNames.addAll(first.rotations.keySet());
        boneNames.addAll(second.rotations.keySet());
        boneNames.addAll(first.positions.keySet());
        boneNames.addAll(second.positions.keySet());
        boneNames.addAll(first.scales.keySet());
        boneNames.addAll(second.scales.keySet());

        for (String bone : boneNames) {
            if (first.rotations.containsKey(bone) || second.rotations.containsKey(bone)) {
                result.setRotation(
                        bone,
                        MathHelper.lerp(t, first.rotationX(bone), second.rotationX(bone)),
                        MathHelper.lerp(t, first.rotationY(bone), second.rotationY(bone)),
                        MathHelper.lerp(t, first.rotationZ(bone), second.rotationZ(bone))
                );
            }
            if (first.positions.containsKey(bone) || second.positions.containsKey(bone)) {
                result.setPosition(
                        bone,
                        MathHelper.lerp(t, first.positionX(bone), second.positionX(bone)),
                        MathHelper.lerp(t, first.positionY(bone), second.positionY(bone)),
                        MathHelper.lerp(t, first.positionZ(bone), second.positionZ(bone))
                );
            }
            if (first.scales.containsKey(bone) || second.scales.containsKey(bone)) {
                result.setScale(
                        bone,
                        MathHelper.lerp(t, first.scaleX(bone), second.scaleX(bone)),
                        MathHelper.lerp(t, first.scaleY(bone), second.scaleY(bone)),
                        MathHelper.lerp(t, first.scaleZ(bone), second.scaleZ(bone))
                );
            }
        }
        return result;
    }

    private static void copyMap(Map<String, float[]> source, Map<String, float[]> target) {
        for (Map.Entry<String, float[]> entry : source.entrySet()) {
            target.put(entry.getKey(), entry.getValue().clone());
        }
    }

    private static float component(Map<String, float[]> values, String bone, int index) {
        float[] value = values.get(bone);
        return value == null || index < 0 || index >= value.length ? 0f : value[index];
    }
}