package unkw.visualtest.client.hud;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.util.Identifier;

final class SmoothGuiRenderer {
    private SmoothGuiRenderer() {
    }

    static void draw(DrawContext context, Identifier texture, int x, int y, int width, int height,
                     int sourceWidth, int sourceHeight) {
        drawRegion(context, texture, x, y, width, height, 0, 0, sourceWidth, sourceHeight, sourceWidth, sourceHeight);
    }

    static void drawNineSlice(DrawContext context, Identifier texture, int x, int y, int width, int height,
                              int sourceWidth, int sourceHeight, int border) {
        drawNineSlice(context, texture, x, y, width, height, sourceWidth, sourceHeight, border, border);
    }

    static void drawNineSlice(DrawContext context, Identifier texture, int x, int y, int width, int height,
                              int sourceWidth, int sourceHeight, int sourceBorder, int border) {
        int left = Math.min(border, width / 2);
        int right = Math.min(border, width / 2);
        int top = Math.min(border, height / 2);
        int bottom = Math.min(border, height / 2);
        int centerWidth = width - left - right;
        int centerHeight = height - top - bottom;
        int sourceCenterWidth = sourceWidth - sourceBorder * 2;
        int sourceCenterHeight = sourceHeight - sourceBorder * 2;

        drawRegion(context, texture, x, y, left, top, 0, 0, sourceBorder, sourceBorder, sourceWidth, sourceHeight);
        drawRegion(context, texture, x + left, y, centerWidth, top, sourceBorder, 0, sourceCenterWidth, sourceBorder, sourceWidth, sourceHeight);
        drawRegion(context, texture, x + width - right, y, right, top, sourceWidth - sourceBorder, 0, sourceBorder, sourceBorder, sourceWidth, sourceHeight);
        drawRegion(context, texture, x, y + top, left, centerHeight, 0, sourceBorder, sourceBorder, sourceCenterHeight, sourceWidth, sourceHeight);
        drawRegion(context, texture, x + left, y + top, centerWidth, centerHeight, sourceBorder, sourceBorder, sourceCenterWidth, sourceCenterHeight, sourceWidth, sourceHeight);
        drawRegion(context, texture, x + width - right, y + top, right, centerHeight, sourceWidth - sourceBorder, sourceBorder, sourceBorder, sourceCenterHeight, sourceWidth, sourceHeight);
        drawRegion(context, texture, x, y + height - bottom, left, bottom, 0, sourceHeight - sourceBorder, sourceBorder, sourceBorder, sourceWidth, sourceHeight);
        drawRegion(context, texture, x + left, y + height - bottom, centerWidth, bottom, sourceBorder, sourceHeight - sourceBorder, sourceCenterWidth, sourceBorder, sourceWidth, sourceHeight);
        drawRegion(context, texture, x + width - right, y + height - bottom, right, bottom, sourceWidth - sourceBorder, sourceHeight - sourceBorder, sourceBorder, sourceBorder, sourceWidth, sourceHeight);
    }

    static void drawRegion(DrawContext context, Identifier texture, int x, int y, int width, int height,
                           int sourceX, int sourceY, int regionWidth, int regionHeight,
                           int textureWidth, int textureHeight) {
        if (width <= 0 || height <= 0 || regionWidth <= 0 || regionHeight <= 0) {
            return;
        }
        context.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                texture,
                x,
                y,
                (float) sourceX,
                (float) sourceY,
                width,
                height,
                regionWidth,
                regionHeight,
                textureWidth,
                textureHeight
        );
    }
}
