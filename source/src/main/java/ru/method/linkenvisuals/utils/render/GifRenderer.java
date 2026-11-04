package ru.method.linkenvisuals.utils.render;

import org.joml.Matrix3x2fStack;
import net.minecraft.util.Identifier;
import java.awt.Color;

public final class GifRenderer {
    private GifRenderer() {
    }

    public static void drawGif(Matrix3x2fStack ms, GifTexture gif, float x, float y, float width, float height, float alpha) {
        if (gif == null || !gif.isLoaded() || alpha <= 0.01f || width <= 0f || height <= 0f) {
            return;
        }

        Identifier texture = gif.getCurrentFrameTexture();
        if (texture == null) {
            return;
        }

        int a = Math.round(Math.max(0f, Math.min(1f, alpha)) * 255);
        Render2D.drawTexture(ms, x, y, width, height, 0f, texture, new Color(255, 255, 255, a));
    }
}
