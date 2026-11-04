package ru.method.linkenvisuals.screen.mainmenu;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import org.joml.Matrix3x2fStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.screen.clickgui.redesign.ClickGuiIcons;
import ru.method.linkenvisuals.utils.animations.Animation;
import ru.method.linkenvisuals.utils.animations.Easing;
import ru.method.linkenvisuals.utils.render.Render2D;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;
import ru.method.linkenvisuals.utils.render.fonts.Instance;

import java.awt.Color;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * Общий «хром» экранов редизайна (мейнменю «Linken Menu 1b» и «Сетевая игра» 4b): палитра-токены,
 * материал кнопок, лого, часы, медиаплеер, фон-арт и утилиты. Оба экрана рисуют одно и то же одним
 * кодом — дублирования нет.
 *
 * <p>Все размеры — в дизайн-единицах виртуального холста 1920×1080 (высота-эталон 1080).
 * Состояние медиаплеера статическое, поэтому трек переживает переход между экранами меню.</p>
 */
public final class MenuChrome {

    private MenuChrome() {}

    // --- Виртуальная система координат: эталон по высоте 1080, дизайн-координаты 1:1. ---
    public static final float DESIGN_HEIGHT = 1080f;

    /** Версия игры и сборка клиента — единый источник для дисклеймера меню и лейбла «Сетевой игры». */
    public static final String MC_VERSION = "1.21.11";
    public static final String BUILD = "1.0.147";

    // --- Палитра (дизайн-токены). ---
    public static final Color TEXT   = new Color(0xee, 0xf2, 0xef);
    public static final Color TEXT2  = new Color(0xdf, 0xe5, 0xe1);
    public static final Color TEXT3  = new Color(0xc3, 0xcc, 0xc7);
    public static final Color TEXT4  = new Color(0xa8, 0xb1, 0xac);
    public static final Color MUTED  = new Color(0x8b, 0x96, 0x8f);
    public static final Color MUTED2 = new Color(0x78, 0x83, 0x7d);
    public static final Color MUTED3 = new Color(0x5d, 0x68, 0x62);
    public static final Color MUTED4 = new Color(0x4c, 0x56, 0x51);
    public static final Color INK    = new Color(0x0b, 0x12, 0x0e); // текст на акценте
    public static final Color DANGER = new Color(0xe0, 0x8c, 0x8c);

    /** Единственный источник акцента для обоих экранов (дефолт #7edfb4). Все зелёные rgba выводятся отсюда. */
    public static Color accent() {
        return CustomMainMenuScreen.ACCENT;
    }

    /** Акцент с заданной альфой (0..255) — замена ручных rgba(126,223,180,a). */
    public static Color accent(int alpha) {
        Color a = accent();
        return new Color(a.getRed(), a.getGreen(), a.getBlue(), MathHelper.clamp(alpha, 0, 255));
    }

    /** Акцент с долей альфы (0..1) — прямой перевод rgba(accent, x) из спеки. */
    public static Color accentA(float alpha) {
        return accent(Math.round(alpha * 255f));
    }

    /** Белый с долей альфы — перевод rgba(255,255,255,x) из спеки. */
    public static Color white(float alpha) {
        return new Color(255, 255, 255, MathHelper.clamp(Math.round(alpha * 255f), 0, 255));
    }

    /**
     * Мягкое свечение/тень «размытием» — несколько расширяющихся скруглённых прямоугольников с
     * затухающей альфой (аппроксимация gaussian-blur из CSS box-shadow). Замена сплошного цветного
     * блока-подложки. {@code layers} слоёв, шаг {@code step}px, смещение вниз {@code dy}.
     */
    public static void glow(Matrix3x2fStack ms, float x, float y, float w, float h, float r, Color col, float dy, int layers, float step) {
        for (int i = layers; i >= 1; i--) {
            float e = i * step;
            int a = Math.round(col.getAlpha() * (1f - (i - 1) / (float) layers) / (float) layers * 2.2f);
            if (a <= 0) continue;
            Render2D.drawRoundedRect(ms, x - e, y + dy - e, w + 2f * e, h + 2f * e, r + e,
                    new Color(col.getRed(), col.getGreen(), col.getBlue(), Math.min(255, a)));
        }
    }

    // --- Фон ---
    public static final Identifier BG = LinkenVisuals.id("mainmenu/bg-tower.png");
    public static final float BG_W = 1270f, BG_H = 999f;

    // --- Медиаплеер (существующий трек меню) ---
    private static final Identifier MUSIC_PLAY = LinkenVisuals.id("music/play.png");
    private static final Identifier MUSIC_PAUSE = LinkenVisuals.id("music/pause.png");
    private static final String TRACK_TITLE = "EVILSHIT";
    private static final String TRACK_ARTIST = "ARLEKIN 40 000";
    private static final long TRACK_DURATION_MS = 131_300L;
    private static final float TRACK_VOLUME = 0.4f;
    private static net.minecraft.client.sound.SoundInstance musicInstance;
    private static boolean musicAutoStarted, musicPlaying, musicPaused;
    private static long musicStartMs, musicPausedAtMs;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    // --- Иконки-глифы (шрифт ICONS2): A=человек, B=шестерёнка, C=глобус, D=power, E=telegram, F=discord, H=код ---
    public static final String IC_GLOBE = "C", IC_PERSON = "A", IC_GEAR = "B", IC_POWER = "D";
    public static final String IC_TG = "E", IC_DISCORD = "F", IC_CODE = "H";

    /** Hover-анимация ~130ms (ease-out cubic) — общий тайминг ховеров редизайна. */
    public static Animation anim() {
        return new Animation(130, 1f, false, Easing.EASE_OUT_CUBIC);
    }

    // ---------- Фон-арт ----------

    /**
     * Арт мейнменю в режиме cover с параллакс-сдвигом. Оверлей (скрим/виньетку) каждый экран
     * рисует сам — он у мейнменю и «Сетевой игры» разный.
     */
    public static void drawBackgroundArt(Matrix3x2fStack ms, int vw, int vh, float parX, float parY) {
        float scale = Math.max(vw / BG_W, vh / BG_H) * 1.07f; // оверскейл под параллакс-сдвиг
        float w = BG_W * scale, h = BG_H * scale;
        float dx = -parX * Math.min((w - vw) / 2f, 42f);
        float dy = -parY * Math.min((h - vh) / 2f, 26f);
        Render2D.drawTexture(ms, (vw - w) / 2f + dx, (vh - h) / 2f + dy, w, h, 0f, BG, new Color(255, 255, 255, 255));
    }

    /**
     * Единый фон экранов редизайна по спеке handoff'а «Фон»:
     * <ul>
     *   <li>из мейнменю (мир не загружен) — арт {@code bg-tower.png} cover + виньетка;</li>
     *   <li>из игры (мир загружен) — замороженный кадр мира с blur 14px + та же виньетка, арт не рисуем.</li>
     * </ul>
     * Никаких чёрных заливок и ванильного dirt-фона (renderBackground у экранов — no-op).
     */
    public static void drawScreenBackground(Matrix3x2fStack ms, int vw, int vh, float parX, float parY) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c != null && c.world != null) {
            // Кадр мира уже отрисован под экраном — матируем его размытием (backdrop blur 14px).
            Render2D.drawBlurredRect(ms, 0, 0, vw, vh, 0f, 14f, new Color(4, 7, 6, 48), true);
        } else {
            drawBackgroundArt(ms, vw, vh, parX, parY);
        }
        drawRadialVignette(ms, vw, vh);
    }

    /**
     * Виньетка по спеке {@code radial-gradient(ellipse 52% 46% at 50% 44%, rgba(4,7,6,0.3), rgba(4,7,6,0.74))}.
     * Запечена в текстуру (гладкий градиент без бандинга) и растягивается cover — центр/радиусы в долях
     * экрана совпадают со спекой при любом соотношении сторон.
     */
    public static void drawRadialVignette(Matrix3x2fStack ms, int vw, int vh) {
        NativeImageBackedTexture tex = vignetteTexture();
        if (tex != null) {
            Render2D.drawTexture(ms, 0, 0, vw, vh, 0f, tex, new Color(255, 255, 255, 255));
        }
    }

    private static NativeImageBackedTexture vignetteTex;

    /** Ленивая генерация текстуры виньетки (один раз за сессию, на рендер-треде). */
    private static NativeImageBackedTexture vignetteTexture() {
        if (vignetteTex != null) return vignetteTex;
        final int S = 192;
        final float ccx = 0.50f, ccy = 0.44f; // центр в UV (спека: at 50% 44%)
        final float ellRx = 0.52f, ellRy = 0.46f; // радиусы эллипса в долях (спека: 52% 46%)
        NativeImage img = new NativeImage(NativeImage.Format.RGBA, S, S, false);
        for (int py = 0; py < S; py++) {
            for (int px = 0; px < S; px++) {
                float u = (px + 0.5f) / S, v = (py + 0.5f) / S;
                float dx = (u - ccx) / ellRx;
                float dy = (v - ccy) / ellRy;
                float d = MathHelper.clamp((float) Math.sqrt(dx * dx + dy * dy), 0f, 1f); // 0 центр → 1 край эллипса
                float a = 0.30f + (0.74f - 0.30f) * d; // rgba(4,7,6, 0.3 → 0.74)
                int alpha = MathHelper.clamp(Math.round(a * 255f), 0, 255);
                // ARGB: A<<24 | R<<16 | G<<8 | B ; цвет базы #040706 (r=4,g=7,b=6).
                img.setColorArgb(px, py, (alpha << 24) | (4 << 16) | (7 << 8) | 6);
            }
        }
        vignetteTex = new NativeImageBackedTexture(() -> "linken_vignette", img);
        vignetteTex.upload();
        return vignetteTex;
    }

    // ---------- Чип «Назад» (общий для экранов редизайна: мультиплеер/настройки) ----------
    /**
     * Рисует чип «Назад» (верх-лево 56/44) по спеке хендоффа и кладёт его хит-бокс в {@code rectOut}.
     * Вызывающий на клике проверяет {@link #in(float[], float, float)} по {@code rectOut} и делает back.
     */
    public static void drawBackChip(Matrix3x2fStack ms, int mx, int my, Animation hoverAnim, float[] rectOut) {
        Instance f = Fonts.UNBOUNDED.getFont(10f);
        String label = "Назад";
        float h = 38f, icon = 17f;
        float w = 12f + icon + 9f + f.getWidth(label) + 15f;
        float x = 56f, y = 44f;
        boolean hov = hit(mx, my, x, y, w, h);
        float t = upd(hoverAnim, hov);
        float ty = y - t; // подъём 1px на ховере
        drawSecondaryMaterial(ms, x, ty, w, h, 8f, t, false);
        ClickGuiIcons.arrowLeft(ms, x + 12f + icon / 2f, ty + h / 2f, icon, MUTED);
        Render2D.drawFont(ms, f, label, x + 12f + icon + 9f, ty + h / 2f - f.getHeight() / 2f, TEXT2);
        set(rectOut, x, y, w, h);
    }

    /** Футер-подпись экранов редизайна: mono 9.5 #4c5651, слева-снизу (56/44) или по центру (9d). */
    public static void drawFooterSignature(Matrix3x2fStack ms, int vw, int vh, boolean centered) {
        Instance f = Fonts.PLEXMONO.getFont(9.5f);
        String s = "by @emper0rhero and telegram.me/linkenvisuals";
        float y = vh - 44f - f.getHeight();
        float x = centered ? (vw - f.getWidth(s)) / 2f : 56f;
        Render2D.drawFont(ms, f, s, x, y, MUTED4);
    }

    // ---------- Логотип (top 44, center) ----------
    public static void drawLogo(Matrix3x2fStack ms, int vw) {
        Instance f = Fonts.GOLOS_SEMI.getFont(23f);
        float wLinken = widthSpaced(f, "linken", -0.035f * 23f);
        float wDot = f.getWidth(".");
        float total = wLinken + wDot;
        float x = vw / 2f - total / 2f;
        float y = 44f;
        drawSpaced(ms, f, "linken", x, y, TEXT, -0.035f * 23f);
        Render2D.drawFont(ms, f, ".", x + wLinken, y, accent());
    }

    // ---------- Часы + дата (top 38, right 56) ----------
    private static final Locale RU = Locale.forLanguageTag("ru");
    // Кэш строк часов/даты: Locale/format/DisplayName пересчитываем раз в секунду, не каждый кадр.
    private static long clockSec = -1L;
    private static String clockHh = "", clockMm = "", clockDay = "", clockRest = "";

    public static void drawClock(Matrix3x2fStack ms, int vw) {
        long sec = System.currentTimeMillis() / 1000L;
        if (sec != clockSec) {
            clockSec = sec;
            String time = LocalTime.now().format(TIME_FMT);
            clockHh = time.substring(0, 2);
            clockMm = time.substring(3, 5);
            LocalDate d = LocalDate.now();
            clockDay = cap(d.getDayOfWeek().getDisplayName(TextStyle.SHORT, RU), RU) + ", "; // «вс, »
            clockRest = d.getDayOfMonth() + " " + d.getMonth().getDisplayName(TextStyle.FULL, RU); // «5 июля»
        }
        Instance cf = Fonts.PLEXMONO.getFont(44f);
        float gap = 44f * 0.15f, dot = 44f * 0.10f;
        float total = cf.getWidth(clockHh) + gap + dot + gap + cf.getWidth(clockMm);
        float rx = vw - 56f;
        drawClockTime(ms, cf, clockHh, clockMm, rx - total, 38f, TEXT, 44f, gap, dot);

        Instance df = Fonts.GOLOS.getFont(12.5f);
        float sp = 0.06f * 12.5f;
        float lw = widthSpaced(df, clockDay, sp);
        float rw = widthSpaced(df, clockRest, sp);
        float dateY = 38f + 44f + 6f;
        drawSpaced(ms, df, clockDay, rx - lw - rw, dateY, MUTED2, sp);
        drawSpaced(ms, df, clockRest, rx - rw, dateY, TEXT3, sp);
    }

    /** Часы «HH:MM»: цифры моно-шрифтом, двоеточие — две точки по оптическому центру цифр. */
    public static void drawClockTime(Matrix3x2fStack ms, Instance f, String hh, String mm, float x, float y, Color color, float fs, float gap, float dot) {
        Render2D.drawFont(ms, f, hh, x, y, color);
        float cx = x + f.getWidth(hh) + gap;
        // Двоеточие центрируем по ОПТИЧЕСКОМУ центру цифр (baseline на y+1.025·fs, бокс цифры
        // 0.223·fs..1.140·fs ⇒ центр ≈ 0.681·fs). Расстояние между точками 0.22·fs.
        Render2D.drawRoundedRect(ms, cx, y + fs * 0.52f, dot, dot, dot / 2f, color);
        Render2D.drawRoundedRect(ms, cx, y + fs * 0.74f, dot, dot, dot / 2f, color);
        Render2D.drawFont(ms, f, mm, cx + dot + gap, y, color);
    }

    // ---------- Кнопка (материал редизайна) ----------
    public static void drawButton(Matrix3x2fStack ms, float x, float y, float w, float h, boolean primary,
                                  String iconGlyph, String label, Color labelColor, float hoverT, boolean centered, boolean danger) {
        float ty = y - hoverT; // translateY(-1) на hover
        float r = 8f;
        if (primary) {
            Color acc = accent();
            // тень-глоу под кнопкой
            Render2D.drawRoundedRect(ms, x + 4f, ty + h - 6f, w - 8f, 12f, 8f,
                    new Color(acc.getRed(), acc.getGreen(), acc.getBlue(), Math.round(60 + 40 * hoverT)));
            int br = Math.min(255, Math.round(acc.getRed() * (1f + 0.07f * hoverT)));
            int bg = Math.min(255, Math.round(acc.getGreen() * (1f + 0.07f * hoverT)));
            int bb = Math.min(255, Math.round(acc.getBlue() * (1f + 0.07f * hoverT)));
            Render2D.drawRoundedRect(ms, x, ty, w, h, r, new Color(br, bg, bb));
            // верхний внутренний блик
            Render2D.drawRoundedRect(ms, x + r, ty + 1f, w - r * 2f, 1f, 0.5f, new Color(255, 255, 255, 90));
        } else {
            drawSecondaryMaterial(ms, x, ty, w, h, r, hoverT, danger);
        }
        float iconSize = 15f;
        Instance icf = Fonts.ICONS2.getFont(iconSize);
        Instance lf = (primary ? Fonts.UNBOUNDED : Fonts.UNBOUNDED_L).getFont(12.5f);
        float iconW = icf.getWidth(iconGlyph), lw = lf.getWidth(label);
        float cy = ty + h / 2f;
        if (centered) {
            float content = iconW + 9f + lw;
            float cx = x + (w - content) / 2f;
            Render2D.drawFont(ms, icf, iconGlyph, cx, cy - icf.getHeight() / 2f, labelColor);
            Render2D.drawFont(ms, lf, label, cx + iconW + 9f, cy - lf.getHeight() / 2f, labelColor);
        } else {
            float ix = x + 16f;
            Render2D.drawFont(ms, icf, iconGlyph, ix, cy - icf.getHeight() / 2f, primary ? INK : MUTED);
            Render2D.drawFont(ms, lf, label, ix + iconW + 12f, cy - lf.getHeight() / 2f, labelColor);
        }
    }

    /**
     * Материал вторичной кнопки (фон+блик+рамка) без содержимого — общий для кнопок меню,
     * чипа «Назад» и пилюли аккаунта: linear-gradient(180deg, rgba(24,30,27,0.85), rgba(15,19,17,0.85)),
     * рамка rgba(255,255,255,0.09) → 0.24 на ховере, inset 0 1px 0 rgba(255,255,255,0.05).
     */
    public static void drawSecondaryMaterial(Matrix3x2fStack ms, float x, float y, float w, float h, float r, float hoverT, boolean danger) {
        Render2D.drawRoundedVerticalGradient(ms, x, y, w, h, r,
                new Color(24, 30, 27, 217), new Color(15, 19, 17, 217));
        Render2D.drawRoundedRect(ms, x + r, y + 1f, w - r * 2f, 1f, 0.5f, new Color(255, 255, 255, 13));
        Color bc = danger
                ? lerp(new Color(255, 255, 255, 23), new Color(224, 110, 110, 140), hoverT)
                : lerp(new Color(255, 255, 255, 23), new Color(255, 255, 255, 61), hoverT);
        Render2D.drawBorder(ms, x, y, w, h, r, 1.0f, 1.4f, bc);
    }

    // ---------- Медиаплеер (bottom 44, center, width 320) ----------

    /** Есть ли что показывать в плеере — если модуль музыки не играл ни разу, блок скрывается. */
    public static boolean hasMusic() {
        return musicPlaying || musicPaused;
    }

    /** Автостарт трека один раз за сессию (после велкома). */
    public static void autoStartOnce() {
        if (musicAutoStarted) return;
        musicAutoStarted = true;
        startMusic();
    }

    /** Трек доиграл — сбрасываем состояние. Вызывать каждый кадр из активного экрана меню. */
    public static void tickMusic() {
        if (musicPlaying && !musicPaused && System.currentTimeMillis() - musicStartMs >= TRACK_DURATION_MS) {
            musicPlaying = false;
            musicInstance = null;
        }
    }

    public static void drawMusic(Matrix3x2fStack ms, int vw, int vh, int mx, int my, Animation hCover, float[] rCover) {
        float w = 320f;
        float x = vw / 2f - w / 2f;
        float bottom = vh - 44f;
        float coverSize = 34f;
        Instance tc = Fonts.PLEXMONO.getFont(10.5f);
        float progRowY = bottom - tc.getHeight();
        float rowY = progRowY - 12f - coverSize;
        float labelY = rowY - 9f - Fonts.GOLOS.getFont(10.5f).getHeight();

        // 1. «Сейчас играет ♪» по центру
        Instance nf = Fonts.GOLOS.getFont(10.5f);
        String now = "Сейчас играет";
        float nwd = widthSpaced(nf, now, 0.08f * 10.5f);
        drawSpaced(ms, nf, now, x + (w - nwd) / 2f, labelY, MUTED2, 0.08f * 10.5f);

        // 2. обложка + название
        boolean covH = hit(mx, my, x, rowY, coverSize, coverSize);
        float covT = upd(hCover, covH);
        Render2D.drawRoundedVerticalGradient(ms, x, rowY, coverSize, coverSize, coverSize / 2f, new Color(0x37, 0x30, 0x2a), new Color(0x12, 0x10, 0x0e));
        Identifier pi = (musicPlaying && !musicPaused) ? MUSIC_PAUSE : MUSIC_PLAY;
        float ps = 9f;
        Render2D.drawTexture(ms, x + (coverSize - ps) / 2f, rowY + (coverSize - ps) / 2f, ps, ps, 0f, pi, lerp(MUTED, TEXT, covT));
        set(rCover, x, rowY, coverSize, coverSize);
        Instance trk = Fonts.UNBOUNDED.getFont(13f);
        Instance byf = Fonts.UNBOUNDED_L.getFont(11f);
        float ttx = x + coverSize + 12f;
        float tty = rowY + (coverSize - trk.getHeight()) / 2f;
        Render2D.drawFont(ms, trk, TRACK_TITLE + " ", ttx, tty, Color.WHITE);
        float aw = trk.getWidth(TRACK_TITLE + " ");
        Render2D.drawFont(ms, byf, "by ", ttx + aw, tty + 2f, MUTED2);
        float bw = byf.getWidth("by ");
        Render2D.drawFont(ms, trk, TRACK_ARTIST, ttx + aw + bw, tty, Color.WHITE);

        // 3. прогресс
        float progress = 0f;
        if (musicPlaying && !musicPaused) progress = MathHelper.clamp((System.currentTimeMillis() - musicStartMs) / (float) TRACK_DURATION_MS, 0f, 1f);
        else if (musicPaused) progress = MathHelper.clamp((musicPausedAtMs - musicStartMs) / (float) TRACK_DURATION_MS, 0f, 1f);
        long posMs = Math.round((double) progress * TRACK_DURATION_MS);
        String elapsed = fmtTime(posMs);
        String remain = "-" + fmtTime(TRACK_DURATION_MS - posMs);
        float ew = tc.getWidth(elapsed), rw = tc.getWidth(remain);
        Render2D.drawFont(ms, tc, elapsed, x, progRowY, MUTED);
        Render2D.drawFont(ms, tc, remain, x + w - rw, progRowY, MUTED);
        float barX = x + ew + 10f, barW = w - ew - rw - 20f;
        float barY = progRowY + tc.getHeight() / 2f - 1f;
        Render2D.drawRoundedRect(ms, barX, barY, barW, 2f, 1f, new Color(255, 255, 255, 41));
        if (progress > 0.001f) Render2D.drawRoundedRect(ms, barX, barY, barW * progress, 2f, 1f, TEXT);
    }

    // ---------- Музыка ----------

    /**
     * Переход внутри кластера меню (мейнменю ↔ «Сетевая игра»): трек не глушим.
     * Флаг нужен потому, что {@code MinecraftClient.setScreen} зовёт {@code removed()} у старого экрана
     * ДО присвоения нового — проверить {@code mc.currentScreen} в removed() невозможно.
     */
    private static boolean keepMusicOnSwap;

    /** Вызвать ПЕРЕД setScreen на соседний экран меню, чтобы трек продолжил играть. */
    public static void keepMusicAcrossSwap() {
        keepMusicOnSwap = true;
    }

    /** Вызывать из {@code removed()} экранов меню вместо прямого stopMusic(). */
    public static void onMenuScreenRemoved() {
        if (keepMusicOnSwap) {
            keepMusicOnSwap = false; // переход меню↔«Сетевая игра» — трек продолжается
            return;
        }
        stopMusic();
    }

    public static void toggleMusic() {
        if (musicPlaying && !musicPaused) pauseMusic();
        else if (musicPaused) resumeMusic();
        else startMusic();
    }

    public static void startMusic() {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c == null) return;
        stopMusic();
        // Не-позиционный master-звук (как рабочие звуки мода) — надёжно проигрывает трек (в т.ч. стерео).
        net.minecraft.sound.SoundEvent trackEvent =
                net.minecraft.sound.SoundEvent.of(LinkenVisuals.id("mainmenu/track"));
        musicInstance = new net.minecraft.client.sound.PositionedSoundInstance(
                trackEvent, net.minecraft.sound.SoundCategory.MASTER,
                TRACK_VOLUME, 1.0f, net.minecraft.client.sound.SoundInstance.createRandom(),
                0.0, 0.0, 0.0);
        c.getSoundManager().play(musicInstance);
        musicPlaying = true; musicPaused = false; musicStartMs = System.currentTimeMillis();
    }

    public static void pauseMusic() {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c == null || !musicPlaying || musicPaused) return;
        c.getSoundManager().pauseAllExcept(); musicPaused = true; musicPausedAtMs = System.currentTimeMillis();
    }

    public static void resumeMusic() {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c == null || !musicPaused) return;
        c.getSoundManager().resumeAll(); musicStartMs += System.currentTimeMillis() - musicPausedAtMs; musicPaused = false;
    }

    public static void stopMusic() {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c != null) {
            if (musicPaused) c.getSoundManager().resumeAll();
            if (musicInstance != null) c.getSoundManager().stop(musicInstance);
        }
        musicInstance = null; musicPlaying = false; musicPaused = false;
    }

    // ---------- Утилиты рендера ----------
    public static void drawSpaced(Matrix3x2fStack ms, Instance f, String text, float x, float y, Color color, float spacing) {
        float cx = x;
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));
            Render2D.drawFont(ms, f, ch, cx, y, color);
            cx += f.getWidth(ch) + spacing;
        }
    }

    public static float widthSpaced(Instance f, String text, float spacing) {
        float w = 0f;
        for (int i = 0; i < text.length(); i++) w += f.getWidth(String.valueOf(text.charAt(i))) + spacing;
        return Math.max(0f, w - spacing);
    }

    public static boolean hit(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    public static boolean in(float[] r, float mx, float my) {
        return mx >= r[0] && mx <= r[0] + r[2] && my >= r[1] && my <= r[1] + r[3];
    }

    public static void set(float[] r, float x, float y, float w, float h) {
        r[0] = x; r[1] = y; r[2] = w; r[3] = h;
    }

    public static float upd(Animation a, boolean hovered) {
        a.update(hovered);
        return MathHelper.clamp(a.getValue(), 0f, 1f);
    }

    public static Color lerp(Color a, Color b, float t) {
        t = MathHelper.clamp(t, 0f, 1f);
        return new Color(
                Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t),
                Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t));
    }

    public static String cap(String s, Locale l) {
        return s == null || s.isEmpty() ? s : s.substring(0, 1).toUpperCase(l) + s.substring(1);
    }

    /** Ник текущей сессии (фолбэк «player»). */
    public static String nick() {
        MinecraftClient c = MinecraftClient.getInstance();
        return c != null && c.getSession() != null ? c.getSession().getUsername() : "player";
    }

    public static String licenseWord() {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c == null || c.getSession() == null) return "оффлайн";
        String token = c.getSession().getAccessToken();
        return token != null && !token.isBlank() ? "лицензия" : "оффлайн";
    }

    public static String fmtTime(long ms) {
        long s = Math.max(0L, ms) / 1000L;
        return (s / 60L) + ":" + (s % 60L < 10 ? "0" : "") + (s % 60L);
    }
}
