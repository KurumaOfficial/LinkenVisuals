package ru.method.linkenvisuals.screen.settings;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmLinkScreen;
import net.minecraft.client.gui.screen.CreditsScreen;
import net.minecraft.client.gui.screen.Screen;
import org.joml.Matrix3x2fStack;
import net.minecraft.util.Urls;
import net.minecraft.util.math.MathHelper;
import ru.method.linkenvisuals.screen.clickgui.redesign.ClickGuiIcons;
import ru.method.linkenvisuals.screen.mainmenu.MenuChrome;
import ru.method.linkenvisuals.screen.multiplayer.WindowMaterial;
import ru.method.linkenvisuals.utils.animations.Animation;
import ru.method.linkenvisuals.utils.render.Render2D;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;
import ru.method.linkenvisuals.utils.render.fonts.Instance;

import java.awt.Color;

/**
 * Экран «Титры и атрибуции» в стиле 9c. Набор действий 1:1 повторяет ванильный
 * CreditsAndAttributionScreen: титры (ванильный ролик CreditsScreen с возвратом сюда),
 * атрибуция (Urls.JAVA_ATTRIBUTION) и лицензии (Urls.JAVA_LICENSES) — обе ссылки через
 * ConfirmLinkScreen, чтобы браузер не открывался без подтверждения.
 */
public final class CreditsListScreen extends LinkenSettingsScreen {

    private static final float WIN_W = 1060f, WIN_TOP = 130f, PAD_X = 22f;
    private static final float TOPBAR_H = 64f, FOOTER_H = 64f;
    private static final float ROW_H = 44f, ROW_GAP = 8f, PAD_Y = 20f;

    private static final String CRUMB = "Титры и атрибуции";
    private static final String[] LABELS = {"Титры", "Атрибуция", "Лицензии"};

    private final Animation hBack = MenuChrome.anim(), hDone = MenuChrome.anim();
    private final Animation[] hRows = {MenuChrome.anim(), MenuChrome.anim(), MenuChrome.anim()};
    private final float[] rBack = new float[4], rDone = new float[4];
    private final float[][] rRows = new float[LABELS.length][4];

    public CreditsListScreen(Screen parent) {
        super(parent, "Настройки — " + CRUMB);
    }

    private float winH() {
        return TOPBAR_H + PAD_Y * 2f + LABELS.length * ROW_H + (LABELS.length - 1) * ROW_GAP + FOOTER_H;
    }

    @Override
    protected void drawContent(DrawContext context, Matrix3x2fStack ms, int mx, int my) {
        float x = winX(WIN_W), y = WIN_TOP, w = WIN_W, h = winH();
        WindowMaterial.window(ms, x, y, w, h);

        drawTopbar(ms, x, y, w, mx, my);
        Render2D.drawRoundedRect(ms, x, y + TOPBAR_H - 1f, w, 1f, 0f, MenuChrome.white(0.07f));

        drawRows(ms, x, y + TOPBAR_H, w, mx, my);
        drawFooter(ms, x, y + h - FOOTER_H, w, mx, my);

        WindowMaterial.corners(ms, x, y, w, h);
    }

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

    private void drawRows(Matrix3x2fStack ms, float x, float y, float w, int mx, int my) {
        long now = System.currentTimeMillis();
        float rw = w - PAD_X * 2f, rx = x + PAD_X;

        for (int i = 0; i < LABELS.length; i++) {
            float ryRest = y + PAD_Y + i * (ROW_H + ROW_GAP);
            boolean hov = MenuChrome.hit(mx, my, rx, ryRest, rw, ROW_H);
            float t = MenuChrome.upd(hRows[i], hov);
            float appear = MathHelper.clamp((now - openMs - i * 22L) / 240f, 0f, 1f);
            appear = 1f - (1f - appear) * (1f - appear);
            float ry = ryRest + (1f - appear) * 12f;

            Render2D.drawRoundedVerticalGradient(ms, rx, ry, rw, ROW_H, 10f,
                    fade(new Color(24, 30, 27, 179), appear), fade(new Color(15, 19, 17, 179), appear));
            Render2D.drawBorder(ms, rx, ry, rw, ROW_H, 10f, 1.0f, 1.4f,
                    MenuChrome.lerp(MenuChrome.white(0.08f), MenuChrome.white(0.22f), t));

            Instance lf = Fonts.GOLOS.getFont(13f);
            Render2D.drawFont(ms, lf, LABELS[i], rx + 16f, ry + ROW_H / 2f - lf.getHeight() / 2f, fade(MenuChrome.TEXT2, appear));
            Instance cf = Fonts.GOLOS.getFont(15f);
            Render2D.drawFont(ms, cf, "›", rx + rw - 18f, ry + ROW_H / 2f - cf.getHeight() / 2f,
                    fade(MenuChrome.lerp(MenuChrome.MUTED3, MenuChrome.TEXT3, t), appear));

            MenuChrome.set(rRows[i], rx, ryRest, rw, ROW_H); // хитбокс по «покою»
        }
    }

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

    @Override
    protected boolean contentClick(float mx, float my, int button) {
        if (button != 0) return false;
        if (MenuChrome.in(rBack, mx, my)) { back(); return true; }
        if (MenuChrome.in(rDone, mx, my)) { back(); return true; }
        for (int i = 0; i < rRows.length; i++) {
            if (!MenuChrome.in(rRows[i], mx, my)) continue;
            activate(i);
            return true;
        }
        return false;
    }

    private void activate(int index) {
        if (index == 0) {
            // Ванильный ролик титров; по завершении возвращает на этот экран.
            mc.setScreen(new CreditsScreen(false, () -> mc.setScreen(this)));
        } else if (index == 1) {
            ConfirmLinkScreen.open(this, Urls.JAVA_ATTRIBUTION);
        } else if (index == 2) {
            ConfirmLinkScreen.open(this, Urls.JAVA_LICENSES);
        }
    }

    private static Color fade(Color c, float k) {
        if (k >= 0.999f) return c;
        return new Color(c.getRed(), c.getGreen(), c.getBlue(),
                Math.round(c.getAlpha() * MathHelper.clamp(k, 0f, 1f)));
    }
}
