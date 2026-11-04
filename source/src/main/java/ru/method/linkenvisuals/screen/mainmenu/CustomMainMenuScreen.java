package ru.method.linkenvisuals.screen.mainmenu;

import net.minecraft.client.gui.Click;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.session.Session;
import org.joml.Matrix3x2fStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.utils.CommunityLinks;
import ru.method.linkenvisuals.utils.Wrapper;
import ru.method.linkenvisuals.utils.animations.Animation;
import ru.method.linkenvisuals.utils.animations.Easing;
import ru.method.linkenvisuals.utils.client.MainMenuGreeting;
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
 * Главное меню клиента — редизайн «Linken Menu 1b (Компакт)».
 * Реализовано по дизайн-хендоффу пиксель-в-пиксель, масштаб от эталона 1920×1080.
 * Layout анкорится к краям экрана; все размеры/отступы — в единицах дизайна (виртуальная высота 1080).
 */
public final class CustomMainMenuScreen extends Screen implements Wrapper {

    // --- Виртуальная система координат: эталон по высоте 1080, дизайн-координаты 1:1. ---
    private static final float DESIGN_HEIGHT = MenuChrome.DESIGN_HEIGHT;
    private float uiScale = 1f;
    private int vw = 1, vh = 1;

    // --- Дизайн-токен: акцент (конфиг, заменяется глобально; дефолт #7edfb4). ---
    public static Color ACCENT = new Color(0x7e, 0xdf, 0xb4);

    // --- Палитра, фон, плеер, глифы — единый источник в MenuChrome (общий с экраном «Сетевая игра»). ---
    private static final Color TEXT   = MenuChrome.TEXT;
    private static final Color TEXT2  = MenuChrome.TEXT2;
    private static final Color TEXT3  = MenuChrome.TEXT3;
    private static final Color TEXT4  = MenuChrome.TEXT4;
    private static final Color MUTED  = MenuChrome.MUTED;
    private static final Color MUTED2 = MenuChrome.MUTED2;
    private static final Color MUTED4 = MenuChrome.MUTED4;
    private static final Color INK    = MenuChrome.INK;
    private static final Color DANGER = MenuChrome.DANGER;

    private static final String IC_GLOBE = MenuChrome.IC_GLOBE, IC_PERSON = MenuChrome.IC_PERSON;
    private static final String IC_GEAR = MenuChrome.IC_GEAR, IC_POWER = MenuChrome.IC_POWER;
    private static final String IC_TG = MenuChrome.IC_TG, IC_DISCORD = MenuChrome.IC_DISCORD, IC_CODE = MenuChrome.IC_CODE;

    // --- Hover-анимации (~130ms) + хит-прямоугольники ---
    private final Animation hNet = anim(), hSingle = anim(), hSettings = anim(), hExit = anim(), hChange = anim();
    private final Animation[] hSoc = { anim(), anim(), anim(), anim() };
    private final Animation hCover = anim();
    private final float[] rNet = new float[4], rSingle = new float[4], rSettings = new float[4], rExit = new float[4], rChange = new float[4];
    private final float[][] rSoc = { new float[4], new float[4], new float[4], new float[4] };
    private final float[] rCover = new float[4];

    // Выход: подтверждение по второму клику.
    private long exitArmedUntil = 0L;
    // Сглаженный параллакс-сдвиг фона от курсора (-1..1).
    private float parX, parY;
    // Плавное появление контента меню после Lottie-велкома (fade + слайд снизу).
    private long welcomeEndMs = 0L;

    private static Animation anim() { return MenuChrome.anim(); }

    public CustomMainMenuScreen() {
        super(Text.literal("LinkenVisuals"));
    }

    private boolean recomputeScale() {
        if (mc == null || mc.getWindow() == null) return false;
        // HiDPI-safe: масштаб считаем от GUI-scaled размеров (в них рисует DrawContext). Раньше брали
        // getHeight()/getScaleFactor() — но getHeight() это ЛОГИЧЕСКИЙ размер окна, а scaleFactor
        // считается от фреймбуфера; на Retina (fb ×2) uiScale выходил вдвое меньше → меню в углу экрана.
        float ns = Math.max(0.05f, mc.getWindow().getScaledHeight() / DESIGN_HEIGHT);
        int nvw = Math.max(1, Math.round(mc.getWindow().getScaledWidth() / ns));
        int nvh = Math.max(1, Math.round(mc.getWindow().getScaledHeight() / ns));
        if (nvw == vw && nvh == vh && Math.abs(ns - uiScale) < 0.0005f) return false;
        uiScale = ns; vw = nvw; vh = nvh; return true;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        recomputeScale();
        ru.method.linkenvisuals.utils.client.MainMenuGreeting.requestPlayback();
        ru.method.linkenvisuals.utils.render.Render2D.prepareHudFrame(context);
        Matrix3x2fStack ms = context.getMatrices();
        ms.pushMatrix();
        ms.scale(uiScale, uiScale);
        int vmx = Math.round(mouseX / uiScale);
        int vmy = Math.round(mouseY / uiScale);

        // Параллакс фона от курсора (сглаженный).
        float tpx = MathHelper.clamp((vmx - vw / 2f) / (vw / 2f), -1f, 1f);
        float tpy = MathHelper.clamp((vmy - vh / 2f) / (vh / 2f), -1f, 1f);
        parX += (tpx - parX) * 0.08f;
        parY += (tpy - parY) * 0.08f;

        drawBackground(ms);

        boolean lottieActive = MainMenuGreeting.isLottieActive();
        // Автостарт трека — только когда велком доиграл; состояние плеера живёт в MenuChrome.
        if (!lottieActive) MenuChrome.autoStartOnce();
        MenuChrome.tickMusic();

        if (lottieActive) {
            welcomeEndMs = 0L; // велком ещё идёт — сброс, чтобы после него интро проиграло с нуля
            ms.popMatrix();
            renderLottie(context);
            return;
        }

        // Плавный переход после велкома: контент выезжает снизу и проявляется из затемнения (~450ms).
        if (welcomeEndMs == 0L) welcomeEndMs = System.currentTimeMillis();
        float intro = (float) Math.min(1.0, (System.currentTimeMillis() - welcomeEndMs) / 450.0);
        intro = 1f - (1f - intro) * (1f - intro) * (1f - intro); // ease-out cubic

        ms.pushMatrix();
        ms.translate(0f, (1f - intro) * 22f); // слайд-ин снизу
        MenuChrome.drawLogo(ms, vw);
        MenuChrome.drawClock(ms, vw);
        drawWelcomeBlock(ms, vmx, vmy, delta);
        drawSocialsAndDisclaimer(ms, vmx, vmy, delta);
        MenuChrome.drawMusic(ms, vw, vh, vmx, vmy, hCover, rCover);
        ms.popMatrix();
        // Затемняющий скрим, гаснущий по мере появления контента.
        if (intro < 0.999f) {
            Render2D.drawRoundedRect(ms, 0, 0, vw, vh, 0f, new Color(6, 9, 8, Math.round(200 * (1f - intro))));
        }

        ms.popMatrix();
    }

    // ---------- Фон + скрим ----------
    private void drawBackground(Matrix3x2fStack ms) {
        MenuChrome.drawBackgroundArt(ms, vw, vh, parX, parY);
        drawScrim(ms);
    }

    /** Скрим — ОДИН градиентный квад на всю ширину (нет внутренних стыков → нет вертикальных полос вообще),
     *  умеренный слева (0.5) и в ноль справа, чтобы фон просвечивал, а не «чёрное пространство». */
    private void drawScrim(Matrix3x2fStack ms) {
        Render2D.drawHorizontalGradient(ms, 0, 0, vw, vh, new Color(6, 9, 8, 128), new Color(6, 9, 8, 0));
    }

    // ---------- Блок приветствия + аккаунт + кнопки (left 56, top 300) ----------
    private void drawWelcomeBlock(Matrix3x2fStack ms, int mx, int my, float delta) {
        float left = 56f, top = 300f, colW = 340f;
        String nick = mc != null && mc.getSession() != null ? mc.getSession().getUsername() : "player";

        // 1. Ярлык
        Instance lab = Fonts.UNBOUNDED_L.getFont(10f);
        drawSpaced(ms, lab, "ДАВНО НЕ ВИДЕЛИСЬ", left, top, ACCENT, 0.3f * 10f);
        float y = top + lab.getHeight() + 14f;

        // 2. Заголовок (2 строки)
        Instance h = Fonts.GOLOS_SEMI.getFont(38f);
        float lineH = 38f * 1.15f;
        Render2D.drawFont(ms, h, "Добро пожаловать", left, y, TEXT);
        Render2D.drawFont(ms, h, "вновь, " + nick, left, y + lineH, TEXT);
        y += lineH * 2f + 8f;

        // 3. Подпись
        Instance sub = Fonts.GOLOS.getFont(14f);
        Render2D.drawFont(ms, sub, "Готов прыгать по блокам?", left, y, MUTED2);
        y += sub.getHeight() + 28f;

        // --- Строка аккаунта ---
        float avatar = 38f;
        float accY = y;
        // аватар: тёмный круг + буква
        Render2D.drawRoundedVerticalGradient(ms, left, accY, avatar, avatar, avatar / 2f,
                new Color(0x2a, 0x38, 0x30), new Color(0x10, 0x16, 0x13));
        Instance av = Fonts.UNBOUNDED.getFont(12f);
        String initial = nick.isEmpty() ? "?" : nick.substring(0, 1).toUpperCase(Locale.ROOT);
        Render2D.drawFont(ms, av, initial, left + (avatar - av.getWidth(initial)) / 2f, accY + (avatar - av.getHeight()) / 2f, ACCENT);
        // мета + ник
        float tx = left + avatar + 12f;
        Instance meta = Fonts.GOLOS.getFont(10.5f);
        String license = licenseWord();
        Render2D.drawFont(ms, Fonts.ICONS2.getFont(9f), IC_PERSON, tx, accY + 1f, MUTED2);
        drawSpaced(ms, meta, " текущий аккаунт · " + license, tx + 10f, accY + 1f, MUTED2, 0.08f * 10.5f);
        Instance nf = Fonts.UNBOUNDED.getFont(13f);
        Render2D.drawFont(ms, nf, nick, tx, accY + 18f, TEXT);
        // «сменить»
        Instance chf = Fonts.GOLOS.getFont(11f);
        float chW = chf.getWidth("сменить");
        // По горизонтали ставим «сменить» на одну ось с текстом кнопки «Выход» ниже (она по центру
        // правого блока шириной exitW=108 колонки) — линк и кнопка выровнены по одному уровню.
        float chX = (left + colW - 108f / 2f) - chW / 2f, chY = accY + (avatar - chf.getHeight()) / 2f;
        boolean chH = hit(mx, my, chX, chY, chW, chf.getHeight());
        float chT = upd(hChange, chH);
        // Посветлее: базовый цвет MUTED (был MUTED3 — читался слишком тускло), hover — почти белый.
        Render2D.drawFont(ms, chf, "сменить", chX, chY, lerp(MUTED, TEXT, chT));
        set(rChange, chX, chY, chW, chf.getHeight());

        y = accY + avatar + 18f;

        // --- Кнопки ---
        float bh = 50f, gap = 9f;
        // 1. Сетевая игра (primary)
        boolean netH = hit(mx, my, left, y, colW, bh);
        drawButton(ms, left, y, colW, bh, true, IC_GLOBE, "Сетевая игра", INK, upd(hNet, netH), false, false);
        set(rNet, left, y, colW, bh);
        y += bh + gap;
        // 2. Одиночная (secondary)
        boolean sgH = hit(mx, my, left, y, colW, bh);
        drawButton(ms, left, y, colW, bh, false, IC_PERSON, "Одиночная игра", TEXT2, upd(hSingle, sgH), false, false);
        set(rSingle, left, y, colW, bh);
        y += bh + gap;
        // 3. Настройки + Выход
        float exitW = 108f, setW = colW - exitW - gap;
        boolean stH = hit(mx, my, left, y, setW, bh);
        drawButton(ms, left, y, setW, bh, false, IC_GEAR, "Настройки", TEXT2, upd(hSettings, stH), false, false);
        set(rSettings, left, y, setW, bh);
        float ex = left + setW + gap;
        boolean armed = System.currentTimeMillis() < exitArmedUntil;
        boolean exH = hit(mx, my, ex, y, exitW, bh);
        drawButton(ms, ex, y, exitW, bh, false, IC_POWER, armed ? "Точно?" : "Выход", armed ? DANGER : TEXT4, upd(hExit, exH), true, true);
        set(rExit, ex, y, exitW, bh);
    }

    // ---------- Кнопка (материал общий с «Сетевой игрой» — см. MenuChrome) ----------
    private void drawButton(Matrix3x2fStack ms, float x, float y, float w, float h, boolean primary,
                            String iconGlyph, String label, Color labelColor, float hoverT, boolean centered, boolean danger) {
        MenuChrome.drawButton(ms, x, y, w, h, primary, iconGlyph, label, labelColor, hoverT, centered, danger);
    }

    // ---------- Соц-иконки + дисклеймер (left 56, bottom 44) ----------
    private void drawSocialsAndDisclaimer(Matrix3x2fStack ms, int mx, int my, float delta) {
        float left = 56f;
        Instance disc = Fonts.GOLOS.getFont(11f);
        String[] lines = {
                "Linken Visuals не связан с Mojang и Microsoft и не является",
                "официальным продуктом Minecraft. Версия " + MenuChrome.MC_VERSION + ", сборка " + MenuChrome.BUILD + "."
        };
        float discLineH = disc.getHeight() * 1.5f;
        float discTop = vh - 44f - discLineH * lines.length;
        for (int i = 0; i < lines.length; i++) {
            Render2D.drawFont(ms, disc, lines[i], left, discTop + i * discLineH, MUTED4);
        }

        float sq = 38f, gap = 9f;
        float socY = discTop - 16f - sq;
        String[] glyphs = { IC_TG, IC_DISCORD, null /*LV*/, IC_CODE };
        for (int i = 0; i < 4; i++) {
            float sx = left + i * (sq + gap);
            boolean hh = hit(mx, my, sx, socY, sq, sq);
            float t = upd(hSoc[i], hh);
            float sy = socY - 2f * t; // translateY(-2)
            Render2D.drawRoundedVerticalGradient(ms, sx, sy, sq, sq, 8f, new Color(24, 30, 27, 217), new Color(15, 19, 17, 217));
            Render2D.drawRoundedRect(ms, sx + 8f, sy + 1f, sq - 16f, 1f, 0.5f, new Color(255, 255, 255, 13));
            Render2D.drawBorder(ms, sx, sy, sq, sq, 8f, 1.0f, 1.4f, lerp(new Color(255, 255, 255, 23), new Color(255, 255, 255, 61), t));
            if (glyphs[i] != null) {
                Instance gf = Fonts.ICONS2.getFont(17f);
                Render2D.drawFont(ms, gf, glyphs[i], sx + (sq - gf.getWidth(glyphs[i])) / 2f, sy + (sq - gf.getHeight()) / 2f, lerp(MUTED, ACCENT, t));
            } else {
                // «LV»
                Instance lvf = Fonts.UNBOUNDED.getFont(12f);
                float lw = lvf.getWidth("L"), vwd = lvf.getWidth("V");
                float lvx = sx + (sq - (lw + vwd)) / 2f, lvy = sy + (sq - lvf.getHeight()) / 2f;
                Render2D.drawFont(ms, lvf, "L", lvx, lvy, TEXT2);
                Render2D.drawFont(ms, lvf, "V", lvx + lw, lvy, ACCENT);
            }
            set(rSoc[i], sx, socY, sq, sq);
        }
    }

    // ---------- Клики ----------
    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubleClick) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        if (MainMenuGreeting.isLottieActive()) return true;
        if (button != 0) return super.mouseClicked(click, doubleClick);
        float mx = (float) (mouseX / uiScale), my = (float) (mouseY / uiScale);
        if (in(rNet, mx, my)) {
            MenuChrome.keepMusicAcrossSwap(); // трек продолжает играть на «Сетевой игре»
            mc.setScreen(new ru.method.linkenvisuals.screen.multiplayer.CustomMultiplayerScreen(this));
            return true;
        }
        if (in(rSingle, mx, my)) { mc.setScreen(new ru.method.linkenvisuals.screen.world.CustomSingleplayerScreen(this)); return true; }
        if (in(rSettings, mx, my)) {
            MenuChrome.keepMusicAcrossSwap(); // трек продолжает играть в хабе настроек
            mc.setScreen(new ru.method.linkenvisuals.screen.settings.SettingsHubScreen(this));
            return true;
        }
        if (in(rExit, mx, my)) {
            if (System.currentTimeMillis() < exitArmedUntil) { mc.scheduleStop(); }
            else { exitArmedUntil = System.currentTimeMillis() + 3000L; }
            return true;
        }
        if (in(rChange, mx, my)) { openAccountSwitcher(); return true; }
        for (int i = 0; i < 4; i++) if (in(rSoc[i], mx, my)) { openSocial(i); return true; }
        if (in(rCover, mx, my)) { toggleMusic(); return true; }
        return super.mouseClicked(click, doubleClick);
    }

    /** «Сменить» → кастомный свитчер аккаунтов поверх моста к моду «ias» (опционален). */
    private void openAccountSwitcher() {
        if (mc == null || !ru.method.linkenvisuals.screen.account.IasBridge.available()) return;
        mc.setScreen(new ru.method.linkenvisuals.screen.account.AccountSwitcherScreen(this));
    }

    private void openSocial(int i) {
        String url = switch (i) {
            case 0 -> CommunityLinks.TELEGRAM_URL;
            case 1 -> CommunityLinks.DISCORD_URL;
            case 2 -> CommunityLinks.SITE_URL;
            default -> CommunityLinks.DEV_URL;
        };
        try { net.minecraft.util.Util.getOperatingSystem().open(java.net.URI.create(url)); }
        catch (Exception e) { LinkenVisuals.LOGGER.warn("[MainMenu] open url {}", url, e); }
    }

    // ---------- Утилиты рендера (реализация — в MenuChrome, общая с «Сетевой игрой») ----------
    private void drawSpaced(Matrix3x2fStack ms, Instance f, String text, float x, float y, Color color, float spacing) {
        MenuChrome.drawSpaced(ms, f, text, x, y, color, spacing);
    }

    private boolean hit(int mx, int my, float x, float y, float w, float h) { return MenuChrome.hit(mx, my, x, y, w, h); }
    private boolean in(float[] r, float mx, float my) { return MenuChrome.in(r, mx, my); }
    private void set(float[] r, float x, float y, float w, float h) { MenuChrome.set(r, x, y, w, h); }
    private float upd(Animation a, boolean hovered) { return MenuChrome.upd(a, hovered); }
    private Color lerp(Color a, Color b, float t) { return MenuChrome.lerp(a, b, t); }
    private String licenseWord() { return MenuChrome.licenseWord(); }

    private void renderLottie(DrawContext context) {
        Matrix3x2fStack ms = context.getMatrices();
        ms.pushMatrix();
        ms.scale(uiScale, uiScale);
        MainMenuGreeting.renderLottie(context, vw, vh);
        ms.popMatrix();
    }

    // ---------- Музыка (состояние и управление — в MenuChrome, общее с «Сетевой игрой») ----------
    private void toggleMusic() { MenuChrome.toggleMusic(); }

    /** Трек глушим, только если уходим из кластера меню; переход меню↔«Сетевая игра» его не прерывает. */
    @Override public void removed() { MenuChrome.onMenuScreenRemoved(); super.removed(); }
    @Override public boolean shouldCloseOnEsc() { return false; }

    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }
}
