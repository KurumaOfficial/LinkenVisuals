package ru.method.linkenvisuals.utils.debug;

import net.minecraft.client.gui.DrawContext;

/**
 * @deprecated Use {@link FullFrameProfiler} directly. Kept for existing call sites.
 */
@Deprecated
public final class RenderProfiler {

    private RenderProfiler() {
    }

    public static void setEnabled(boolean value) {
        FullFrameProfiler.setEnabled(value);
    }

    public static boolean isEnabled() {
        return FullFrameProfiler.isEnabled();
    }

    public static void beginFrame() {
        FullFrameProfiler.beginFrame();
    }

    public static void endFrame() {
        FullFrameProfiler.endFrame();
    }

    public static boolean isActive() {
        return FullFrameProfiler.isActive();
    }

    public static long startSection() {
        return FullFrameProfiler.startSection();
    }

    public static void endSection(String key, long startNanos) {
        FullFrameProfiler.endSection(key, startNanos);
    }

    public static void record(String key, long nanos) {
        FullFrameProfiler.recordSection(key, nanos);
    }

    public static void incrementCounter(String key) {
        FullFrameProfiler.incrementCounter(key);
    }

    public static void incrementCounter(String key, int amount) {
        FullFrameProfiler.incrementCounter(key, amount);
    }

    public static void renderOverlay(DrawContext context) {
        FullFrameProfiler.renderOverlay(context);
    }
}
