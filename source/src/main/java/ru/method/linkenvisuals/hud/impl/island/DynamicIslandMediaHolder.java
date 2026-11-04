package ru.method.linkenvisuals.hud.impl.island;

/**
 * Single media session shared by Dynamic Island and Link GUI music strip.
 */
public final class DynamicIslandMediaHolder {
    private static final DynamicIslandMedia SHARED = new DynamicIslandMedia();

    private DynamicIslandMediaHolder() {
    }

    public static DynamicIslandMedia shared() {
        return SHARED;
    }
}
