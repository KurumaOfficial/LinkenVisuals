package ru.method.linkenvisuals.screen.clickgui.redesign;

import org.joml.Matrix3x2fStack;
import ru.method.linkenvisuals.utils.render.Render2D;
import ru.method.linkenvisuals.utils.render.fonts.Font;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;
import ru.method.linkenvisuals.utils.render.fonts.Instance;

import java.awt.Color;

/**
 * UI-иконки нового ClickGUI/редизайна. Все — глифы MSDF-атласа {@link Fonts#ICONSFA}
 * (FontAwesome Solid → mtsdf): раньше большинство рисовалось ломаными из drawLine, из-за чего
 * на стыках сегментов были зазубрины, а компенсация толщины штриха под pixelScale давала
 * неровные утолщения. Глифы рендерятся тем же MSDF-шейдером, что и весь текст мода —
 * гладко на любом масштабе, центровка единая для всех иконок.
 *
 * <p>API прежний: (ms, cx, cy, size, color), где (cx, cy) — ЦЕНТР иконки.</p>
 */
public final class ClickGuiIcons {
    private ClickGuiIcons() {}

    /**
     * Оставлено для совместимости вызовов: раньше подкручивало толщину векторного штриха под
     * суб-пиксельный масштаб экрана. Глифам MSDF компенсация не нужна.
     */
    public static void pixelScale(float s) { }

    /**
     * Центрированная отрисовка глифа в точке (cx, cy). По вертикали центрируем видимую часть
     * глифа: у FontAwesome бокс строки asc..desc чуть ниже оптического центра (descender −0.125em),
     * компенсируем его половиной — иначе все иконки сидели бы на 5–6 % ниже центра кнопок.
     */
    private static void glyph(Matrix3x2fStack ms, Font font, String ch, float cx, float cy, float size, Color c) {
        Instance inst = font.getFont(size);
        float lift = font == Fonts.ICONSFA ? size * 0.0625f : 0f;
        Render2D.drawFont(ms, inst, ch, cx - font.getWidth(ch, size) / 2f,
                cy - font.getHeight(size) / 2f - lift, c);
    }

    private static void fa(Matrix3x2fStack ms, char cp, float cx, float cy, float size, Color c) {
        glyph(ms, Fonts.ICONSFA, String.valueOf(cp), cx, cy, size, c);
    }

    public static void search(Matrix3x2fStack ms, float cx, float cy, float size, Color c)         { fa(ms, '\uF002', cx, cy, size, c); }
    public static void sliders(Matrix3x2fStack ms, float cx, float cy, float size, Color c)        { fa(ms, '\uF1DE', cx, cy, size, c); }
    public static void moreHorizontal(Matrix3x2fStack ms, float cx, float cy, float size, Color c) { fa(ms, '\uF141', cx, cy, size, c); }
    public static void close(Matrix3x2fStack ms, float cx, float cy, float size, Color c)          { fa(ms, '\uF00D', cx, cy, size * 0.92f, c); }
    public static void check(Matrix3x2fStack ms, float cx, float cy, float size, Color c)          { fa(ms, '\uF00C', cx, cy, size * 0.92f, c); }
    public static void chevronDown(Matrix3x2fStack ms, float cx, float cy, float size, Color c)    { fa(ms, '\uF078', cx, cy, size * 0.85f, c); }
    public static void chevronUp(Matrix3x2fStack ms, float cx, float cy, float size, Color c)      { fa(ms, '\uF077', cx, cy, size * 0.85f, c); }
    public static void chevronLeft(Matrix3x2fStack ms, float cx, float cy, float size, Color c)    { fa(ms, '\uF053', cx, cy, size * 0.85f, c); }
    public static void users(Matrix3x2fStack ms, float cx, float cy, float size, Color c)          { fa(ms, '\uF0C0', cx, cy, size, c); }
    public static void mapPin(Matrix3x2fStack ms, float cx, float cy, float size, Color c)         { fa(ms, '\uF3C5', cx, cy, size, c); }
    public static void arrowLeft(Matrix3x2fStack ms, float cx, float cy, float size, Color c)      { fa(ms, '\uF060', cx, cy, size * 0.9f, c); }
    public static void arrowRight(Matrix3x2fStack ms, float cx, float cy, float size, Color c)     { fa(ms, '\uF061', cx, cy, size * 0.9f, c); }
    public static void plus(Matrix3x2fStack ms, float cx, float cy, float size, Color c)           { fa(ms, '\uF067', cx, cy, size * 0.9f, c); }
    public static void gear(Matrix3x2fStack ms, float cx, float cy, float size, Color c)           { fa(ms, '\uF013', cx, cy, size, c); }
    public static void trash(Matrix3x2fStack ms, float cx, float cy, float size, Color c)          { fa(ms, '\uF2ED', cx, cy, size * 0.95f, c); }
    public static void link(Matrix3x2fStack ms, float cx, float cy, float size, Color c)           { fa(ms, '\uF0C1', cx, cy, size * 0.95f, c); }
    public static void refresh(Matrix3x2fStack ms, float cx, float cy, float size, Color c)        { fa(ms, '\uF021', cx, cy, size * 0.95f, c); }
    public static void list(Matrix3x2fStack ms, float cx, float cy, float size, Color c)           { fa(ms, '\uF03A', cx, cy, size * 0.95f, c); }
    public static void wifi(Matrix3x2fStack ms, float cx, float cy, float size, Color c)           { fa(ms, '\uF1EB', cx, cy, size, c); }
    public static void droplet(Matrix3x2fStack ms, float cx, float cy, float size, Color c)        { fa(ms, '\uF043', cx, cy, size, c); }
    public static void user(Matrix3x2fStack ms, float cx, float cy, float size, Color c)           { fa(ms, '\uF007', cx, cy, size, c); }
    public static void musicNote(Matrix3x2fStack ms, float cx, float cy, float size, Color c)      { fa(ms, '\uF001', cx, cy, size, c); }
    public static void monitor(Matrix3x2fStack ms, float cx, float cy, float size, Color c)        { fa(ms, '\uF108', cx, cy, size, c); }
    public static void keyboard(Matrix3x2fStack ms, float cx, float cy, float size, Color c)       { fa(ms, '\uF11C', cx, cy, size, c); }
    public static void globe(Matrix3x2fStack ms, float cx, float cy, float size, Color c)          { fa(ms, '\uF0AC', cx, cy, size, c); }
    public static void chat(Matrix3x2fStack ms, float cx, float cy, float size, Color c)           { fa(ms, '\uF075', cx, cy, size, c); }
    public static void layers(Matrix3x2fStack ms, float cx, float cy, float size, Color c)         { fa(ms, '\uF5FD', cx, cy, size, c); }
    public static void accessibility(Matrix3x2fStack ms, float cx, float cy, float size, Color c)  { fa(ms, '\uF29A', cx, cy, size, c); }
    public static void database(Matrix3x2fStack ms, float cx, float cy, float size, Color c)       { fa(ms, '\uF1C0', cx, cy, size, c); }
    public static void info(Matrix3x2fStack ms, float cx, float cy, float size, Color c)           { fa(ms, '\uF05A', cx, cy, size, c); }
    public static void play          (Matrix3x2fStack ms, float cx, float cy, float size, Color c) { fa(ms, '\uF04B', cx, cy, size * 0.9f, c); }
    public static void flag          (Matrix3x2fStack ms, float cx, float cy, float size, Color c) { fa(ms, '\uF024', cx, cy, size, c); }
    public static void chartBar      (Matrix3x2fStack ms, float cx, float cy, float size, Color c) { fa(ms, '\uF080', cx, cy, size, c); }
    public static void trophy        (Matrix3x2fStack ms, float cx, float cy, float size, Color c) { fa(ms, '\uF091', cx, cy, size, c); }
    public static void cube          (Matrix3x2fStack ms, float cx, float cy, float size, Color c) { fa(ms, '\uF1B2', cx, cy, size, c); }
    public static void signOut       (Matrix3x2fStack ms, float cx, float cy, float size, Color c) { fa(ms, '\uF08B', cx, cy, size * 0.95f, c); }
}
