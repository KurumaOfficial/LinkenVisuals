package ru.method.linkenvisuals.utils.render;

import org.joml.Matrix3x2fStack;
import net.minecraft.util.Identifier;
import java.awt.Color;

public final class CapePreviewRenderer {

    private static final float FACE_U0 = 1f / 64f, FACE_V0 = 1f / 32f;
    private static final float FACE_U1 = 11f / 64f, FACE_V1 = 17f / 32f;

    private CapePreviewRenderer() {
    }

    public static void render(Matrix3x2fStack matrices, float x, float y, float width, float height,
                              float radius, Identifier texture, float alpha, float amount) {
        render(matrices, x, y, width, height, radius, texture, alpha, amount,
                FACE_U0, FACE_V0, FACE_U1, FACE_V1);
    }

    public static void render(Matrix3x2fStack matrices, float x, float y, float width, float height,
                              float radius, Identifier texture, float alpha, float amount,
                              float u0, float v0, float u1, float v1) {
        if (width <= 0f || height <= 0f || alpha <= 0f || texture == null) {
            return;
        }
        Render2D.drawTexture(matrices, x, y, width, height, radius,
                u0, v0, u1, v1, texture,
                new Color(1f, 1f, 1f, Math.max(0f, Math.min(1f, alpha))));
    }
}
