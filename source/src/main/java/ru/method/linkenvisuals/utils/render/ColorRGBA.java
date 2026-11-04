package ru.method.linkenvisuals.utils.render;

import java.awt.Color;

public record ColorRGBA(int red, int green, int blue, int alpha) {

    public static ColorRGBA of(int red, int green, int blue, int alpha) {
        return new ColorRGBA(red, green, blue, alpha);
    }

    public static ColorRGBA of(int red, int green, int blue) {
        return new ColorRGBA(red, green, blue, 255);
    }

    public ColorRGBA withAlpha(int alpha) {
        return new ColorRGBA(red, green, blue, alpha);
    }

    public int getR() {
        return red;
    }

    public int getG() {
        return green;
    }

    public int getB() {
        return blue;
    }

    public int getA() {
        return alpha;
    }

    public ColorRGBA(Color color) {
        this(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
    }

    public Color toAwtColor() {
        return new Color(
                clamp(red),
                clamp(green),
                clamp(blue),
                clamp(alpha)
        );
    }

    private int clamp(int channel) {
        if (channel < 0) return 0;
        if (channel > 255) return 255;
        return channel;
    }
}
