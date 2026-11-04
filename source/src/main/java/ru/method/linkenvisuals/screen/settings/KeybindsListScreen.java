package ru.method.linkenvisuals.screen.settings;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.joml.Matrix3x2fStack;
import net.minecraft.text.Text;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 9c — «Настройки / Клавиши»: все бинды игры строками (как менеджер аккаунтов), сгруппированные по категориям.
 * Клик по кейкапу — режим записи, следующая клавиша/кнопка мыши становится биндом, ESC отменяет запись.
 */
public final class KeybindsListScreen extends LinkenSettingsScreen {

    private static final float WIN_W = 1060f, WIN_TOP = 130f, PAD_X = 22f;
    private static final float TOPBAR_H = 64f, FOOTER_H = 64f;
    private static final float ROW_H = 44f, ROW_GAP = 8f, PAD_Y = 20f;
    private static final float HEAD_H = 34f, KEY_W_MIN = 120f, KEY_H = 28f, RESET_W = 28f;

    private final Animation hBack = MenuChrome.anim(), hDone = MenuChrome.anim(),
            hSearch = MenuChrome.anim(), hResetAll = MenuChrome.anim();
    private final float[] rBack = new float[4], rDone = new float[4], rSearch = new float[4], rResetAll = new float[4];

    /** Строка списка: либо заголовок категории (binding == null), либо бинд. */
    private static final class Row {
        final KeyBinding binding;
        final String label;
        int index;
        Row(KeyBinding binding, String label) { this.binding = binding; this.label = label; }
    }

    private final List<Row> rows = new ArrayList<>();
    private final Set<KeyBinding> conflicts = new HashSet<>();
    private Animation[] hKey, hReset;
    private float[][] rKey, rReset;
    private boolean[] visKey, visReset;

    private String query = "";
    private boolean searchFocused;
    private float scroll, scrollTarget;
    private long lastFrameMs;

    private KeyBinding recording;

    public KeybindsListScreen(Screen parent) {
        super(parent, "Настройки — Клавиши");
        build();
    }

    private void build() {
        List<KeyBinding> all = new ArrayList<>(List.of(mc.options.allKeys));
        all.sort(KeyBinding::compareTo); // сортировка биндов сама группирует по порядку категорий
        KeyBinding.Category lastCat = null;
        for (KeyBinding b : all) {
            if (!b.getCategory().equals(lastCat)) {
                lastCat = b.getCategory();
                rows.add(new Row(null, lastCat.getLabel().getString()));
            }
            rows.add(new Row(b, Text.translatable(b.getId()).getString()));
        }
        int n = rows.size();
        for (int i = 0; i < n; i++) rows.get(i).index = i;
        hKey = new Animation[n];
        hReset = new Animation[n];
        rKey = new float[n][4];
        rReset = new float[n][4];
        visKey = new boolean[n];
        visReset = new boolean[n];
        for (int i = 0; i < n; i++) { hKey[i] = MenuChrome.anim(); hReset[i] = MenuChrome.anim(); }
        recalcConflicts();
    }

    /** Конфликты считаем разово (O(n²) по ~130 биндам), а не каждый кадр. */
    private void recalcConflicts() {
        conflicts.clear();
        KeyBinding[] all = mc.options.allKeys;
        for (int i = 0; i < all.length; i++) {
            if (all[i].isUnbound()) continue;
            for (int j = i + 1; j < all.length; j++) {
                if (all[i].equals(all[j])) { conflicts.add(all[i]); conflicts.add(all[j]); }
            }
        }
    }

    private float winH() {
        float contentAll = PAD_Y * 2f + contentH();
        float desired = TOPBAR_H + contentAll + FOOTER_H;
        float maxH = vh - WIN_TOP - 74f;
        return Math.min(desired, Math.max(TOPBAR_H + ROW_H + FOOTER_H, maxH));
    }

    private float contentH() {
        float h = 0f;
        for (Row r : visible()) h += (r.binding == null ? HEAD_H : ROW_H) + ROW_GAP;
        return Math.max(0f, h - ROW_GAP);
    }

    /** Видимые строки с учётом поиска: заголовок без непустой категории отбрасывается. */
    private List<Row> visible() {
        String q = query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return rows;
        List<Row> out = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            if (r.binding == null) {
                boolean any = false;
                for (int k = i + 1; k < rows.size() && rows.get(k).binding != null; k++) {
                    if (rows.get(k).label.toLowerCase(Locale.ROOT).contains(q)) { any = true; break; }
                }
                if (any) out.add(r);
            } else if (r.label.toLowerCase(Locale.ROOT).contains(q)) {
                out.add(r);
            }
        }
        return out;
    }

    @Override
    protected void drawContent(DrawContext context, Matrix3x2fStack ms, int mx, int my) {
        float x = winX(WIN_W), y = WIN_TOP, w = WIN_W, h = winH();
        WindowMaterial.window(ms, x, y, w, h);

        drawTopbar(ms, x, y, w, mx, my);
        Render2D.drawRoundedRect(ms, x, y + TOPBAR_H - 1f, w, 1f, 0f, MenuChrome.white(0.07f));

        drawRows(context, ms, x, y + TOPBAR_H, w, h - TOPBAR_H - FOOTER_H, mx, my);
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
        Render2D.drawFont(ms, tf, "Клавиши", cx, cy - tf.getHeight() / 2f, MenuChrome.TEXT);

        float sw = 250f, sh = 36f, sx = x + w - PAD_X - sw, sy = cy - sh / 2f;
        boolean sHov = MenuChrome.hit(mx, my, sx, sy, sw, sh);
        float st = MenuChrome.upd(hSearch, sHov || searchFocused);
        WindowMaterial.fieldBox(ms, sx, sy, sw, sh, st, false);
        ClickGuiIcons.search(ms, sx + 13f + 7f, sy + sh / 2f, 16f, MenuChrome.lerp(MenuChrome.MUTED3, MenuChrome.accent(), searchFocused ? 1f : 0f));
        Instance pf = Fonts.GOLOS.getFont(12.5f);
        float qx = sx + 13f + 14f + 10f;
        if (query.isEmpty() && !searchFocused) {
            Render2D.drawFont(ms, pf, "Поиск клавиши...", qx, sy + sh / 2f - pf.getHeight() / 2f, MenuChrome.MUTED3);
        } else {
            Render2D.drawFont(ms, pf, query, qx, sy + sh / 2f - pf.getHeight() / 2f, MenuChrome.TEXT2);
            if (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
                Render2D.drawRoundedRect(ms, qx + pf.getWidth(query) + 1f, sy + sh / 2f - 6f, 1f, 12f, 0.5f, MenuChrome.TEXT2);
            }
        }
        MenuChrome.set(rSearch, sx, sy, sw, sh);
    }

    // ---------- Список ----------
    private void drawRows(DrawContext context, Matrix3x2fStack ms, float x, float y, float w, float viewH, int mx, int my) {
        for (int i = 0; i < visKey.length; i++) { visKey[i] = false; visReset[i] = false; }

        List<Row> vis = visible();
        float contentH = PAD_Y * 2f + contentH();
        float maxScroll = Math.max(0f, contentH - viewH);

        long now = System.currentTimeMillis();
        float dt = lastFrameMs == 0L ? 0.016f : Math.min(0.05f, (now - lastFrameMs) / 1000f);
        lastFrameMs = now;
        scrollTarget = MathHelper.clamp(scrollTarget, 0f, maxScroll);
        scroll += (scrollTarget - scroll) * (1f - (float) Math.exp(-13f * dt));
        if (Math.abs(scrollTarget - scroll) < 0.4f) scroll = scrollTarget;

        float rowW = w - PAD_X * 2f;
        float ry = y + PAD_Y - scroll;
        Render2D.startScissor(context, x, y, w, viewH);
        for (int k = 0; k < vis.size(); k++) {
            Row r = vis.get(k);
            float rh = r.binding == null ? HEAD_H : ROW_H;
            float appear = MathHelper.clamp((now - openMs - k * 12L) / 220f, 0f, 1f);
            appear = 1f - (1f - appear) * (1f - appear);
            float ay = ry + (1f - appear) * 12f;
            if (ay + rh >= y && ay <= y + viewH) {
                if (r.binding == null) drawHeader(ms, r, x + PAD_X, ay, rowW);
                else drawBindRow(ms, r.index, r, x + PAD_X, ay, rowW, mx, my);
            }
            ry += rh + ROW_GAP;
        }
        Render2D.stopScissor(context);

        if (maxScroll > 0.5f) {
            float trackH = viewH - 8f;
            float thumbH = Math.max(24f, trackH * (viewH / contentH));
            float thumbY = y + 4f + (trackH - thumbH) * (scroll / maxScroll);
            Render2D.drawRoundedRect(ms, x + w - 6f, thumbY, 3f, thumbH, 1.5f, MenuChrome.accentA(0.5f));
        }
    }

    private void drawHeader(Matrix3x2fStack ms, Row r, float x, float y, float w) {
        Instance hf = Fonts.GOLOS_SEMI.getFont(12.5f);
        float ty = y + HEAD_H / 2f - hf.getHeight() / 2f;
        Render2D.drawFont(ms, hf, r.label, x + 2f, ty, MenuChrome.MUTED);
        float lx = x + 2f + hf.getWidth(r.label) + 12f;
        Render2D.drawRoundedRect(ms, lx, y + HEAD_H / 2f, w - (lx - x) - 2f, 1f, 0f, MenuChrome.white(0.07f));
    }

    private void drawBindRow(Matrix3x2fStack ms, int idx, Row r, float x, float y, float w, int mx, int my) {
        KeyBinding b = r.binding;
        boolean hov = MenuChrome.hit(mx, my, x, y, w, ROW_H);
        Render2D.drawRoundedVerticalGradient(ms, x, y, w, ROW_H, 10f,
                new Color(24, 30, 27, 179), new Color(15, 19, 17, 179));
        Render2D.drawBorder(ms, x, y, w, ROW_H, 10f, 1.0f, 1.4f,
                MenuChrome.lerp(MenuChrome.white(0.08f), MenuChrome.white(0.22f), hov ? 1f : 0f));

        Instance lf = Fonts.GOLOS.getFont(13f);
        Render2D.drawFont(ms, lf, r.label, x + 16f, y + ROW_H / 2f - lf.getHeight() / 2f, MenuChrome.TEXT2);

        float cy = y + ROW_H / 2f, right = x + w - 14f;

        // Кейкап
        boolean rec = recording == b;
        boolean conflict = !rec && conflicts.contains(b);
        Instance kf = Fonts.GOLOS.getFont(12f);
        String key;
        if (rec) key = "> ... <";
        else key = b.getBoundKeyLocalizedText().getString();
        float kw = Math.max(KEY_W_MIN, kf.getWidth(key) + 24f);
        float kx = right - kw, ky = cy - KEY_H / 2f;
        boolean kHov = MenuChrome.hit(mx, my, kx, ky, kw, KEY_H);
        float kt = MenuChrome.upd(hKey[idx], kHov || rec);
        WindowMaterial.fieldBox(ms, kx, ky, kw, KEY_H, kt, conflict);
        Color kc;
        if (rec) kc = MenuChrome.accent();
        else if (conflict) kc = MenuChrome.DANGER;
        else kc = MenuChrome.TEXT2;
        Render2D.drawFont(ms, kf, key, kx + kw / 2f - kf.getWidth(key) / 2f, ky + KEY_H / 2f - kf.getHeight() / 2f, kc);
        MenuChrome.set(rKey[idx], kx, ky, kw, KEY_H);
        visKey[idx] = true;

        float cursor = kx - 10f;
        if (conflict) {
            ClickGuiIcons.info(ms, cursor - 7f, cy, 14f, MenuChrome.DANGER);
            cursor -= 14f + 10f;
        }

        // «Сбросить» — только у изменённых биндов
        if (!b.isDefault()) {
            float rx = cursor - RESET_W, ryy = cy - RESET_W / 2f;
            boolean rHov = MenuChrome.hit(mx, my, rx, ryy, RESET_W, RESET_W);
            float rt = MenuChrome.upd(hReset[idx], rHov);
            MenuChrome.drawSecondaryMaterial(ms, rx, ryy, RESET_W, RESET_W, 7f, rt, false);
            ClickGuiIcons.refresh(ms, rx + RESET_W / 2f, cy, 13f, MenuChrome.lerp(MenuChrome.MUTED, MenuChrome.TEXT2, rt));
            MenuChrome.set(rReset[idx], rx, ryy, RESET_W, RESET_W);
            visReset[idx] = true;
        }
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

        // «Сбросить всё» — вторичная, слева от «Готово»
        Instance sf = Fonts.UNBOUNDED.getFont(11f);
        String sl = "Сбросить всё";
        float sw = 20f + 15f + 9f + sf.getWidth(sl) + 20f;
        float sx = bx - 10f - sw, sy = cy - bh / 2f - 9f;
        boolean sHov = MenuChrome.hit(mx, my, sx, sy, sw, bh);
        float rt = MenuChrome.upd(hResetAll, sHov);
        MenuChrome.drawSecondaryMaterial(ms, sx, sy - rt, sw, bh, 8f, rt, false);
        ClickGuiIcons.refresh(ms, sx + 20f + 7.5f, sy - rt + bh / 2f, 15f, MenuChrome.lerp(MenuChrome.MUTED, MenuChrome.TEXT2, rt));
        Render2D.drawFont(ms, sf, sl, sx + 20f + 15f + 9f, sy - rt + bh / 2f - sf.getHeight() / 2f,
                MenuChrome.lerp(MenuChrome.TEXT3, MenuChrome.TEXT, rt));
        MenuChrome.set(rResetAll, sx, sy, sw, bh);
    }

    // ---------- Ввод ----------
    private void applyKey(InputUtil.Key key) {
        recording.setBoundKey(key);
        recording = null;
        KeyBinding.updateKeysByCode();
        recalcConflicts();
        mc.options.write();
    }

    @Override
    protected boolean contentClick(float mx, float my, int button) {
        // В режиме записи любая кнопка мыши становится биндом.
        if (recording != null) {
            applyKey(InputUtil.Type.MOUSE.createFromCode(button));
            return true;
        }
        if (button != 0) return false;

        searchFocused = MenuChrome.in(rSearch, mx, my);
        if (searchFocused) return true;
        if (MenuChrome.in(rBack, mx, my)) { back(); return true; }
        if (MenuChrome.in(rDone, mx, my)) { mc.options.write(); back(); return true; }
        if (MenuChrome.in(rResetAll, mx, my)) {
            for (KeyBinding b : mc.options.allKeys) b.setBoundKey(b.getDefaultKey());
            KeyBinding.updateKeysByCode();
            recalcConflicts();
            mc.options.write();
            return true;
        }
        // Строки обрезаны ножницами по вьюпорту списка, а хит-ректы пишутся целиком — без отсечки
        // клик по полосе топбара/футера попадал в кейкап наполовину прокрученной невидимой строки.
        if (my < WIN_TOP + TOPBAR_H || my > WIN_TOP + winH() - FOOTER_H) return false;
        for (int i = 0; i < rows.size(); i++) {
            KeyBinding b = rows.get(i).binding;
            if (b == null) continue;
            if (visReset[i] && MenuChrome.in(rReset[i], mx, my)) {
                b.setBoundKey(b.getDefaultKey());
                KeyBinding.updateKeysByCode();
                recalcConflicts();
                mc.options.write();
                return true;
            }
            if (visKey[i] && MenuChrome.in(rKey[i], mx, my)) {
                recording = b;
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
        if (recording != null) {
            // ESC отменяет запись и НЕ должен закрывать экран — вернуть true раньше обработки ESC базой.
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) { recording = null; return true; }
            // Именно fromKeyCode, а не KEYSYM: у клавиш без GLFW-keysym keyCode = -1, и KEYSYM.createFromCode(-1)
            // отдаёт UNKNOWN_KEY, то есть бинд снимался бы. Ваниль в этом случае биндит по scancode.
            applyKey(keyCode != -1 ? InputUtil.Type.KEYSYM.createFromCode(keyCode) : InputUtil.Type.SCANCODE.createFromCode(scanCode));
            return true;
        }
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
