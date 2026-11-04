package ru.method.linkenvisuals.utils.animations;

import net.minecraft.util.math.MathHelper;

/**
 * Frame-step animation matching ExampleMod GUIPane (BACK_OUT, per-tick speed).
 */
public final class FrameStepAnimation {

    private float value;
    private float prevValue;
    private float animationSpeed;
    private float fromValue;
    private float toValue;
    private float animationValue = 1f;

    public void resetOpen() {
        value = 0f;
        prevValue = 0f;
        animationValue = 0f;
    }

    public void resetClosed() {
        value = 1f;
        prevValue = 1f;
        animationValue = 1f;
    }

    public void update(boolean opening) {
        prevValue = value;
        value = MathHelper.clamp(value + (opening ? animationSpeed : -animationSpeed), fromValue, toValue);
    }

    public void animate(float fromValue, float toValue, float animationSpeed, float partialTicks) {
        this.animationSpeed = animationSpeed;
        this.fromValue = fromValue;
        this.toValue = toValue;
        float interpolated = MathHelper.lerp(partialTicks, prevValue, value);
        animationValue = (float) Easing.EASE_OUT_BACK.apply(interpolated);
    }

    public float getValue() {
        return value;
    }

    public float getPrevValue() {
        return prevValue;
    }

    public float getAnimationValue() {
        return animationValue;
    }

    public boolean isFinishedClosing() {
        return value <= 0f && prevValue <= 0f;
    }
}
