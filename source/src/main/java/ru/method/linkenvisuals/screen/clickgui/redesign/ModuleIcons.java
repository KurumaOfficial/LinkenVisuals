package ru.method.linkenvisuals.screen.clickgui.redesign;

import org.joml.Matrix3x2fStack;
import ru.method.linkenvisuals.utils.render.ColorRGBA;
import ru.method.linkenvisuals.utils.render.Render2D;
import ru.method.linkenvisuals.utils.render.RoundedStrokeBatch;

import java.awt.Color;
import java.util.Locale;

/**
 * Штриховые (stroke) иконки модулей ClickGUI — порт SVG из design_handoff_linken_clickgui_7c/icons/.
 * Все координаты — из 24×24 viewBox, stroke-width 1.8 (в долях viewBox), round caps; рисуются
 * примитивами {@link Render2D} (линии/дуги/кольца) и центрируются в (cx, cy), масштаб к size.
 *
 * <p>{@link #draw} возвращает {@code true}, если для модуля есть штриховая иконка; иначе вызывающая
 * сторона рисует глиф-фолбэк (иконку категории), НЕ букву.</p>
 */
public final class ModuleIcons {
    private ModuleIcons() {}

    // Компенсация суб-пиксельного рендера (весь LinkGui рисуется под ms.scale(s), s может быть < 1):
    // тонкий design-штрих иначе < 1px device и «исчезает». stroke = max(1.15/pixelScale, size*0.075).
    private static float pixelScale = 1f;
    public static void pixelScale(float s) { pixelScale = s > 0.01f ? s : 1f; }
    private static float stroke(float size) { return Math.max(1.15f / pixelScale, size * 0.075f); }

    private static float sx(float cx, float size, float x) { return cx - size / 2f + x / 24f * size; }
    private static float sy(float cy, float size, float y) { return cy - size / 2f + y / 24f * size; }

    private static void line(Matrix3x2fStack ms, float cx, float cy, float size, float x1, float y1, float x2, float y2, Color c) {
        Render2D.drawLine(ms, sx(cx, size, x1), sy(cy, size, y1), sx(cx, size, x2), sy(cy, size, y2), stroke(size), new ColorRGBA(c));
    }

    private static void poly(Matrix3x2fStack ms, float cx, float cy, float size, Color c, float... pts) {
        for (int i = 0; i + 3 < pts.length; i += 2) {
            line(ms, cx, cy, size, pts[i], pts[i + 1], pts[i + 2], pts[i + 3], c);
        }
    }

    private static void dot(Matrix3x2fStack ms, float cx, float cy, float size, float x, float y, float r, Color c) {
        RoundedStrokeBatch.flush();
        Render2D.drawCircle(ms, sx(cx, size, x), sy(cy, size, y), r / 24f * size, c);
    }

    private static void ring(Matrix3x2fStack ms, float cx, float cy, float size, float x, float y, float r, Color c) {
        RoundedStrokeBatch.flush();
        Render2D.drawRing(ms, sx(cx, size, x), sy(cy, size, y), r / 24f * size, stroke(size), c);
    }

    /** Дуга полилинией в координатах viewBox (ось Y вниз: 0°=вправо, 90°=вниз). */
    private static void arc(Matrix3x2fStack ms, float cx, float cy, float size,
                            float ax, float ay, float r, float startDeg, float endDeg, Color c) {
        int seg = Math.max(4, Math.round(Math.abs(endDeg - startDeg) / 14f));
        float px = 0f, py = 0f;
        for (int i = 0; i <= seg; i++) {
            float t = (float) i / seg;
            double a = Math.toRadians(startDeg + (endDeg - startDeg) * t);
            float nx = ax + (float) Math.cos(a) * r;
            float ny = ay + (float) Math.sin(a) * r;
            if (i > 0) line(ms, cx, cy, size, px, py, nx, ny, c);
            px = nx; py = ny;
        }
    }

    /**
     * Рисует штриховую иконку модуля. Возвращает false, если для этого модуля порта нет —
     * тогда вызывающая сторона рисует глиф-фолбэк.
     */
    public static boolean draw(Matrix3x2fStack ms, String moduleName, float cx, float cy, float size, Color c) {
        RoundedStrokeBatch.begin();
        try {
            return drawIcon(ms, moduleName, cx, cy, size, c);
        } finally {
            RoundedStrokeBatch.end();
        }
    }

    private static boolean drawIcon(Matrix3x2fStack ms, String moduleName, float cx, float cy, float size, Color c) {
        if (moduleName == null) return false;
        switch (moduleName.toLowerCase(Locale.ROOT)) {
            case "sprint" -> { sprint(ms, cx, cy, size, c); return true; }
            case "trajectories" -> { trajectories(ms, cx, cy, size, c); return true; }
            case "chinahat" -> { chinaHat(ms, cx, cy, size, c); return true; }
            case "movementhelper" -> { movementHelper(ms, cx, cy, size, c); return true; }
            case "discordrpc" -> { discord(ms, cx, cy, size, c); return true; }
            case "norender" -> { noRender(ms, cx, cy, size, c); return true; }
            case "itemscroller" -> { mouse(ms, cx, cy, size, c); return true; }
            case "targetesp" -> { target(ms, cx, cy, size, c); return true; }
            case "allskins" -> { users(ms, cx, cy, size, c); return true; }
            case "hitrange" -> { dotTarget(ms, cx, cy, size, c); return true; }
            // Дополнительные силуэты для частых модулей (тот же стиль: 24 viewBox, 1–3 штриха).
            case "zoom" -> { zoom(ms, cx, cy, size, c); return true; }
            case "fullbright" -> { sun(ms, cx, cy, size, c); return true; }
            case "crosshair" -> { crosshair(ms, cx, cy, size, c); return true; }
            case "freelook" -> { eye(ms, cx, cy, size, c); return true; }
            case "timechanger" -> { clock(ms, cx, cy, size, c); return true; }
            case "skychanger" -> { cloud(ms, cx, cy, size, c); return true; }
            default -> { return false; }
        }
    }

    // ── порт SVG ──

    /** wind — три ветровые линии с завитком (icons/sprint.svg). */
    private static void sprint(Matrix3x2fStack ms, float cx, float cy, float size, Color c) {
        line(ms, cx, cy, size, 2f, 8f, 10f, 8f, c);
        arc(ms, cx, cy, size, 10f, 6.2f, 1.9f, 60f, 320f, c);
        line(ms, cx, cy, size, 2f, 16f, 13f, 16f, c);
        arc(ms, cx, cy, size, 13f, 17.8f, 1.9f, -60f, 210f, c);
        line(ms, cx, cy, size, 2f, 12f, 18f, 12f, c);
        arc(ms, cx, cy, size, 18f, 9.9f, 2.1f, 40f, 300f, c);
    }

    /** trending-up: линия-график + уголок-стрелка (icons/trajectories.svg). */
    private static void trajectories(Matrix3x2fStack ms, float cx, float cy, float size, Color c) {
        poly(ms, cx, cy, size, c, 23f, 6f, 13.5f, 15.5f, 8.5f, 10.5f, 1f, 18f);
        poly(ms, cx, cy, size, c, 17f, 6f, 23f, 6f, 23f, 12f);
    }

    /** треугольная шляпа + две ножки (icons/chinahat.svg). */
    private static void chinaHat(Matrix3x2fStack ms, float cx, float cy, float size, Color c) {
        poly(ms, cx, cy, size, c, 12f, 4f, 22f, 18f, 2f, 18f, 12f, 4f);
        line(ms, cx, cy, size, 8f, 18f, 8f, 20f, c);
        line(ms, cx, cy, size, 16f, 18f, 16f, 20f, c);
    }

    /** move: крест + четыре стрелки (icons/movementhelper.svg). */
    private static void movementHelper(Matrix3x2fStack ms, float cx, float cy, float size, Color c) {
        line(ms, cx, cy, size, 2f, 12f, 22f, 12f, c);
        line(ms, cx, cy, size, 12f, 2f, 12f, 22f, c);
        poly(ms, cx, cy, size, c, 5f, 9f, 2f, 12f, 5f, 15f);
        poly(ms, cx, cy, size, c, 9f, 5f, 12f, 2f, 15f, 5f);
        poly(ms, cx, cy, size, c, 15f, 19f, 12f, 22f, 9f, 19f);
        poly(ms, cx, cy, size, c, 19f, 9f, 22f, 12f, 19f, 15f);
    }

    /** речевой пузырь (icons/discordrpc.svg). */
    private static void discord(Matrix3x2fStack ms, float cx, float cy, float size, Color c) {
        arc(ms, cx, cy, size, 12.5f, 11f, 8f, 135f, 400f, c);
        // хвостик к нижне-левому углу
        poly(ms, cx, cy, size, c, 6.3f, 16.6f, 3f, 21f, 8.4f, 18.6f);
    }

    /** eye-off: глаз + зрачок + перечёркивающая линия (icons/norender.svg). */
    private static void noRender(Matrix3x2fStack ms, float cx, float cy, float size, Color c) {
        arc(ms, cx, cy, size, 12f, 20f, 9.2f, 235f, 305f, c); // верхнее веко
        arc(ms, cx, cy, size, 12f, 4f, 9.2f, 55f, 125f, c);   // нижнее веко
        ring(ms, cx, cy, size, 12f, 12f, 2.7f, c);
        line(ms, cx, cy, size, 2f, 2f, 22f, 22f, c);
    }

    /** мышь: скруглённое тело + колесо (icons/itemscroller.svg). */
    private static void mouse(Matrix3x2fStack ms, float cx, float cy, float size, Color c) {
        line(ms, cx, cy, size, 5f, 9f, 5f, 15f, c);
        line(ms, cx, cy, size, 19f, 9f, 19f, 15f, c);
        arc(ms, cx, cy, size, 12f, 9f, 7f, 180f, 360f, c);
        arc(ms, cx, cy, size, 12f, 15f, 7f, 0f, 180f, c);
        line(ms, cx, cy, size, 12f, 5.5f, 12f, 9.5f, c);
    }

    /** прицел-мишень: кольцо + 4 засечки (icons/targetesp.svg). */
    private static void target(Matrix3x2fStack ms, float cx, float cy, float size, Color c) {
        ring(ms, cx, cy, size, 12f, 12f, 10f, c);
        line(ms, cx, cy, size, 22f, 12f, 18f, 12f, c);
        line(ms, cx, cy, size, 6f, 12f, 2f, 12f, c);
        line(ms, cx, cy, size, 12f, 6f, 12f, 2f, c);
        line(ms, cx, cy, size, 12f, 22f, 12f, 18f, c);
    }

    /** два человека (icons/allskins.svg). */
    private static void users(Matrix3x2fStack ms, float cx, float cy, float size, Color c) {
        ring(ms, cx, cy, size, 9f, 7f, 4f, c);
        arc(ms, cx, cy, size, 9f, 21f, 8f, 200f, 340f, c);   // плечи основного
        arc(ms, cx, cy, size, 16.5f, 7f, 3.6f, 300f, 480f, c); // голова второго (часть)
        arc(ms, cx, cy, size, 18.5f, 21f, 6f, 210f, 330f, c);  // плечо второго
    }

    /** мишень с точкой (icons/hitrange.svg). */
    private static void dotTarget(Matrix3x2fStack ms, float cx, float cy, float size, Color c) {
        ring(ms, cx, cy, size, 12f, 12f, 9f, c);
        dot(ms, cx, cy, size, 12f, 12f, 2.2f, c);
    }

    // ── доп. силуэты ──

    /** лупа (Zoom). */
    private static void zoom(Matrix3x2fStack ms, float cx, float cy, float size, Color c) {
        ring(ms, cx, cy, size, 10.5f, 10.5f, 6.5f, c);
        line(ms, cx, cy, size, 15.2f, 15.2f, 21f, 21f, c);
    }

    /** солнце (Fullbright). */
    private static void sun(Matrix3x2fStack ms, float cx, float cy, float size, Color c) {
        ring(ms, cx, cy, size, 12f, 12f, 4.2f, c);
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(i * 45);
            line(ms, cx, cy, size, 12f + (float) Math.cos(a) * 7.2f, 12f + (float) Math.sin(a) * 7.2f,
                    12f + (float) Math.cos(a) * 9.6f, 12f + (float) Math.sin(a) * 9.6f, c);
        }
    }

    /** прицел + ; (Crosshair). */
    private static void crosshair(Matrix3x2fStack ms, float cx, float cy, float size, Color c) {
        ring(ms, cx, cy, size, 12f, 12f, 7f, c);
        line(ms, cx, cy, size, 12f, 2.5f, 12f, 7f, c);
        line(ms, cx, cy, size, 12f, 17f, 12f, 21.5f, c);
        line(ms, cx, cy, size, 2.5f, 12f, 7f, 12f, c);
        line(ms, cx, cy, size, 17f, 12f, 21.5f, 12f, c);
    }

    /** глаз (Freelook). */
    private static void eye(Matrix3x2fStack ms, float cx, float cy, float size, Color c) {
        arc(ms, cx, cy, size, 12f, 19.5f, 9.5f, 235f, 305f, c);
        arc(ms, cx, cy, size, 12f, 4.5f, 9.5f, 55f, 125f, c);
        ring(ms, cx, cy, size, 12f, 12f, 2.7f, c);
    }

    /** часы (TimeChanger). */
    private static void clock(Matrix3x2fStack ms, float cx, float cy, float size, Color c) {
        ring(ms, cx, cy, size, 12f, 12f, 9f, c);
        line(ms, cx, cy, size, 12f, 12f, 12f, 7f, c);
        line(ms, cx, cy, size, 12f, 12f, 15.5f, 13.5f, c);
    }

    /** облако (SkyChanger). */
    private static void cloud(Matrix3x2fStack ms, float cx, float cy, float size, Color c) {
        arc(ms, cx, cy, size, 9f, 12f, 4f, 90f, 270f, c);
        arc(ms, cx, cy, size, 14f, 10.5f, 5.2f, 250f, 380f, c);
        line(ms, cx, cy, size, 9f, 8f, 15.5f, 6.7f, c);
        line(ms, cx, cy, size, 5f, 16f, 18.5f, 16f, c);
    }
}
