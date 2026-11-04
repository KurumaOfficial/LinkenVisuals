package ru.method.linkenvisuals.screen.cosmetics.widget;

/** Axis-aligned bounds in canvas design-space (1120×700). */
public record CosmeticsBounds(float x, float y, float w, float h) {
    public boolean contains(float mx, float my) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
