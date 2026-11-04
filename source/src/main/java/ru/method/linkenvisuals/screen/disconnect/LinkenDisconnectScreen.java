package ru.method.linkenvisuals.screen.disconnect;

import net.minecraft.client.input.KeyInput;

import net.minecraft.client.gui.Click;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import org.joml.Matrix3x2fStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import ru.method.linkenvisuals.screen.clickgui.redesign.ClickGuiIcons;
import ru.method.linkenvisuals.screen.mainmenu.CustomMainMenuScreen;
import ru.method.linkenvisuals.screen.mainmenu.MenuChrome;
import ru.method.linkenvisuals.screen.multiplayer.CustomMultiplayerScreen;
import ru.method.linkenvisuals.screen.multiplayer.WindowMaterial;
import ru.method.linkenvisuals.utils.Wrapper;
import ru.method.linkenvisuals.utils.animations.Animation;
import ru.method.linkenvisuals.utils.render.Render2D;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;
import ru.method.linkenvisuals.utils.render.fonts.Instance;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Кастомный экран «Соединение потеряно» — замена ванильного DisconnectedScreen (хендофф
 * design_handoff_linken_disconnect). Собран из готового «хрома» клиента: материал центрального окна
 * ({@link WindowMaterial}), кнопки/виньетка/футер/цвета ({@link MenuChrome}), иконки ({@link ClickGuiIcons}),
 * TTF-шрифты ({@link Fonts}). Фон — затемнение + виньетка (мир к этому моменту уже выгружен).
 */
public final class LinkenDisconnectScreen extends Screen implements Wrapper {

    private static final float WIN_W = 520f;
    private static final float PAD = 36f;                 // боковые/верхний отступы контента
    private static final float PAD_BOTTOM = 28f;
    private static final float TILE = 52f;
    private static final int REASON_MAX_LINES = 6;        // после — скролл

    private final Text titleText;
    private final Text reasonText;
    private final boolean toServerList;                   // мультиплеер: primary = «К списку серверов»
    private final ServerInfo lastServer;
    private final boolean canReconnect;

    // Виртуальные координаты холста 1080p (как в остальных редизайн-экранах).
    private float uiScale = 1f;
    private int vw = 1, vh = 1;
    private long openMs;

    private final Animation hPrimary = MenuChrome.anim();
    private final Animation hSecondary = MenuChrome.anim();
    private final float[] rPrimary = new float[4];
    private final float[] rSecondary = new float[4];

    private List<String> reasonLines;
    private String reasonWrapKey;                          // кэш переноса: ширина|текст
    private float reasonScroll;
    private float reasonMaxScroll;

    public LinkenDisconnectScreen(Text titleText, Text reasonText, boolean toServerList, ServerInfo lastServer) {
        super(titleText == null ? Text.literal("Соединение потеряно") : titleText);
        this.titleText = this.getTitle();
        this.reasonText = reasonText == null ? Text.empty() : reasonText;
        this.toServerList = toServerList;
        this.lastServer = lastServer;
        this.canReconnect = toServerList && lastServer != null;
    }

    private void recomputeScale() {
        if (client == null || client.getWindow() == null) return;
        // HiDPI-safe: от GUI-scaled размеров (getHeight() — логический размер окна, scaleFactor — от
        // фреймбуфера; на Retina давало uiScale вдвое меньше). См. CustomMainMenuScreen.
        uiScale = Math.max(0.05f, client.getWindow().getScaledHeight() / MenuChrome.DESIGN_HEIGHT);
        vw = Math.max(1, Math.round(client.getWindow().getScaledWidth() / uiScale));
        vh = Math.max(1, Math.round(client.getWindow().getScaledHeight() / uiScale));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        ru.method.linkenvisuals.utils.render.Render2D.prepareHudFrame(context);
        if (openMs == 0L) openMs = System.currentTimeMillis();
        recomputeScale();

        Matrix3x2fStack ms = context.getMatrices();
        ms.pushMatrix();
        ms.scale(uiScale, uiScale);
        float mx = mouseX / uiScale, my = mouseY / uiScale;
        ClickGuiIcons.pixelScale(uiScale);

        // Фон: затемнение (~brightness 0.62 поверх почти чёрного) + радиальная виньетка.
        Render2D.drawRoundedRect(ms, 0f, 0f, vw, vh, 0f, new Color(4, 7, 6, 235));
        MenuChrome.drawRadialVignette(ms, vw, vh);

        float contentW = WIN_W - PAD * 2f;
        List<String> lines = wrapReason(contentW);
        int shownLines = Math.min(lines.size(), REASON_MAX_LINES);
        Instance rf = Fonts.GOLOS.getFont(13f);
        float lineH = rf.getHeight() + 5f;

        // Высота рамки причины: лейбл + зазор + видимые строки, внутренний паддинг 12.
        Instance lblF = Fonts.PLEXMONO.getFont(10f);
        float reasonInner = lblF.getHeight() + 8f + shownLines * lineH;
        float reasonBoxH = 12f + reasonInner + 12f;

        Instance titleF = Fonts.GOLOS_SEMI.getFont(22f);
        Instance subF = Fonts.GOLOS.getFont(13f);
        float titleH = titleF.getHeight(), subH = subF.getHeight();

        float btnH = 44f;
        float buttonsH = btnH + (canReconnect ? 8f + btnH : 0f);

        float winH = PAD + TILE + 18f + titleH + 6f + subH + 22f + reasonBoxH + 22f + buttonsH + PAD_BOTTOM;
        float winX = vw / 2f - WIN_W / 2f;
        float winY = vh / 2f - winH * 0.54f;             // translate(-50%, -54%) — окно чуть выше центра

        // Появление окна: scale 0.98→1 от его центра (150–200 ms).
        long now = System.currentTimeMillis();
        float openT = MathHelper.clamp((now - openMs) / 180f, 0f, 1f);
        float ease = 1f - (1f - openT) * (1f - openT);
        float appScale = 0.98f + 0.02f * ease;
        float cx = winX + WIN_W / 2f, cy = winY + winH / 2f;
        ms.pushMatrix();
        ms.translate(cx, cy);
        ms.scale(appScale, appScale);
        ms.translate(-cx, -cy);

        // Окно в материале клиента + уголки-скобки.
        WindowMaterial.window(ms, winX, winY, WIN_W, winH);
        WindowMaterial.corners(ms, winX, winY, WIN_W, winH);

        float centerX = winX + WIN_W / 2f;
        float y = winY + PAD;

        // 1) Красный тайл wifi-off со свечением.
        float tileX = centerX - TILE / 2f;
        Color danger = MenuChrome.DANGER;
        MenuChrome.glow(ms, tileX, y, TILE, TILE, 12f,
                new Color(danger.getRed(), danger.getGreen(), danger.getBlue(), 60), 0f, 5, 3f);
        Render2D.drawRoundedRect(ms, tileX, y, TILE, TILE, 12f, new Color(224, 110, 110, 20));
        Render2D.drawBorder(ms, tileX, y, TILE, TILE, 12f, 1.0f, 1.4f, new Color(224, 110, 110, 102));
        // Иконка — ГЛИФ шрифта (как весь UI клиента). Раньше звался ClickGuiIcons.wifi (FA ),
        // но этого глифа нет в атласе iconsfa — рисовался пустой символ, и от «wifi-off» оставалась
        // одна голая диагональная палка. Глобус () в атласе есть и по смыслу тот же «сеть».
        // ClickGuiIcons.glyph поднимает FA-глиф на size*0.0625 (компенсация descender'а — нужна в
        // кнопках, где иконка стоит рядом с текстом). В квадратном тайле этот подъём лишний и глиф
        // сидит выше центра — возвращаем его обратно, чтобы иконка встала ровно в середину тайла.
        float iconSize = 22f;
        float iconCy = y + TILE / 2f + iconSize * 0.0625f;
        // −1px по X: глиф центруется по advance-ширине, а у globe справа есть пустое поле,
        // из-за которого видимый круг визуально уезжал вправо от середины тайла.
        float iconCx = centerX - 1f;
        ClickGuiIcons.globe(ms, iconCx, iconCy, iconSize, danger);
        // диагональный слэш «off» поверх глобуса — та же композиция «сети нет», что в хендоффе
        drawSlash(ms, iconCx, iconCy, 20f, danger);
        y += TILE + 18f;

        // 2) Заголовок.
        Render2D.drawFont(ms, titleF, titleText.getString(), centerX - titleF.getWidth(titleText.getString()) / 2f, y, MenuChrome.TEXT);
        y += titleH + 6f;

        // 3) Подзаголовок.
        String sub = "Сервер разорвал подключение";
        Render2D.drawFont(ms, subF, sub, centerX - subF.getWidth(sub) / 2f, y, MenuChrome.MUTED2);
        y += subH + 22f;

        // 4) Рамка причины.
        float boxX = winX + PAD, boxW = contentW, boxY = y;
        Render2D.drawRoundedRect(ms, boxX, boxY, boxW, reasonBoxH, 8f, new Color(255, 255, 255, 8));
        Render2D.drawBorder(ms, boxX, boxY, boxW, reasonBoxH, 8f, 1.0f, 1.2f, MenuChrome.white(0.07f));
        MenuChrome.drawSpaced(ms, lblF, "ПРИЧИНА", boxX + 14f, boxY + 12f, MenuChrome.MUTED4, 0.14f * 10f);
        // Строки причины — со скиссором и скроллом (если длинная).
        float textTop = boxY + 12f + lblF.getHeight() + 8f;
        float clipH = shownLines * lineH;
        reasonMaxScroll = Math.max(0f, lines.size() * lineH - clipH);
        reasonScroll = MathHelper.clamp(reasonScroll, 0f, reasonMaxScroll);
        Render2D.startScissor(context, boxX + 1f, textTop - 1f, boxW - 2f, clipH + 2f);
        float ty = textTop - reasonScroll;
        for (String line : lines) {
            Render2D.drawFont(ms, rf, line, boxX + 14f, ty, MenuChrome.TEXT3);
            ty += lineH;
        }
        Render2D.stopScissor(context);
        y += reasonBoxH + 22f;

        // 5) Кнопки.
        float btnX = winX + PAD, btnW = contentW;
        boolean pHov = MenuChrome.hit(mx, my, btnX, y, btnW, btnH);
        float pT = MenuChrome.upd(hPrimary, pHov);
        drawActionButton(ms, btnX, y, btnW, btnH, true, toServerList ? "list" : "menu",
                toServerList ? "К списку серверов" : "В главное меню", pT);
        MenuChrome.set(rPrimary, btnX, y, btnW, btnH);
        y += btnH;

        if (canReconnect) {
            y += 8f;
            boolean sHov = MenuChrome.hit(mx, my, btnX, y, btnW, btnH);
            float sT = MenuChrome.upd(hSecondary, sHov);
            drawActionButton(ms, btnX, y, btnW, btnH, false, "reconnect", "Переподключиться", sT);
            MenuChrome.set(rSecondary, btnX, y, btnW, btnH);
        } else {
            rSecondary[2] = 0f;
        }

        ms.popMatrix(); // окно (scale появления)

        // Низ холста: ESC-чип + подпись (всегда).
        drawEscChip(ms);
        MenuChrome.drawFooterSignature(ms, vw, vh, true);

        ms.popMatrix();
        ClickGuiIcons.pixelScale(1f);
    }

    /** Кнопка действия: primary — акцентный материал (как MenuChrome.drawButton), secondary — вторичный. */
    private void drawActionButton(Matrix3x2fStack ms, float x, float y, float w, float h, boolean primary,
                                  String icon, String label, float hoverT) {
        float ty = y - hoverT;                            // подъём 1px на ховере
        float r = 8f;
        Color labelColor;
        if (primary) {
            Color acc = MenuChrome.accent();
            Render2D.drawRoundedRect(ms, x + 4f, ty + h - 6f, w - 8f, 12f, 8f,
                    new Color(acc.getRed(), acc.getGreen(), acc.getBlue(), Math.round(70 + 50 * hoverT)));
            int br = Math.min(255, Math.round(acc.getRed() * (1f + 0.07f * hoverT)));
            int bg = Math.min(255, Math.round(acc.getGreen() * (1f + 0.07f * hoverT)));
            int bb = Math.min(255, Math.round(acc.getBlue() * (1f + 0.07f * hoverT)));
            Render2D.drawRoundedRect(ms, x, ty, w, h, r, new Color(br, bg, bb));
            Render2D.drawRoundedRect(ms, x + r, ty + 1f, w - r * 2f, 1f, 0.5f, new Color(255, 255, 255, 90));
            labelColor = MenuChrome.INK;
        } else {
            MenuChrome.drawSecondaryMaterial(ms, x, ty, w, h, r, hoverT, false);
            labelColor = MenuChrome.TEXT2;
        }
        Instance lf = Fonts.UNBOUNDED.getFont(11f);
        float iconSize = 14f;
        float lw = lf.getWidth(label);
        float content = iconSize + 9f + lw;
        float ix = x + (w - content) / 2f + iconSize / 2f;
        float cyv = ty + h / 2f;
        Color iconColor = primary ? MenuChrome.INK : MenuChrome.MUTED;
        switch (icon) {
            case "list" -> ClickGuiIcons.list(ms, ix, cyv, iconSize, iconColor);
            case "reconnect" -> ClickGuiIcons.refresh(ms, ix, cyv, iconSize, iconColor);
            default -> ClickGuiIcons.chevronLeft(ms, ix, cyv, iconSize, iconColor); // «В главное меню»
        }
        Render2D.drawFont(ms, lf, label, ix + iconSize / 2f + 9f, cyv - lf.getHeight() / 2f, labelColor);
    }

    /** Диагональный слэш поверх wifi-глифа — превращает его в «wifi-off». */
    private void drawSlash(Matrix3x2fStack ms, float cx, float cy, float len, Color c) {
        ms.pushMatrix();
        ms.translate(cx, cy);
        ms.rotate((float) Math.toRadians(-45f));
        Render2D.drawRoundedRect(ms, -len / 2f, -0.9f, len, 1.8f, 0.9f, c);
        ms.popMatrix();
    }

    /** ESC-чип по центру над подписью: «[ESC]» + подпись действия primary. */
    private void drawEscChip(Matrix3x2fStack ms) {
        Instance kf = Fonts.PLEXMONO.getFont(10f);
        Instance lf = Fonts.GOLOS.getFont(11.5f);
        String label = toServerList ? "к списку серверов" : "в главное меню";
        float capW = kf.getWidth("ESC") + 16f, capH = 22f, gap = 8f;
        float w = capW + gap + lf.getWidth(label);
        Instance sigF = Fonts.PLEXMONO.getFont(9.5f);
        float sigY = vh - 44f - sigF.getHeight();          // как в drawFooterSignature
        float y = sigY - 14f - capH;                        // gap 14 над подписью
        float x = vw / 2f - w / 2f;
        // кейкап ESC
        Render2D.drawRoundedRect(ms, x, y, capW, capH, 5f, new Color(255, 255, 255, 8));
        Render2D.drawBorder(ms, x, y, capW, capH, 5f, 1.0f, 1.2f, MenuChrome.white(0.14f));
        Render2D.drawFont(ms, kf, "ESC", x + 8f, y + capH / 2f - kf.getHeight() / 2f, MenuChrome.MUTED);
        Render2D.drawFont(ms, lf, label, x + capW + gap, y + capH / 2f - lf.getHeight() / 2f, MenuChrome.MUTED3);
    }

    /** Перенос причины по словам под ширину рамки (минус паддинг 28). Кэшируется по ширине+тексту. */
    private List<String> wrapReason(float boxContentW) {
        float maxW = boxContentW - 28f;
        String plain = reasonText.getString();
        String key = ((int) maxW) + "|" + plain;
        if (reasonLines != null && key.equals(reasonWrapKey)) return reasonLines;
        Instance f = Fonts.GOLOS.getFont(13f);
        List<String> out = new ArrayList<>();
        for (String hard : plain.split("\n", -1)) {
            if (hard.isEmpty()) { out.add(""); continue; }
            StringBuilder cur = new StringBuilder();
            for (String word : hard.split(" ")) {
                String probe = cur.length() == 0 ? word : cur + " " + word;
                if (f.getWidth(probe) <= maxW || cur.length() == 0) {
                    cur.setLength(0);
                    cur.append(probe);
                } else {
                    out.add(cur.toString());
                    cur.setLength(0);
                    cur.append(word);
                }
            }
            out.add(cur.toString());
        }
        reasonLines = out;
        reasonWrapKey = key;
        return out;
    }

    // ---------- ввод ----------

    private void doPrimary() {
        if (toServerList) {
            client.setScreen(new CustomMultiplayerScreen(null));
        } else {
            client.setScreen(new CustomMainMenuScreen());
        }
    }

    private void doReconnect() {
        if (!canReconnect) return;
        // connect() сам выставляет ConnectScreen; parent — куда вернуться при неудаче (список серверов).
        ConnectScreen.connect(new CustomMultiplayerScreen(null), client,
                ServerAddress.parse(lastServer.address), lastServer, false, null);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubleClick) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        if (button == 0) {
            float mx = (float) (mouseX / uiScale), my = (float) (mouseY / uiScale);
            if (MenuChrome.in(rPrimary, mx, my)) { doPrimary(); return true; }
            if (canReconnect && MenuChrome.in(rSecondary, mx, my)) { doReconnect(); return true; }
        }
        return super.mouseClicked(click, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (reasonMaxScroll > 0f) {
            reasonScroll = MathHelper.clamp(reasonScroll - (float) vertical * 16f, 0f, reasonMaxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
        int keyCode = input.key();
        int scanCode = input.scancode();
        int modifiers = input.modifiers();
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            doPrimary();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override public boolean shouldCloseOnEsc() { return false; }
    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }
    @Override public boolean shouldPause() { return false; }
}
