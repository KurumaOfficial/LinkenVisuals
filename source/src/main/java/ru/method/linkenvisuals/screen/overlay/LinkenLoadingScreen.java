package ru.method.linkenvisuals.screen.overlay;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;
import ru.method.linkenvisuals.screen.overlay.SplashFont.Face;
import ru.method.linkenvisuals.screen.overlay.SplashFont.Run;

/**
 * Экран 9d — загрузочный экран клиента вместо красного сплэша Mojang.
 *
 * <p>Собран строго по дизайн-пакету (холст 1920×1080): тёмный вертикальный градиент, акцентное
 * свечение за логотипом, уголки-скобки, вордмарк «linken.» + «VISUALS», полоса прогресса с этапом и
 * процентом, футер-подпись.
 *
 * <p><b>Два правила, из-за которых прошлые попытки выглядели пустым чёрным экраном:</b>
 * <ol>
 *   <li>Рисовать можно ТОЛЬКО {@code fill}/{@code fillGradient}/{@code drawTexture} — во время
 *       ресурс-релоада не загружены ни шейдеры мода, ни ванильный шейдер текста, поэтому
 *       {@code Render2D} и {@code drawText} молча не рисуют ничего. Текст идёт через
 *       {@link SplashFont} — MSDF-атласы мода, растеризованные на CPU в обычную текстуру.</li>
 *   <li>Ни одного размера в фиксированных пикселях. Всё считается от {@code k} — множителя
 *       дизайн-холста к физическим пикселям экрана. Раньше логотип 120px и полоса 280px были
 *       фиксированными: на маленькой канве они занимали пол-экрана, а в полноэкранном режиме на
 *       большом мониторе съёживались до пары процентов ширины, и экран читался как чёрный.</li>
 * </ol>
 *
 * <p>{@code Overlay} не получает уведомления о смене разрешения (у него нет {@code resize}), а при
 * запуске с {@code fullscreen} в options.txt переключение режима происходит ровно посреди сплэша —
 * поэтому геометрия здесь не кэшируется между кадрами, а пересчитывается каждый кадр.
 */
public final class LinkenLoadingScreen {

    private LinkenLoadingScreen() {}

    private static final int BG_TOP = 0x0a0e0c;
    private static final int BG_MID = 0x060908;
    private static final int BG_BOTTOM = 0x050706;
    private static final int ACCENT = 0x7edfb4;

    private static final int C_WORDMARK = 0xeef2ef;
    private static final int C_VISUALS = 0x5d6862;
    private static final int C_STAGE = 0x78837d;
    private static final int C_PERCENT = 0xc3ccc7;
    private static final int C_FOOTER = 0x4c5651;
    private static final int C_DISCLAIMER = 0x3f4a44;

    private static final String FOOTER = "by @emper0rhero and telegram.me/linkenvisuals";
    private static final String DISCLAIMER = "Не связан с Mojang и Microsoft";

    /**
     * @param progress01 сглаженный прогресс загрузки 0..1
     * @param alpha      прозрачность проявления 0..1 (перезагрузка ресурсов уже в игре)
     * @param exit       уход экрана вверх 0..1: 0 — на месте, 1 — полностью за верхней кромкой
     */
    public static void render(DrawContext context, float progress01, float alpha, float exit) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null) return;

        float fade = MathHelper.clamp(alpha, 0f, 1f);
        int a255 = Math.round(fade * 255f);
        if (a255 <= 1) return;

        int sw = context.getScaledWindowWidth();
        int sh = context.getScaledWindowHeight();
        if (sw <= 0 || sh <= 0) return;

        float slide = MathHelper.clamp(exit, 0f, 1f);
        if (slide >= 1f) return;
        if (slide > 0f) {
            // Экран уезжает вверх целиком, открывая уже отрисованное под ним главное меню.
            // Ускорение к концу (обратный smoothstep) — трогается мягко, уходит решительно.
            float eased = slide * slide * (3f - 2f * slide);
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(0f, -eased * sh);
        }
        try {
            draw(context, sw, sh, mc, progress01, fade, a255);
        } finally {
            if (slide > 0f) context.getMatrices().popMatrix();
        }
    }

    private static void draw(DrawContext context, int sw, int sh, MinecraftClient mc,
                             float progress01, float fade, int a255) {

        float progress = MathHelper.clamp(progress01, 0f, 1f);
        int tint = ColorHelper.getWhite(fade);

        // Логические координаты (в них рисует DrawContext) против физических пикселей (в них
        // растеризуется текст, чтобы оставаться чётким при любом guiScale).
        double gui = mc.getWindow().getScaleFactor();
        if (gui <= 0d) gui = 1d;
        int alignTo = Math.max(1, (int) Math.round(gui));
        float fw = (float) (sw * gui);
        float fh = (float) (sh * gui);
        // Множитель дизайн-холста 1920×1080 → физические пиксели. Вписываем (min), потому что вся
        // композиция 9d привязана к вертикали: логотип на 44 % высоты, прогресс и футер — от низа.
        float k = Math.min(fw / 1920f, fh / 1080f);
        if (k <= 0f) return;

        drawBackground(context, sw, sh, a255);
        drawGlow(context, sw, sh, tint);
        drawCorners(context, sw, sh, k, gui, a255);
        drawWordmark(context, fw, fh, k, gui, alignTo, tint);
        drawProgress(context, fw, fh, k, gui, alignTo, tint, fade, progress);
        drawFooter(context, fw, fh, k, gui, alignTo, tint);
    }

    /** Освобождает текстуры сплэша — зовётся один раз при снятии оверлея. */
    public static void dispose() {
        SplashFont.dispose();
    }

    // ─────────────────────────────── слои ───────────────────────────────

    private static void drawBackground(DrawContext context, int sw, int sh, int a255) {
        int split = Math.round(sh * 0.55f);
        context.fillGradient(0, 0, sw, split, argb(a255, BG_TOP), argb(a255, BG_MID));
        context.fillGradient(0, split, sw, sh, argb(a255, BG_MID), argb(a255, BG_BOTTOM));
    }

    private static void drawGlow(DrawContext context, int sw, int sh, int tint) {
        Run glow = SplashFont.glow(ACCENT, 0.06f);
        if (glow == null) return;
        // Эллипс 46 % × 38 % от кадра с центром на 50 % / 42 % — значит полные габариты вдвое больше.
        int width = Math.round(sw * 0.92f);
        int height = Math.round(sh * 0.76f);
        int x = (sw - width) / 2;
        int y = Math.round(sh * 0.42f) - height / 2;
        context.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, glow.id(), x, y, 0f, 0f,
                width, height, glow.width(), glow.height(), glow.width(), glow.height(), tint);
    }

    private static void drawCorners(DrawContext context, int sw, int sh, float k, double gui, int a255) {
        int inset = logical(24f * k, gui);
        int length = Math.max(2, logical(18f * k, gui));
        int thick = Math.max(1, logical(k, gui));
        int color = argb(Math.round(a255 * 0.35f), ACCENT);

        context.fill(inset, inset, inset + length, inset + thick, color);
        context.fill(inset, inset, inset + thick, inset + length, color);
        context.fill(sw - inset - length, sh - inset - thick, sw - inset, sh - inset, color);
        context.fill(sw - inset - thick, sh - inset - length, sw - inset, sh - inset, color);
    }

    private static void drawWordmark(DrawContext context, float fw, float fh, float k, double gui,
                                     int alignTo, int tint) {
        int wordPx = Math.max(8, Math.round(72f * k));
        int visualsPx = Math.max(5, Math.round(12f * k));

        Run word = SplashFont.text(Face.GOLOS_SEMI, "linken", wordPx, C_WORDMARK, -0.04f, alignTo);
        Run dot = SplashFont.text(Face.GOLOS_SEMI, ".", wordPx, ACCENT, 0f, alignTo);
        Run visuals = SplashFont.text(Face.UNBOUNDED, "VISUALS", visualsPx, C_VISUALS, 0.42f, alignTo);
        if (word == null) return;

        float wordBox = word.height() - word.pad() * 2f;
        float visualsBox = visuals == null ? 0f : visuals.height() - visuals.pad() * 2f;
        float gap = visuals == null ? 0f : 10f * k;
        float blockHeight = wordBox + gap + visualsBox;
        // Блок центрируется по 44 % высоты (в макете — translate(-50%, -50%)).
        float blockTop = fh * 0.44f - blockHeight / 2f;

        float lineAdvance = word.advance() + (dot == null ? 0f : dot.advance());
        float pen = fw / 2f - lineAdvance / 2f;
        float baseline = blockTop + (word.baseline() - word.pad());

        draw(context, word, pen, baseline, gui, tint);
        if (dot != null) draw(context, dot, pen + word.advance(), baseline, gui, tint);

        if (visuals != null) {
            float visualsTop = blockTop + wordBox + gap;
            draw(context, visuals, fw / 2f - visuals.advance() / 2f,
                    visualsTop + (visuals.baseline() - visuals.pad()), gui, tint);
        }
    }

    private static void drawProgress(DrawContext context, float fw, float fh, float k, double gui,
                                     int alignTo, int tint, float fade, float progress) {
        int stagePx = Math.max(5, Math.round(12.5f * k));
        int percentPx = Math.max(5, Math.round(11.5f * k));

        Run stage = SplashFont.text(Face.GOLOS, stage(progress), stagePx, C_STAGE, 0f, alignTo);
        Run percent = SplashFont.text(Face.PLEXMONO, Math.round(progress * 100f) + "%",
                percentPx, C_PERCENT, 0f, alignTo);

        float trackWidth = 460f * k;
        float trackLeft = fw / 2f - trackWidth / 2f;
        // Контейнер прогресса прижат к низу на 200 дизайн-пикселей; его последний ребёнок — строка
        // подписей, поэтому от низа отсчитываем именно её.
        float rowBottom = fh - 200f * k;
        float baseline = rowBottom;
        float rowTop = rowBottom;
        if (stage != null) {
            float descender = stage.height() - stage.pad() - stage.baseline();
            baseline = rowBottom - descender;
            rowTop = baseline - (stage.baseline() - stage.pad());
        }

        float trackHeight = Math.max(1f, 3f * k);
        float trackBottom = rowTop - 12f * k;
        float trackTop = trackBottom - trackHeight;

        int x1 = logical(trackLeft, gui);
        int x2 = logical(trackLeft + trackWidth, gui);
        int y1 = logical(trackTop, gui);
        // Высота бара — от округлённой ТОЛЩИНЫ, а не от разности округлённых краёв: иначе при
        // guiScale 2 толщина скакала 2↔4 физ.px в зависимости от чётности округления trackTop.
        int y2 = y1 + Math.max(1, (int) Math.round(trackHeight / gui));

        fillRounded(context, x1, y1, x2, y2, argb(Math.round(fade * 255f * 0.08f), 0xffffff));

        int filled = logical(trackLeft + trackWidth * progress, gui);
        if (filled > x1) {
            // Свечение полосы (в макете box-shadow 0 0 12px). Блюр-пасса на сплэше нет, поэтому берём
            // вертикальные градиенты вверх и вниз: у них мягкая кромка, в отличие от вложенных
            // прямоугольников, которые читались как ярко-зелёная коробка вокруг полосы.
            int spread = Math.max(1, logical(12f * k, gui));
            int halo = Math.round(fade * 255f * 0.42f);
            if (halo > 2) {
                context.fillGradient(x1, y1 - spread, filled, y1, argb(0, ACCENT), argb(halo, ACCENT));
                context.fillGradient(x1, y2, filled, y2 + spread, argb(halo, ACCENT), argb(0, ACCENT));
            }
            fillRounded(context, x1, y1, filled, y2, argb(Math.round(fade * 255f), ACCENT));
        }

        if (stage != null) draw(context, stage, trackLeft, baseline, gui, tint);
        if (percent != null) {
            draw(context, percent, trackLeft + trackWidth - percent.advance(), baseline, gui, tint);
        }
    }

    private static void drawFooter(DrawContext context, float fw, float fh, float k, double gui,
                                   int alignTo, int tint) {
        int footerPx = Math.max(5, Math.round(9.5f * k));
        int disclaimerPx = Math.max(5, Math.round(10.5f * k));

        Run footer = SplashFont.text(Face.PLEXMONO, FOOTER, footerPx, C_FOOTER, 0f, alignTo);
        Run disclaimer = SplashFont.text(Face.GOLOS, DISCLAIMER, disclaimerPx, C_DISCLAIMER, 0f, alignTo);

        float bottom = fh - 44f * k;
        if (disclaimer != null) {
            float box = disclaimer.height() - disclaimer.pad() * 2f;
            float top = bottom - box;
            draw(context, disclaimer, fw / 2f - disclaimer.advance() / 2f,
                    top + (disclaimer.baseline() - disclaimer.pad()), gui, tint);
            bottom = top - 6f * k;
        }
        if (footer != null) {
            float box = footer.height() - footer.pad() * 2f;
            float top = bottom - box;
            draw(context, footer, fw / 2f - footer.advance() / 2f,
                    top + (footer.baseline() - footer.pad()), gui, tint);
        }
    }

    // ─────────────────────────────── вспомогательное ───────────────────────────────

    /**
     * Выводит растр строки: на вход — перо и базовая линия в физических пикселях, внутри переводим в
     * логические координаты {@code DrawContext}.
     */
    private static void draw(DrawContext context, Run run, float penX, float baselineY, double gui, int tint) {
        int x = logical(penX - run.pad(), gui);
        int y = logical(baselineY - run.baseline(), gui);
        int width = Math.max(1, (int) Math.round(run.width() / gui));
        int height = Math.max(1, (int) Math.round(run.height() / gui));
        context.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, run.id(), x, y, 0f, 0f,
                width, height, run.width(), run.height(), run.width(), run.height(), tint);
    }

    private static int logical(float physical, double gui) {
        return (int) Math.round(physical / gui);
    }

    /**
     * Прямоугольник со скруглёнными торцами из голых fill'ов: на сплэше шейдер скруглений недоступен,
     * поэтому радиус имитируем срезом углов на 1px + полупрозрачным пикселем — на баре высотой 2–4px
     * этого достаточно, чтобы концы читались круглыми, а не рублеными.
     */
    private static void fillRounded(DrawContext context, int x1, int y1, int x2, int y2, int color) {
        int w = x2 - x1, h = y2 - y1;
        if (w <= 2 || h <= 2) {
            context.fill(x1, y1, x2, y2, color);
            return;
        }
        int soft = (color & 0xFFFFFF) | (Math.round(((color >>> 24) & 0xFF) * 0.45f) << 24);
        context.fill(x1 + 1, y1, x2 - 1, y2, color);
        context.fill(x1, y1 + 1, x1 + 1, y2 - 1, color);
        context.fill(x2 - 1, y1 + 1, x2, y2 - 1, color);
        context.fill(x1, y1, x1 + 1, y1 + 1, soft);
        context.fill(x1, y2 - 1, x1 + 1, y2, soft);
        context.fill(x2 - 1, y1, x2, y1 + 1, soft);
        context.fill(x2 - 1, y2 - 1, x2, y2, soft);
    }

    private static int argb(int alpha, int rgb) {
        return (MathHelper.clamp(alpha, 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    private static String stage(float progress) {
        if (progress < 0.33f) return "Загружаем ресурспаки";
        if (progress < 0.66f) return "Загружаем шейдеры";
        if (progress < 0.999f) return "Загружаем текстуры";
        return "Готово";
    }
}
