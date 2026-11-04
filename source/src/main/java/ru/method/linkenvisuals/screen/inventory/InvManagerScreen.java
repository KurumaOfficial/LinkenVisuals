package ru.method.linkenvisuals.screen.inventory;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import org.joml.Matrix3x2fStack;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
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
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Экран 12a — «Менеджер инвентарей». Хром/фон/ESC/масштаб/появление даёт {@link LinkenSettingsScreen}:
 * здесь только окно 1060px в две колонки и content-хуки ввода. Слева — поле имени + «Сохранить»,
 * список сохранений со скроллом и confirm-удалением; справа — превью раскладки настоящими ItemStack'ами.
 * Данные только через {@link InventorySetupManager}.
 */
public final class InvManagerScreen extends LinkenSettingsScreen {

    private static final float WIN_W = 1060f, WIN_H_MAX = 600f, PAD_X = 20f;
    private static final float TOPBAR_H = 64f;
    private static final float RIGHT_W = 452f, COL_GAP = 20f;

    private static final float ROW_H = 54f, ROW_GAP = 8f, PAD_Y = 14f;
    private static final float TILE = 34f;

    private static final float SLOT = 42f, SLOT_GAP = 4f;
    private static final float GRID_W = 9f * SLOT + 8f * SLOT_GAP; // 410

    /** Порядок брони в ряду превью: шлем→ботинки. Индексы слотов PlayerInventory: 39=HEAD…36=FEET. */
    private static final int[] ARMOR_ORDER = {39, 38, 37, 36};

    private InventorySetup selected;

    private String nameDraft = "";
    private boolean nameFocused;
    private float caretX;

    private float scroll, scrollTarget;
    private long lastFrameMs, listAnimMs;

    private InventorySetup deleteArmed;
    private long deleteArmedUntil;

    private final Animation hName = MenuChrome.anim(), hSave = MenuChrome.anim(), hPill = MenuChrome.anim();
    private final Animation hToastClose = MenuChrome.anim();
    private final Map<InventorySetup, Animation> hRow = new IdentityHashMap<>();
    private final Map<InventorySetup, Animation> hLoad = new IdentityHashMap<>();
    private final Map<InventorySetup, Animation> hDel = new IdentityHashMap<>();

    private final float[] rName = new float[4], rSave = new float[4], rPill = new float[4], rToastClose = new float[4];
    private final List<Object[]> rowHits = new ArrayList<>();  // {float[] rect, InventorySetup, "row"|"load"|"del"}

    private boolean toast;

    public InvManagerScreen(Screen parent) {
        super(parent, "Менеджер инвентарей");
        selected = InventorySetupManager.loaded();
        List<InventorySetup> all = InventorySetupManager.setups();
        if (selected == null && !all.isEmpty()) selected = all.get(0);
        // Приветствие показываем ровно один раз за всё время — флаг гасим сразу при открытии.
        toast = !InventorySetupManager.greeted();
        if (toast) InventorySetupManager.setGreeted(true);
    }

    @Override
    protected String escHintLabel() {
        return "назад";
    }

    private float winH() {
        return Math.min(WIN_H_MAX, Math.max(420f, vh - 100f));
    }

    // ---------- Рендер ----------
    @Override
    protected void drawContent(DrawContext context, Matrix3x2fStack ms, int mx, int my) {
        float w = WIN_W, h = winH();
        float x = winX(w), y = vh / 2f - h * 0.54f;

        WindowMaterial.window(ms, x, y, w, h);

        drawTopbar(ms, x, y, w, mx, my);
        Render2D.drawRoundedRect(ms, x, y + TOPBAR_H - 1f, w, 1f, 0f, MenuChrome.white(0.07f));

        float bodyY = y + TOPBAR_H, bodyH = h - TOPBAR_H;
        float rightX = x + w - PAD_X - RIGHT_W;
        float divX = rightX - COL_GAP;
        Render2D.drawRoundedRect(ms, divX, bodyY, 1f, bodyH, 0f, MenuChrome.white(0.07f));

        float leftX = x + PAD_X, leftW = divX - COL_GAP - leftX;
        drawLeft(context, ms, leftX, bodyY, leftW, bodyH, mx, my);
        drawRight(context, ms, rightX, bodyY, RIGHT_W, bodyH, mx, my);

        WindowMaterial.corners(ms, x, y, w, h);

        if (toast) drawToast(ms, x, y, w, h, mx, my);
    }

    // ---------- Топбар ----------
    private void drawTopbar(Matrix3x2fStack ms, float x, float y, float w, int mx, int my) {
        float cy = y + TOPBAR_H / 2f;

        Instance tf = Fonts.GOLOS_SEMI.getFont(17f);
        String title = "Менеджер инвентарей";
        Render2D.drawFont(ms, tf, title, x + PAD_X, cy - tf.getHeight() / 2f, MenuChrome.TEXT);

        Instance cf = Fonts.PLEXMONO.getFont(10f);
        String cnt = String.valueOf(InventorySetupManager.setups().size());
        float chW = cf.getWidth(cnt) + 16f, chH = 20f;
        float chX = x + PAD_X + tf.getWidth(title) + 10f, chY = cy - chH / 2f;
        Render2D.drawRoundedRect(ms, chX, chY, chW, chH, chH / 2f, MenuChrome.accentA(0.12f));
        Render2D.drawFont(ms, cf, cnt, chX + 8f, chY + chH / 2f - cf.getHeight() / 2f, MenuChrome.accent());

        drawOverlayPill(ms, x + w - PAD_X, cy, mx, my);
    }

    /** Пилюля-тоггл оверлея, выровненная правым краем по {@code right}. */
    private void drawOverlayPill(Matrix3x2fStack ms, float right, float cy, int mx, int my) {
        boolean on = InventorySetupManager.overlayEnabled();
        String label = on ? "Оверлей включён" : "Оверлей выключен";
        Instance f = Fonts.GOLOS.getFont(12f);
        float ph = 30f, pw = 15f + 7f + 9f + f.getWidth(label) + 16f;
        float px = right - pw, py = cy - ph / 2f;

        float t = MenuChrome.upd(hPill, MenuChrome.hit(mx, my, px, py, pw, ph));
        Render2D.drawRoundedRect(ms, px, py, pw, ph, ph / 2f,
                on ? MenuChrome.accentA(0.10f + 0.04f * t) : MenuChrome.white(0.04f + 0.03f * t));
        Render2D.drawBorder(ms, px, py, pw, ph, ph / 2f, 1.0f, 1.4f,
                on ? MenuChrome.accentA(0.35f) : MenuChrome.white(0.08f + 0.10f * t));

        float dx = px + 15f, dy = cy;
        if (on) {
            MenuChrome.glow(ms, dx - 3.5f, dy - 3.5f, 7f, 7f, 3.5f, MenuChrome.accentA(0.30f), 0f, 4, 1.7f);
            Render2D.drawCircle(ms, dx, dy, 3.5f, MenuChrome.accent());
        } else {
            Render2D.drawCircle(ms, dx, dy, 3.5f, MenuChrome.MUTED3);
        }
        Render2D.drawFont(ms, f, label, px + 15f + 7f + 9f, cy - f.getHeight() / 2f,
                on ? MenuChrome.TEXT3 : MenuChrome.MUTED2);
        MenuChrome.set(rPill, px, py, pw, ph);
    }

    // ---------- Левая колонка ----------
    private void drawLeft(DrawContext context, Matrix3x2fStack ms, float x, float y, float w, float h, int mx, int my) {
        // Поле имени + primary «Сохранить».
        float fy = y + 18f, fh = 44f;
        float saveW = actionW("Сохранить", true);
        float fw = w - 10f - saveW;

        boolean canSave = inGame();
        float ft = MenuChrome.upd(hName, MenuChrome.hit(mx, my, x, fy, fw, fh) || nameFocused);
        WindowMaterial.fieldBox(ms, x, fy, fw, fh, ft, false);
        Instance pf = Fonts.GOLOS.getFont(12.5f);
        float qx = x + 16f, midY = fy + fh / 2f;
        if (nameDraft.isEmpty() && !nameFocused) {
            Render2D.drawFont(ms, pf, "Название инвентаря...", qx, midY - pf.getHeight() / 2f, MenuChrome.MUTED3);
        } else {
            Render2D.drawFont(ms, pf, nameDraft, qx, midY - pf.getHeight() / 2f, MenuChrome.TEXT2);
            if (nameFocused) drawCaret(ms, qx + pf.getWidth(nameDraft) + 1f, midY);
        }
        MenuChrome.set(rName, x, fy, fw, fh);

        float sx = x + fw + 10f;
        float st = MenuChrome.upd(hSave, canSave && MenuChrome.hit(mx, my, sx, fy, saveW, fh));
        drawAction(ms, sx, fy, saveW, fh, true, "plus", "Сохранить", st, canSave);
        MenuChrome.set(rSave, sx, fy, saveW, fh);

        // Лейбл секции.
        Instance lf = Fonts.GOLOS.getFont(10.5f);
        float ly = fy + fh + 20f;
        MenuChrome.drawSpaced(ms, lf, "СОХРАНЕНИЯ", x, ly, MenuChrome.MUTED4, 1.4f);

        // Подсказка внизу.
        float hintH = 18f, hintY = y + h - 22f - hintH;
        ClickGuiIcons.info(ms, x + 6f, hintY + hintH / 2f, 12f, MenuChrome.MUTED3);
        Instance hf = Fonts.GOLOS.getFont(11f);
        Render2D.drawFont(ms, hf, "Загруженный шаблон подсвечивает недостающие предметы призрачными иконками",
                x + 18f, hintY + hintH / 2f - hf.getHeight() / 2f, MenuChrome.MUTED2);

        float listY = ly + lf.getHeight() + 12f;
        drawRows(context, ms, x, listY, w, hintY - 12f - listY, mx, my);
    }

    private void drawRows(DrawContext context, Matrix3x2fStack ms, float x, float y, float w, float viewH, int mx, int my) {
        rowHits.clear();
        List<InventorySetup> list = InventorySetupManager.setups();

        float contentH = PAD_Y * 2f + list.size() * ROW_H + Math.max(0, list.size() - 1) * ROW_GAP;
        float maxScroll = Math.max(0f, contentH - viewH);

        long now = System.currentTimeMillis();
        float dt = lastFrameMs == 0L ? 0.016f : Math.min(0.05f, (now - lastFrameMs) / 1000f);
        lastFrameMs = now;
        scrollTarget = MathHelper.clamp(scrollTarget, 0f, maxScroll);
        scroll += (scrollTarget - scroll) * (1f - (float) Math.exp(-13f * dt));
        if (Math.abs(scrollTarget - scroll) < 0.4f) scroll = scrollTarget;

        if (list.isEmpty()) {
            Instance ef = Fonts.GOLOS.getFont(13f);
            String msg = "Нет сохранений";
            Render2D.drawFont(ms, ef, msg, x + w / 2f - ef.getWidth(msg) / 2f,
                    y + viewH / 2f - ef.getHeight() / 2f, MenuChrome.MUTED2);
            return;
        }

        Render2D.startScissor(context, x, y, w, viewH);
        long from = Math.max(openMs, listAnimMs);
        for (int k = 0; k < list.size(); k++) {
            InventorySetup s = list.get(k);
            float ry = y + PAD_Y + k * (ROW_H + ROW_GAP) - scroll;
            float appear = MathHelper.clamp((now - from - k * 16L) / 220f, 0f, 1f);
            appear = 1f - (1f - appear) * (1f - appear);
            float ary = ry + (1f - appear) * 12f;
            if (ary + ROW_H < y || ary > y + viewH) continue;
            float rw = w - 8f; // 8px под скроллбар
            drawRow(ms, s, x, ary, rw, mx, my);
            float bh = 32f, by = ary + (ROW_H - bh) / 2f;
            float delX = x + rw - 12f - bh;
            float loadW = loadBtnW(s);
            float loadX = delX - 8f - loadW;
            addClipped(loadX, by, loadW, bh, y, viewH, s, "load");
            addClipped(delX, by, bh, bh, y, viewH, s, "del");
            // Хитбоксы режем по вьюпорту: ножницы обрезают пиксели, но не клики.
            // Строку кладём ПОСЛЕ кнопок: их прямоугольники лежат внутри неё,
            // а contentClick() возвращается на первом совпадении.
            float top = Math.max(ary, y), bot = Math.min(ary + ROW_H, y + viewH);
            if (bot - top >= 1f) rowHits.add(new Object[]{new float[]{x, top, rw, bot - top}, s, "row"});
        }
        Render2D.stopScissor(context);

        if (maxScroll > 0.5f) {
            float trackH = viewH - 8f;
            float thumbH = Math.max(24f, trackH * (viewH / contentH));
            float thumbY = y + 4f + (trackH - thumbH) * (scroll / maxScroll);
            Render2D.drawRoundedRect(ms, x + w - 6f, thumbY, 3f, thumbH, 1.5f, MenuChrome.accentA(0.5f));
        }
    }

    private void addClipped(float bx, float by, float bw, float bh, float viewY, float viewH,
                            InventorySetup s, String kind) {
        float top = Math.max(by, viewY), bot = Math.min(by + bh, viewY + viewH);
        if (bot - top >= 1f) rowHits.add(new Object[]{new float[]{bx, top, bw, bot - top}, s, kind});
    }

    private void drawRow(Matrix3x2fStack ms, InventorySetup s, float x, float y, float w, int mx, int my) {
        boolean sel = s == selected;
        boolean hov = MenuChrome.hit(mx, my, x, y, w, ROW_H);
        float ht = MenuChrome.upd(hRow.computeIfAbsent(s, k -> MenuChrome.anim()), hov);

        if (sel) {
            Render2D.drawRoundedRect(ms, x, y, w, ROW_H, 10f, MenuChrome.accentA(0.05f));
        } else {
            Render2D.drawRoundedVerticalGradient(ms, x, y, w, ROW_H, 10f,
                    new Color(24, 30, 27, 179), new Color(15, 19, 17, 179));
        }
        Color bd = sel ? MenuChrome.accentA(0.55f)
                : MenuChrome.lerp(MenuChrome.white(0.08f), MenuChrome.white(0.22f), ht);
        Render2D.drawBorder(ms, x, y, w, ROW_H, 10f, 1.0f, 1.4f, bd);
        if (sel) Render2D.drawRoundedRect(ms, x + 2f, y + 8f, 2f, ROW_H - 16f, 1f, MenuChrome.accent());

        // Тайл с сундуком (иконки сундука в атласе нет — cube ближайшая по смыслу).
        float tx = x + 14f, ty = y + (ROW_H - TILE) / 2f;
        Render2D.drawRoundedRect(ms, tx, ty, TILE, TILE, 8f, sel ? MenuChrome.accentA(0.10f) : MenuChrome.white(0.04f));
        Render2D.drawBorder(ms, tx, ty, TILE, TILE, 8f, 1.0f, 1.4f,
                sel ? MenuChrome.accentA(0.45f) : MenuChrome.white(0.08f));
        ClickGuiIcons.cube(ms, tx + TILE / 2f, ty + TILE / 2f, 15f,
                sel ? MenuChrome.accent() : MenuChrome.MUTED);

        float nx = tx + TILE + 14f, cy = y + ROW_H / 2f;
        Instance nf = Fonts.UNBOUNDED.getFont(11.5f);
        Render2D.drawFont(ms, nf, s.name, nx, cy - nf.getHeight() - 2f, MenuChrome.TEXT);
        Instance sf = Fonts.GOLOS.getFont(12f);
        Render2D.drawFont(ms, sf, s.itemCount + " предметов", nx, cy + 3f, MenuChrome.MUTED2);

        // Кнопки справа: «Загрузить» + иконка удаления (confirm в два клика).
        float bh = 32f, by = y + (ROW_H - bh) / 2f;
        float delX = x + w - 12f - bh;
        boolean armed = s == deleteArmed && System.currentTimeMillis() < deleteArmedUntil;
        float dt = MenuChrome.upd(hDel.computeIfAbsent(s, k -> MenuChrome.anim()),
                MenuChrome.hit(mx, my, delX, by, bh, bh) || armed);
        WindowMaterial.fieldBox(ms, delX, by, bh, bh, dt, true);
        ClickGuiIcons.trash(ms, delX + bh / 2f, by + bh / 2f, 14f,
                armed ? MenuChrome.DANGER : MenuChrome.lerp(MenuChrome.MUTED, MenuChrome.DANGER, dt));

        // Загруженный шаблон меняет саму кнопку: «Загрузить» → «Загружен» (акцентная, с галкой).
        // Отдельный чип рядом с именем не нужен — состояние читается там, куда игрок и жмёт.
        boolean isLoaded = loadedState(s);
        float loadW = loadBtnW(s);
        float loadX = delX - 8f - loadW;
        float lt = MenuChrome.upd(hLoad.computeIfAbsent(s, k -> MenuChrome.anim()),
                MenuChrome.hit(mx, my, loadX, by, loadW, bh));
        drawAction(ms, loadX, by, loadW, bh, isLoaded,
                isLoaded ? "check" : "arrowRight", loadLabel(s), lt, true);
    }

    private static boolean loadedState(InventorySetup s) {
        return s == InventorySetupManager.loaded();
    }

    private static String loadLabel(InventorySetup s) {
        return loadedState(s) ? "Загружен" : "Загрузить";
    }

    /** Ширина кнопки строки. Хитбокс в drawRows и отрисовка в drawRow обязаны звать одно и то же. */
    private float loadBtnW(InventorySetup s) {
        return actionW(loadLabel(s), loadedState(s));
    }

    // ---------- Правая колонка: превью ----------
    private void drawRight(DrawContext context, Matrix3x2fStack ms, float x, float y, float w, float h, int mx, int my) {
        InventorySetup s = selected;

        float hy = y + 16f, hh = 24f, hcy = hy + hh / 2f;
        ClickGuiIcons.search(ms, x + 8f, hcy, 14f, MenuChrome.MUTED);
        Instance tf = Fonts.GOLOS_SEMI.getFont(13f);
        Render2D.drawFont(ms, tf, "Просмотр", x + 20f, hcy - tf.getHeight() / 2f, MenuChrome.TEXT2);
        float cx = x + 20f + tf.getWidth("Просмотр") + 10f;
        if (s != null) {
            Instance nf = Fonts.GOLOS.getFont(11f);
            float chW = nf.getWidth(s.name) + 16f, chH = 19f, chY = hcy - chH / 2f;
            Render2D.drawRoundedRect(ms, cx, chY, chW, chH, chH / 2f, MenuChrome.accentA(0.12f));
            Render2D.drawFont(ms, nf, s.name, cx + 8f, chY + chH / 2f - nf.getHeight() / 2f, MenuChrome.accent());
        }
        // Шрифт как у заголовка окна (GOLOS_SEMI), а не моноширинный PlexMono, и на размер мельче.
        Instance mf = Fonts.GOLOS_SEMI.getFont(9f);
        String cnt = (s == null ? 0 : s.itemCount) + " предм.";
        Render2D.drawFont(ms, mf, cnt, x + w - mf.getWidth(cnt), hcy - mf.getHeight() / 2f, MenuChrome.MUTED2);

        float gx = x + (w - GRID_W) / 2f;

        // Ряд брони (4 слева) + оффхенд справа.
        float ay = y + 56f;
        for (int i = 0; i < 4; i++) {
            drawSlot(context, ms, gx + i * (SLOT + SLOT_GAP), ay, stackAt(s, ARMOR_ORDER[i]));
        }
        drawSlot(context, ms, gx + GRID_W - SLOT, ay, stackAt(s, 40));

        // Основная сетка 9×3 (индексы 9..35).
        float gy = ay + SLOT + 16f;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                drawSlot(context, ms, gx + c * (SLOT + SLOT_GAP), gy + r * (SLOT + SLOT_GAP),
                        stackAt(s, 9 + r * 9 + c));
            }
        }

        // Хотбар (индексы 0..8), отделён hairline сверху.
        float gridBottom = gy + 3f * SLOT + 2f * SLOT_GAP;
        Render2D.drawRoundedRect(ms, gx, gridBottom + 14f, GRID_W, 1f, 0f, MenuChrome.white(0.07f));
        float hby = gridBottom + 28f;
        for (int c = 0; c < 9; c++) {
            drawSlot(context, ms, gx + c * (SLOT + SLOT_GAP), hby, stackAt(s, c));
        }
    }

    private ItemStack stackAt(InventorySetup s, int index) {
        if (s == null || index < 0 || index >= s.slots.length) return ItemStack.EMPTY;
        ItemStack st = s.slots[index];
        return st == null ? ItemStack.EMPTY : st;
    }

    private void drawSlot(DrawContext context, Matrix3x2fStack ms, float sx, float sy, ItemStack st) {
        boolean empty = st.isEmpty();
        Render2D.drawRoundedRect(ms, sx, sy, SLOT, SLOT, 6f, MenuChrome.white(empty ? 0.03f : 0.05f));
        Render2D.drawBorder(ms, sx, sy, SLOT, SLOT, 6f, 1.0f, 1.4f, MenuChrome.white(empty ? 0.07f : 0.11f));
        if (empty) return;

        float s = SLOT / 16f;
        ms.pushMatrix();
        ms.translate(sx, sy);
        // База масштабирует только x/y (z=1) — без компенсации z итоговая матрица неравномерна
        // и 3D-модели блоков сплющивает по глубине.
        ms.scale(s, s);
        if (mc.player != null) {
            context.drawItem(mc.player, st, 0, 0, 0);
        } else {
            context.drawItem(st, 0, 0);
        }
        context.drawStackOverlay(mc.textRenderer, st, 0, 0);
        ms.popMatrix();
    }

    // ---------- Тост-приветствие ----------
    private void drawToast(Matrix3x2fStack ms, float wx, float wy, float ww, float wh, int mx, int my) {
        long now = System.currentTimeMillis();
        float in = MathHelper.clamp((now - openMs) / 260f, 0f, 1f);
        in = 1f - (1f - in) * (1f - in) * (1f - in);
        float out = MathHelper.clamp((now - openMs - 10_000L) / 220f, 0f, 1f);
        if (out >= 1f) { toast = false; return; }
        float a = in * (1f - out);
        float dy = (1f - in) * 8f + out * 8f;

        float tw = 620f, th = 76f;
        float tx = wx + ww / 2f - tw / 2f, ty = wy + wh - 92f - th + dy;

        Render2D.drawRoundedRect(ms, tx, ty, tw, th, 12f, fade(new Color(17, 23, 20), 0.96f * a));
        Render2D.drawBorder(ms, tx, ty, tw, th, 12f, 1.0f, 1.4f, MenuChrome.white(0.10f * a));

        float ts = 36f, tix = tx + 18f, tiy = ty + (th - ts) / 2f;
        Render2D.drawRoundedRect(ms, tix, tiy, ts, ts, 9f, MenuChrome.accentA(0.12f * a));
        ClickGuiIcons.cube(ms, tix + ts / 2f, tiy + ts / 2f, 16f, MenuChrome.accentA(a));

        Instance hf = Fonts.GOLOS_SEMI.getFont(13f);
        Instance df = Fonts.GOLOS.getFont(11.5f);
        float txx = tix + ts + 14f;
        // Текст режем по ФАКТИЧЕСКОМУ месту до крестика: длинная строка вылезала за рамку тоста
        // и заезжала под кнопку закрытия.
        float textMaxW = (tx + tw - 14f - 26f - 10f) - txx;
        Render2D.drawFont(ms, hf, clip(hf, "Менеджер инвентарей", textMaxW), txx, ty + 20f, fade(MenuChrome.TEXT, a));
        Render2D.drawFont(ms, df,
                clip(df, "Сохраните раскладку и загрузите её как шаблон — недостающее подсветится в игре.", textMaxW),
                txx, ty + 40f, fade(MenuChrome.MUTED2, a));

        float cs = 26f, ccx = tx + tw - 14f - cs, ccy = ty + (th - cs) / 2f;
        float ct = MenuChrome.upd(hToastClose, MenuChrome.hit(mx, my, ccx, ccy, cs, cs));
        Render2D.drawRoundedRect(ms, ccx, ccy, cs, cs, 7f, MenuChrome.white((0.04f + 0.05f * ct) * a));
        ClickGuiIcons.close(ms, ccx + cs / 2f, ccy + cs / 2f, 12f,
                fade(MenuChrome.lerp(MenuChrome.MUTED, MenuChrome.TEXT2, ct), a));
        MenuChrome.set(rToastClose, ccx, ccy, cs, cs);
    }

    private static Color fade(Color c, float a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(),
                MathHelper.clamp(Math.round(c.getAlpha() * a), 0, 255));
    }

    // ---------- Общие примитивы (паттерн AccountSwitcherScreen) ----------
    private void drawCaret(Matrix3x2fStack ms, float target, float cyMid) {
        if (caretX <= 0f) caretX = target;
        caretX += (target - caretX) * 0.4f;
        float blink = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 260.0));
        Color c = new Color(MenuChrome.TEXT2.getRed(), MenuChrome.TEXT2.getGreen(),
                MenuChrome.TEXT2.getBlue(), Math.round(255 * blink));
        Render2D.drawRoundedRect(ms, caretX, cyMid - 6f, 1f, 12f, 0.5f, c);
    }

    private float actionW(String label, boolean primary) {
        Instance lf = (primary ? Fonts.UNBOUNDED : Fonts.UNBOUNDED_L).getFont(11f);
        return 18f + 15f + 9f + lf.getWidth(label) + 18f;
    }

    private void drawAction(Matrix3x2fStack ms, float x, float y, float w, float h, boolean primary,
                            String icon, String label, float hoverT, boolean enabled) {
        float a = enabled ? 1f : 0.4f;
        Color labelColor = primary ? MenuChrome.INK
                : new Color(MenuChrome.TEXT2.getRed(), MenuChrome.TEXT2.getGreen(), MenuChrome.TEXT2.getBlue(),
                Math.round(255 * a));
        float ty = y - hoverT;
        if (primary) {
            Color acc = MenuChrome.accent();
            Render2D.drawRoundedRect(ms, x + 4f, ty + h - 6f, w - 8f, 12f, 8f,
                    MenuChrome.accentA((enabled ? 0.24f : 0.10f) + 0.14f * hoverT));
            int br = Math.min(255, Math.round(acc.getRed() * (1f + 0.07f * hoverT)));
            int bg = Math.min(255, Math.round(acc.getGreen() * (1f + 0.07f * hoverT)));
            int bb = Math.min(255, Math.round(acc.getBlue() * (1f + 0.07f * hoverT)));
            Render2D.drawRoundedRect(ms, x, ty, w, h, 8f, new Color(br, bg, bb, enabled ? 255 : 130));
            Render2D.drawRoundedRect(ms, x + 8f, ty + 1f, w - 16f, 1f, 0.5f, MenuChrome.white(0.35f));
        } else {
            MenuChrome.drawSecondaryMaterial(ms, x, ty, w, h, 8f, hoverT, false);
        }
        Color iconColor = primary ? MenuChrome.INK
                : new Color(MenuChrome.MUTED.getRed(), MenuChrome.MUTED.getGreen(), MenuChrome.MUTED.getBlue(),
                Math.round(255 * a));
        float icx = x + 18f + 15f / 2f, icy = ty + h / 2f;
        if ("plus".equals(icon)) ClickGuiIcons.plus(ms, icx, icy, 15f, iconColor);
        else if ("check".equals(icon)) ClickGuiIcons.check(ms, icx, icy, 15f, iconColor);
        else ClickGuiIcons.arrowRight(ms, icx, icy, 15f, iconColor);
        Instance lf = (primary ? Fonts.UNBOUNDED : Fonts.UNBOUNDED_L).getFont(11f);
        Render2D.drawFont(ms, lf, label, x + 18f + 15f + 9f, ty + h / 2f - lf.getHeight() / 2f, labelColor);
    }

    // ---------- Ввод ----------
    @Override
    protected boolean contentClick(float mx, float my, int button) {
        if (button != 0) return false;

        if (toast && MenuChrome.in(rToastClose, mx, my)) { toast = false; click(); return true; }

        if (MenuChrome.in(rPill, mx, my)) {
            InventorySetupManager.setOverlayEnabled(!InventorySetupManager.overlayEnabled());
            click();
            return true;
        }
        nameFocused = MenuChrome.in(rName, mx, my);
        if (nameFocused) return true;
        if (MenuChrome.in(rSave, mx, my)) { save(); return true; }

        for (Object[] hitRow : rowHits) {
            float[] r = (float[]) hitRow[0];
            if (!MenuChrome.hit(mx, my, r[0], r[1], r[2], r[3])) continue;
            InventorySetup s = (InventorySetup) hitRow[1];
            String kind = (String) hitRow[2];
            switch (kind) {
                case "del" -> armOrDelete(s);
                case "load" -> {
                    selected = s;
                    deleteArmed = null;
                    InventorySetupManager.load(s);
                    click();
                }
                default -> {
                    selected = s;
                    // Смена выбора обязана снять взвод удаления — иначе confirm перенесётся на другую строку.
                    deleteArmed = null;
                    click();
                }
            }
            return true;
        }
        return false;
    }

    private void save() {
        if (!inGame()) return;
        String name = nameDraft.trim();
        if (name.isEmpty()) name = autoName();
        InventorySetup s = InventorySetupManager.saveCurrent(name);
        if (s != null) selected = s;
        nameDraft = "";
        nameFocused = false;
        deleteArmed = null;
        listAnimMs = System.currentTimeMillis();
        click();
    }

    private String autoName() {
        List<InventorySetup> list = InventorySetupManager.setups();
        int n = list.size() + 1;
        outer:
        while (true) {
            String candidate = "inv" + n;
            for (InventorySetup s : list) {
                if (candidate.equals(s.name)) { n++; continue outer; }
            }
            return candidate;
        }
    }

    private void armOrDelete(InventorySetup s) {
        if (s == deleteArmed && System.currentTimeMillis() < deleteArmedUntil) {
            InventorySetupManager.remove(s);
            deleteArmed = null;
            hRow.remove(s);
            hLoad.remove(s);
            hDel.remove(s);
            if (selected == s) {
                List<InventorySetup> list = InventorySetupManager.setups();
                selected = list.isEmpty() ? null : list.get(0);
            }
            listAnimMs = System.currentTimeMillis();
        } else {
            deleteArmed = s;
            deleteArmedUntil = System.currentTimeMillis() + 2500L;
        }
        click();
    }

    @Override
    protected boolean contentScroll(float mx, float my, double vertical) {
        scrollTarget -= (float) vertical * (ROW_H + ROW_GAP) * 0.9f;
        return true;
    }

    @Override
    protected boolean contentKey(int keyCode, int scanCode, int modifiers) {
        if (!nameFocused) return false;
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (!nameDraft.isEmpty()) nameDraft = nameDraft.substring(0, nameDraft.length() - 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) { save(); return true; }
        return false;
    }

    @Override
    protected boolean contentChar(char chr, int modifiers) {
        if (nameFocused && chr >= ' ' && nameDraft.length() < 24) { nameDraft += chr; return true; }
        return false;
    }

    /** Обрезка строки по доступной ширине с многоточием — двоичным поиском, а не посимвольно. */
    private static String clip(Instance f, String s, float maxW) {
        if (maxW <= 0f || f.getWidth(s) <= maxW) return s;
        int lo = 0, hi = s.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (f.getWidth(s.substring(0, mid) + "…") <= maxW) lo = mid; else hi = mid - 1;
        }
        return s.substring(0, lo) + "…";
    }

    private void click() {
        try {
            if (mc == null || mc.getSoundManager() == null) return;
            float pitch = 1.7f + (float) (Math.random() * 0.35);
            mc.getSoundManager().play(PositionedSoundInstance.ui(
                    SoundEvents.UI_BUTTON_CLICK.value(), pitch, 0.25f));
        } catch (Throwable ignored) {
        }
    }
}
