package ru.method.linkenvisuals.screen.windowgui;

import java.awt.Color;

public final class WindowGuiColors {
    private WindowGuiColors() {}

    public static Color withAlpha(Color color, float alpha) {
        if (color == null) return new Color(0, 0, 0, 0);
        int a = Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f);
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), a);
    }

    public static Color withAlpha(int argb, float alpha) {
        Color color = new Color(argb, true);
        return withAlpha(color, alpha);
    }

    public static Color lerp(Color from, Color to, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = Math.round(from.getRed() + (to.getRed() - from.getRed()) * t);
        int g = Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * t);
        int b = Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * t);
        int a = Math.round(from.getAlpha() + (to.getAlpha() - from.getAlpha()) * t);
        return new Color(r, g, b, a);
    }

    public static Color whiteAlpha(float alpha) {
        return withAlpha(Color.WHITE, alpha);
    }
}
