package ru.method.linkenvisuals.screen.settings;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.joml.Matrix3x2fStack;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import ru.method.linkenvisuals.screen.clickgui.redesign.ClickGuiIcons;
import ru.method.linkenvisuals.screen.mainmenu.MenuChrome;
import ru.method.linkenvisuals.screen.multiplayer.WindowMaterial;
import ru.method.linkenvisuals.utils.animations.Animation;
import ru.method.linkenvisuals.utils.render.ColorRGBA;
import ru.method.linkenvisuals.utils.render.Render2D;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;
import ru.method.linkenvisuals.utils.render.fonts.Instance;

import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 9c — «Наборы ресурсов»: две колонки строк-плиток («Доступны» / «Выбраны») со стрелками переноса и
 * стрелками порядка в правой колонке. Выбор применяется по «Готово»: {@code setEnabledProfiles} +
 * {@code options.refreshResourcePacks} (последний сам пишет options и дёргает reloadResources).
 */
public final class PacksListScreen extends LinkenSettingsScreen {

    private static final float WIN_W = 1060f, WIN_TOP = 130f, PAD_X = 22f;
    private static final float TOPBAR_H = 64f, FOOTER_H = 64f;
    private static final float ROW_H = 56f, ROW_GAP = 8f, COL_GAP = 14f, PAD_Y = 20f, HEAD_H = 28f;
    private static final float BTN = 26f, BTN_GAP = 6f;

    private static final String CRUMB = "Наборы ресурсов";

    private final Animation hBack = MenuChrome.anim(), hDone = MenuChrome.anim(), hFolder = MenuChrome.anim();
    private final float[] rBack = new float[4], rDone = new float[4], rFolder = new float[4];

    private final List<ResourcePackProfile> available = new ArrayList<>();
    private final List<ResourcePackProfile> selected = new ArrayList<>();
    /** Скрытые включённые паки Fabric — в UI не показываются, но при применении остаются включёнными. */
    private final List<ResourcePackProfile> hiddenEnabled = new ArrayList<>();

    // Кликабельные зоны кадра: {x, y, w, h, индекс строки}. Пересобираются каждый рендер.
    private final List<float[]> hitEnable = new ArrayList<>();
    private final List<float[]> hitDisable = new ArrayList<>();
    private final List<float[]> hitUp = new ArrayList<>();
    private final List<float[]> hitDown = new ArrayList<>();

    private float scrollL, scrollLTarget, scrollR, scrollRTarget;
    /** Границы области списков — по ним отсекаем клики по хит-ректам, которые обрезаны ножницами. */
    private float listTop, listBottom;
    private long lastFrameMs;

    public PacksListScreen(Screen parent) {
        super(parent, "Настройки — " + CRUMB);
        reload();
    }

    private void reload() {
        available.clear();
        selected.clear();
        hiddenEnabled.clear();
        ResourcePackManager m = mc.getResourcePackManager();
        m.scanPacks();
        for (ResourcePackProfile p : m.getEnabledProfiles()) {
            if (isHiddenPack(p)) hiddenEnabled.add(p); else selected.add(p);
        }
        // getEnabledProfiles отдаёт порядок ЗАГРУЗКИ: индекс 0 — низший приоритет. В колонке
        // «Выбраны» сверху должен быть самый приоритетный пак, поэтому разворачиваем.
        java.util.Collections.reverse(selected);
        for (ResourcePackProfile p : m.getProfiles()) {
            if (isHiddenPack(p)) continue;
            if (!selected.contains(p)) available.add(p);
        }
    }

    private static java.lang.reflect.Method fabricIsHidden;
    private static boolean fabricIsHiddenLookupDone;

    /**
     * Служебные паки Fabric (по одному на каждый мод: fabric-api-base, fabric-block-api-v1, …).
     * Ваниль их не показывает: Fabric помечает такие профили своим injected-интерфейсом
     * {@code FabricResourcePackProfile.fabric_isHidden()} и фильтрует СВОИМ миксином в ванильном
     * PackScreen — наш экран тот миксин обходит, поэтому фильтруем сами. Рефлексия, а не импорт
     * impl-класса Fabric: пакет impl нестабилен между версиями fabric-api. Скрытые паки при
     * применении ОБЯЗАНЫ остаться включёнными (см. apply) — это ресурсы модов.
     */
    private static boolean isHiddenPack(ResourcePackProfile p) {
        // Агрегат «Моды Fabric» (id "fabric") — служебный: выключать его нельзя, показывать смысла нет.
        if ("fabric".equals(p.getId())) return true;
        if (!fabricIsHiddenLookupDone) {
            fabricIsHiddenLookupDone = true;
            try {
                fabricIsHidden = p.getClass().getMethod("fabric_isHidden");
            } catch (Throwable ignored) {
                // Fabric-интерфейса нет (изменилось API) — не скрываем ничего, это безопасный отказ.
            }
        }
        if (fabricIsHidden == null) return false;
        try {
            return (boolean) fabricIsHidden.invoke(p);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static final net.minecraft.util.Identifier UNKNOWN_PACK =
            net.minecraft.util.Identifier.ofVanilla("textures/misc/unknown_pack.png");
    /** id пака → текстура его pack.png; грузится лениво один раз за жизнь экрана. */
    private final java.util.Map<String, net.minecraft.util.Identifier> iconCache = new java.util.HashMap<>();

    /**
     * Иконка пака — как ванильный {@code PackScreen.loadPackIcon}: открыть пак, прочитать pack.png
     * из корня, залить динамической текстурой; при любом сбое — ванильная unknown_pack.
     */
    private net.minecraft.util.Identifier packIcon(ResourcePackProfile profile) {
        return iconCache.computeIfAbsent(profile.getId(), id -> {
            try (net.minecraft.resource.ResourcePack pack = profile.createResourcePack()) {
                net.minecraft.resource.InputSupplier<java.io.InputStream> supplier = pack.openRoot("pack.png");
                if (supplier == null) return UNKNOWN_PACK;
                try (java.io.InputStream in = supplier.get()) {
                    net.minecraft.client.texture.NativeImage image = net.minecraft.client.texture.NativeImage.read(in);
                    net.minecraft.util.Identifier tex = ru.method.linkenvisuals.LinkenVisuals.id(
                            "packicon/" + id.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_"));
                    mc.getTextureManager().registerTexture(tex,
                            new net.minecraft.client.texture.NativeImageBackedTexture(() -> "pack_icon", image));
                    return tex;
                }
            } catch (Throwable t) {
                return UNKNOWN_PACK;
            }
        });
    }

    @Override
    public void removed() {
        // Текстуры иконок живут только пока открыт экран — иначе каждая переоткрытая вкладка
        // копила бы по картинке на пак.
        for (net.minecraft.util.Identifier tex : iconCache.values()) {
            if (tex != UNKNOWN_PACK) {
                try {
                    mc.getTextureManager().destroyTexture(tex);
                } catch (Throwable ignored) {
                }
            }
        }
        iconCache.clear();
        super.removed();
    }

    private float winH() {
        int rows = Math.max(available.size(), selected.size());
        float desired = TOPBAR_H + PAD_Y * 2f + HEAD_H
                + rows * ROW_H + Math.max(0, rows - 1) * ROW_GAP + FOOTER_H;
        float maxH = vh - WIN_TOP - 74f;
        return Math.min(desired, Math.max(TOPBAR_H + HEAD_H + ROW_H + FOOTER_H, maxH));
    }

    @Override
    protected void drawContent(DrawContext context, Matrix3x2fStack ms, int mx, int my) {
        float x = winX(WIN_W), y = WIN_TOP, w = WIN_W, h = winH();
        WindowMaterial.window(ms, x, y, w, h);

        drawTopbar(ms, x, y, w, mx, my);
        Render2D.drawRoundedRect(ms, x, y + TOPBAR_H - 1f, w, 1f, 0f, MenuChrome.white(0.07f));

        drawColumns(context, ms, x, y + TOPBAR_H, w, h - TOPBAR_H - FOOTER_H, mx, my);
        drawFooter(ms, x, y + h - FOOTER_H, w, mx, my);

        WindowMaterial.corners(ms, x, y, w, h);
    }

    // ---------- Топбар: ‹ + крошка ----------
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
    }

    // ---------- Две колонки ----------
    private void drawColumns(DrawContext context, Matrix3x2fStack ms, float x, float y, float w, float areaH, int mx, int my) {
        hitEnable.clear();
        hitDisable.clear();
        hitUp.clear();
        hitDown.clear();

        long now = System.currentTimeMillis();
        float dt = lastFrameMs == 0L ? 0.016f : Math.min(0.05f, (now - lastFrameMs) / 1000f);
        lastFrameMs = now;
        float k = 1f - (float) Math.exp(-13f * dt);

        float colW = (w - PAD_X * 2f - COL_GAP) / 2f;
        float headY = y + PAD_Y;
        float listY = headY + HEAD_H;
        float viewH = areaH - PAD_Y * 2f - HEAD_H;
        listTop = listY;
        listBottom = listY + viewH;

        float lx = x + PAD_X;
        float rx = lx + colW + COL_GAP;

        drawHeader(ms, lx, headY, colW, "Доступны", available.size());
        drawHeader(ms, rx, headY, colW, "Выбраны", selected.size());

        scrollLTarget = MathHelper.clamp(scrollLTarget, 0f, maxScroll(available.size(), viewH));
        scrollL += (scrollLTarget - scrollL) * k;
        if (Math.abs(scrollLTarget - scrollL) < 0.4f) scrollL = scrollLTarget;

        scrollRTarget = MathHelper.clamp(scrollRTarget, 0f, maxScroll(selected.size(), viewH));
        scrollR += (scrollRTarget - scrollR) * k;
        if (Math.abs(scrollRTarget - scrollR) < 0.4f) scrollR = scrollRTarget;

        drawList(context, ms, lx, listY, colW, viewH, mx, my, true);
        drawList(context, ms, rx, listY, colW, viewH, mx, my, false);
    }

    private static float maxScroll(int count, float viewH) {
        float contentH = count * ROW_H + Math.max(0, count - 1) * ROW_GAP;
        return Math.max(0f, contentH - viewH);
    }

    private void drawHeader(Matrix3x2fStack ms, float x, float y, float w, String title, int count) {
        Instance hf = Fonts.GOLOS_SEMI.getFont(12.5f);
        Render2D.drawFont(ms, hf, title, x + 2f, y + 2f, MenuChrome.TEXT3);
        Instance cf = Fonts.GOLOS.getFont(11.5f);
        String n = String.valueOf(count);
        Render2D.drawFont(ms, cf, n, x + w - 2f - cf.getWidth(n), y + 3f, MenuChrome.MUTED3);
        Render2D.drawRoundedRect(ms, x, y + HEAD_H - 10f, w, 1f, 0f, MenuChrome.white(0.06f));
    }

    private void drawList(DrawContext context, Matrix3x2fStack ms, float x, float y, float w, float viewH,
                          int mx, int my, boolean left) {
        List<ResourcePackProfile> list;
        float scroll;
        if (left) { list = available; scroll = scrollL; } else { list = selected; scroll = scrollR; }

        long nowA = System.currentTimeMillis();
        Render2D.startScissor(context, x, y, w, viewH);
        for (int i = 0; i < list.size(); i++) {
            float ry = y + i * (ROW_H + ROW_GAP) - scroll;
            float appear = MathHelper.clamp((nowA - openMs - i * 16L) / 220f, 0f, 1f);
            appear = 1f - (1f - appear) * (1f - appear);
            float ary = ry + (1f - appear) * 12f;
            if (ary + ROW_H < y || ary > y + viewH) continue;
            drawRow(ms, list.get(i), i, x, ary, w, mx, my, left);
        }
        Render2D.stopScissor(context);

        float max = maxScroll(list.size(), viewH);
        if (max > 0.5f) {
            float contentH = list.size() * ROW_H + Math.max(0, list.size() - 1) * ROW_GAP;
            float trackH = viewH - 8f;
            float thumbH = Math.max(24f, trackH * (viewH / contentH));
            float thumbY = y + 4f + (trackH - thumbH) * (scroll / max);
            Render2D.drawRoundedRect(ms, x + w - 4f, thumbY, 3f, thumbH, 1.5f, MenuChrome.accentA(0.5f));
        }
    }

    private void drawRow(Matrix3x2fStack ms, ResourcePackProfile p, int idx, float x, float y, float w,
                         int mx, int my, boolean left) {
        boolean hov = MenuChrome.hit(mx, my, x, y, w, ROW_H);
        Render2D.drawRoundedVerticalGradient(ms, x, y, w, ROW_H, 10f,
                new Color(24, 30, 27, 179), new Color(15, 19, 17, 179));
        Render2D.drawBorder(ms, x, y, w, ROW_H, 10f, 1.0f, 1.4f,
                MenuChrome.lerp(MenuChrome.white(0.08f), MenuChrome.white(0.22f), hov ? 1f : 0f));

        // Иконка пака 34×34: pack.png из корня пака (как ванильный PackScreen.loadPackIcon),
        // с фолбэком на ванильную unknown_pack.
        float it = 34f, ix = x + 11f, iy = y + (ROW_H - it) / 2f;
        Render2D.drawRoundedRect(ms, ix, iy, it, it, 8f, MenuChrome.white(0.04f));
        Render2D.drawTexture(ms, ix + 1f, iy + 1f, it - 2f, it - 2f, 7f,
                0f, 0f, 1f, 1f, packIcon(p), java.awt.Color.WHITE);
        Render2D.drawBorder(ms, ix, iy, it, it, 8f, 1.0f, 1.2f, MenuChrome.white(0.07f));

        int btns = 1;
        if (!left) btns = 3;
        float ctrlW = btns * BTN + (btns - 1) * BTN_GAP;
        float textX = ix + it + 12f;
        float textW = Math.max(20f, x + w - 12f - ctrlW - 10f - textX);

        Instance nf = Fonts.GOLOS.getFont(13f);
        Render2D.drawFont(ms, nf, clip(nf, flat(p.getDisplayName().getString()), textW), textX, y + 12f, MenuChrome.TEXT2);
        Instance df = Fonts.GOLOS.getFont(11.5f);
        Render2D.drawFont(ms, df, clip(df, flat(p.getDescription().getString()), textW), textX, y + 30f, MenuChrome.MUTED2);

        float bx = x + w - 12f - ctrlW, by = y + (ROW_H - BTN) / 2f;
        if (left) {
            iconButton(ms, bx, by, mx, my, 0, idx, hitEnable);
        } else {
            iconButton(ms, bx, by, mx, my, 1, idx, hitUp);
            iconButton(ms, bx + BTN + BTN_GAP, by, mx, my, 2, idx, hitDown);
            // Обязательный пак выключить нельзя — рисуем стрелку приглушённой и хитбокс не регистрируем.
            if (p.isRequired()) {
                WindowMaterial.fieldBox(ms, bx + (BTN + BTN_GAP) * 2f, by, BTN, BTN, 0f, false);
                ClickGuiIcons.arrowLeft(ms, bx + (BTN + BTN_GAP) * 2f + BTN / 2f, by + BTN / 2f, 14f, MenuChrome.MUTED4);
            } else {
                iconButton(ms, bx + (BTN + BTN_GAP) * 2f, by, mx, my, 3, idx, hitDisable);
            }
        }
    }

    /** Квадратная кнопка-стрелка 26×26 + регистрация хитбокса со строчным индексом. */
    private void iconButton(Matrix3x2fStack ms, float x, float y, int mx, int my, int kind, int idx, List<float[]> sink) {
        boolean hov = MenuChrome.hit(mx, my, x, y, BTN, BTN);
        float t = hov ? 1f : 0f;
        WindowMaterial.fieldBox(ms, x, y, BTN, BTN, t, false);
        Color c = MenuChrome.lerp(MenuChrome.MUTED, MenuChrome.accent(), t);
        float cx = x + BTN / 2f, cy = y + BTN / 2f;
        if (kind == 0) ClickGuiIcons.arrowRight(ms, cx, cy, 14f, c);
        else if (kind == 1) ClickGuiIcons.chevronUp(ms, cx, cy, 14f, c);
        else if (kind == 2) ClickGuiIcons.chevronDown(ms, cx, cy, 14f, c);
        else ClickGuiIcons.arrowLeft(ms, cx, cy, 14f, c);
        sink.add(new float[]{x, y, BTN, BTN, idx});
    }

    private static String flat(String s) {
        return s.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private static String clip(Instance f, String s, float maxW) {
        if (f.getWidth(s) <= maxW) return s;
        String cut = s;
        while (!cut.isEmpty() && f.getWidth(cut + "...") > maxW) cut = cut.substring(0, cut.length() - 1);
        return cut + "...";
    }

    // ---------- Футер: «Открыть папку» (secondary) + «Готово» (primary) ----------
    private void drawFooter(Matrix3x2fStack ms, float x, float y, float w, int mx, int my) {
        float cy = y + FOOTER_H / 2f;

        Instance bf = Fonts.UNBOUNDED.getFont(11f);
        String fLabel = "Открыть папку";
        float fIcon = 15f, fh = 44f; // та же высота и ось, что у «Готово» — пара стояла вразнобой
        float fw = 18f + fIcon + 9f + bf.getWidth(fLabel) + 18f;
        float fx = x + PAD_X, fy = cy - fh / 2f - 9f;
        boolean fHov = MenuChrome.hit(mx, my, fx, fy, fw, fh);
        float ft = MenuChrome.upd(hFolder, fHov);
        MenuChrome.drawSecondaryMaterial(ms, fx, fy, fw, fh, 8f, ft, false);
        ClickGuiIcons.layers(ms, fx + 18f + fIcon / 2f, fy + fh / 2f, fIcon, MenuChrome.lerp(MenuChrome.MUTED, MenuChrome.TEXT2, ft));
        Render2D.drawFont(ms, bf, fLabel, fx + 18f + fIcon + 9f, fy + fh / 2f - bf.getHeight() / 2f,
                MenuChrome.lerp(MenuChrome.TEXT3, MenuChrome.TEXT, ft));
        MenuChrome.set(rFolder, fx, fy, fw, fh);

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

    // ---------- Ввод ----------
    @Override
    protected boolean contentClick(float mx, float my, int button) {
        if (button != 0) return false;

        if (MenuChrome.in(rBack, mx, my)) { back(); return true; }
        if (MenuChrome.in(rDone, mx, my)) { apply(); back(); return true; }
        if (MenuChrome.in(rFolder, mx, my)) { openFolder(); return true; }

        // Строка на кромке вьюпорта рисуется частично, но её хит-рект 26×26 может целиком уехать под
        // ножницы: клик по пустому месту над/под списком не должен молча переносить пак.
        if (my < listTop || my > listBottom) return false;

        int i = pick(hitEnable, mx, my);
        // Включённый пак встаёт наверх колонки — он приоритетнее уже выбранных (как в ванили).
        if (i >= 0 && i < available.size()) { selected.add(0, available.remove(i)); return true; }

        i = pick(hitDisable, mx, my);
        if (i >= 0 && i < selected.size()) { available.add(selected.remove(i)); return true; }

        i = pick(hitUp, mx, my);
        if (i > 0 && i < selected.size()) { selected.add(i - 1, selected.remove(i)); return true; }

        i = pick(hitDown, mx, my);
        if (i >= 0 && i < selected.size() - 1) { selected.add(i + 1, selected.remove(i)); return true; }

        return false;
    }

    private static int pick(List<float[]> rects, float mx, float my) {
        for (float[] r : rects) {
            if (mx >= r[0] && mx <= r[0] + r[2] && my >= r[1] && my <= r[1] + r[3]) return (int) r[4];
        }
        return -1;
    }

    @Override
    protected boolean contentScroll(float mx, float my, double vertical) {
        // Колонка определяется по X: скролл у каждой независимый.
        float step = (float) vertical * (ROW_H + ROW_GAP) * 0.9f;
        if (mx < winX(WIN_W) + WIN_W / 2f) scrollLTarget -= step;
        else scrollRTarget -= step;
        return true;
    }

    private void apply() {
        ResourcePackManager m = mc.getResourcePackManager();
        List<String> ids = new ArrayList<>();
        // Скрытые служебные паки Fabric идут первыми (низший приоритет, как и были) — выключить их
        // из этого экрана нельзя, это ресурсы модов.
        for (ResourcePackProfile p : hiddenEnabled) ids.add(p.getId());
        // Обратно в порядок загрузки: в UI сверху приоритетный, менеджеру нужен обратный список.
        for (int i = selected.size() - 1; i >= 0; i--) ids.add(selected.get(i).getId());
        m.setEnabledProfiles(ids);
        // Сам пишет options и перезагружает ресурсы, если список реально изменился.
        mc.options.refreshResourcePacks(m);
    }

    private void openFolder() {
        Path dir = mc.getResourcePackDir();
        try {
            Files.createDirectories(dir);
        } catch (Exception ignored) {
            // Папки может не быть только до первого пака — открыть всё равно пробуем.
        }
        Util.getOperatingSystem().open(dir);
    }
}
