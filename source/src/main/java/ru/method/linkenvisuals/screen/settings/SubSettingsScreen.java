package ru.method.linkenvisuals.screen.settings;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
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
import java.util.List;
import java.util.Locale;

/**
 * 9c — универсальный шаблон под-окна настроек. Рисует список опций {@link SettingsOption} строками в две
 * колонки с тремя типами контролов (селект-чип / слайдер / тоггл 32×18), поиском по параметрам и вертикальным
 * скроллом. Один и тот же шаблон переиспользуется для любого под-окна (графика, музыка, управление, чат…) —
 * различается только заголовок крошки и список опций.
 */
public final class SubSettingsScreen extends LinkenSettingsScreen {

    private static final float WIN_W = 1060f, WIN_TOP = 130f, PAD_X = 22f;
    private static final float TOPBAR_H = 64f, FOOTER_H = 64f;
    private static final float ROW_H = 44f, ROW_GAP = 8f, COL_GAP = 14f, PAD_Y = 20f;

    private final String crumb;
    private final List<SettingsOption> options;

    private final Animation hBack = MenuChrome.anim(), hDone = MenuChrome.anim(), hSearch = MenuChrome.anim();
    private final float[] rBack = new float[4], rDone = new float[4], rSearch = new float[4], rCrumb = new float[4];
    private final float[][] ctrlRect;
    private final boolean[] ctrlVisible;
    private final float[] toggleAnim;
    private final Animation[] hRow;

    private String query = "";
    private boolean searchFocused;
    /** Момент последней смены запроса — по нему заново проигрывается появление отфильтрованных строк. */
    private long listAnimMs;
    private float caretX;
    /** Показанная доля слайдера: тянется к реальной, чтобы значение не прыгало скачком. */
    private final float[] sliderAnim;
    private float scroll, scrollTarget;
    private long lastFrameMs;
    private int draggingSlider = -1;

    // Дропдаун селекта: открытая опция + геометрия чипа (сташится в drawRow, рисуется поверх всего).
    private int openDropdown = -1;
    private int dropdownFor = -1;
    /** Список схлопывается: рисуем его ещё несколько кадров, пока анимация не дойдёт до нуля. */
    private boolean closingDropdown;
    private int dropdownClosingFor = -1;
    private float dropdownX, dropdownY, dropdownW;
    private float dropdownAnim;
    private final List<float[]> dropdownRects = new ArrayList<>();
    /** Индекс варианта для каждого видимого прямоугольника: список прокручивается, номера расходятся. */
    private final List<Integer> dropdownIdx = new ArrayList<>();
    private final float[] rDropdown = new float[4];
    private float dropdownScroll, dropdownScrollTarget, dropdownMaxScroll;
    /** Максимум строк в раскрытом списке — дальше прокрутка колесом. */
    private static final int DROPDOWN_ROWS = 8;

    public SubSettingsScreen(Screen parent, String crumb, List<SettingsOption> options) {
        super(parent, "Настройки — " + crumb);
        this.crumb = crumb;
        this.options = options;
        this.ctrlRect = new float[options.size()][4];
        this.ctrlVisible = new boolean[options.size()];
        this.toggleAnim = new float[options.size()];
        this.hRow = new Animation[options.size()];
        this.sliderAnim = new float[options.size()];
        for (int i = 0; i < options.size(); i++) hRow[i] = MenuChrome.anim();
        for (int i = 0; i < options.size(); i++) {
            SettingsOption o = options.get(i);
            if (o.kind == SettingsOption.Kind.TOGGLE) toggleAnim[i] = o.state.getAsBoolean() ? 1f : 0f;
            if (o.kind == SettingsOption.Kind.SLIDER) sliderAnim[i] = (float) MathHelper.clamp(o.frac.getAsDouble(), 0.0, 1.0);
        }
    }

    private float winH() {
        int rowsAll = (options.size() + 1) / 2;
        float contentAll = PAD_Y * 2f + rowsAll * ROW_H + Math.max(0, rowsAll - 1) * ROW_GAP;
        float desired = TOPBAR_H + contentAll + FOOTER_H;
        float maxH = vh - WIN_TOP - 74f;
        return Math.min(desired, Math.max(TOPBAR_H + ROW_H + FOOTER_H, maxH));
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

        // Дропдаун селекта — поверх всего (окно/футер/уголки).
        if (dropdownFor >= 0 || closingDropdown) drawDropdown(context, ms, mx, my);
    }

    // ---------- Топбар: ‹ + крошка + поиск ----------
    private void drawTopbar(Matrix3x2fStack ms, float x, float y, float w, int mx, int my) {
        float cy = y + TOPBAR_H / 2f;

        float bs = 36f, bx = x + PAD_X, by = cy - bs / 2f + 6f; // «Назад» на 3px ниже
        boolean bHov = MenuChrome.hit(mx, my, bx, by, bs, bs);
        float bt = MenuChrome.upd(hBack, bHov);
        WindowMaterial.fieldBox(ms, bx, by, bs, bs, bt, false);
        ClickGuiIcons.arrowLeft(ms, bx + bs / 2f, by + bs / 2f, 16f, MenuChrome.lerp(MenuChrome.MUTED, MenuChrome.TEXT2, bt));
        MenuChrome.set(rBack, bx, by, bs, bs);

        float cx = bx + bs + 16f;
        Instance rf = Fonts.GOLOS.getFont(13f);
        // Крошка «Настройки» кликабельна и ведёт в хаб (спека 10c).
        float crumbW = rf.getWidth("Настройки");
        boolean crumbHov = MenuChrome.hit(mx, my, cx, cy - 9f, crumbW, 18f);
        Render2D.drawFont(ms, rf, "Настройки", cx, cy - rf.getHeight() / 2f,
                crumbHov ? MenuChrome.TEXT2 : MenuChrome.MUTED2);
        MenuChrome.set(rCrumb, cx, cy - 9f, crumbW, 18f);
        cx += crumbW + 8f;
        Render2D.drawFont(ms, rf, "/", cx, cy - rf.getHeight() / 2f, MenuChrome.MUTED4);
        cx += rf.getWidth("/") + 8f;
        Instance tf = Fonts.GOLOS_SEMI.getFont(17f);
        Render2D.drawFont(ms, tf, crumb, cx, cy - tf.getHeight() / 2f, MenuChrome.TEXT);

        // Поиск 250×36
        float sw = 250f, sh = 36f, sx = x + w - PAD_X - sw, sy = cy - sh / 2f;
        boolean sHov = MenuChrome.hit(mx, my, sx, sy, sw, sh);
        float st = MenuChrome.upd(hSearch, sHov || searchFocused);
        WindowMaterial.fieldBox(ms, sx, sy, sw, sh, st, false);
        ClickGuiIcons.search(ms, sx + 13f + 7f, sy + sh / 2f, 16f, MenuChrome.lerp(MenuChrome.MUTED3, MenuChrome.accent(), searchFocused ? 1f : 0f));
        Instance pf = Fonts.GOLOS.getFont(12.5f);
        float qx = sx + 13f + 14f + 10f;
        if (query.isEmpty() && !searchFocused) {
            Render2D.drawFont(ms, pf, "Поиск параметра...", qx, sy + sh / 2f - pf.getHeight() / 2f, MenuChrome.MUTED3);
        } else {
            Render2D.drawFont(ms, pf, query, qx, sy + sh / 2f - pf.getHeight() / 2f, MenuChrome.TEXT2);
            if (searchFocused) {
                // Каретка едет за текстом, а не прыгает: цель — конец строки, к ней плавно подтягиваемся.
                float target = qx + pf.getWidth(query) + 1f;
                if (caretX <= 0f) caretX = target;
                caretX += (target - caretX) * 0.4f;
                // Мигание синусом вместо щелчка по чётности полусекунды.
                float blink = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 260.0));
                Color caret = new Color(MenuChrome.TEXT2.getRed(), MenuChrome.TEXT2.getGreen(),
                        MenuChrome.TEXT2.getBlue(), Math.round(255 * blink));
                Render2D.drawRoundedRect(ms, caretX, sy + sh / 2f - 6f, 1f, 12f, 0.5f, caret);
            }
        }
        MenuChrome.set(rSearch, sx, sy, sw, sh);
    }

    // ---------- Строки параметров ----------
    private void drawRows(DrawContext context, Matrix3x2fStack ms, float x, float y, float w, float viewH, int mx, int my) {
        for (int i = 0; i < ctrlVisible.length; i++) ctrlVisible[i] = false;
        dropdownFor = closingDropdown ? dropdownClosingFor : -1;

        List<Integer> vis = new ArrayList<>();
        String q = query.trim().toLowerCase(Locale.ROOT);
        for (int i = 0; i < options.size(); i++) {
            if (q.isEmpty() || options.get(i).label.toLowerCase(Locale.ROOT).contains(q)) vis.add(i);
        }

        int rowsPerCol = (vis.size() + 1) / 2;
        float contentH = PAD_Y * 2f + rowsPerCol * ROW_H + Math.max(0, rowsPerCol - 1) * ROW_GAP;
        float maxScroll = Math.max(0f, contentH - viewH);

        long now = System.currentTimeMillis();
        float dt = lastFrameMs == 0L ? 0.016f : Math.min(0.05f, (now - lastFrameMs) / 1000f);
        lastFrameMs = now;
        scrollTarget = MathHelper.clamp(scrollTarget, 0f, maxScroll);
        scroll += (scrollTarget - scroll) * (1f - (float) Math.exp(-13f * dt));
        if (Math.abs(scrollTarget - scroll) < 0.4f) scroll = scrollTarget;

        float colW = (w - PAD_X * 2f - COL_GAP) / 2f;
        long nowA = System.currentTimeMillis();
        Render2D.startScissor(context, x, y, w, viewH);
        for (int k = 0; k < vis.size(); k++) {
            int oi = vis.get(k);
            int col = k % 2, row = k / 2;
            float rx = x + PAD_X + col * (colW + COL_GAP);
            float ry = y + PAD_Y + row * (ROW_H + ROW_GAP) - scroll;
            // Стаггер-появление: строки подъезжают снизу с задержкой по индексу.
            // Отсчёт от последней смены запроса: результаты поиска выезжают заново, а не подменяются.
            long from = Math.max(openMs, listAnimMs);
            float appear = MathHelper.clamp((nowA - from - k * 16L) / 220f, 0f, 1f);
            appear = 1f - (1f - appear) * (1f - appear);
            float ary = ry + (1f - appear) * 12f;
            if (ary + ROW_H < y || ary > y + viewH) continue;
            drawRow(ms, oi, options.get(oi), rx, ary, colW, mx, my);
        }
        Render2D.stopScissor(context);

        // Тонкий скроллбар
        if (maxScroll > 0.5f) {
            float trackH = viewH - 8f;
            float thumbH = Math.max(24f, trackH * (viewH / contentH));
            float thumbY = y + 4f + (trackH - thumbH) * (scroll / maxScroll);
            Render2D.drawRoundedRect(ms, x + w - 6f, thumbY, 3f, thumbH, 1.5f, MenuChrome.accentA(0.5f));
        }
    }

    private void drawRow(Matrix3x2fStack ms, int oi, SettingsOption o, float x, float y, float w, int mx, int my) {
        boolean hov = MenuChrome.hit(mx, my, x, y, w, ROW_H);
        // Наведение на строку — плавное: было мгновенное 0→1, из-за чего рамка щёлкала.
        float ht = MenuChrome.upd(hRow[oi], hov);
        Render2D.drawRoundedVerticalGradient(ms, x, y, w, ROW_H, 10f,
                new Color(24, 30, 27, 179), new Color(15, 19, 17, 179));
        Render2D.drawBorder(ms, x, y, w, ROW_H, 10f, 1.0f, 1.4f,
                MenuChrome.lerp(MenuChrome.white(0.08f), MenuChrome.white(0.22f), ht));

        Instance lf = Fonts.GOLOS.getFont(13f);
        Render2D.drawFont(ms, lf, o.label, x + 16f, y + ROW_H / 2f - lf.getHeight() / 2f, MenuChrome.TEXT2);

        float cy = y + ROW_H / 2f, right = x + w - 14f;
        switch (o.kind) {
            case TOGGLE -> {
                float tw = 32f, th = 18f, tx = right - tw, ty = cy - th / 2f;
                float target = o.state.getAsBoolean() ? 1f : 0f;
                toggleAnim[oi] += (target - toggleAnim[oi]) * 0.35f;
                if (Math.abs(target - toggleAnim[oi]) < 0.01f) toggleAnim[oi] = target;
                drawToggle(ms, tx, ty, tw, th, toggleAnim[oi]);
                MenuChrome.set(ctrlRect[oi], tx, ty, tw, th);
                ctrlVisible[oi] = true;
            }
            case SELECT -> {
                Instance vf = Fonts.GOLOS.getFont(12f);
                String val = o.value.get();
                boolean interactive = o.options != null;
                float ch = 28f, chevW = interactive ? 12f : 0f;
                float cw = 10f + vf.getWidth(val) + (interactive ? 7f + chevW : 0f) + 10f;
                float cx = right - cw, cyy = cy - ch / 2f;
                boolean open = openDropdown == oi;
                WindowMaterial.fieldBox(ms, cx, cyy, cw, ch, open ? 1f : 0f, false);
                Render2D.drawFont(ms, vf, val, cx + 10f, cyy + ch / 2f - vf.getHeight() / 2f,
                        interactive ? MenuChrome.TEXT2 : MenuChrome.TEXT3);
                if (interactive) {
                    ClickGuiIcons.chevronDown(ms, cx + 10f + vf.getWidth(val) + 7f + chevW / 2f, cyy + ch / 2f, chevW,
                            open ? MenuChrome.accent() : MenuChrome.MUTED3);
                    MenuChrome.set(ctrlRect[oi], cx, cyy, cw, ch);
                    ctrlVisible[oi] = true;
                    if (open) { dropdownFor = oi; dropdownX = cx; dropdownY = cyy; dropdownW = cw; }
                }
            }
            case ACTION -> {
                Instance vf = Fonts.GOLOS.getFont(12f);
                String val = o.value == null ? "" : o.value.get();
                float ch = 28f, chevW = 12f;
                float cw = 10f + vf.getWidth(val) + 7f + chevW + 10f;
                float cx = right - cw, cyy = cy - ch / 2f;
                WindowMaterial.fieldBox(ms, cx, cyy, cw, ch, hov ? 1f : 0f, false);
                Render2D.drawFont(ms, vf, val, cx + 10f, cyy + ch / 2f - vf.getHeight() / 2f, MenuChrome.TEXT2);
                ClickGuiIcons.arrowRight(ms, cx + 10f + vf.getWidth(val) + 7f + chevW / 2f, cyy + ch / 2f, chevW,
                        hov ? MenuChrome.accent() : MenuChrome.MUTED3);
                MenuChrome.set(ctrlRect[oi], cx, cyy, cw, ch);
                ctrlVisible[oi] = true;
            }
            case SLIDER -> {
                Instance vf = Fonts.GOLOS.getFont(11.5f);
                String val = o.value.get();
                float valW = Math.max(60f, vf.getWidth(val));
                Render2D.drawFont(ms, vf, val, right - vf.getWidth(val), cy - vf.getHeight() / 2f, MenuChrome.TEXT3);
                float trackRight = right - valW - 12f;
                float trackW = 150f, trackX = trackRight - trackW;

                if (draggingSlider == oi) {
                    long handle = mc.getWindow().getHandle();
                    if (GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS) {
                        o.setFrac.accept(MathHelper.clamp((mx - trackX) / trackW, 0f, 1f));
                    } else {
                        draggingSlider = -1;
                    }
                }
                // Заливка и кноб догоняют значение, а не телепортируются: шаговые опции (прорисовка,
                // guiScale) прыгали на целый шаг за кадр.
                float actual = (float) MathHelper.clamp(o.frac.getAsDouble(), 0.0, 1.0);
                sliderAnim[oi] += (actual - sliderAnim[oi]) * 0.4f;
                if (Math.abs(actual - sliderAnim[oi]) < 0.002f) sliderAnim[oi] = actual;
                float frac = sliderAnim[oi];
                float ty = cy - 2f;
                Render2D.drawRoundedRect(ms, trackX, ty, trackW, 4f, 2f, MenuChrome.white(0.09f));
                if (frac > 0.001f) {
                    MenuChrome.glow(ms, trackX, ty, trackW * frac, 4f, 2f, MenuChrome.accentA(0.5f), 0f, 3, 1.3f);
                    Render2D.drawRoundedRect(ms, trackX, ty, trackW * frac, 4f, 2f, MenuChrome.accent());
                }
                float knobX = trackX + trackW * frac;
                Render2D.drawCircle(ms, knobX, cy, 6f, new Color(0, 0, 0, 90));
                Render2D.drawCircle(ms, knobX, cy, 6f, MenuChrome.TEXT);
                MenuChrome.set(ctrlRect[oi], trackX - 6f, cy - 11f, trackW + 12f, 22f);
                ctrlVisible[oi] = true;
            }
        }
    }

    /** Выпадающий список вариантов селекта — поверх всего, с анимацией раскрытия и флипом вверх у низа. */
    /** Запускает схлопывание вместо мгновенного исчезновения списка. */
    private void closeDropdown() {
        if (openDropdown < 0) return;
        dropdownClosingFor = openDropdown;
        closingDropdown = true;
        openDropdown = -1;
    }

    /** При раскрытии сразу показываем выбранный вариант — иначе в длинном списке его не видно. */
    private void scrollDropdownToCurrent(SettingsOption o) {
        dropdownScroll = 0f;
        dropdownScrollTarget = 0f;
        if (o.options == null || o.value == null) return;
        List<String> opts = o.options.get();
        String cur = o.value.get();
        int index = opts.indexOf(cur);
        if (index < DROPDOWN_ROWS) return;
        float target = (index - DROPDOWN_ROWS / 2f) * 26f;
        dropdownScroll = target;
        dropdownScrollTarget = target;
    }

    private void drawDropdown(DrawContext context, Matrix3x2fStack ms, int mx, int my) {
        SettingsOption o = options.get(dropdownFor);
        List<String> opts = o.options.get();
        // Раскрытие и схлопывание — одной анимацией: цель 1 пока список открыт, 0 когда закрывается.
        float target = closingDropdown ? 0f : 1f;
        dropdownAnim += (target - dropdownAnim) * 0.35f;
        if (Math.abs(target - dropdownAnim) < 0.005f) dropdownAnim = target;
        if (closingDropdown && dropdownAnim <= 0.001f) {
            closingDropdown = false;
            dropdownClosingFor = -1;
            dropdownRects.clear();
            dropdownIdx.clear();
            return;
        }

        Instance vf = Fonts.GOLOS.getFont(12f);
        float rowH = 26f, pad = 4f;
        float widest = 0f;
        for (String s : opts) widest = Math.max(widest, vf.getWidth(s));
        float dw = Math.max(dropdownW, widest + 24f);
        // Список вариантов бывает длинным (видеорежимы монитора — под сорок штук): панель ограничена
        // DROPDOWN_ROWS строками, остальное прокручивается колесом.
        int visibleRows = Math.min(opts.size(), DROPDOWN_ROWS);
        float fullH = visibleRows * rowH + pad * 2f;
        float listH = opts.size() * rowH;
        dropdownMaxScroll = Math.max(0f, listH - visibleRows * rowH);
        dropdownScrollTarget = MathHelper.clamp(dropdownScrollTarget, 0f, dropdownMaxScroll);
        dropdownScroll += (dropdownScrollTarget - dropdownScroll) * 0.35f;
        if (Math.abs(dropdownScrollTarget - dropdownScroll) < 0.4f) dropdownScroll = dropdownScrollTarget;
        float dh = fullH * dropdownAnim;

        float chipH = 28f;
        float belowY = dropdownY + chipH + 3f;
        float aboveY = dropdownY - 3f - fullH;
        float winBottom = WIN_TOP + winH();
        float dy = (belowY + fullH > winBottom - 10f && aboveY > WIN_TOP + TOPBAR_H) ? aboveY : belowY;
        // Панель прижата ПРАВЫМ краем к правому краю чипа и раскрывается влево: dw >= ширины чипа,
        // и рост вправо пробивал границу окна у правой колонки (длинные видеорежимы).
        float dx = dropdownX + dropdownW - dw;
        MenuChrome.set(rDropdown, dx, dy, dw, dh);

        MenuChrome.glow(ms, dx, dy, dw, dh, 8f, new Color(0, 0, 0, 120), 2f, 5, 2.2f);
        Render2D.drawRoundedRect(ms, dx, dy, dw, dh, 8f, new Color(18, 24, 20, 250));
        Render2D.drawBorder(ms, dx, dy, dw, dh, 8f, 1.0f, 1.4f, MenuChrome.white(0.14f));

        dropdownRects.clear();
        dropdownIdx.clear();
        if (dropdownAnim < 0.55f) return; // строки появляются после раскрытия панели

        String cur = o.value.get();
        float viewTop = dy + pad, viewH = visibleRows * rowH;
        // startScissor ждёт ДИЗАЙН-координаты: матрицу масштаба он применяет сам.
        // Ножницы — по ФАКТИЧЕСКОЙ высоте панели (dh растёт с анимацией): иначе во время
        // раскрытия/схлопывания строки висели «в воздухе» под ещё не выросшим фоном.
        Render2D.startScissor(context, dx, viewTop, dw, Math.max(0f, Math.min(viewH, dh - pad * 2f)));
        for (int i = 0; i < opts.size(); i++) {
            float ry = viewTop + i * rowH - dropdownScroll;
            if (ry + rowH < viewTop || ry > viewTop + viewH) continue;
            boolean hov = MenuChrome.hit(mx, my, dx + pad, ry, dw - pad * 2f, rowH);
            boolean sel = opts.get(i).equals(cur);
            if (hov) Render2D.drawRoundedRect(ms, dx + pad, ry, dw - pad * 2f, rowH, 6f, MenuChrome.accentA(0.12f));
            else if (sel) Render2D.drawRoundedRect(ms, dx + pad, ry, dw - pad * 2f, rowH, 6f, MenuChrome.accentA(0.06f));
            Color c = sel ? MenuChrome.accent() : (hov ? MenuChrome.TEXT : MenuChrome.TEXT2);
            Render2D.drawFont(ms, vf, opts.get(i), dx + 12f, ry + rowH / 2f - vf.getHeight() / 2f, c);
            // Хитбокс режем по видимой области: ножницы обрезают только пиксели, не клики.
            float hitTop = Math.max(ry, viewTop), hitBot = Math.min(ry + rowH, viewTop + viewH);
            if (hitBot - hitTop < 1f) continue;
            float[] r = new float[4];
            MenuChrome.set(r, dx + pad, hitTop, dw - pad * 2f, hitBot - hitTop);
            dropdownRects.add(r);
            dropdownIdx.add(i);
        }
        Render2D.stopScissor(context);

        if (dropdownAnim > 0.95f && dropdownMaxScroll > 0.5f) {
            float trackH = viewH - 6f;
            float thumbH = Math.max(20f, trackH * (viewH / listH));
            float thumbY = viewTop + 3f + (trackH - thumbH) * (dropdownScroll / dropdownMaxScroll);
            Render2D.drawRoundedRect(ms, dx + dw - 6f, thumbY, 3f, thumbH, 1.5f, MenuChrome.accentA(0.5f));
        }
    }

    /** Тоггл 32×18 (как в ClickGUI): ON — акцентный градиент+glow+блик, OFF — тёмный трек; кноб лерпается. */
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
        float bx = x + w - PAD_X - bw, by = cy - bh / 2f - 9f; // «Готово» ещё на 3px выше (суммарно 6)
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

    // ---------- Ввод ----------
    @Override
    protected boolean contentClick(float mx, float my, int button) {
        if (button != 0) return false;

        // Открытый дропдаун обрабатываем первым.
        if (openDropdown >= 0) {
            for (int k = 0; k < dropdownRects.size(); k++) {
                if (MenuChrome.in(dropdownRects.get(k), mx, my)) {
                    SettingsOption o = options.get(openDropdown);
                    // Индекс берём из dropdownIdx, а не k: при прокрутке видимые строки смещены,
                    // и k — это номер видимой строки, а не варианта.
                    if (o.pick != null) o.pick.accept(dropdownIdx.get(k));
                    closeDropdown();
                    return true;
                }
            }
            int wasOpen = openDropdown;
            closeDropdown();
            // Клик по тому же чипу — закрыли; по другому месту — падаем ниже (переоткроем/переключим).
            if (wasOpen < ctrlRect.length && ctrlVisible[wasOpen] && MenuChrome.in(ctrlRect[wasOpen], mx, my)) return true;
        }

        searchFocused = MenuChrome.in(rSearch, mx, my);
        if (searchFocused) return true;
        if (MenuChrome.in(rBack, mx, my) || MenuChrome.in(rCrumb, mx, my)) { back(); return true; }
        if (MenuChrome.in(rDone, mx, my)) { mc.options.write(); back(); return true; }
        for (int i = 0; i < options.size(); i++) {
            if (!ctrlVisible[i] || !MenuChrome.in(ctrlRect[i], mx, my)) continue;
            SettingsOption o = options.get(i);
            switch (o.kind) {
                case TOGGLE, ACTION -> o.toggle.run();
                case SELECT -> {
                    openDropdown = i;
                    closingDropdown = false;
                    dropdownClosingFor = -1;
                    dropdownAnim = 0f;
                    scrollDropdownToCurrent(o);
                }
                case SLIDER -> draggingSlider = i;
            }
            return true;
        }
        return false;
    }

    @Override
    protected void contentRelease(float mx, float my, int button) {
        if (button == 0) draggingSlider = -1;
    }

    @Override
    protected boolean contentScroll(float mx, float my, double vertical) {
        // Колесо над раскрытым списком листает ЕГО, а не закрывает: у длинных списков (видеорежимы)
        // иначе до нижних вариантов вообще не добраться.
        if (openDropdown >= 0 && MenuChrome.in(rDropdown, mx, my)) {
            dropdownScrollTarget = MathHelper.clamp(
                    dropdownScrollTarget - (float) vertical * 26f * 2f, 0f, dropdownMaxScroll);
            return true;
        }
        closeDropdown();
        scrollTarget -= (float) vertical * (ROW_H + ROW_GAP) * 0.9f;
        return true;
    }

    @Override
    protected boolean contentKey(int keyCode, int scanCode, int modifiers) {
        if (searchFocused) {
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
        if (searchFocused && chr >= ' ') { query += chr; onQueryChanged(); return true; }
        return false;
    }

    /** Смена запроса: перезапускаем появление списка и щёлкаем «печатной машинкой», как в SearchBar. */
    private void onQueryChanged() {
        listAnimMs = System.currentTimeMillis();
        try {
            if (mc.getSoundManager() == null) return;
            float pitch = 1.7f + (float) (Math.random() * 0.35);
            mc.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.ui(
                    net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK.value(), pitch, 0.25f));
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void back() {
        mc.options.write();
        super.back();
    }
}
