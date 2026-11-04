package ru.method.linkenvisuals.screen.cosmetics.widget;

import net.minecraft.client.gui.DrawContext;
import ru.method.linkenvisuals.screen.cosmetics.CosmeticsWingsColorPicker;

/** Wings / slot tint — swatch + RGB spectrum slider + draggable thumb. */
public final class CosmeticsColorTintWidget {
    // Виджет показывается только на вкладке WINGS, поэтому подпись — цвет крыльев.
    private static final String LABEL_KEY = "cosmetics.wings.tint";

    private final CosmeticsWingsColorPicker picker;

    public CosmeticsColorTintWidget(CosmeticsWingsColorPicker picker) {
        this.picker = picker;
    }

    // Параметр scale сейчас всегда передаётся вызывающими как 1f (масштабирование пикера пока не
    // реализовано), поэтому он игнорируется и делегируется к базовому API CosmeticsWingsColorPicker.
    public float referenceHeight(float scale) {
        return picker.expandedHeight();
    }

    public void updateDrag(int mouseX, int mouseY, float x, float y, float width, float scale) {
        picker.updateDrag(mouseX, mouseY, x, y, width);
    }

    public void render(DrawContext context, float x, float y, float width, float scale,
                       int mouseX, int mouseY, int alpha) {
        picker.render(context, x, y, width, mouseX, mouseY, alpha, LABEL_KEY);
    }

    public boolean mouseClicked(float x, float y, float width, float scale,
                                double mouseX, double mouseY, int button) {
        return picker.mouseClicked(x, y, width, mouseX, mouseY, button);
    }

    public boolean mouseReleased(int button) {
        return picker.mouseReleased(button);
    }

    public boolean consumeDirty() {
        return picker.consumeDirty();
    }

    public int colorArgb() {
        return picker.colorArgb();
    }

    public void setColor(int argb) {
        picker.setColor(argb);
    }

    public boolean isAdjusting() {
        return picker.isAdjusting();
    }
}
