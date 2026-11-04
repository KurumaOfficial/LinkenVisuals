package ru.method.linkenvisuals.cosmetics.bedrock;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class MoreCosmeticsKeyframeTrack {
    private static final float[] ZERO = new float[]{0f, 0f, 0f};

    private final List<Keyframe> keyframes;

    private MoreCosmeticsKeyframeTrack(List<Keyframe> keyframes) {
        this.keyframes = keyframes;
    }

    static MoreCosmeticsKeyframeTrack parse(JsonObject channelObject) {
        if (channelObject == null) {
            return empty();
        }

        List<Keyframe> keyframes = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : channelObject.entrySet()) {
            float time = parseTime(entry.getKey());
            ParsedKeyframe parsed = parseKeyframe(entry.getValue());
            if (parsed != null) {
                keyframes.add(new Keyframe(time, parsed.vector(), parsed.lerpMode()));
            }
        }

        keyframes.sort(Comparator.comparingDouble(keyframe -> keyframe.time));
        return new MoreCosmeticsKeyframeTrack(keyframes);
    }

    static MoreCosmeticsKeyframeTrack empty() {
        return new MoreCosmeticsKeyframeTrack(List.of());
    }

    boolean isEmpty() {
        return keyframes.isEmpty();
    }

    float[] sample(float timeSeconds, float animationLength, boolean loop) {
        if (keyframes.isEmpty()) {
            return ZERO;
        }
        if (keyframes.size() == 1) {
            return keyframes.getFirst().vector.clone();
        }

        float time = loop ? wrapTime(timeSeconds, animationLength) : MathHelper.clamp(timeSeconds, 0f, animationLength);
        int nextIndex = findNextIndex(time);
        int previousIndex = Math.max(0, nextIndex - 1);
        Keyframe previous = keyframes.get(previousIndex);
        Keyframe next = keyframes.get(nextIndex);

        if (next.time <= previous.time) {
            return previous.vector.clone();
        }

        float alpha = (time - previous.time) / (next.time - previous.time);
        alpha = applyEasing(alpha, next.lerpMode());

        if (useCatmullRom(next.lerpMode()) && keyframes.size() >= 4) {
            int p0 = Math.max(0, previousIndex - 1);
            int p3 = Math.min(keyframes.size() - 1, nextIndex + 1);
            return new float[]{
                    catmullRom(keyframes.get(p0).vector[0], previous.vector[0], next.vector[0], keyframes.get(p3).vector[0], alpha),
                    catmullRom(keyframes.get(p0).vector[1], previous.vector[1], next.vector[1], keyframes.get(p3).vector[1], alpha),
                    catmullRom(keyframes.get(p0).vector[2], previous.vector[2], next.vector[2], keyframes.get(p3).vector[2], alpha)
            };
        }

        return new float[]{
                lerp(previous.vector[0], next.vector[0], alpha),
                lerp(previous.vector[1], next.vector[1], alpha),
                lerp(previous.vector[2], next.vector[2], alpha)
        };
    }

    private int findNextIndex(float time) {
        for (int i = 0; i < keyframes.size(); i++) {
            if (time <= keyframes.get(i).time) {
                return i;
            }
        }
        return keyframes.size() - 1;
    }

    private static float wrapTime(float timeSeconds, float animationLength) {
        if (animationLength <= 0f) {
            return timeSeconds;
        }
        float wrapped = timeSeconds % animationLength;
        return wrapped < 0f ? wrapped + animationLength : wrapped;
    }

    private static float parseTime(String raw) {
        try {
            return Float.parseFloat(raw);
        } catch (NumberFormatException ignored) {
            return 0f;
        }
    }

    private static ParsedKeyframe parseKeyframe(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonArray()) {
            float[] vector = readVectorArray(element.getAsJsonArray());
            return vector == null ? null : new ParsedKeyframe(vector, "linear");
        }
        if (!element.isJsonObject()) {
            return null;
        }

        JsonObject object = element.getAsJsonObject();
        float[] vector = object.has("vector") && object.get("vector").isJsonArray()
                ? readVectorArray(object.getAsJsonArray("vector"))
                : null;
        if (vector == null) {
            return null;
        }
        String lerpMode = object.has("lerp_mode")
                ? object.get("lerp_mode").getAsString()
                : "linear";
        return new ParsedKeyframe(vector, lerpMode);
    }

    private static float[] readVectorArray(com.google.gson.JsonArray array) {
        if (array.size() < 3) {
            return null;
        }
        return new float[]{
                array.get(0).getAsFloat(),
                array.get(1).getAsFloat(),
                array.get(2).getAsFloat()
        };
    }

    private static boolean useCatmullRom(String lerpMode) {
        return lerpMode != null && lerpMode.toLowerCase(Locale.ROOT).contains("catmull");
    }

    private static float applyEasing(float alpha, String lerpMode) {
        if (lerpMode == null) {
            return alpha;
        }
        String mode = lerpMode.toLowerCase(Locale.ROOT);
        if (mode.contains("easeinout")) {
            return alpha * alpha * (3f - 2f * alpha);
        }
        return alpha;
    }

    private static float catmullRom(float p0, float p1, float p2, float p3, float t) {
        float t2 = t * t;
        float t3 = t2 * t;
        return 0.5f * (
                (2f * p1)
                        + (-p0 + p2) * t
                        + (2f * p0 - 5f * p1 + 4f * p2 - p3) * t2
                        + (-p0 + 3f * p1 - 3f * p2 + p3) * t3
        );
    }

    private static float lerp(float from, float to, float alpha) {
        return from + (to - from) * alpha;
    }

    private record ParsedKeyframe(float[] vector, String lerpMode) {
    }

    private record Keyframe(float time, float[] vector, String lerpMode) {
    }
}
