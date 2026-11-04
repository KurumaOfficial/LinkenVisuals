package ru.method.linkenvisuals.utils.render;

/**
 * 1.21.11 port shim: the donor's raw-GL frame-blend implementation is replaced by the
 * engine-pipeline version in {@code dev.linkenvisuals.render.MotionBlurRenderer}
 * (two FBOs + blend pipeline). Kept under the donor FQN so the module/mixin call sites
 * stay untouched.
 */
public final class MotionBlurRenderer {

    private MotionBlurRenderer() {
    }

    public static void render(float strength) {
        dev.linkenvisuals.render.MotionBlurRenderer.render(strength);
    }

    public static void release() {
        dev.linkenvisuals.render.MotionBlurRenderer.shutdown();
    }
}
