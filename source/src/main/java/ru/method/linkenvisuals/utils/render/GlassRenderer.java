package ru.method.linkenvisuals.utils.render;

import dev.linkenvisuals.render.GlassPanelPipeline;
import dev.linkenvisuals.render.HudGlassCapture;
import net.minecraft.client.gui.DrawContext;

/**
 * 1.21.11 bridge for the donor's framebuffer-driven glass surfaces.
 *
 * <p>The donor captured the framebuffer into dedicated FBOs and ran
 * {@code liquidglassnew} per panel. In 1.21.11 the same look is provided by
 * the ported glass pipeline which samples the HUD downscale chain. Capture is
 * refreshed at most once per frame, before the first glass surface is queued.</p>
 */
public final class GlassRenderer {

    private static long capturedFrame = Long.MIN_VALUE;

    private GlassRenderer() {
    }

    public static void ensureBackdrop() {
        long frame = RenderFrameTracker.getFrameId();
        if (frame == capturedFrame) {
            return;
        }
        capturedFrame = frame;
        try {
            HudGlassCapture.refresh();
        } catch (Throwable ignored) {
            // Backdrop unavailable (world reload, resource reload) - the
            // pipeline falls back to the white texture for this frame.
        }
    }

    public static void invalidate() {
        capturedFrame = Long.MIN_VALUE;
    }

    public static void draw(DrawContext context, float x, float y, float width, float height,
                            float radius, int argb) {
        if (context == null || width <= 0f || height <= 0f) {
            return;
        }
        ensureBackdrop();
        GlassPanelPipeline.draw(context, x, y, width, height, radius, argb);
    }
}
