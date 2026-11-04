package ru.method.linkenvisuals.utils.render;

import net.minecraft.client.util.math.MatrixStack;

import java.awt.Color;

/**
 * Rounded outline ring via SDF shader ({@code rounded_outline.fsh}), ported from
 * DrainWalk {@code rounded_outline.fsh}. Ring only вЂ” never a solid outer fill.
 */
public final class StencilOutline {
    /** DrainWalk / glass outline width. */
    public static final float OUTLINE_WIDTH = 0.68f;
    public static final float SMOOTH_MIN = 0.0f;
    public static final float SMOOTH_MAX = 0.34f;

    private StencilOutline() {
    }

    public static void drawRoundedOutline(
            MatrixStack stack,
            float x,
            float y,
            float width,
            float height,
            float radius,
            float thickness,
            Color color
    ) {
        if (stack == null || color == null || color.getAlpha() <= 0
                || width <= 0f || height <= 0f) {
            return;
        }

        float outlineWidth = thickness > 0.001f ? thickness : OUTLINE_WIDTH;
        Render2D.drawBorder(stack, x, y, width, height, radius, outlineWidth, color);
    }
}

