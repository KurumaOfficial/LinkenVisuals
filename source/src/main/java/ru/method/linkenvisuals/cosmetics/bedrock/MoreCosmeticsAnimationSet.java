package ru.method.linkenvisuals.cosmetics.bedrock;

import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;

public final class MoreCosmeticsAnimationSet {
    private final Map<String, MoreCosmeticsAnimationClip> clips;

    private MoreCosmeticsAnimationSet(Map<String, MoreCosmeticsAnimationClip> clips) {
        this.clips = clips;
    }

    public static MoreCosmeticsAnimationSet parse(JsonObject animationRoot) {
        if (animationRoot == null || !animationRoot.has("animations") || !animationRoot.get("animations").isJsonObject()) {
            return empty();
        }

        Map<String, MoreCosmeticsAnimationClip> clips = new HashMap<>();
        JsonObject animations = animationRoot.getAsJsonObject("animations");
        for (Map.Entry<String, com.google.gson.JsonElement> entry : animations.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            MoreCosmeticsAnimationClip clip = MoreCosmeticsAnimationClip.parse(entry.getValue().getAsJsonObject());
            if (clip != null) {
                clips.put(entry.getKey(), clip);
            }
        }
        return new MoreCosmeticsAnimationSet(clips);
    }

    public static MoreCosmeticsAnimationSet empty() {
        return new MoreCosmeticsAnimationSet(Map.of());
    }

    public boolean isEmpty() {
        return clips.isEmpty();
    }

    public MoreCosmeticsAnimationClip clip(String name) {
        return clips.get(name);
    }

    public MoreCosmeticsAnimationClip resolve(boolean moving) {
        MoreCosmeticsAnimationClip clip = clips.get(moving ? "moving" : "idle");
        if (clip != null) {
            return clip;
        }
        return clips.get("idle");
    }
}
