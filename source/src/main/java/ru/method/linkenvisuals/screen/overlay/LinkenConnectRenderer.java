package ru.method.linkenvisuals.screen.overlay;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix3x2fStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import ru.method.linkenvisuals.screen.mainmenu.MenuChrome;
import ru.method.linkenvisuals.screen.multiplayer.WindowMaterial;
import ru.method.linkenvisuals.utils.render.Render2D;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;
import ru.method.linkenvisuals.utils.render.fonts.Instance;

import java.awt.Color;

/**
 * 9e — рескин экрана «Подключение к серверу». Размытый текущий кадр + карточка со спиннером, именем/адресом
 * сервера, бегущим баром, реальным этапом и «Отмена». Только рисование; жизненный цикл подключения и ввод
 * (клик/ESC) — в {@code ConnectScreenMixin}. Хит-бокс «Отмены» и масштаб экспонируются статически для миксина.
 */
public final class LinkenConnectRenderer {

    private LinkenConnectRenderer() {}

    /** Последний хит-бокс «Отмены» (в дизайн-координатах) и масштаб — читает миксин для обработки клика. */
    public static final float[] CANCEL_RECT = new float[4];
    public static float uiScale = 1f;

    // ─── Цель подключения и признак «идёт вход на сервер» ───
    // Живут здесь, а не в ConnectScreenMixin, потому что окно должно пережить смену экрана: после
    // ConnectScreen идут DownloadingTerrainScreen и ReconfiguringScreen, и им нужны те же имя/адрес.
    private static String targetName;
    private static String targetAddress;
    private static boolean joining;

    public static void beginJoin(String name, String address) {
        targetName = name;
        targetAddress = address;
        joining = true;
    }

    /** Вход завершён (или сорван) — дальше экраны рисуются ванилью. */
    public static void endJoin() {
        joining = false;
    }

    public static String targetName() {
        return targetName;
    }

    public static String targetAddress() {
        return targetAddress;
    }

    /**
     * Должен ли этот экран быть закрыт нашим окном подключения. Ванильная цепочка входа на сервер —
     * это НЕСКОЛЬКО экранов подряд: ConnectScreen → (ReconfiguringScreen) → DownloadingTerrainScreen,
     * плюс промежуточные MessageScreen. Свой рескин только первого из них и давал «дальше ваниль».
     */
    public static boolean covers(net.minecraft.client.gui.screen.Screen screen) {
        if (!joining || screen == null) return false;
        return screen instanceof net.minecraft.client.gui.screen.world.LevelLoadingScreen
                || screen instanceof net.minecraft.client.gui.screen.ReconfiguringScreen
                || screen instanceof net.minecraft.client.gui.screen.MessageScreen;
    }

    /** Этап берём из заголовка самого экрана — это реальный локализованный текст ванили. */
    public static Text stageOf(net.minecraft.client.gui.screen.Screen screen) {
        Text title = screen == null ? null : screen.getTitle();
        if (title == null || title.getString().isBlank()) return Text.literal("Вход в мир");
        return title;
    }

    public static void render(DrawContext context, int mouseX, int mouseY, Text status, String name, String address) {
        render(context, mouseX, mouseY, status, name, address, true);
    }

    /**
     * @param showCancel рисовать ли «Отмена». На поздних этапах (загрузка территории) ваниль отмену не
     *                   предлагает и ESC не обрабатывает — рисовать мёртвую кнопку нельзя.
     */
    public static void render(DrawContext context, int mouseX, int mouseY, Text status, String name, String address,
                              boolean showCancel) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null) return;
        // The donor renderer draws through Render2D's matrix-stack overloads.
        // In 1.21.11 those resolve DrawContext through Render2D.activeContext,
        // which is only set by prepareHudFrame(). Without this call every
        // logo/window/progress draw is a silent no-op and the connect screen
        // stays black (the reported loading-screen bug).
        Render2D.prepareHudFrame(context);
        ru.method.linkenvisuals.utils.render.LinkGuiRenderContext.resetFade();
        // HiDPI-safe: от GUI-scaled размеров (getHeight() — логический размер окна, scaleFactor — от
        // фреймбуфера; на Retina давало uiScale вдвое меньше). См. CustomMainMenuScreen.
        uiScale = Math.max(0.05f, mc.getWindow().getScaledHeight() / MenuChrome.DESIGN_HEIGHT);
        int vw = Math.max(1, Math.round(mc.getWindow().getScaledWidth() / uiScale));
        int vh = Math.max(1, Math.round(mc.getWindow().getScaledHeight() / uiScale));
        int mx = Math.round(mouseX / uiScale), my = Math.round(mouseY / uiScale);
        long now = System.currentTimeMillis();

        Matrix3x2fStack ms = context.getMatrices();
        ms.pushMatrix();
        ms.scale(uiScale, uiScale);
        ru.method.linkenvisuals.screen.clickgui.redesign.ClickGuiIcons.pixelScale(uiScale);

        MenuChrome.drawScreenBackground(ms, vw, vh, 0f, 0f);
        MenuChrome.drawLogo(ms, vw);
        MenuChrome.drawClock(ms, vw);
        MenuChrome.drawFooterSignature(ms, vw, vh, false);

        float cw = 420f;
        // Без «Отмены» карточка ниже ровно на кнопку с отступом — иначе снизу зияет пустое поле.
        float ch = showCancel ? 268f : 268f - 62f;
        float cx = vw / 2f - cw / 2f;
        float cy = vh / 2f - ch * 0.54f;

        WindowMaterial.window(ms, cx, cy, cw, ch);

        float centerX = cx + cw / 2f;
        float y = cy + 30f;

        // 1. Спиннер 44×44
        drawSpinner(ms, centerX, y + 22f, now);
        y += 44f + 18f;

        // 2. Заголовок
        Instance tf = Fonts.GOLOS_SEMI.getFont(16f);
        String title = "Подключение к серверу";
        Render2D.drawFont(ms, tf, title, centerX - tf.getWidth(title) / 2f, y, MenuChrome.TEXT);
        y += tf.getHeight() + 9f;

        // 3. Строка сервера: имя · адрес
        Instance nf = Fonts.UNBOUNDED.getFont(12f);
        Instance af = Fonts.PLEXMONO.getFont(12.5f);
        String nm = cleanServerName(name, address);
        String ad = address == null ? "" : address;
        // Имя дублирует адрес (в списке серверов именем часто сохранён сам хост) — адрес не повторяем.
        boolean showAddress = !ad.isEmpty() && !nm.toLowerCase(java.util.Locale.ROOT)
                .contains(ad.toLowerCase(java.util.Locale.ROOT).replaceAll(":\\d+$", ""));
        // Имя не должно выталкивать строку за карточку: режем с многоточием по остатку ширины.
        float maxRowW = cw - 56f;
        float dotW = showAddress ? 10f : 0f;
        float adW = showAddress ? af.getWidth(ad) : 0f;
        nm = clipText(nf, nm, maxRowW - dotW - adW);
        float rowW = nf.getWidth(nm) + dotW + adW;
        float rx = centerX - rowW / 2f;
        Render2D.drawFont(ms, nf, nm, rx, y, MenuChrome.TEXT3);
        if (showAddress) {
            rx += nf.getWidth(nm) + dotW / 2f - 1f;
            Render2D.drawRoundedRect(ms, rx, y + af.getHeight() / 2f - 1.5f, 3f, 3f, 1.5f, MenuChrome.MUTED4);
            rx += dotW / 2f + 1f;
            Render2D.drawFont(ms, af, ad, rx, y, MenuChrome.MUTED2);
        }
        y += Math.max(nf.getHeight(), af.getHeight()) + 20f;

        // 4. Индетерминированный бар
        float bw = cw - 56f, bx = centerX - bw / 2f;
        Render2D.drawRoundedRect(ms, bx, y, bw, 3f, 1.5f, MenuChrome.white(0.08f));
        float segW = bw * 0.38f;
        float p = (now % 1400L) / 1400f;
        float e = p < 0.5f ? 2f * p * p : 1f - (float) Math.pow(-2f * p + 2f, 2) / 2f; // easeInOut
        float segX = bx + (bw - segW) * e;
        MenuChrome.glow(ms, segX, y, segW, 3f, 1.5f, MenuChrome.accentA(0.7f), 0f, 3, 1.3f);
        Render2D.drawRoundedRect(ms, segX, y, segW, 3f, 1.5f, MenuChrome.accent());
        y += 3f + 9f;

        // 5. Этап (реальный статус из ConnectScreen)
        Instance sf = Fonts.GOLOS.getFont(12f);
        String stage = status == null ? "" : status.getString();
        Render2D.drawFont(ms, sf, stage, centerX - sf.getWidth(stage) / 2f, y, MenuChrome.MUTED2);
        y += sf.getHeight() + 20f;

        // 6. «Отмена» (secondary, hover — красная рамка)
        if (showCancel) {
            float bh = 42f, ox = cx + 28f, ow = cw - 56f, oy = y;
            boolean hov = MenuChrome.hit(mx, my, ox, oy, ow, bh);
            MenuChrome.drawSecondaryMaterial(ms, ox, oy, ow, bh, 8f, hov ? 1f : 0f, true);
            Instance cf = Fonts.UNBOUNDED.getFont(11f);
            String cancel = "Отмена";
            float icon = 14f;
            float content = icon + 9f + cf.getWidth(cancel);
            float ix = ox + (ow - content) / 2f;
            ru.method.linkenvisuals.screen.clickgui.redesign.ClickGuiIcons.close(ms, ix + icon / 2f, oy + bh / 2f, icon, MenuChrome.MUTED);
            Render2D.drawFont(ms, cf, cancel, ix + icon + 9f, oy + bh / 2f - cf.getHeight() / 2f, MenuChrome.TEXT2);
            MenuChrome.set(CANCEL_RECT, ox, oy, ow, bh);
        } else {
            // Хит-бокс обнуляем, иначе клик по пустому месту сработал бы по «призраку» прошлого кадра.
            MenuChrome.set(CANCEL_RECT, 0f, 0f, 0f, 0f);
        }

        WindowMaterial.corners(ms, cx, cy, cw, ch);

        ms.popMatrix();
        ru.method.linkenvisuals.screen.clickgui.redesign.ClickGuiIcons.pixelScale(1f);
    }

    /** Спиннер: подложка-кольцо + вращающаяся акцентная дуга (~110°) + центральная точка. */
    /**
     * Имя сервера для карточки. В списке серверов именем часто сохранена рекламная строка вида
     * «host (12+,erid:...)» — маркировку в скобках отрезаем, пустое имя заменяем адресом без порта.
     */
    private static String cleanServerName(String name, String address) {
        String nm = name == null ? "" : name.trim();
        nm = nm.replaceAll("\\s*\\((?:[^)]*erid[^)]*|\\d{1,2}\\+[^)]*)\\)\\s*$", "").trim();
        if (nm.isEmpty()) {
            String host = address == null ? "" : address.replaceAll(":\\d+$", "");
            return host.isEmpty() ? "Сервер" : host;
        }
        return nm;
    }

    private static String clipText(Instance f, String s, float maxW) {
        if (maxW <= 0f || f.getWidth(s) <= maxW) return s;
        int lo = 0, hi = s.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (f.getWidth(s.substring(0, mid) + "…") <= maxW) lo = mid; else hi = mid - 1;
        }
        return s.substring(0, lo) + "…";
    }

    private static void drawSpinner(Matrix3x2fStack ms, float cx, float cy, long now) {
        float r = 18f, th = 3f;
        Render2D.drawRing(ms, cx, cy, r, th, MenuChrome.white(0.09f));
        float base = (now % 1100L) / 1100f * 360f;
        float span = 110f;
        // Дуга — перекрывающимися кружками вдоль пути (шаг ~2px), а не ломаной из drawLine:
        // стыки сегментов давали зазубрины «пиксельной» дуги, круг же рисуется шейдером гладко,
        // и концы дуги выходят закруглёнными сами собой.
        java.awt.Color accent = MenuChrome.accent();
        float arcLen = (float) (Math.toRadians(span) * r);
        // Шаг ~1px: при шаге 2px мягкая кромка кружков радиуса 1.5 не смыкалась и дуга читалась бусами.
        int dots = Math.max(24, Math.round(arcLen));
        for (int i = 0; i <= dots; i++) {
            double a = Math.toRadians(base + span * i / dots);
            // Хвост дуги плавно тает — как у нормального «материального» спиннера.
            float fade = 0.35f + 0.65f * (i / (float) dots);
            Render2D.drawCircle(ms,
                    cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r,
                    th / 2f, MenuChrome.accentA(fade));
        }
        Render2D.drawCircle(ms, cx, cy, 3f, accent);
    }
}
