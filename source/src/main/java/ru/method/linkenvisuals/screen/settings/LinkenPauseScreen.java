package ru.method.linkenvisuals.screen.settings;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.PressableWidget;
import org.joml.Matrix3x2fStack;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.math.MathHelper;
import ru.method.linkenvisuals.screen.clickgui.redesign.ClickGuiIcons;
import ru.method.linkenvisuals.screen.mainmenu.MenuChrome;
import ru.method.linkenvisuals.screen.multiplayer.WindowMaterial;
import ru.method.linkenvisuals.utils.animations.Animation;
import ru.method.linkenvisuals.utils.render.Render2D;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;
import ru.method.linkenvisuals.utils.render.fonts.Instance;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 11a — кастомное меню паузы (ESC) вместо ванильного {@link GameMenuScreen}.
 *
 * <p>Фон/появление/ESC-чип наследуются от {@link LinkenSettingsScreen} (в игре: движковый блюр +
 * затемнение + виньетка, fade+scale 180 мс, ввод заморожен на время ухода).
 *
 * <p><b>Адаптивность к набору кнопок:</b> состав плиток не захардкожен — при открытии создаётся
 * НАСТОЯЩИЙ ванильный {@code GameMenuScreen}, инициализируется в размере окна, и его виджеты
 * (включая добавленные модами: «Моды», WorldTools и т.п.) собираются в плитки. Клик по плитке
 * вызывает {@code onPress} исходного виджета — вся логика пунктов остаётся ванильной/модовой.
 * «Вернуться к игре» всегда сверху, «Отключиться»/«Сохранить и выйти» всегда снизу (подпись
 * приходит из виджета уже локализованной под контекст сервер/одиночка).
 */
public final class LinkenPauseScreen extends LinkenSettingsScreen {

    private static final float WIN_W = 440f, PAD_X = 22f;
    private static final float TOPBAR_H = 56f, ROW_H = 44f, GAP = 8f;
    private static final float BODY_TOP = 18f, BODY_BOTTOM = 20f, DIVIDER_PAD = 6f;
    /** Больше этой высоты окно не растёт — дальше плитки скроллятся (README: рост до ~640). */
    private static final float MAX_WIN_H = 640f;

    /** Плитка раздела: подпись, ключ перевода (для иконки) и действие исходного виджета. */
    private record Item(String label, String key, Runnable action, boolean active) {}

    private final List<Item> tiles = new ArrayList<>();
    private String disconnectLabel = "Отключиться";
    private Runnable disconnectAction;

    private final Animation hResume = MenuChrome.anim(), hDisconnect = MenuChrome.anim();
    private final float[] rResume = new float[4], rDisconnect = new float[4];
    private final List<float[]> tileRects = new ArrayList<>();
    private final List<Animation> tileAnims = new ArrayList<>();
    private float scroll, scrollTarget, maxScroll;
    private final float[] rTilesView = new float[4];

    public LinkenPauseScreen() {
        super(null, "Меню");
        harvest();
        reorderPreferredTiles();
    }

    /**
     * По запросу: «Настройки» встают на место «Достижений», «Аккаунты» — на место «Статистики».
     * Своп (не удаление) — исходные плитки не теряются, просто меняются местами в сетке.
     */
    private void reorderPreferredTiles() {
        swapTiles(indexOfTileKey("options"), indexOfTileKey("advancements"));
    }

    private int indexOfTileKey(String keyPart) {
        for (int i = 0; i < tiles.size(); i++) {
            String k = tiles.get(i).key();
            if (k != null && k.contains(keyPart)) return i;
        }
        return -1;
    }

    private void swapTiles(int a, int b) {
        if (a < 0 || b < 0 || a == b) return;
        java.util.Collections.swap(tiles, a, b);
    }

    /** Сбор пунктов из живого ванильного меню — см. javadoc класса. */
    private void harvest() {
        try {
            GameMenuScreen vanilla = new GameMenuScreen(true);
            vanilla.init(mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight());
            for (Element e : vanilla.children()) {
                if (!(e instanceof ClickableWidget w) || !(e instanceof PressableWidget p)) continue;
                if (!w.visible) continue;
                String key = translationKey(w.getMessage());
                if ("menu.returnToGame".equals(key)) continue; // наш primary сверху
                String label = w.getMessage().getString().trim();
                if ("menu.disconnect".equals(key) || "menu.returnToMenu".equals(key)) {
                    if (!label.isEmpty()) disconnectLabel = label;
                    disconnectAction = () -> p.onPress(new net.minecraft.client.input.MouseInput(0, 0));
                    continue;
                }
                if (label.isEmpty()) label = "Мод"; // иконка-квадратик без текста — встраиваем плиткой
                tiles.add(new Item(label, key, () -> p.onPress(new net.minecraft.client.input.MouseInput(0, 0)), w.active));
            }
        } catch (Throwable t) {
            // Сбор не должен ронять паузу: остаёмся с тем, что успели собрать.
        }
        // Явная плитка свитчера аккаунтов (parent=пауза, чтобы ESC вернул в паузу).
        while (tileAnims.size() < tiles.size()) tileAnims.add(MenuChrome.anim());
    }

    private static String translationKey(Text text) {
        return text != null && text.getContent() instanceof TranslatableTextContent t ? t.getKey() : "";
    }

    @Override
    protected String escHintLabel() {
        return "вернуться к игре";
    }

    // ---------- Геометрия ----------

    private int tileRows() {
        return (tiles.size() + 1) / 2;
    }

    private float tilesDesiredH() {
        int rows = tileRows();
        return rows == 0 ? 0f : rows * ROW_H + (rows - 1) * GAP;
    }

    private float winH() {
        float desired = TOPBAR_H + BODY_TOP + ROW_H + GAP + tilesDesiredH()
                + DIVIDER_PAD * 2f + 1f + GAP + ROW_H + BODY_BOTTOM;
        return Math.min(desired, MAX_WIN_H);
    }

    @Override
    protected void drawContent(DrawContext context, Matrix3x2fStack ms, int mx, int my) {
        float w = WIN_W, h = winH();
        float x = winX(w);
        // Чуть выше середины: translate(-50%, -53%) из макета.
        float y = vh / 2f - h * 0.53f;

        WindowMaterial.window(ms, x, y, w, h);
        drawTopbar(ms, x, y, w);
        Render2D.drawRoundedRect(ms, x, y + TOPBAR_H - 1f, w, 1f, 0f, MenuChrome.white(0.07f));

        float cy = y + TOPBAR_H + BODY_TOP;

        // 1. «Вернуться к игре» — primary во всю ширину.
        drawResume(ms, x + PAD_X, cy, w - PAD_X * 2f, mx, my);
        cy += ROW_H + GAP;

        // 2. Плитки — 2 колонки; последняя нечётная на обе; скролл при переполнении.
        float viewH = h - (cy - y) - (DIVIDER_PAD * 2f + 1f + GAP + ROW_H + BODY_BOTTOM);
        drawTiles(context, ms, x, cy, w, viewH, mx, my);
        cy += viewH;

        // 3. Разделитель.
        cy += DIVIDER_PAD;
        Render2D.drawRoundedRect(ms, x + PAD_X, cy, w - PAD_X * 2f, 1f, 0f, MenuChrome.white(0.07f));
        cy += 1f + DIVIDER_PAD + GAP - DIVIDER_PAD;

        // 4. «Отключиться» / «Сохранить и выйти».
        drawDisconnect(ms, x + PAD_X, cy, w - PAD_X * 2f, mx, my);

        WindowMaterial.corners(ms, x, y, w, h);
    }

    // ---------- Топбар: «Меню» + чип сервера ----------

    private void drawTopbar(Matrix3x2fStack ms, float x, float y, float w) {
        float cy = y + TOPBAR_H / 2f;
        Instance tf = Fonts.GOLOS_SEMI.getFont(17f);
        Render2D.drawFont(ms, tf, "Меню", x + PAD_X, cy - tf.getHeight() / 2f, MenuChrome.TEXT);

        boolean single = mc.isInSingleplayer();
        String name = serverName(single);
        Instance nf = Fonts.UNBOUNDED.getFont(9.5f);
        Instance pf = Fonts.PLEXMONO.getFont(10f);
        String ping = single ? "" : ru.method.linkenvisuals.utils.server.Server.getPing(mc.player) + " мс";

        float chipRight = x + w - PAD_X;
        float px = chipRight - (ping.isEmpty() ? 0f : pf.getWidth(ping));
        if (!ping.isEmpty()) {
            Render2D.drawFont(ms, pf, ping, px, cy - pf.getHeight() / 2f, MenuChrome.MUTED3);
            px -= 8f;
        }
        // Имя не должно налезать на заголовок «Меню»: ужимаем по свободной ширине между заголовком
        // и чипом пинга (плюс место под точку слева). Иначе длинное имя/реклама-MOTD уезжает под «Меню».
        float titleRight = x + PAD_X + tf.getWidth("Меню");
        float dotReserve = single ? 0f : 14f;
        name = fit(nf, name, Math.max(0f, px - dotReserve - (titleRight + 12f)));
        float nx = px - nf.getWidth(name);
        Render2D.drawFont(ms, nf, name, nx, cy - nf.getHeight() / 2f, MenuChrome.TEXT3);
        if (!single) {
            // Точка 6px с акцентным глоу — только у сервера.
            float dx = nx - 8f - 3f;
            MenuChrome.glow(ms, dx - 2f, cy - 5f, 10f, 10f, 5f, MenuChrome.accentA(0.5f), 0f, 3, 1.3f);
            Render2D.drawCircle(ms, dx + 3f, cy, 3f, MenuChrome.accent());
        }
    }

    /** Ужать в одну строку по ширине с многоточием. */
    private static String fit(Instance f, String s, float maxW) {
        if (s == null || s.isEmpty()) return "";
        if (maxW <= 0f) return "";
        if (f.getWidth(s) <= maxW) return s;
        float ew = f.getWidth("…");
        int end = s.length();
        while (end > 0 && f.getWidth(s.substring(0, end)) + ew > maxW) end--;
        return s.substring(0, end) + "…";
    }

    /** Имя сервера без рекламной маркировки «(12+,erid:…)» в хвосте. */
    private static String cleanServerName(String name) {
        String nm = name == null ? "" : name.trim();
        nm = nm.replaceAll("\\s*\\((?:[^)]*erid[^)]*|\\d{1,2}\\+[^)]*)\\)\\s*$", "").trim();
        return nm.isEmpty() ? name : nm;
    }

    private String serverName(boolean single) {
        try {
            if (single && mc.getServer() != null) {
                return mc.getServer().getSaveProperties().getLevelName();
            }
            var entry = mc.getCurrentServerEntry();
            if (entry != null && entry.name != null && !entry.name.isBlank()) return cleanServerName(entry.name);
            return entry != null ? entry.address : "Сервер";
        } catch (Throwable t) {
            return "Сервер";
        }
    }

    // ---------- Primary «Вернуться к игре» ----------

    private void drawResume(Matrix3x2fStack ms, float x, float y, float w, int mx, int my) {
        boolean hov = MenuChrome.hit(mx, my, x, y, w, ROW_H);
        float t = MenuChrome.upd(hResume, hov);
        float ty = y - t;

        Color acc = MenuChrome.accent();
        Render2D.drawRoundedRect(ms, x + 4f, ty + ROW_H - 6f, w - 8f, 12f, 8f, MenuChrome.accentA(0.24f + 0.14f * t));
        int br = Math.min(255, Math.round(acc.getRed() * (1f + 0.07f * t)));
        int bg = Math.min(255, Math.round(acc.getGreen() * (1f + 0.07f * t)));
        int bb = Math.min(255, Math.round(acc.getBlue() * (1f + 0.07f * t)));
        Render2D.drawRoundedRect(ms, x, ty, w, ROW_H, 8f, new Color(br, bg, bb));
        Render2D.drawRoundedRect(ms, x + 8f, ty + 1f, w - 16f, 1f, 0.5f, MenuChrome.white(0.35f));

        Instance bf = Fonts.UNBOUNDED.getFont(11f);
        String label = "Вернуться к игре";
        float icon = 14f;
        float content = icon + 9f + bf.getWidth(label);
        float ix = x + (w - content) / 2f;
        ClickGuiIcons.play(ms, ix + icon / 2f, ty + ROW_H / 2f, icon, MenuChrome.INK);
        Render2D.drawFont(ms, bf, label, ix + icon + 9f, ty + ROW_H / 2f - bf.getHeight() / 2f, MenuChrome.INK);
        MenuChrome.set(rResume, x, y, w, ROW_H);
    }

    // ---------- Плитки ----------

    private void drawTiles(DrawContext context, Matrix3x2fStack ms, float winLeft, float y, float winW,
                           float viewH, int mx, int my) {
        tileRects.clear();
        float x = winLeft + PAD_X;
        float w = winW - PAD_X * 2f;
        float colW = (w - GAP) / 2f;

        maxScroll = Math.max(0f, tilesDesiredH() - viewH);
        scrollTarget = MathHelper.clamp(scrollTarget, 0f, maxScroll);
        scroll += (scrollTarget - scroll) * 0.35f;
        if (Math.abs(scrollTarget - scroll) < 0.4f) scroll = scrollTarget;
        MenuChrome.set(rTilesView, x, y, w, viewH);

        // Ножницы нужны только когда список реально скроллится: без скролла они срезали верхнюю
        // кромку рамок первого ряда (бордер рисуется с полупиксельным свесом за ty).
        boolean clip = maxScroll > 0.5f;
        if (clip) Render2D.startScissor(context, x, y - 1f, w + 2f, viewH + 2f);
        for (int i = 0; i < tiles.size(); i++) {
            Item it = tiles.get(i);
            int col = i % 2, row = i / 2;
            boolean lastOdd = i == tiles.size() - 1 && (tiles.size() % 2) == 1;
            float tw = lastOdd ? w : colW;
            float tx = lastOdd ? x : x + col * (colW + GAP);
            float ty = y + row * (ROW_H + GAP) - scroll;
            if (ty + ROW_H < y || ty > y + viewH) continue;

            boolean hov = it.active() && MenuChrome.hit(mx, my, tx, Math.max(ty, y), tw,
                    Math.min(ty + ROW_H, y + viewH) - Math.max(ty, y));
            float t = MenuChrome.upd(tileAnims.get(i), hov);

            Render2D.drawRoundedRect(ms, tx, ty, tw, ROW_H, 8f, MenuChrome.white(it.active() ? 0.04f : 0.02f));
            Render2D.drawBorder(ms, tx, ty, tw, ROW_H, 8f, 1.0f, 1.2f,
                    MenuChrome.lerp(MenuChrome.white(0.08f), MenuChrome.white(0.24f), t));

            Color ic = it.active() ? MenuChrome.MUTED : MenuChrome.MUTED4;
            drawTileIcon(ms, it, tx + 15f + 7.5f, ty + ROW_H / 2f, 15f, ic);

            Instance lf = Fonts.UNBOUNDED.getFont(10f);
            float textX = tx + 15f + 15f + 10f;
            float maxTextW = tw - (textX - tx) - 12f;
            drawTileLabel(ms, lf, it, textX, ty + ROW_H / 2f - lf.getHeight() / 2f, maxTextW);

            // Хитбокс режем по вьюпорту — ножницы обрезают только пиксели, не клики.
            float hitTop = Math.max(ty, y), hitBot = Math.min(ty + ROW_H, y + viewH);
            if (hitBot - hitTop >= 1f && it.active()) {
                tileRects.add(new float[]{tx, hitTop, tw, hitBot - hitTop, i});
            }
        }
        if (clip) Render2D.stopScissor(context);

        if (maxScroll > 0.5f) {
            float trackH = viewH - 6f;
            float thumbH = Math.max(20f, trackH * (viewH / tilesDesiredH()));
            float thumbY = y + 3f + (trackH - thumbH) * (scroll / maxScroll);
            Render2D.drawRoundedRect(ms, winLeft + winW - 8f, thumbY, 3f, thumbH, 1.5f, MenuChrome.accentA(0.5f));
        }
    }

    /** «World» в WorldTools красится акцентом (спека) — остальным плиткам обычная подпись. */
    private void drawTileLabel(Matrix3x2fStack ms, Instance lf, Item it, float x, float y, float maxW) {
        String label = clip(lf, it.label(), maxW);
        String lower = label.toLowerCase(Locale.ROOT);
        int worldIdx = lower.indexOf("world");
        if (worldIdx == 0 && lower.contains("tools")) {
            String world = label.substring(0, 5);
            String rest = label.substring(5);
            Render2D.drawFont(ms, lf, world, x, y, MenuChrome.accent());
            Render2D.drawFont(ms, lf, rest, x + lf.getWidth(world), y, MenuChrome.TEXT2);
        } else {
            Render2D.drawFont(ms, lf, label, x, y, MenuChrome.TEXT2);
        }
    }

    private void drawTileIcon(Matrix3x2fStack ms, Item it, float cx, float cy, float size, Color c) {
        String k = it.key() == null ? "" : it.key();
        String l = it.label().toLowerCase(Locale.ROOT);
        if (k.contains("advancements")) ClickGuiIcons.trophy(ms, cx, cy, size, c);
        else if (k.contains("stats")) ClickGuiIcons.chartBar(ms, cx, cy, size, c);
        else if (k.contains("options")) ClickGuiIcons.sliders(ms, cx, cy, size, c);
        else if (k.contains("Feedback") || k.contains("feedback")) ClickGuiIcons.chat(ms, cx, cy, size, c);
        else if (k.contains("reportBugs")) ClickGuiIcons.info(ms, cx, cy, size, c);
        else if (k.contains("playerReporting") || l.contains("жалоб")) ClickGuiIcons.flag(ms, cx, cy, size, c);
        else if (k.contains("shareToLan")) ClickGuiIcons.globe(ms, cx, cy, size, c);
        else if (l.contains("world")) ClickGuiIcons.globe(ms, cx, cy, size, c);
        else if (l.contains("мод") || l.contains("mod")) ClickGuiIcons.cube(ms, cx, cy, size, c);
        else ClickGuiIcons.cube(ms, cx, cy, size, c);
    }

    // ---------- «Отключиться» ----------

    private void drawDisconnect(Matrix3x2fStack ms, float x, float y, float w, int mx, int my) {
        boolean hov = MenuChrome.hit(mx, my, x, y, w, ROW_H);
        float t = MenuChrome.upd(hDisconnect, hov);
        // danger=true: на hover рамка уходит в красный — как у «Отмены» на экране подключения.
        MenuChrome.drawSecondaryMaterial(ms, x, y, w, ROW_H, 8f, t, true);

        Instance bf = Fonts.UNBOUNDED.getFont(11f);
        float icon = 14f;
        float content = icon + 9f + bf.getWidth(disconnectLabel);
        float ix = x + (w - content) / 2f;
        ClickGuiIcons.signOut(ms, ix + icon / 2f, y + ROW_H / 2f, icon, MenuChrome.MUTED);
        Render2D.drawFont(ms, bf, disconnectLabel, ix + icon + 9f, y + ROW_H / 2f - bf.getHeight() / 2f, MenuChrome.TEXT2);
        MenuChrome.set(rDisconnect, x, y, w, ROW_H);
    }

    private static String clip(Instance f, String s, float maxW) {
        if (maxW <= 0f || f.getWidth(s) <= maxW) return s;
        int lo = 0, hi = s.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (f.getWidth(s.substring(0, mid) + "…") <= maxW) lo = mid; else hi = mid - 1;
        }
        return s.substring(0, lo) + "…";
    }

    // ---------- Ввод ----------

    @Override
    protected boolean contentClick(float mx, float my, int button) {
        if (button != 0) return false;
        if (MenuChrome.in(rResume, mx, my)) { mc.setScreen(null); return true; }
        if (MenuChrome.in(rDisconnect, mx, my)) {
            runTileAction(disconnectAction);
            return true;
        }
        for (float[] r : tileRects) {
            if (MenuChrome.hit(mx, my, r[0], r[1], r[2], r[3])) {
                runTileAction(tiles.get((int) r[4]).action());
                return true;
            }
        }
        return false;
    }

    /**
     * Действие плитки — это {@code onPress} ванильного/модового виджета (открытие StatsScreen и т.п.).
     * Оборачиваем в try/catch: если такой экран падает при открытии, ловим и логируем стек (иначе
     * исключение прорывалось бы наружу без ясного источника), а меню не «умирает» молча.
     */
    private void runTileAction(Runnable action) {
        if (action == null) return;
        try {
            action.run();
        } catch (Throwable t) {
            ru.method.linkenvisuals.LinkenVisuals.LOGGER.error("[Pause] пункт меню упал при открытии", t);
        }
    }

    @Override
    protected boolean contentScroll(float mx, float my, double vertical) {
        if (maxScroll > 0.5f && MenuChrome.in(rTilesView, mx, my)) {
            scrollTarget = MathHelper.clamp(scrollTarget - (float) vertical * (ROW_H + GAP), 0f, maxScroll);
            return true;
        }
        return false;
    }
}
