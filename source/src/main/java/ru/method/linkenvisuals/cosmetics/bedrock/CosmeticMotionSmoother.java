package ru.method.linkenvisuals.cosmetics.bedrock;

import net.minecraft.util.math.MathHelper;

public final class CosmeticMotionSmoother {
    private float smoothedLimbDistance;
    private float movingBlend;

    public void update(boolean entityMoving, float limbDistance, float deltaSeconds, boolean previewWalking) {
        if (previewWalking) {
            movingBlend = MathHelper.lerp(MathHelper.clamp(deltaSeconds * 6f, 0f, 1f), movingBlend, 1f);
            return;
        }

        smoothedLimbDistance = MathHelper.lerp(MathHelper.clamp(deltaSeconds * 8f, 0f, 1f), smoothedLimbDistance, limbDistance);
        boolean wantsMoving = entityMoving || smoothedLimbDistance > 0.03f;
        float target = wantsMoving ? 1f : 0f;
        float blendSpeed = target > movingBlend ? 5f : 3f;
        movingBlend = MathHelper.lerp(MathHelper.clamp(deltaSeconds * blendSpeed, 0f, 1f), movingBlend, target);
    }

    public float movingBlend() {
        return movingBlend;
    }

    public void reset() {
        smoothedLimbDistance = 0f;
        movingBlend = 0f;
    }
}
