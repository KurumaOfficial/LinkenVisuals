package ru.method.linkenvisuals.screen.linkgui;

import lombok.experimental.UtilityClass;
import org.joml.Matrix3x2fStack;
import net.minecraft.util.math.MathHelper;
import ru.method.linkenvisuals.modules.impl.client.UI;

/**
 * Единый pipeline масштаба/центрирования GUI-канваса (LinkGuiScreen.renderCanvas).
 */
@UtilityClass
public final class LinkGuiCanvasTransform {
    public static final float SLIDE_DISTANCE = 60f;

    private static final float SCREEN_PAD = 12f;
    private static final float MIN_FIT_SCALE = 0.55f;

    public static float computeFitScale(float screenW, float screenH, float canvasW, float canvasH) {
        return computeFitScale(screenW, screenH, canvasW, canvasH, SCREEN_PAD);
    }

    public static float computeFitScale(float screenW, float screenH, float canvasW, float canvasH, float screenPad) {
        if (screenW <= 0f || screenH <= 0f) {
            return 1f;
        }
        float pad = Math.max(0f, screenPad);
        float fitX = (screenW - pad * 2f) / canvasW;
        float fitY = (screenH - pad * 2f) / canvasH;
        float rawFit = Math.min(fitX, fitY);
        if (rawFit >= 1f) {
            return 1f;
        }
        return MathHelper.clamp(rawFit, MIN_FIT_SCALE, 1f);
    }

    public static float layoutScale(float screenW, float screenH, float canvasW, float canvasH) {
        return layoutScale(screenW, screenH, canvasW, canvasH, SCREEN_PAD);
    }

    public static float layoutScale(float screenW, float screenH, float canvasW, float canvasH, float screenPad) {
        return Math.max(0.01f, UI.getInterfaceScale() * computeFitScale(screenW, screenH, canvasW, canvasH, screenPad));
    }

    public static float slideForProgress(float progress) {
        float prog = MathHelper.clamp(progress, 0f, 1f);
        return (1f - prog) * SLIDE_DISTANCE;
    }

    public static void applyCanvas(Matrix3x2fStack stack, int screenW, int screenH,
                                   float renderScale, float slideY,
                                   float canvasCenterX, float canvasCenterY) {
        stack.translate(screenW / 2f, screenH / 2f + slideY);
        stack.scale(renderScale, renderScale);
        stack.translate(-canvasCenterX, -canvasCenterY);
    }

    /** Как LinkGuiScreen.transformMouse — slide только визуальный, не в hit-test. */
    public static float[] screenToCanvas(double mouseX, double mouseY,
                                         int screenW, int screenH,
                                         float scale,
                                         float canvasCenterX, float canvasCenterY) {
        if (scale <= 0.01f) {
            scale = 0.01f;
        }
        return new float[] {
                (float) (canvasCenterX + (mouseX - screenW / 2f) / scale),
                (float) (canvasCenterY + (mouseY - screenH / 2f) / scale)
        };
    }

    /** Canvas rect → screen px (для drawEntity / scissor вне Matrix3x2fStack scale). */
    public static float[] canvasRectToScreen(float canvasX, float canvasY, float canvasW, float canvasH,
                                             int screenW, int screenH,
                                             float scale, float slideY,
                                             float canvasCenterX, float canvasCenterY) {
        float sx = screenW / 2f + (canvasX - canvasCenterX) * scale;
        float sy = screenH / 2f + slideY + (canvasY - canvasCenterY) * scale;
        return new float[] { sx, sy, canvasW * scale, canvasH * scale };
    }
}
