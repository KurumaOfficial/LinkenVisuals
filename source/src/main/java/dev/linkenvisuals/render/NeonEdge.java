package dev.linkenvisuals.render;

import dev.linkenvisuals.ui.Theme;
import net.minecraft.client.gui.DrawContext;

public final class NeonEdge {
    public static int cool() {
        return Theme.lighten(Theme.accent(), 0.25f);
    }

    public static int warm() {
        return Theme.lighten(Theme.secondary(), 0.25f);
    }

    private NeonEdge() {
    }

    public static void frame(DrawContext context, float x, float y, float w, float h, int seed, float intensity) {
        frame(context, x, y, w, h, (float) Theme.radiusLevel(), seed, intensity, cool());
    }

    public static void frame(DrawContext context, float x, float y, float w, float h, int seed, float intensity, int color) {
        frame(context, x, y, w, h, (float) Theme.radiusLevel(), seed, intensity, color);
    }

    public static void frame(DrawContext context, float x, float y, float w, float h, float radius, int seed, float intensity) {
        frame(context, x, y, w, h, radius, seed, intensity, cool());
    }

    public static void frame(DrawContext context, float x, float y, float w, float h, float radius, int seed, float intensity, int color) {
        if (intensity <= 0.01f || w <= 2.0f || h <= 2.0f) {
            return;
        }
        RoundedRectPipeline.drawOutline(context, x, y, w, h, radius,
            Math.round(26.0f * intensity) << 24 | 0xFFFFFF);

        float reach = Math.min(w * 0.62f, w - 8.0f);
        if (reach <= 2.0f) {
            return;
        }
        GlowPipeline.line(context, x + w * 0.5f, y + 0.5f, reach, 6.0f,
            Math.round(30.0f * intensity) << 24 | color & 0xFFFFFF);
        GlowPipeline.line(context, x + w * 0.5f, y + 0.5f, reach * 0.45f, 3.0f,
            Math.round(38.0f * intensity) << 24 | color & 0xFFFFFF);
    }
}
