package ru.method.linkenvisuals.screen.account;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.DefaultSkinHelper;
import org.joml.Matrix3x2fStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import ru.method.linkenvisuals.screen.account.IasBridge.IasAccount;
import ru.method.linkenvisuals.screen.clickgui.redesign.ClickGuiIcons;
import ru.method.linkenvisuals.screen.mainmenu.MenuChrome;
import ru.method.linkenvisuals.screen.multiplayer.WindowMaterial;
import ru.method.linkenvisuals.screen.settings.LinkenSettingsScreen;
import ru.method.linkenvisuals.utils.animations.Animation;
import ru.method.linkenvisuals.utils.render.Render2D;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;
import ru.method.linkenvisuals.utils.render.fonts.Instance;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Экран 11b — кастомный свитчер аккаунтов поверх моста {@link IasBridge}. Наследует хром/фон/ESC/масштаб
 * от {@link LinkenSettingsScreen}: рисуем только окно 620px и обрабатываем ввод в content-хуках.
 * Список строк с головой скина, чипами «текущий»/тип, поиском, скроллом; двойной клик = вход. Футер:
 * Изменить/Удалить (иконки, удаление с confirm), Добавить/Войти оффлайн (secondary), Войти (primary).
 * Формы Добавить/Изменить — инлайн-модалки в том же материале (ponytail: отдельные классы не заводим).
 */
public final class AccountSwitcherScreen extends LinkenSettingsScreen {

    private static final float WIN_W = 620f, WIN_TOP = 130f, PAD_X = 20f;
    private static final float TOPBAR_H = 64f, FOOTER_H = 72f;
    private static final float ROW_H = 52f, ROW_GAP = 8f, PAD_Y = 16f;
    private static final float HEAD = 30f;

    private enum Modal { NONE, ADD, EDIT, OFFLINE }

    private List<IasAccount> accounts = new ArrayList<>();
    private UUID selectedUuid;

    private final Animation hSearch = MenuChrome.anim();
    private final Animation hEdit = MenuChrome.anim(), hDelete = MenuChrome.anim();
    private final Animation hAdd = MenuChrome.anim(), hOffline = MenuChrome.anim(), hLogin = MenuChrome.anim();
    private final float[] rSearch = new float[4];
    private final float[] rEdit = new float[4], rDelete = new float[4];
    private final float[] rAdd = new float[4], rOffline = new float[4], rLogin = new float[4];
    private final List<float[]> rowRects = new ArrayList<>(); // {x,y,w,h, accountIndex}
    private final Map<UUID, Animation> rowHover = new HashMap<>();
    private final Map<UUID, Identifier> skinCache = new HashMap<>();

    private String query = "";
    private boolean searchFocused;
    private long listAnimMs;
    private float caretX;

    private float scroll, scrollTarget;
    private long lastFrameMs;

    // Двойной клик = вход.
    private long lastClickMs;
    private UUID lastClickUuid;

    // Удаление с подтверждением: первый клик «взводит» на 2.5с (красный hover), второй — удаляет.
    private long deleteArmedUntil;

    // Инлайн-модалка Добавить/Изменить.
    private Modal modal = Modal.NONE;
    private String draft = "";
    private boolean draftSelected; // Ctrl+A выделил весь ник: следующий ввод/вставка заменяет, backspace чистит
    // Анимация ввода (как в поле адреса MultiplayerModal): последний введённый глиф «въезжает» снизу с фейдом.
    private long draftEditMs;
    private boolean draftEditInsert;
    private final Animation hModalOk = MenuChrome.anim(), hModalCancel = MenuChrome.anim();
    private final float[] rModalOk = new float[4], rModalCancel = new float[4], rModalWin = new float[4];

    public AccountSwitcherScreen(Screen parent) {
        super(parent, "Аккаунты");
        refresh();
        // Стартовый выбор — текущий аккаунт, иначе первый.
        UUID cur = IasBridge.currentUuid();
        for (IasAccount a : accounts) {
            if (a.uuid().equals(cur)) { selectedUuid = a.uuid(); break; }
        }
        if (selectedUuid == null && !accounts.isEmpty()) selectedUuid = accounts.get(0).uuid();
    }

    private void refresh() {
        accounts = IasBridge.list();
    }

    @Override
    protected String escHintLabel() {
        return "назад";
    }

    private List<IasAccount> visible() {
        String q = query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return accounts;
        List<IasAccount> out = new ArrayList<>();
        for (IasAccount a : accounts) {
            if (a.name().toLowerCase(Locale.ROOT).contains(q)) out.add(a);
        }
        return out;
    }

    private IasAccount selected() {
        if (selectedUuid == null) return null;
        for (IasAccount a : accounts) if (a.uuid().equals(selectedUuid)) return a;
        return null;
    }

    private boolean isActive(IasAccount a) {
        UUID cu = IasBridge.currentUuid();
        String cn = IasBridge.currentName();
        return cu != null && cu.equals(a.uuid()) && a.name().equals(cn);
    }

    private float winH() {
        int rows = Math.max(1, visible().size());
        float contentAll = PAD_Y * 2f + rows * ROW_H + Math.max(0, rows - 1) * ROW_GAP;
        float desired = TOPBAR_H + contentAll + FOOTER_H;
        float maxH = vh - WIN_TOP - 74f;
        return Math.min(desired, Math.max(TOPBAR_H + ROW_H + FOOTER_H, maxH));
    }

    // ---------- Рендер ----------
    @Override
    protected void drawContent(DrawContext context, Matrix3x2fStack ms, int mx, int my) {
        float x = winX(WIN_W), y = WIN_TOP, w = WIN_W, h = winH();
        WindowMaterial.window(ms, x, y, w, h);

        drawTopbar(ms, x, y, w, mx, my);
        Render2D.drawRoundedRect(ms, x, y + TOPBAR_H - 1f, w, 1f, 0f, MenuChrome.white(0.07f));

        drawRows(context, ms, x, y + TOPBAR_H, w, h - TOPBAR_H - FOOTER_H, mx, my);
        drawFooter(ms, x, y + h - FOOTER_H, w, mx, my);

        WindowMaterial.corners(ms, x, y, w, h);

        if (modal != Modal.NONE) drawModal(ms, mx, my);
    }

    // ---------- Топбар: заголовок + чип-счётчик + поиск ----------
    private void drawTopbar(Matrix3x2fStack ms, float x, float y, float w, int mx, int my) {
        float cy = y + TOPBAR_H / 2f;

        Instance tf = Fonts.GOLOS_SEMI.getFont(17f);
        float tx = x + PAD_X;
        Render2D.drawFont(ms, tf, "Аккаунты", tx, cy - tf.getHeight() / 2f, MenuChrome.TEXT);
        float cx = tx + tf.getWidth("Аккаунты") + 10f;

        // Чип-счётчик.
        Instance cf = Fonts.GOLOS.getFont(11.5f);
        String cnt = String.valueOf(accounts.size());
        float chW = cf.getWidth(cnt) + 16f, chH = 20f, chY = cy - chH / 2f;
        Render2D.drawRoundedRect(ms, cx, chY, chW, chH, chH / 2f, MenuChrome.accentA(0.12f));
        Render2D.drawFont(ms, cf, cnt, cx + 8f, chY + chH / 2f - cf.getHeight() / 2f, MenuChrome.accent());

        // Поиск справа.
        float sw = 230f, sh = 36f, sx = x + w - PAD_X - sw, sy = cy - sh / 2f;
        boolean sHov = MenuChrome.hit(mx, my, sx, sy, sw, sh);
        float st = MenuChrome.upd(hSearch, sHov || searchFocused);
        WindowMaterial.fieldBox(ms, sx, sy, sw, sh, st, false);
        ClickGuiIcons.search(ms, sx + 20f, sy + sh / 2f, 16f,
                MenuChrome.lerp(MenuChrome.MUTED3, MenuChrome.accent(), searchFocused ? 1f : 0f));
        Instance pf = Fonts.GOLOS.getFont(12.5f);
        float qx = sx + 37f;
        if (query.isEmpty() && !searchFocused) {
            Render2D.drawFont(ms, pf, "Поиск аккаунта...", qx, sy + sh / 2f - pf.getHeight() / 2f, MenuChrome.MUTED3);
        } else {
            Render2D.drawFont(ms, pf, query, qx, sy + sh / 2f - pf.getHeight() / 2f, MenuChrome.TEXT2);
            if (searchFocused) drawCaret(ms, qx + pf.getWidth(query) + 1f, sy + sh / 2f);
        }
        MenuChrome.set(rSearch, sx, sy, sw, sh);
    }

    private void drawCaret(Matrix3x2fStack ms, float target, float cyMid) {
        if (caretX <= 0f) caretX = target;
        caretX += (target - caretX) * 0.4f;
        float blink = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 260.0));
        Color c = new Color(MenuChrome.TEXT2.getRed(), MenuChrome.TEXT2.getGreen(),
                MenuChrome.TEXT2.getBlue(), Math.round(255 * blink));
        Render2D.drawRoundedRect(ms, caretX, cyMid - 6f, 1f, 12f, 0.5f, c);
    }

    // ---------- Строки аккаунтов ----------
    private void drawRows(DrawContext context, Matrix3x2fStack ms, float x, float y, float w, float viewH, int mx, int my) {
        rowRects.clear();
        List<IasAccount> vis = visible();

        float contentH = PAD_Y * 2f + vis.size() * ROW_H + Math.max(0, vis.size() - 1) * ROW_GAP;
        float maxScroll = Math.max(0f, contentH - viewH);

        long now = System.currentTimeMillis();
        float dt = lastFrameMs == 0L ? 0.016f : Math.min(0.05f, (now - lastFrameMs) / 1000f);
        lastFrameMs = now;
        scrollTarget = MathHelper.clamp(scrollTarget, 0f, maxScroll);
        scroll += (scrollTarget - scroll) * (1f - (float) Math.exp(-13f * dt));
        if (Math.abs(scrollTarget - scroll) < 0.4f) scroll = scrollTarget;

        if (vis.isEmpty()) {
            Instance ef = Fonts.GOLOS.getFont(13f);
            String msg = accounts.isEmpty() ? "Нет аккаунтов" : "Ничего не найдено";
            Render2D.drawFont(ms, ef, msg, x + w / 2f - ef.getWidth(msg) / 2f, y + viewH / 2f - ef.getHeight() / 2f, MenuChrome.MUTED2);
            return;
        }

        Render2D.startScissor(context, x, y, w, viewH);
        long from = Math.max(openMs, listAnimMs);
        for (int k = 0; k < vis.size(); k++) {
            IasAccount a = vis.get(k);
            float ry = y + PAD_Y + k * (ROW_H + ROW_GAP) - scroll;
            float appear = MathHelper.clamp((now - from - k * 16L) / 220f, 0f, 1f);
            appear = 1f - (1f - appear) * (1f - appear);
            float ary = ry + (1f - appear) * 12f;
            if (ary + ROW_H < y || ary > y + viewH) continue;
            float rx = x + PAD_X, rw = w - PAD_X * 2f - 8f; // 8px под скроллбар
            drawRow(ms, a, rx, ary, rw, mx, my);
            // Хитбокс режем по вьюпорту (ножницы обрезают пиксели, но не клики).
            float hitTop = Math.max(ary, y), hitBot = Math.min(ary + ROW_H, y + viewH);
            if (hitBot - hitTop >= 1f) rowRects.add(new float[]{rx, hitTop, rw, hitBot - hitTop, k});
        }
        Render2D.stopScissor(context);

        if (maxScroll > 0.5f) {
            float trackH = viewH - 8f;
            float thumbH = Math.max(24f, trackH * (viewH / contentH));
            float thumbY = y + 4f + (trackH - thumbH) * (scroll / maxScroll);
            Render2D.drawRoundedRect(ms, x + w - 6f, thumbY, 3f, thumbH, 1.5f, MenuChrome.accentA(0.5f));
        }
    }

    private void drawRow(Matrix3x2fStack ms, IasAccount a, float x, float y, float w, int mx, int my) {
        boolean active = isActive(a);
        boolean sel = a.uuid().equals(selectedUuid);
        boolean hov = MenuChrome.hit(mx, my, x, y, w, ROW_H);
        float ht = MenuChrome.upd(rowHover.computeIfAbsent(a.uuid(), u -> MenuChrome.anim()), hov);

        Render2D.drawRoundedVerticalGradient(ms, x, y, w, ROW_H, 10f,
                new Color(24, 30, 27, 179), new Color(15, 19, 17, 179));
        Color bd = sel
                ? MenuChrome.accentA(0.55f)
                : MenuChrome.lerp(MenuChrome.white(0.08f), MenuChrome.white(0.22f), ht);
        Render2D.drawBorder(ms, x, y, w, ROW_H, 10f, 1.0f, 1.4f, bd);

        // Акцентная полоса слева у активного.
        if (active) Render2D.drawRoundedRect(ms, x + 2f, y + 8f, 3f, ROW_H - 16f, 1.5f, MenuChrome.accent());

        // Голова скина.
        float hx = x + 16f, hy = y + (ROW_H - HEAD) / 2f;
        drawHead(ms, a.uuid(), hx, hy);

        // Ник.
        Instance nf = Fonts.UNBOUNDED.getFont(13f);
        float nx = hx + HEAD + 14f;
        Render2D.drawFont(ms, nf, a.name(), nx, y + ROW_H / 2f - nf.getHeight() / 2f, MenuChrome.TEXT);

        // Чипы справа: тип + (текущий).
        float cy = y + ROW_H / 2f, right = x + w - 14f;
        right = drawChip(ms, right, cy,
                a.canLogin() ? "лицензия" : "оффлайн",
                a.canLogin() ? MenuChrome.accent() : MenuChrome.MUTED,
                a.canLogin() ? MenuChrome.accentA(0.12f) : MenuChrome.white(0.05f)) - 8f;
        if (active) {
            drawChip(ms, right, cy, "текущий", MenuChrome.INK, MenuChrome.accent());
        }
    }

    /** Чип, выровненный правым краем по {@code right}. Возвращает левый край нарисованного чипа. */
    private float drawChip(Matrix3x2fStack ms, float right, float cy, String text, Color textColor, Color bg) {
        Instance f = Fonts.GOLOS.getFont(11f);
        float pad = 8f, h = 19f;
        float w = f.getWidth(text) + pad * 2f;
        float x = right - w, y = cy - h / 2f;
        Render2D.drawRoundedRect(ms, x, y, w, h, h / 2f, bg);
        Render2D.drawFont(ms, f, text, x + pad, y + h / 2f - f.getHeight() / 2f, textColor);
        return x;
    }

    private void drawHead(Matrix3x2fStack ms, UUID uuid, float x, float y) {
        Identifier skin = skinCache.computeIfAbsent(uuid, u -> {
            try { return DefaultSkinHelper.getSkinTextures(u).body().texturePath(); }
            catch (Throwable t) { return DefaultSkinHelper.getTexture(); }
        });
        Render2D.drawRoundedRect(ms, x, y, HEAD, HEAD, 6f, new Color(0, 0, 0, 90));
        if (skin == null) return;
        Color tint = new Color(255, 255, 255, 255);
        Render2D.drawTextureNearest(ms, x, y, HEAD, HEAD, 6f, 8f / 64f, 8f / 64f, 16f / 64f, 16f / 64f, skin, tint);
        Render2D.drawTextureNearest(ms, x, y, HEAD, HEAD, 6f, 40f / 64f, 8f / 64f, 48f / 64f, 16f / 64f, skin, tint);
    }

    // ---------- Футер ----------
    private void drawFooter(Matrix3x2fStack ms, float x, float y, float w, int mx, int my) {
        float cy = y + FOOTER_H / 2f;
        IasAccount sel = selected();

        // Иконки-кнопки слева: Изменить (только offline), Удалить.
        float bs = 38f, bx = x + PAD_X, by = cy - bs / 2f;
        boolean editEnabled = sel != null && !sel.canLogin();
        boolean eHov = editEnabled && MenuChrome.hit(mx, my, bx, by, bs, bs);
        float et = MenuChrome.upd(hEdit, eHov);
        WindowMaterial.fieldBox(ms, bx, by, bs, bs, et, false);
        ClickGuiIcons.sliders(ms, bx + bs / 2f, by + bs / 2f, 16f,
                editEnabled ? MenuChrome.lerp(MenuChrome.MUTED, MenuChrome.TEXT2, et) : MenuChrome.MUTED4);
        MenuChrome.set(rEdit, bx, by, bs, bs);

        float dx = bx + bs + 8f;
        boolean delEnabled = sel != null;
        boolean armed = System.currentTimeMillis() < deleteArmedUntil;
        boolean dHov = delEnabled && MenuChrome.hit(mx, my, dx, by, bs, bs);
        float dt = MenuChrome.upd(hDelete, dHov || armed);
        WindowMaterial.fieldBox(ms, dx, by, bs, bs, dt, true);
        Color trashC = !delEnabled ? MenuChrome.MUTED4
                : (armed ? MenuChrome.DANGER : MenuChrome.lerp(MenuChrome.MUTED, MenuChrome.DANGER, dt));
        ClickGuiIcons.trash(ms, dx + bs / 2f, by + bs / 2f, 15f, trashC);
        MenuChrome.set(rDelete, dx, by, bs, bs);

        // Кнопки справа: Войти (primary), Войти оффлайн (secondary), Добавить (secondary).
        float bh = 44f, byy = cy - bh / 2f;
        boolean loginEnabled = sel != null && !IasBridge.inWorld();

        float lw = actionW("Войти", true);
        float lx = x + w - PAD_X - lw;
        float lt = MenuChrome.upd(hLogin, loginEnabled && MenuChrome.hit(mx, my, lx, byy, lw, bh));
        drawAction(ms, lx, byy, lw, bh, true, "arrowRight", "Войти", lt, loginEnabled);
        MenuChrome.set(rLogin, lx, byy, lw, bh);

        float ow = actionW("Войти оффлайн", false);
        float ox = lx - 10f - ow;
        boolean offEnabled = !IasBridge.inWorld();
        float ot = MenuChrome.upd(hOffline, offEnabled && MenuChrome.hit(mx, my, ox, byy, ow, bh));
        drawAction(ms, ox, byy, ow, bh, false, "user", "Войти оффлайн", ot, offEnabled);
        MenuChrome.set(rOffline, ox, byy, ow, bh);

        float aw = actionW("Добавить", false);
        float ax = ox - 10f - aw;
        float at = MenuChrome.upd(hAdd, MenuChrome.hit(mx, my, ax, byy, aw, bh));
        drawAction(ms, ax, byy, aw, bh, false, "plus", "Добавить", at, true);
        MenuChrome.set(rAdd, ax, byy, aw, bh);
    }

    private float actionW(String label, boolean primary) {
        Instance lf = (primary ? Fonts.UNBOUNDED : Fonts.UNBOUNDED_L).getFont(11f);
        return 18f + 15f + 9f + lf.getWidth(label) + 18f;
    }

    private void drawAction(Matrix3x2fStack ms, float x, float y, float w, float h, boolean primary,
                            String icon, String label, float hoverT, boolean enabled) {
        float a = enabled ? 1f : 0.4f;
        Color labelColor = primary ? MenuChrome.INK
                : new Color(MenuChrome.TEXT2.getRed(), MenuChrome.TEXT2.getGreen(), MenuChrome.TEXT2.getBlue(), Math.round(255 * a));
        // Кнопка НЕ «съезжает» вверх при наведении: позиция фиксирована (ty = y), а ховер даёт
        // только подсветку/глоу через hoverT. Раньше ty = y - hoverT приподнимал её на ховере.
        float ty = y;
        if (primary) {
            Color acc = MenuChrome.accent();
            Render2D.drawRoundedRect(ms, x + 4f, ty + h - 6f, w - 8f, 12f, 8f, MenuChrome.accentA((enabled ? 0.24f : 0.10f) + 0.14f * hoverT));
            int br = Math.min(255, Math.round(acc.getRed() * (1f + 0.07f * hoverT)));
            int bg = Math.min(255, Math.round(acc.getGreen() * (1f + 0.07f * hoverT)));
            int bb = Math.min(255, Math.round(acc.getBlue() * (1f + 0.07f * hoverT)));
            Render2D.drawRoundedRect(ms, x, ty, w, h, 8f, new Color(br, bg, bb, enabled ? 255 : 130));
            Render2D.drawRoundedRect(ms, x + 8f, ty + 1f, w - 16f, 1f, 0.5f, MenuChrome.white(0.35f));
        } else {
            MenuChrome.drawSecondaryMaterial(ms, x, ty, w, h, 8f, hoverT, false);
        }
        Color iconColor = primary ? MenuChrome.INK
                : new Color(MenuChrome.MUTED.getRed(), MenuChrome.MUTED.getGreen(), MenuChrome.MUTED.getBlue(), Math.round(255 * a));
        drawIcon(ms, icon, x + 18f + 15f / 2f, ty + h / 2f, 15f, iconColor);
        Instance lf = (primary ? Fonts.UNBOUNDED : Fonts.UNBOUNDED_L).getFont(11f);
        Render2D.drawFont(ms, lf, label, x + 18f + 15f + 9f, ty + h / 2f - lf.getHeight() / 2f, labelColor);
    }

    private void drawIcon(Matrix3x2fStack ms, String name, float cx, float cy, float size, Color c) {
        switch (name) {
            case "arrowRight" -> ClickGuiIcons.arrowRight(ms, cx, cy, size, c);
            case "user" -> ClickGuiIcons.user(ms, cx, cy, size, c);
            case "plus" -> ClickGuiIcons.plus(ms, cx, cy, size, c);
            case "check" -> ClickGuiIcons.check(ms, cx, cy, size, c);
            default -> ClickGuiIcons.user(ms, cx, cy, size, c);
        }
    }

    // ---------- Модалка Добавить/Изменить ----------
    private void drawModal(Matrix3x2fStack ms, int mx, int my) {
        // Затемнение всего холста.
        Render2D.drawRoundedRect(ms, 0f, 0f, vw, vh, 0f, new Color(0, 0, 0, 130));

        float w = 380f, h = 190f, x = vw / 2f - w / 2f, y = vh / 2f - h / 2f;
        MenuChrome.set(rModalWin, x, y, w, h);
        WindowMaterial.window(ms, x, y, w, h);

        Instance tf = Fonts.GOLOS_SEMI.getFont(16f);
        String title = switch (modal) {
            case OFFLINE -> "Войти оффлайн";
            case EDIT -> "Изменить ник";
            default -> "Добавить оффлайн";
        };
        Render2D.drawFont(ms, tf, title, x + 22f, y + 24f, MenuChrome.TEXT);

        // Поле ввода ника.
        float fx = x + 22f, fw = w - 44f, fh = 40f, fy = y + 56f;
        boolean valid = validDraft();
        WindowMaterial.fieldBox(ms, fx, fy, fw, fh, 1f, false);
        Instance pf = Fonts.GOLOS.getFont(13f);
        float qx = fx + 14f, midY = fy + fh / 2f;
        float ty = midY - pf.getHeight() / 2f;
        if (draft.isEmpty()) {
            Render2D.drawFont(ms, pf, "Ник (3-16, A-Z 0-9 _)", qx, ty, MenuChrome.MUTED3);
        } else {
            // Ctrl+A — акцентная подсветка под всем ником (обычная логика выделения текста).
            if (draftSelected) {
                float dw = pf.getWidth(draft);
                Render2D.drawRoundedRect(ms, qx - 2f, midY - pf.getHeight() / 2f - 2f, dw + 4f, pf.getHeight() + 4f, 3f,
                        MenuChrome.accentA(0.30f));
            }
            Color base = valid ? MenuChrome.TEXT2 : MenuChrome.DANGER;
            // Прогресс «свежести» правки: 0 сразу после ввода → 1 через 150мс.
            float editT = MathHelper.clamp((System.currentTimeMillis() - draftEditMs) / 150f, 0f, 1f);
            boolean animIn = draftEditInsert && editT < 1f && !draftSelected;
            if (animIn) {
                // Последний символ въезжает снизу с нарастанием прозрачности; остальное статично.
                String head = draft.substring(0, draft.length() - 1);
                String tail = draft.substring(draft.length() - 1);
                Render2D.drawFont(ms, pf, head, qx, ty, base);
                float ease = 1f - (1f - editT) * (1f - editT); // easeOutQuad
                Color tailCol = new Color(base.getRed(), base.getGreen(), base.getBlue(), Math.round(ease * 255f));
                Render2D.drawFont(ms, pf, tail, qx + pf.getWidth(head), ty + (1f - ease) * 5f, tailCol);
            } else {
                Render2D.drawFont(ms, pf, draft, qx, ty, base);
            }
        }
        if (!draftSelected) drawCaret(ms, qx + pf.getWidth(draft) + 1f, midY);

        // Кнопки: Отмена (secondary) + primary.
        float bh = 40f, by = y + h - 22f - bh;
        String okLabel = switch (modal) {
            case OFFLINE -> "Войти";
            case EDIT -> "Сохранить";
            default -> "Добавить";
        };
        // Ширина — тем же хелпером, что у кнопок футера (18 + иконка 15 + 9 + текст + 18): раньше было
        // 44 + текст, и надписи справа оставалось 2px — она упиралась в скругление кнопки.
        float okW = actionW(okLabel, true);
        float okX = x + w - 22f - okW;
        float okT = valid ? MenuChrome.upd(hModalOk, MenuChrome.hit(mx, my, okX, by, okW, bh)) : 0f;
        drawAction(ms, okX, by, okW, bh, true, modal == Modal.OFFLINE ? "play" : "check", okLabel, okT, valid);
        MenuChrome.set(rModalOk, okX, by, okW, bh);

        Instance cf = Fonts.UNBOUNDED_L.getFont(11f);
        String cLabel = "Отмена";
        float cW = 36f + cf.getWidth(cLabel);
        float cX = okX - 10f - cW;
        float cT = MenuChrome.upd(hModalCancel, MenuChrome.hit(mx, my, cX, by, cW, bh));
        MenuChrome.drawSecondaryMaterial(ms, cX, by - cT, cW, bh, 8f, cT, false);
        Render2D.drawFont(ms, cf, cLabel, cX + (cW - cf.getWidth(cLabel)) / 2f, by - cT + bh / 2f - cf.getHeight() / 2f, MenuChrome.TEXT3);
        MenuChrome.set(rModalCancel, cX, by, cW, bh);
    }

    private boolean validDraft() {
        int n = draft.length();
        if (n < 3 || n > 16) return false;
        for (int i = 0; i < n; i++) {
            char c = draft.charAt(i);
            boolean ok = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9') || c == '_';
            if (!ok) return false;
        }
        return true;
    }

    // ---------- Ввод ----------
    @Override
    protected boolean contentClick(float mx, float my, int button) {
        if (button != 0) return false;

        if (modal != Modal.NONE) {
            if (MenuChrome.in(rModalOk, mx, my)) { applyModal(); return true; }
            if (MenuChrome.in(rModalCancel, mx, my)) { closeModal(); return true; }
            if (!MenuChrome.in(rModalWin, mx, my)) closeModal(); // клик мимо окна — закрыть
            return true;
        }

        searchFocused = MenuChrome.in(rSearch, mx, my);
        if (searchFocused) return true;

        // Футер.
        IasAccount sel = selected();
        if (MenuChrome.in(rEdit, mx, my)) {
            if (sel != null && !sel.canLogin()) { modal = Modal.EDIT; draft = sel.name(); click(); }
            return true;
        }
        if (MenuChrome.in(rDelete, mx, my)) {
            if (sel != null) armOrDelete(sel);
            return true;
        }
        if (MenuChrome.in(rAdd, mx, my)) { modal = Modal.ADD; draft = ""; click(); return true; }
        if (MenuChrome.in(rOffline, mx, my)) {
            // «Войти оффлайн» = ввести ник и СРАЗУ войти (не путать с «Добавить», который лишь
            // сохраняет аккаунт в список). Оффлайн-имя выбранного аккаунта подставляем в поле.
            if (!IasBridge.inWorld()) {
                modal = Modal.OFFLINE;
                draft = sel != null && !sel.canLogin() ? sel.name() : "";
                click();
            }
            return true;
        }
        if (MenuChrome.in(rLogin, mx, my)) {
            if (sel != null && !IasBridge.inWorld()) IasBridge.loginSelected(sel, parent);
            return true;
        }

        // Строки: выбор + двойной клик = вход.
        for (float[] r : rowRects) {
            if (!MenuChrome.hit(mx, my, r[0], r[1], r[2], r[3])) continue;
            List<IasAccount> vis = visible();
            int k = (int) r[4];
            if (k < 0 || k >= vis.size()) return true;
            IasAccount a = vis.get(k);
            long now = System.currentTimeMillis();
            boolean dbl = a.uuid().equals(lastClickUuid) && now - lastClickMs < 300L;
            selectedUuid = a.uuid();
            deleteArmedUntil = 0L; // смена выбора снимает взвод удаления
            lastClickUuid = a.uuid();
            lastClickMs = now;
            if (dbl && !IasBridge.inWorld()) IasBridge.loginSelected(a, parent);
            return true;
        }
        // Клик по пустому месту снимает фокус поиска.
        searchFocused = false;
        return false;
    }

    private void armOrDelete(IasAccount sel) {
        if (System.currentTimeMillis() < deleteArmedUntil) {
            IasBridge.remove(sel);
            deleteArmedUntil = 0L;
            selectedUuid = null;
            refresh();
            // Выбор — на первый ВИДИМЫЙ (с учётом фильтра поиска), а не на accounts.get(0):
            // иначе при активном фильтре выбор уедет на отфильтрованную, невидимую строку.
            List<IasAccount> vis = visible();
            if (!vis.isEmpty()) selectedUuid = vis.get(0).uuid();
            listAnimMs = System.currentTimeMillis();
        } else {
            deleteArmedUntil = System.currentTimeMillis() + 2500L;
        }
        click();
    }

    private void applyModal() {
        if (!validDraft()) return;
        // Любая смена выбора обязана снять взвод удаления — иначе confirm перенесётся на другой
        // аккаунт (добавили B, пока взведён A → «Удалить» стёр бы B).
        deleteArmedUntil = 0L;

        if (modal == Modal.OFFLINE) {
            // Вход оффлайн по нику — экран закрывается (loginOffline сам вернёт parent).
            String nick = draft;
            closeModal();
            click();
            IasBridge.loginOffline(nick, parent);
            return;
        }
        boolean ok;
        if (modal == Modal.ADD) {
            ok = IasBridge.addOffline(draft);
        } else {
            IasAccount sel = selected();
            ok = sel != null && IasBridge.renameOffline(sel, draft);
        }
        if (ok) {
            // Фильтр поиска сбрасываем: иначе новый/переименованный ник может быть отфильтрован,
            // выбор уедет на невидимую строку, а футер продолжит действовать на неё.
            query = "";
            refresh();
            for (IasAccount a : accounts) if (a.name().equals(draft)) { selectedUuid = a.uuid(); break; }
            listAnimMs = System.currentTimeMillis();
        }
        closeModal();
        click();
    }

    private void closeModal() {
        modal = Modal.NONE;
        draft = "";
        draftSelected = false;
        draftEditInsert = false;
        draftEditMs = 0L;
    }

    /** Отметить правку ника для анимации ввода: insert=true — «въезд» последнего глифа, false — без него. */
    private void markDraftEdit(boolean insert) {
        draftEditMs = System.currentTimeMillis();
        draftEditInsert = insert;
    }

    /** Чистит вставляемый текст под ник: только печатные символы, без переводов строк, обрезка до 16. */
    private static String sanitizeNick(String s) {
        if (s == null || s.isEmpty()) return "";
        StringBuilder out = new StringBuilder(16);
        for (int i = 0; i < s.length() && out.length() < 16; i++) {
            char c = s.charAt(i);
            if (c >= ' ' && c != 127) out.append(c);
        }
        return out.toString();
    }

    @Override
    protected boolean contentScroll(float mx, float my, double vertical) {
        if (modal != Modal.NONE) return true;
        scrollTarget -= (float) vertical * (ROW_H + ROW_GAP) * 0.9f;
        return true;
    }

    @Override
    protected boolean contentKey(int keyCode, int scanCode, int modifiers) {
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        if (modal != Modal.NONE) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) { closeModal(); return true; }
            if (ctrl && keyCode == GLFW.GLFW_KEY_A) { draftSelected = !draft.isEmpty(); return true; }
            if (ctrl && keyCode == GLFW.GLFW_KEY_C) {
                if (!draft.isEmpty()) mc.keyboard.setClipboard(draft);
                return true;
            }
            if (ctrl && keyCode == GLFW.GLFW_KEY_X) {
                if (!draft.isEmpty()) mc.keyboard.setClipboard(draft);
                draft = ""; draftSelected = false; markDraftEdit(false); click();
                return true;
            }
            if (ctrl && keyCode == GLFW.GLFW_KEY_V) {
                String clip = sanitizeNick(mc.keyboard.getClipboard());
                draft = sanitizeNick((draftSelected ? "" : draft) + clip);
                draftSelected = false; markDraftEdit(true); click();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (draftSelected) { draft = ""; draftSelected = false; }
                else if (!draft.isEmpty()) draft = draft.substring(0, draft.length() - 1);
                markDraftEdit(false);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) { applyModal(); return true; }
            return true; // модалка поглощает ввод, ESC уже обработан
        }
        if (searchFocused) {
            if (ctrl && keyCode == GLFW.GLFW_KEY_V) {
                query = sanitizeNick(query + sanitizeNick(mc.keyboard.getClipboard())); onQueryChanged();
                return true;
            }
            if (ctrl && keyCode == GLFW.GLFW_KEY_A) { query = ""; onQueryChanged(); return true; }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!query.isEmpty()) { query = query.substring(0, query.length() - 1); onQueryChanged(); }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) { searchFocused = false; return true; }
        }
        return false;
    }

    @Override
    protected boolean contentChar(char chr, int modifiers) {
        if (modal != Modal.NONE) {
            if (chr >= ' ' && chr != 127) {
                if (draftSelected) { draft = ""; draftSelected = false; } // печать поверх выделения = замена
                if (draft.length() < 16) { draft += chr; markDraftEdit(true); click(); }
            }
            return true;
        }
        if (searchFocused && chr >= ' ') { query += chr; onQueryChanged(); return true; }
        return false;
    }

    private void onQueryChanged() {
        listAnimMs = System.currentTimeMillis();
        scrollTarget = 0f;
        click();
    }

    /** Щелчок «печатной машинки» — как в SubSettingsScreen. */
    private void click() {
        try {
            if (mc == null || mc.getSoundManager() == null) return;
            float pitch = 1.7f + (float) (Math.random() * 0.35);
            mc.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.UI_BUTTON_CLICK.value(), pitch, 0.25f));
        } catch (Throwable ignored) {
        }
    }
}
