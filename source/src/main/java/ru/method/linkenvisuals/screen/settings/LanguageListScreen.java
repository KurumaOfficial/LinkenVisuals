package ru.method.linkenvisuals.screen.settings;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.resource.language.LanguageDefinition;
import net.minecraft.client.resource.language.LanguageManager;
import org.joml.Matrix3x2fStack;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import ru.method.linkenvisuals.screen.clickgui.redesign.ClickGuiIcons;
import ru.method.linkenvisuals.screen.mainmenu.MenuChrome;
import ru.method.linkenvisuals.screen.multiplayer.WindowMaterial;
import ru.method.linkenvisuals.utils.animations.Animation;
import ru.method.linkenvisuals.utils.render.Render2D;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;
import ru.method.linkenvisuals.utils.render.fonts.Instance;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 9c — экран выбора языка. Список языков строками: у выбранного слева акцентная полоса 3px и галочка справа.
 * Сверху закреплённая плитка с тоггл «Принудительно юникод» (не скроллится вместе со списком).
 */
public final class LanguageListScreen extends LinkenSettingsScreen {

    private static final float WIN_W = 1060f, WIN_TOP = 130f, PAD_X = 22f;
    private static final float TOPBAR_H = 64f, FOOTER_H = 64f;
    private static final float ROW_H = 44f, ROW_GAP = 8f, PAD_Y = 20f;
    private static final String CRUMB = "Язык";

    /** Одна строка списка: код языка (ru_ru) и его отображаемое имя. */
    private record Lang(String code, String display) {}

    private final List<Lang> langs = new ArrayList<>();

    private final Animation hBack = MenuChrome.anim(), hDone = MenuChrome.anim(), hSearch = MenuChrome.anim();
    private final float[] rBack = new float[4], rDone = new float[4], rSearch = new float[4];
    private final float[] rUnicode = new float[4];
    private float unicodeAnim;

    private String query = "";
    private boolean searchFocused;
    private float scroll, scrollTarget;
    private long lastFrameMs;

    // Хитбоксы видимых строк списка + индексы соответствующих языков (пересобираются каждый кадр).
    private final List<float[]> rowRects = new ArrayList<>();
    private final List<Integer> rowIdx = new ArrayList<>();

    public LanguageListScreen(Screen parent) {
        super(parent, "Настройки — " + CRUMB);
        LanguageManager lm = mc.getLanguageManager();
        for (Map.Entry<String, LanguageDefinition> e : lm.getAllLanguages().entrySet()) {
            langs.add(new Lang(e.getKey(), e.getValue().getDisplayText().getString()));
        }
        langs.sort(Comparator.comparing(Lang::display, String.CASE_INSENSITIVE_ORDER));
        unicodeAnim = forceUnicode() ? 1f : 0f;
    }

    private boolean forceUnicode() {
        return mc.options.getForceUnicodeFont().getValue();
    }

    private float winH() {
        float contentAll = PAD_Y * 2f + (langs.size() + 1) * ROW_H + langs.size() * ROW_GAP + 4f;
        float desired = TOPBAR_H + contentAll + FOOTER_H;
        float maxH = vh - WIN_TOP - 74f;
        return Math.min(desired, Math.max(TOPBAR_H + ROW_H * 2f + FOOTER_H, maxH));
    }

    @Override
    protected void drawContent(DrawContext context, Matrix3x2fStack ms, int mx, int my) {
        float x = winX(WIN_W), y = WIN_TOP, w = WIN_W, h = winH();
        WindowMaterial.window(ms, x, y, w, h);

        drawTopbar(ms, x, y, w, mx, my);
        Render2D.drawRoundedRect(ms, x, y + TOPBAR_H - 1f, w, 1f, 0f, MenuChrome.white(0.07f));

        float bodyY = y + TOPBAR_H, bodyH = h - TOPBAR_H - FOOTER_H;
        drawUnicodeRow(ms, x + PAD_X, bodyY + PAD_Y, w - PAD_X * 2f, mx, my);

        float listY = bodyY + PAD_Y + ROW_H + 12f;
        float listH = bodyY + bodyH - listY - 6f;
        drawList(context, ms, x, listY, w, listH, mx, my);

        drawFooter(ms, x, y + h - FOOTER_H, w, mx, my);
        WindowMaterial.corners(ms, x, y, w, h);
    }

    // ---------- Топбар: ‹ + крошка + поиск ----------
    private void drawTopbar(Matrix3x2fStack ms, float x, float y, float w, int mx, int my) {
        float cy = y + TOPBAR_H / 2f;

        float bs = 36f, bx = x + PAD_X, by = cy - bs / 2f + 6f;
        boolean bHov = MenuChrome.hit(mx, my, bx, by, bs, bs);
        float bt = MenuChrome.upd(hBack, bHov);
        WindowMaterial.fieldBox(ms, bx, by, bs, bs, bt, false);
        ClickGuiIcons.arrowLeft(ms, bx + bs / 2f, by + bs / 2f, 16f, MenuChrome.lerp(MenuChrome.MUTED, MenuChrome.TEXT2, bt));
        MenuChrome.set(rBack, bx, by, bs, bs);

        float cx = bx + bs + 16f;
        Instance rf = Fonts.GOLOS.getFont(13f);
        Render2D.drawFont(ms, rf, "Настройки", cx, cy - rf.getHeight() / 2f, MenuChrome.MUTED2);
        cx += rf.getWidth("Настройки") + 8f;
        Render2D.drawFont(ms, rf, "/", cx, cy - rf.getHeight() / 2f, MenuChrome.MUTED4);
        cx += rf.getWidth("/") + 8f;
        Instance tf = Fonts.GOLOS_SEMI.getFont(17f);
        Render2D.drawFont(ms, tf, CRUMB, cx, cy - tf.getHeight() / 2f, MenuChrome.TEXT);

        float sw = 250f, sh = 36f, sx = x + w - PAD_X - sw, sy = cy - sh / 2f;
        boolean sHov = MenuChrome.hit(mx, my, sx, sy, sw, sh);
        float st = MenuChrome.upd(hSearch, sHov || searchFocused);
        WindowMaterial.fieldBox(ms, sx, sy, sw, sh, st, false);
        ClickGuiIcons.search(ms, sx + 13f + 7f, sy + sh / 2f, 16f, MenuChrome.lerp(MenuChrome.MUTED3, MenuChrome.accent(), searchFocused ? 1f : 0f));
        Instance pf = Fonts.GOLOS.getFont(12.5f);
        float qx = sx + 13f + 14f + 10f;
        if (query.isEmpty() && !searchFocused) {
            Render2D.drawFont(ms, pf, "Поиск языка...", qx, sy + sh / 2f - pf.getHeight() / 2f, MenuChrome.MUTED3);
        } else {
            Render2D.drawFont(ms, pf, query, qx, sy + sh / 2f - pf.getHeight() / 2f, MenuChrome.TEXT2);
            if (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
                Render2D.drawRoundedRect(ms, qx + pf.getWidth(query) + 1f, sy + sh / 2f - 6f, 1f, 12f, 0.5f, MenuChrome.TEXT2);
            }
        }
        MenuChrome.set(rSearch, sx, sy, sw, sh);
    }

    // ---------- Закреплённая плитка с тогглом ----------
    private void drawUnicodeRow(Matrix3x2fStack ms, float x, float y, float w, int mx, int my) {
        boolean hov = MenuChrome.hit(mx, my, x, y, w, ROW_H);
        Render2D.drawRoundedVerticalGradient(ms, x, y, w, ROW_H, 10f,
                new Color(24, 30, 27, 179), new Color(15, 19, 17, 179));
        Render2D.drawBorder(ms, x, y, w, ROW_H, 10f, 1.0f, 1.4f,
                MenuChrome.lerp(MenuChrome.white(0.08f), MenuChrome.white(0.22f), hov ? 1f : 0f));

        Instance lf = Fonts.GOLOS.getFont(13f);
        Render2D.drawFont(ms, lf, "Принудительно юникод", x + 16f, y + ROW_H / 2f - lf.getHeight() / 2f, MenuChrome.TEXT2);

        float cy = y + ROW_H / 2f, right = x + w - 14f;
        float tw = 32f, th = 18f, tx = right - tw, ty = cy - th / 2f;
        float target = forceUnicode() ? 1f : 0f;
        unicodeAnim += (target - unicodeAnim) * 0.35f;
        if (Math.abs(target - unicodeAnim) < 0.01f) unicodeAnim = target;
        drawToggle(ms, tx, ty, tw, th, unicodeAnim);
        MenuChrome.set(rUnicode, tx, ty, tw, th);
    }

    // ---------- Список языков ----------
    private void drawList(DrawContext context, Matrix3x2fStack ms, float x, float y, float w, float viewH, int mx, int my) {
        rowRects.clear();
        rowIdx.clear();

        List<Integer> vis = new ArrayList<>();
        String q = query.trim().toLowerCase(Locale.ROOT);
        for (int i = 0; i < langs.size(); i++) {
            Lang l = langs.get(i);
            if (q.isEmpty() || l.display().toLowerCase(Locale.ROOT).contains(q) || l.code().contains(q)) vis.add(i);
        }

        float contentH = vis.size() * ROW_H + Math.max(0, vis.size() - 1) * ROW_GAP;
        float maxScroll = Math.max(0f, contentH - viewH);

        long now = System.currentTimeMillis();
        float dt = lastFrameMs == 0L ? 0.016f : Math.min(0.05f, (now - lastFrameMs) / 1000f);
        lastFrameMs = now;
        scrollTarget = MathHelper.clamp(scrollTarget, 0f, maxScroll);
        scroll += (scrollTarget - scroll) * (1f - (float) Math.exp(-13f * dt));
        if (Math.abs(scrollTarget - scroll) < 0.4f) scroll = scrollTarget;

        String current = mc.getLanguageManager().getLanguage();
        float rowX = x + PAD_X, rowW = w - PAD_X * 2f;

        Render2D.startScissor(context, x, y, w, viewH);
        for (int k = 0; k < vis.size(); k++) {
            int li = vis.get(k);
            float ry = y + k * (ROW_H + ROW_GAP) - scroll;
            float appear = MathHelper.clamp((now - openMs - k * 12L) / 220f, 0f, 1f);
            appear = 1f - (1f - appear) * (1f - appear);
            float ary = ry + (1f - appear) * 12f;
            if (ary + ROW_H < y || ary > y + viewH) continue;
            drawRow(ms, langs.get(li), rowX, ary, rowW, current, mx, my);
            // Хитбокс режем по вьюпорту и берём от АНИМИРОВАННОЙ ary: scissor обрезает только пиксели,
            // иначе клик по невидимой части строки достаёт до плитки «Юникод» сверху и до футера снизу,
            // а во время стаггер-появления клик расходится с отрисовкой на 12px.
            float hitTop = Math.max(ary, y), hitBot = Math.min(ary + ROW_H, y + viewH);
            if (hitBot - hitTop < 1f) continue;
            float[] r = new float[4];
            MenuChrome.set(r, rowX, hitTop, rowW, hitBot - hitTop);
            rowRects.add(r);
            rowIdx.add(li);
        }
        Render2D.stopScissor(context);

        if (maxScroll > 0.5f) {
            float trackH = viewH - 8f;
            float thumbH = Math.max(24f, trackH * (viewH / contentH));
            float thumbY = y + 4f + (trackH - thumbH) * (scroll / maxScroll);
            Render2D.drawRoundedRect(ms, x + w - 6f, thumbY, 3f, thumbH, 1.5f, MenuChrome.accentA(0.5f));
        }
    }

    private void drawRow(Matrix3x2fStack ms, Lang l, float x, float y, float w, String current, int mx, int my) {
        boolean sel = l.code().equals(current);
        boolean hov = MenuChrome.hit(mx, my, x, y, w, ROW_H);

        Render2D.drawRoundedVerticalGradient(ms, x, y, w, ROW_H, 10f,
                new Color(24, 30, 27, 179), new Color(15, 19, 17, 179));
        if (sel) Render2D.drawRoundedRect(ms, x, y, w, ROW_H, 10f, MenuChrome.accentA(0.07f));
        Color bd;
        if (sel) bd = MenuChrome.accentA(0.35f);
        else bd = MenuChrome.lerp(MenuChrome.white(0.08f), MenuChrome.white(0.22f), hov ? 1f : 0f);
        Render2D.drawBorder(ms, x, y, w, ROW_H, 10f, 1.0f, 1.4f, bd);

        if (sel) Render2D.drawRoundedRect(ms, x + 1f, y + 10f, 3f, ROW_H - 20f, 1.5f, MenuChrome.accent());

        Instance lf = Fonts.GOLOS.getFont(13f);
        Color nameCol;
        if (sel) nameCol = MenuChrome.TEXT;
        else nameCol = MenuChrome.TEXT2;
        Render2D.drawFont(ms, lf, l.display(), x + 16f, y + ROW_H / 2f - lf.getHeight() / 2f, nameCol);

        float cy = y + ROW_H / 2f, right = x + w - 14f;
        if (sel) {
            ClickGuiIcons.check(ms, right - 7f, cy, 14f, MenuChrome.accent());
            right -= 14f + 10f;
        }
        Instance cf = Fonts.GOLOS.getFont(11.5f);
        Render2D.drawFont(ms, cf, l.code(), right - cf.getWidth(l.code()), cy - cf.getHeight() / 2f, MenuChrome.MUTED3);
    }

    private void drawToggle(Matrix3x2fStack ms, float x, float y, float w, float h, float t) {
        Render2D.drawRoundedRect(ms, x, y, w, h, h / 2f, MenuChrome.white(0.10f));
        if (t > 0.01f) {
            Color acc = MenuChrome.accent();
            int a = Math.round(255 * t);
            Color top = new Color(Math.min(255, acc.getRed() + 20), Math.min(255, acc.getGreen() + 16), Math.min(255, acc.getBlue() + 12), a);
            Color bot = new Color(acc.getRed(), acc.getGreen(), acc.getBlue(), a);
            MenuChrome.glow(ms, x, y, w, h, h / 2f, MenuChrome.accentA(0.55f * t), 0f, 3, 1.4f);
            Render2D.drawRoundedVerticalGradient(ms, x, y, w, h, h / 2f, top, bot);
            Render2D.drawRoundedRect(ms, x + 4f, y + 1f, w - 8f, 1f, 0.5f, new Color(255, 255, 255, Math.round(128 * t)));
        }
        float knobCx = x + 9f + t * 14f, knobCy = y + h / 2f;
        Render2D.drawCircle(ms, knobCx, knobCy, 6f, MenuChrome.lerp(MenuChrome.TEXT3, MenuChrome.INK, t));
    }

    // ---------- Футер ----------
    private void drawFooter(Matrix3x2fStack ms, float x, float y, float w, int mx, int my) {
        float cy = y + FOOTER_H / 2f;
        Instance bf = Fonts.UNBOUNDED.getFont(11f);
        String label = "Готово";
        float icon = 16f, bh = 44f;
        float bw = 24f + icon + 9f + bf.getWidth(label) + 24f;
        float bx = x + w - PAD_X - bw, by = cy - bh / 2f - 9f;
        boolean hov = MenuChrome.hit(mx, my, bx, by, bw, bh);
        float t = MenuChrome.upd(hDone, hov);
        float ty = by - t;
        Color acc = MenuChrome.accent();
        Render2D.drawRoundedRect(ms, bx + 4f, ty + bh - 6f, bw - 8f, 12f, 8f, MenuChrome.accentA(0.24f + 0.14f * t));
        int br = Math.min(255, Math.round(acc.getRed() * (1f + 0.07f * t)));
        int bg = Math.min(255, Math.round(acc.getGreen() * (1f + 0.07f * t)));
        int bb = Math.min(255, Math.round(acc.getBlue() * (1f + 0.07f * t)));
        Render2D.drawRoundedRect(ms, bx, ty, bw, bh, 8f, new Color(br, bg, bb));
        Render2D.drawRoundedRect(ms, bx + 8f, ty + 1f, bw - 16f, 1f, 0.5f, MenuChrome.white(0.35f));
        ClickGuiIcons.check(ms, bx + 24f + icon / 2f, ty + bh / 2f, icon, MenuChrome.INK);
        Render2D.drawFont(ms, bf, label, bx + 24f + icon + 9f, ty + bh / 2f - bf.getHeight() / 2f, MenuChrome.INK);
        MenuChrome.set(rDone, bx, by, bw, bh);
    }

    /** Применение языка — тем же порядком, что ваниль (LanguageOptionsScreen.onDone). */
    private void apply(String code) {
        LanguageManager lm = mc.getLanguageManager();
        if (code.equals(lm.getLanguage())) return;
        lm.setLanguage(code);
        mc.options.language = code;
        mc.reloadResources();
        mc.options.write();
    }

    // ---------- Ввод ----------
    @Override
    protected boolean contentClick(float mx, float my, int button) {
        if (button != 0) return false;
        searchFocused = MenuChrome.in(rSearch, mx, my);
        if (searchFocused) return true;
        if (MenuChrome.in(rBack, mx, my)) { back(); return true; }
        if (MenuChrome.in(rDone, mx, my)) { mc.options.write(); back(); return true; }
        if (MenuChrome.in(rUnicode, mx, my)) {
            mc.options.getForceUnicodeFont().setValue(!forceUnicode()); // колбэк опции сам перезагружает шрифты
            mc.options.write();
            return true;
        }
        for (int k = 0; k < rowRects.size(); k++) {
            if (MenuChrome.in(rowRects.get(k), mx, my)) {
                apply(langs.get(rowIdx.get(k)).code());
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean contentScroll(float mx, float my, double vertical) {
        scrollTarget -= (float) vertical * (ROW_H + ROW_GAP) * 0.9f;
        return true;
    }

    @Override
    protected boolean contentKey(int keyCode, int scanCode, int modifiers) {
        if (searchFocused) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!query.isEmpty()) query = query.substring(0, query.length() - 1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) { searchFocused = false; return true; }
        }
        return false;
    }

    @Override
    protected boolean contentChar(char chr, int modifiers) {
        if (searchFocused && chr >= ' ') { query += chr; return true; }
        return false;
    }

    @Override
    protected void back() {
        mc.options.write();
        super.back();
    }
}
