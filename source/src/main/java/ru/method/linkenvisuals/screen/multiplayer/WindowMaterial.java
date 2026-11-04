package ru.method.linkenvisuals.screen.multiplayer;

import org.joml.Matrix3x2fStack;
import ru.method.linkenvisuals.screen.mainmenu.MenuChrome;
import ru.method.linkenvisuals.utils.render.ColorRGBA;
import ru.method.linkenvisuals.utils.render.Render2D;

import java.awt.Color;

/**
 * Материал окна экрана «Сетевая игра» по дизайн-хендоффу 4b — общий для главного окна и модалок.
 * Значения строго из спеки (уголки 14×14/1px/accent 0.65, блюр 14, штриховка -45° через 10px).
 */
public final class WindowMaterial {

    private WindowMaterial() {}

    public static final float RADIUS = 12f;
    private static final Color DANGER_BD = new Color(224, 110, 110, 140); // rgba(224,110,110,0.55)

    /**
     * Окно целиком: ореол+глубина, база, верхний акцентный подсвет, штриховка, блик и рамка.
     * Уголки-скобки рисуются отдельно ({@link #corners}) — поверх контента.
     */
    public static void window(Matrix3x2fStack ms, float x, float y, float w, float h) {
        // 1. Глубина 0 30px 80px -30px rgba(0,0,0,0.8) — тёмная тень со смещением вниз.
        for (int i = 6; i >= 1; i--) {
            float sp = i * 2.6f;
            Render2D.drawRoundedRect(ms, x - sp, y - sp + 10f, w + sp * 2f, h + sp * 2f, RADIUS + sp,
                    new Color(0, 0, 0, Math.round(10f - i * 0.9f)));
        }
        // Неоновый ореол 0 0 70px -28px rgba(accent,0.35) — деликатное свечение по периметру
        // (уменьшено: сильный ореол засвечивал тонкие уголки-скобки).
        MenuChrome.glow(ms, x, y, w, h, RADIUS, MenuChrome.accentA(0.16f), 0f, 6, 2.6f);

        // 1.5 Backdrop-filter blur 14px за окном (спека): матовое стекло под полупрозрачной базой.

        Render2D.drawBlurredRect(ms, x, y, w, h, RADIUS, 14f, new Color(4, 7, 6, 26), true);

        // 2. База linear-gradient(165deg, rgba(17,26,21,0.9), rgba(7,10,9,0.86) 45%, rgba(5,8,7,0.9)).
        // ОДНИМ градиентом на всю высоту: два скруглённых прямоугольника (0-45% и 45-100%) давали
        // видимый шов на стыке — щель между полупрозрачными слоями через всю ширину окна.
        Render2D.drawRoundedVerticalGradient(ms, x, y, w, h, RADIUS,
                new Color(17, 26, 21, 230), new Color(5, 8, 7, 230));

        // 3. Верхний акцентный подсвет — на ВСЮ ширину окна, затухает вниз. Скругление = радиусу
        // окна, поэтому углы градиента повторяют углы базы и ничего не вылезает наружу (прежний
        // вариант с инсетом оставлял тёмные «плечи» по краям узких окон).
        float glowH = Math.min(240f, h);
        Render2D.drawRoundedVerticalGradient(ms, x, y, w, glowH, RADIUS,
                MenuChrome.accentA(0.07f), MenuChrome.accentA(0f));

        // 4. Диагональная штриховка -45°, 1px через каждые 10px, rgba(255,255,255,0.014).
        hatching(ms, x, y, w, h);

        // 5. inset 0 1px 0 rgba(255,255,255,0.05) + рамка rgba(255,255,255,0.09).
        Render2D.drawRoundedRect(ms, x + RADIUS, y + 1f, w - RADIUS * 2f, 1f, 0.5f, MenuChrome.white(0.05f));
        Render2D.drawBorder(ms, x, y, w, h, RADIUS, 1.0f, 1.4f, MenuChrome.white(0.09f));
    }

    /** repeating-linear-gradient(-45deg, rgba(255,255,255,0.014) 0 1px, transparent 1px 10px). */
    private static void hatching(Matrix3x2fStack ms, float x, float y, float w, float h) {
        ColorRGBA c = new ColorRGBA(MenuChrome.white(0.014f));
        float step = 10f * 1.4142f; // шаг по нормали к линиям -45°
        for (float d = step; d < w + h; d += step) {
            float x1 = x + Math.max(0f, d - h), y1 = y + Math.min(d, h);
            float x2 = x + Math.min(d, w), y2 = y + Math.max(0f, d - w);
            Render2D.drawLine(ms, x1, y1, x2, y2, 1f, c);
        }
    }

    /** Уголки-скобки как в ClickGUI: длина 20px, толщина 2px, accent 0.93 через drawRoundedRect
     *  (2px рендерится чётко; тонкий 1px рендерился «сломанно»). Отступ 14, правый — на 2px ниже/правее. */
    public static void corners(Matrix3x2fStack ms, float x, float y, float w, float h) {
        Color c = MenuChrome.accentA(0.93f);
        float s = 20f, th = 2f, o = 14f;
        // левый-верхний ┌
        float tlx = x + o, tly = y + o;
        Render2D.drawRoundedRect(ms, tlx, tly, s, th, th / 2f, c);
        Render2D.drawRoundedRect(ms, tlx, tly, th, s, th / 2f, c);
        // правый-нижний ┘ (на 2px ниже и правее — как в ClickGUI)
        float brx = x + w - o + 2f, bry = y + h - o + 2f;
        Render2D.drawRoundedRect(ms, brx - s, bry - th, s, th, th / 2f, c);
        Render2D.drawRoundedRect(ms, brx - th, bry - s, th, s, th / 2f, c);
    }

    /**
     * Поле/иконка-кнопка: фон rgba(255,255,255,0.04), рамка rgba(255,255,255,0.08) → 0.24 на ховере,
     * radius 7. Общий материал поиска, полей форм и иконок-кнопок футера.
     */
    public static void fieldBox(Matrix3x2fStack ms, float x, float y, float w, float h, float hoverT, boolean danger) {
        Render2D.drawRoundedRect(ms, x, y, w, h, 7f, MenuChrome.white(0.04f));
        Color bd = danger
                ? MenuChrome.lerp(MenuChrome.white(0.08f), DANGER_BD, hoverT)
                : MenuChrome.lerp(MenuChrome.white(0.08f), MenuChrome.white(0.24f), hoverT);
        Render2D.drawBorder(ms, x, y, w, h, 7f, 1.0f, 1.4f, bd);
    }
}
