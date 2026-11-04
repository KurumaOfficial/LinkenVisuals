package ru.method.linkenvisuals.screen.cosmetics;

import lombok.experimental.UtilityClass;
import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix3x2fStack;
import net.minecraft.util.math.RotationAxis;
import ru.method.linkenvisuals.screen.windowgui.WindowGuiRender;
import ru.method.linkenvisuals.utils.render.Render2D;

import java.awt.Color;

/** Мини-иконки для вкладок и заголовков (без внешних PNG — лёгкий процедурный рендер). */
@UtilityClass
public final class CosmeticsTabIcons {
    public void drawLayers(Matrix3x2fStack stack, float cx, float cy, float size, Color color) {
        float w = size * 0.42f;
        float h = size * 0.11f;
        for (int i = 0; i < 3; i++) {
            float y = cy - size * 0.18f + i * h * 1.15f;
            Render2D.drawRoundedRect(stack, cx - w / 2f, y, w, h, h / 2f, color);
        }
    }

    public void drawEye(Matrix3x2fStack stack, float cx, float cy, float size, Color color) {
        float ew = size * 0.54f;
        float eh = size * 0.32f;
        WindowGuiRender.drawRoundedOutlineRect(stack, cx - ew / 2f, cy - eh / 2f, ew, eh, eh / 2f, 1.4f, color);
        Render2D.drawCircle(stack, cx, cy, size * 0.10f, color);
    }

    public void drawPaw(Matrix3x2fStack stack, float cx, float cy, float size, Color color) {
        float pad = size * 0.11f;
        Render2D.drawCircle(stack, cx, cy + pad * 0.35f, size * 0.17f, color);
        Render2D.drawCircle(stack, cx - pad * 1.05f, cy - pad * 0.55f, size * 0.10f, color);
        Render2D.drawCircle(stack, cx - pad * 0.35f, cy - pad * 0.95f, size * 0.10f, color);
        Render2D.drawCircle(stack, cx + pad * 0.35f, cy - pad * 0.95f, size * 0.10f, color);
        Render2D.drawCircle(stack, cx + pad * 1.05f, cy - pad * 0.55f, size * 0.10f, color);
    }

    public void drawSparkle(Matrix3x2fStack stack, float cx, float cy, float size, Color color) {
        float arm = size * 0.22f;
        Render2D.drawRoundedRect(stack, cx - arm / 2f, cy - size * 0.34f, arm, size * 0.68f, arm / 2f, color);
        Render2D.drawRoundedRect(stack, cx - size * 0.34f, cy - arm / 2f, size * 0.68f, arm, arm / 2f, color);
        Render2D.drawCircle(stack, cx, cy, size * 0.11f, color);
    }

    /** Four-pointed diamond star for the COSMETICS header chip. */
    public void drawDiamondStar(Matrix3x2fStack stack, float cx, float cy, float size, Color color) {
        stack.pushMatrix();
        stack.translate(cx, cy);
        stack.rotate((float) Math.toRadians(45f));
        float arm = size * 0.20f;
        Render2D.drawRoundedRect(stack, -arm / 2f, -size * 0.36f, arm, size * 0.72f, arm / 2f, color);
        Render2D.drawRoundedRect(stack, -size * 0.36f, -arm / 2f, size * 0.72f, arm, arm / 2f, color);
        Render2D.drawCircle(stack, 0f, 0f, size * 0.10f, color);
        stack.popMatrix();
    }

    /** Плюс в круге — иконка заголовка COSMETICS на референсе. */
    public void drawPlus(Matrix3x2fStack stack, float cx, float cy, float size, Color color) {
        float stroke = Math.max(1.4f, size * 0.12f);
        Render2D.drawRoundedRect(stack, cx - stroke * 0.5f, cy - size * 0.22f, stroke, size * 0.44f, stroke * 0.5f, color);
        Render2D.drawRoundedRect(stack, cx - size * 0.22f, cy - stroke * 0.5f, size * 0.44f, stroke, stroke * 0.5f, color);
    }

    /** Палочка с искрой — вкладка Animation на референсе. */
    public void drawWand(Matrix3x2fStack stack, float cx, float cy, float size, Color color) {
        stack.pushMatrix();
        stack.translate(cx, cy);
        stack.rotate((float) Math.toRadians(-35f));
        float w = Math.max(1.6f, size * 0.11f);
        Render2D.drawRoundedRect(stack, -size * 0.06f, -size * 0.28f, w, size * 0.52f, w * 0.5f, color);
        stack.popMatrix();
        drawSparkle(stack, cx + size * 0.14f, cy - size * 0.22f, size * 0.38f, color);
    }

    public void drawRun(Matrix3x2fStack stack, float cx, float cy, float size, Color color) {
        float r = size * 0.07f;
        Render2D.drawCircle(stack, cx - size * 0.12f, cy - size * 0.18f, r, color);
        Render2D.drawRoundedRect(stack, cx - size * 0.06f, cy - size * 0.06f, size * 0.22f, size * 0.30f, r, color);
        Render2D.drawRoundedRect(stack, cx - size * 0.18f, cy + size * 0.10f, size * 0.12f, size * 0.22f, r, color);
        Render2D.drawRoundedRect(stack, cx + size * 0.02f, cy + size * 0.06f, size * 0.12f, size * 0.26f, r, color);
    }

    /** Плоский плащ — вкладка Cape / заголовок секции. */
    public void drawCape(Matrix3x2fStack stack, float cx, float cy, float size, Color color) {
        float w = size * 0.68f;
        float h = size * 0.78f;
        float top = cy - h * 0.42f;
        Render2D.drawRoundedRect(stack, cx - w * 0.5f, top, w, h, size * 0.08f, color);
        Color inner = CosmeticsTokens.alpha(color, 0.35f);
        float iw = w * 0.58f;
        float ih = h * 0.82f;
        Render2D.drawRoundedRect(stack, cx - iw * 0.5f, top + h * 0.08f, iw, ih, size * 0.06f, inner);
    }

    /** Цилиндр + поля — вкладка Hat. */
    public void drawHat(Matrix3x2fStack stack, float cx, float cy, float size, Color color) {
        float brimW = size * 0.62f;
        float brimH = size * 0.12f;
        float crownW = size * 0.40f;
        float crownH = size * 0.35f;
        Render2D.drawRoundedRect(stack, cx - brimW * 0.5f, cy + size * 0.12f, brimW, brimH, brimH * 0.5f, color);
        Render2D.drawRoundedRect(stack, cx - crownW * 0.5f, cy - size * 0.22f, crownW, crownH, size * 0.07f, color);
        Render2D.drawRoundedRect(stack, cx - crownW * 0.5f, cy + size * 0.04f, crownW, size * 0.07f, size * 0.035f,
                CosmeticsTokens.alpha(color, 0.5f));
    }

    /** Одно крыло — вкладка Wings. */
    public void drawWings(Matrix3x2fStack stack, float cx, float cy, float size, Color color) {
        float w = size * 0.34f;
        float h = size * 0.78f;
        Render2D.drawRoundedRect(stack, cx - w * 0.15f, cy - h * 0.42f, w, h, w * 0.45f, color);
        Color shade = CosmeticsTokens.alpha(color, 0.55f);
        Render2D.drawRoundedRect(stack, cx + w * 0.05f, cy - h * 0.36f, w * 0.82f, h * 0.88f, w * 0.4f, shade);
    }

    /** Крестик — кнопка закрытия. */
    public void drawClose(Matrix3x2fStack stack, float cx, float cy, float size, Color color) {
        float stroke = Math.max(1.6f, size * 0.10f);
        stack.pushMatrix();
        stack.translate(cx, cy);
        stack.rotate((float) Math.toRadians(45f));
        Render2D.drawRoundedRect(stack, -stroke * 0.5f, -size * 0.27f, stroke, size * 0.54f, stroke * 0.5f, color);
        stack.popMatrix();
        stack.pushMatrix();
        stack.translate(cx, cy);
        stack.rotate((float) Math.toRadians(-45f));
        Render2D.drawRoundedRect(stack, -stroke * 0.5f, -size * 0.27f, stroke, size * 0.54f, stroke * 0.5f, color);
        stack.popMatrix();
    }

    public void drawNoneSlash(DrawContext context, float cx, float cy, float size, Color color) {
        float r = size * 0.24f;
        Render2D.drawRing(context, cx, cy, r, Math.max(1.2f, size * 0.05f), color);
        var ms = context.getMatrices();
        ms.pushMatrix();
        ms.translate(cx, cy);
        ms.rotate((float) Math.toRadians(-45.0));
        Render2D.drawRoundedRect(context, -r * 0.95f, -size * 0.03f, r * 1.9f, size * 0.06f, size * 0.03f, color);
        ms.popMatrix();
    }

    public void drawNoneSlash(Matrix3x2fStack stack, float cx, float cy, float size, Color color) {
        float r = size * 0.24f;
        Render2D.drawRing(stack, cx, cy, r, Math.max(1.2f, size * 0.05f), color);
        stack.pushMatrix();
        stack.translate(cx, cy);
        stack.rotate((float) Math.toRadians(-45f));
        Render2D.drawRoundedRect(stack, -r * 0.95f, -size * 0.03f, r * 1.9f, size * 0.06f, size * 0.03f, color);
        stack.popMatrix();
    }

    public void drawCheckmark(DrawContext context, float cx, float cy, float size, Color color) {
        float stroke = Math.max(2f, size * 0.18f);
        var ms = context.getMatrices();
        ms.pushMatrix();
        ms.translate(cx, cy);
        ms.rotate((float) Math.toRadians(-4.0));
        Render2D.drawRoundedRect(context, -size * 0.32f, -size * 0.05f, size * 0.22f, stroke, stroke * 0.5f, color);
        ms.rotate((float) Math.toRadians(48.0));
        Render2D.drawRoundedRect(context, -stroke * 0.5f, -size * 0.36f, stroke, size * 0.40f, stroke * 0.5f, color);
        ms.popMatrix();
    }

    /** Галочка для активной вкладки — белый штрих в круге. */
    public void drawCheckmark(Matrix3x2fStack stack, float cx, float cy, float size, Color color) {
        float stroke = Math.max(2f, size * 0.18f);
        stack.pushMatrix();
        stack.translate(cx, cy);
        stack.rotate((float) Math.toRadians(-4f));
        Render2D.drawRoundedRect(stack, -size * 0.32f, -size * 0.05f, size * 0.22f, stroke, stroke * 0.5f, color);
        stack.rotate((float) Math.toRadians(48f));
        Render2D.drawRoundedRect(stack, -stroke * 0.5f, -size * 0.36f, stroke, size * 0.40f, stroke * 0.5f, color);
        stack.popMatrix();
    }

    public Color accent() {
        return CosmeticsTokens.PURPLE;
    }

    public Color muted() {
        return CosmeticsTokens.TEXT_MUTED;
    }
}
