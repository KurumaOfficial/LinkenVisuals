package ru.method.linkenvisuals.utils.render;

import net.minecraft.client.gl.Framebuffer;

/**
 * 1.21.11 port shim. The donor kept a downsampled snapshot of the world framebuffer to use
 * as the blurred LinkGUI backdrop. The new renderer owns the main framebuffer and exposes no
 * raw blit path, so the snapshot is disabled; the GUI draws a live engine blur instead.
 */
public final class WorldSnapshotCache {

    private WorldSnapshotCache() {
    }

    public static boolean isValid() {
        return false;
    }

    public static void invalidate() {
    }

    public static void capture(Framebuffer main) {
    }

    public static void blitTo(Framebuffer main) {
    }

    public static void release() {
    }
}
