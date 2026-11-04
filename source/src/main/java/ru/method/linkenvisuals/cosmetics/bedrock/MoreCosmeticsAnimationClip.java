package ru.method.linkenvisuals.cosmetics.bedrock;

import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;

public final class MoreCosmeticsAnimationClip {
    private final float lengthSeconds;
    private final boolean loop;
    private final Map<String, BoneChannels> boneChannels;

    private MoreCosmeticsAnimationClip(float lengthSeconds, boolean loop, Map<String, BoneChannels> boneChannels) {
        this.lengthSeconds = Math.max(0.01f, lengthSeconds);
        this.loop = loop;
        this.boneChannels = boneChannels;
    }

    public static MoreCosmeticsAnimationClip parse(JsonObject animationObject) {
        if (animationObject == null) {
            return null;
        }

        float length = animationObject.has("animation_length")
                ? animationObject.get("animation_length").getAsFloat()
                : 1f;
        boolean loop = !animationObject.has("loop") || animationObject.get("loop").getAsBoolean();
        Map<String, BoneChannels> channels = new HashMap<>();

        if (animationObject.has("bones") && animationObject.get("bones").isJsonObject()) {
            JsonObject bones = animationObject.getAsJsonObject("bones");
            for (Map.Entry<String, com.google.gson.JsonElement> entry : bones.entrySet()) {
                if (!entry.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject boneObject = entry.getValue().getAsJsonObject();
                BoneChannels boneChannels = new BoneChannels(
                        MoreCosmeticsKeyframeTrack.parse(boneObject.has("rotation") ? boneObject.getAsJsonObject("rotation") : null),
                        MoreCosmeticsKeyframeTrack.parse(boneObject.has("position") ? boneObject.getAsJsonObject("position") : null),
                        MoreCosmeticsKeyframeTrack.parse(boneObject.has("scale") ? boneObject.getAsJsonObject("scale") : null)
                );
                if (!boneChannels.isEmpty()) {
                    channels.put(entry.getKey(), boneChannels);
                }
            }
        }

        return new MoreCosmeticsAnimationClip(length, loop, channels);
    }

    public float lengthSeconds() {
        return lengthSeconds;
    }

    public boolean loop() {
        return loop;
    }

    public void apply(BedrockBonePose pose, float timeSeconds) {
        if (pose == null || boneChannels.isEmpty()) {
            return;
        }

        float sampleTime = loop ? timeSeconds : Math.min(Math.max(timeSeconds, 0f), lengthSeconds);
        for (Map.Entry<String, BoneChannels> entry : boneChannels.entrySet()) {
            entry.getValue().apply(pose, entry.getKey(), sampleTime, lengthSeconds, loop);
        }
    }

    private static final class BoneChannels {
        private final MoreCosmeticsKeyframeTrack rotation;
        private final MoreCosmeticsKeyframeTrack position;
        private final MoreCosmeticsKeyframeTrack scale;

        private BoneChannels(
                MoreCosmeticsKeyframeTrack rotation,
                MoreCosmeticsKeyframeTrack position,
                MoreCosmeticsKeyframeTrack scale
        ) {
            this.rotation = rotation == null ? MoreCosmeticsKeyframeTrack.empty() : rotation;
            this.position = position == null ? MoreCosmeticsKeyframeTrack.empty() : position;
            this.scale = scale == null ? MoreCosmeticsKeyframeTrack.empty() : scale;
        }

        private boolean isEmpty() {
            return rotation.isEmpty() && position.isEmpty() && scale.isEmpty();
        }

        private void apply(BedrockBonePose pose, String boneName, float timeSeconds, float lengthSeconds, boolean loop) {
            if (!rotation.isEmpty()) {
                float[] value = rotation.sample(timeSeconds, lengthSeconds, loop);
                pose.setRotation(boneName, -value[0], -value[1], value[2]);
            }
            if (!position.isEmpty()) {
                float[] value = position.sample(timeSeconds, lengthSeconds, loop);
                pose.setPosition(boneName, value[0], value[1], value[2]);
            }
            if (!scale.isEmpty()) {
                float[] value = scale.sample(timeSeconds, lengthSeconds, loop);
                pose.setScale(boneName, value[0], value[1], value[2]);
            }
        }
    }
}
