package ru.method.linkenvisuals.screen.windowgui;

import ru.method.linkenvisuals.utils.render.Render2D;

import java.awt.Color;

public final class WindowGuiOutlineRenderer {
    private WindowGuiOutlineRenderer() {}

    public static void drawVerGradientOutline(Object stack, float x, float y, float width, float height,
                                       float round, float outlineWidth, Color top, Color bottom) {
        Render2D.drawOutlineGradient(stack, x, y, width, height, round, outlineWidth,
                top, bottom, bottom, top);
    }

    public static void drawHorGradientOutline(Object stack, float x, float y, float width, float height,
                                       float round, float outlineWidth, Color left, Color right) {
        Render2D.drawOutlineGradient(stack, x, y, width, height, round, outlineWidth,
                left, right, left, right);
    }

    public static void drawDiagonalGradientOutline(Object stack, float x, float y, float width, float height,
                                            float round, float outlineWidth, Color first, Color second) {
        Color corner = WindowGuiColors.lerp(first, second, 0.5f);
        Render2D.drawOutlineGradient(stack, x, y, width, height, round, outlineWidth,
                first, second, corner, corner);
    }

    public static void drawSolidOutline(Object stack, float x, float y, float width, float height,
                                 float round, float outlineWidth, Color color) {
        Render2D.drawOutline(stack, x, y, width, height, round, outlineWidth, color);
    }
}
