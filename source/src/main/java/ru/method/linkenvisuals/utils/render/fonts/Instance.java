package ru.method.linkenvisuals.utils.render.fonts;

public record Instance(Font font, float size) {
    public float getWidth(String text) {
        return font.getWidth(text, size);
    }

    public float getHeight() {
        return font.getHeight(size);
    }
}
