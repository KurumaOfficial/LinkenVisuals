package ru.method.linkenvisuals.cosmetics.bedrock;

public final class MoreCosmeticsAnimationPlayer {
    private MoreCosmeticsAnimationPlayer() {
    }

    public static void apply(BedrockBonePose pose, BedrockModel model, float timeSeconds, float movingBlend) {
        if (pose == null || model == null) {
            return;
        }

        MoreCosmeticsAnimationSet animationSet = model.animations();
        if (animationSet == null || animationSet.isEmpty()) {
            return;
        }

        MoreCosmeticsAnimationClip idleClip = animationSet.clip("idle");
        MoreCosmeticsAnimationClip movingClip = animationSet.clip("moving");
        if (idleClip == null && movingClip == null) {
            return;
        }

        float blend = Math.max(0f, Math.min(1f, movingBlend));
        boolean moving = blend >= 0.5f;
        MoreCosmeticsAnimationClip clip = moving
                ? (movingClip != null ? movingClip : idleClip)
                : (idleClip != null ? idleClip : movingClip);
        if (clip != null) {
            clip.apply(pose, timeSeconds);
        }
    }
}
