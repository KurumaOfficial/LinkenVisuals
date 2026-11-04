package dev.linkenvisuals.media;

/**
 * Single media session shared by HUD and ClickGUI music strip.
 */
public final class DynamicIslandMediaHolder {
    private static final DynamicIslandMedia SHARED = new DynamicIslandMedia();

    private DynamicIslandMediaHolder() {
    }

    public static DynamicIslandMedia shared() {
        return SHARED;
    }
}
