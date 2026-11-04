package ru.method.linkenvisuals.utils.render;

public final class RenderFrameTracker {

    private static volatile long frameId;

    private RenderFrameTracker() {
    }

    public static void beginFrame() {
        frameId++;
    }

    public static long getFrameId() {
        return frameId;
    }
}

