package ru.method.linkenvisuals.screen.windowgui;

import ru.method.linkenvisuals.utils.render.ColorRGBA;
import ru.method.linkenvisuals.utils.render.Render2D;
import ru.method.linkenvisuals.utils.render.fonts.Font;
import ru.method.linkenvisuals.utils.render.fonts.Instance;

import java.awt.Color;

public final class WindowGuiRender {
    private WindowGuiRender() {}

    public static void drawPlainRect(Object stack, float x, float y, float width, float height, Color color) {
        Render2D.drawRoundedRect(stack, x, y, width, height, 0f, color);
    }

    public static void drawRoundedRect(Object stack, float x, float y, float width, float height, float radius, Color color) {
        Render2D.drawRoundedRect(stack, x, y, width, height, radius, color);
    }

    public static void drawRoundedOutlineRect(Object stack, float x, float y, float width, float height, float radius, float thickness, Color color) {
        WindowGuiOutlineRenderer.drawSolidOutline(stack, x, y, width, height, radius, thickness, color);
    }

    public static void drawRoundedOutlineVerLinearGradientRect(Object stack, float x, float y, float width, float height, float radius, float thickness, Color top, Color bottom) {
        WindowGuiOutlineRenderer.drawVerGradientOutline(stack, x, y, width, height, radius, thickness, top, bottom);
    }

    public static void drawRoundedOutlineHorLinearGradientRect(Object stack, float x, float y, float width, float height, float radius, float thickness, Color left, Color right) {
        WindowGuiOutlineRenderer.drawHorGradientOutline(stack, x, y, width, height, radius, thickness, left, right);
    }

    public static void drawRoundedDiagonalGradientRect(Object stack, float x, float y, float width, float height, float radius, Color first, Color second) {
        ColorRGBA c1 = new ColorRGBA(first);
        ColorRGBA c2 = new ColorRGBA(second);
        Render2D.drawRect(stack, x, y, width, height, radius, c1, c2, c2, c1);
    }

    public static void drawRoundedOutlineDiagonalGradientRect(Object stack, float x, float y, float width, float height, float radius, float thickness, Color first, Color second) {
        WindowGuiOutlineRenderer.drawDiagonalGradientOutline(stack, x, y, width, height, radius, thickness, first, second);
    }

    public static void drawRoundedVerLinearGradientRect(Object stack, float x, float y, float width, float height, float radius, Color top, Color bottom) {
        Render2D.drawRoundedVerticalGradient(stack, x, y, width, height, radius, top, bottom);
    }

    public static void drawRoundedHorLinearGradientRect(Object stack, float x, float y, float width, float height, float radius, Color left, Color right) {
        Render2D.drawRoundedHorizontalGradient(stack, x, y, width, height, radius, left, right);
    }

    public static void drawText(Object stack, Font font, String text, float x, float y, Color color, float size) {
        Render2D.drawFont(stack, font.getFont(size), text, x, y, color);
    }

    public static void drawCenteredText(Object stack, Font font, String text, float centerX, float y, Color color, float size) {
        Instance instance = font.getFont(size);
        float width = instance.getWidth(text);
        Render2D.drawFont(stack, instance, text, centerX - width / 2f, y, color);
    }

    public static float textWidth(Font font, String text, float size) {
        return font.getWidth(text, size);
    }

    public static float textHeight(Font font, float size) {
        return font.getHeight(size);
    }
}
