package ru.method.linkenvisuals.modules.impl.render;

import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.impl.ColorSetting;

import java.awt.Color;

/**
 * Меняет цвет неба на выбранный. Обычный колорпикер (с альфой): альфа цвета = прозрачность подмены —
 * при 255 небо полностью перекрашивается, при 0 остаётся исходным, между — плавное смешивание.
 * Подмена делается в {@code ClientWorldMixin#getSkyColor}.
 */
public class SkyChanger extends Module {
    private final ColorSetting color = new ColorSetting("modules.settings.skychanger.color", new Color(120, 60, 200, 180));

    public SkyChanger() {
        super("SkyChanger", Category.Visuals);
    }

    /** Смешивает исходный цвет неба (0xRRGGBB) с выбранным по альфе (альфа = сила подмены / прозрачность). */
    public int applySkyColor(int original) {
        Color chosen = color.getValue();
        float a = chosen.getAlpha() / 255f;
        int or = (original >> 16) & 0xFF;
        int og = (original >> 8) & 0xFF;
        int ob = original & 0xFF;
        int r = clamp(Math.round(or * (1f - a) + chosen.getRed() * a));
        int g = clamp(Math.round(og * (1f - a) + chosen.getGreen() * a));
        int b = clamp(Math.round(ob * (1f - a) + chosen.getBlue() * a));
        return (r << 16) | (g << 8) | b;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
