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

/**
 * 9b — хаб «Настройки» по дизайн-хендоффу. Заменяет ванильный {@code OptionsScreen}: FOV-слайдер,
 * кнопка «Онлайн», 10 категорий плитками 2×5 («Собираемые данные» задизейблена), «Готово».
 * Категория «Настройки графики» открывает redesign-шаблон {@link SubSettingsScreen}; остальные пока
 * ведут в ванильные под-экраны (постепенно переносятся на тот же шаблон).
 */
public final class SettingsHubScreen extends LinkenSettingsScreen {

    private static final float WIN_W = 840f, WIN_TOP = 150f, PAD_X = 22f;
    private static final float TOPBAR_H = 68f, FOV_H = 50f, FOOTER_H = 64f;
    private static final float TILES_PAD = 20f, TILE_H = 56f, TILE_GAP = 10f;
    private static final float TILES_H = TILES_PAD * 2f + 5f * TILE_H + 4f * TILE_GAP;
    private static final float WIN_H = TOPBAR_H + FOV_H + TILES_H + FOOTER_H;

    private static final int DATA_INDEX = 8;
    private static final String[] CAT = {
            "Внешний вид", "Музыка и звуки", "Настройки графики", "Управление", "Язык",
            "Настройки чата", "Наборы ресурсов", "Специальные возможности", "Собираемые данные",
            "Титры и атрибуции"
    };

    private final Animation hOnline = MenuChrome.anim(), hDone = MenuChrome.anim();
    private final float[] rOnline = new float[4], rDone = new float[4], rFov = new float[4];
    private final float[][] rTiles = new float[10][4];
    private final Animation[] hTiles = new Animation[10];
    private boolean draggingFov;

    public SettingsHubScreen(Screen parent) {
        super(parent, "Настройки");
        for (int i = 0; i < 10; i++) hTiles[i] = MenuChrome.anim();
    }

    @Override
    protected void drawContent(DrawContext context, Matrix3x2fStack ms, int mx, int my) {
        float x = winX(WIN_W), y = WIN_TOP, w = WIN_W, h = WIN_H;
        WindowMaterial.window(ms, x, y, w, h);

        drawTopbar(ms, x, y, w, mx, my);
        Render2D.drawRoundedRect(ms, x, y + TOPBAR_H - 1f, w, 1f, 0f, MenuChrome.white(0.07f));

        drawFovRow(ms, x, y + TOPBAR_H, w, mx);
        Render2D.drawRoundedRect(ms, x, y + TOPBAR_H + FOV_H - 1f, w, 1f, 0f, MenuChrome.white(0.07f));

        drawTiles(ms, x, y + TOPBAR_H + FOV_H, w, mx, my);
        drawFooter(ms, x, y + h - FOOTER_H, w, mx, my);

        WindowMaterial.corners(ms, x, y, w, h);
    }

    // ---------- Топбар: заголовок + «Онлайн» ----------
    private void drawTopbar(Matrix3x2fStack ms, float x, float y, float w, int mx, int my) {
        float cy = y + TOPBAR_H / 2f;
        Instance tf = Fonts.GOLOS_SEMI.getFont(17f);
        Render2D.drawFont(ms, tf, "Настройки", x + PAD_X, cy - tf.getHeight() / 2f, MenuChrome.TEXT);

        Instance bf = Fonts.UNBOUNDED.getFont(11f);
        String label = "Онлайн";
        float icon = 14f, bh = 36f;
        float bw = 14f + icon + 9f + bf.getWidth(label) + 15f;
        float bx = x + w - PAD_X - bw, by = cy - bh / 2f;
        boolean hov = MenuChrome.hit(mx, my, bx, by, bw, bh);
        float t = MenuChrome.upd(hOnline, hov);
        MenuChrome.drawSecondaryMaterial(ms, bx, by, bw, bh, 8f, t, false);
        ClickGuiIcons.globe(ms, bx + 14f + icon / 2f, by + bh / 2f, icon, MenuChrome.MUTED);
        Render2D.drawFont(ms, bf, label, bx + 14f + icon + 9f, by + bh / 2f - bf.getHeight() / 2f, MenuChrome.TEXT2);
        MenuChrome.set(rOnline, bx, by, bw, bh);
    }

    // ---------- Строка FOV ----------
    private void drawFovRow(Matrix3x2fStack ms, float x, float y, float w, int mx) {
        float cy = y + FOV_H / 2f;
        Instance lf = Fonts.GOLOS.getFont(13.5f);
        String lab = "Поле зрения";
        Render2D.drawFont(ms, lf, lab, x + PAD_X, cy - lf.getHeight() / 2f, MenuChrome.TEXT2);

        Instance vf = Fonts.GOLOS.getFont(12f);
        float valW = 118f;
        int fov = mc.options.getFov().getValue();
        String val = fovLabel(fov);
        Render2D.drawFont(ms, vf, val, x + w - PAD_X - valW + (valW - vf.getWidth(val)), cy - vf.getHeight() / 2f, MenuChrome.TEXT3);

        float trackX = x + PAD_X + lf.getWidth(lab) + 18f;
        float trackRight = x + w - PAD_X - valW - 14f;
        float trackW = Math.max(20f, trackRight - trackX);

        if (draggingFov) {
            long handle = mc.getWindow().getHandle();
            if (GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS) {
                float frac = MathHelper.clamp((mx - trackX) / trackW, 0f, 1f);
                mc.options.getFov().setValue(30 + Math.round(frac * 80f));
            } else {
                draggingFov = false;
            }
        }

        float frac = MathHelper.clamp((fov - 30) / 80f, 0f, 1f);
        float ty = cy - 2f;
        Render2D.drawRoundedRect(ms, trackX, ty, trackW, 4f, 2f, MenuChrome.white(0.09f));
        if (frac > 0.001f) {
            MenuChrome.glow(ms, trackX, ty, trackW * frac, 4f, 2f, MenuChrome.accentA(0.5f), 0f, 3, 1.3f);
            Render2D.drawRoundedRect(ms, trackX, ty, trackW * frac, 4f, 2f, MenuChrome.accent());
        }
        float knobX = trackX + trackW * frac;
        Render2D.drawCircle(ms, knobX, cy, 6f, new Color(0, 0, 0, 90));
        Render2D.drawCircle(ms, knobX, cy, 6f, MenuChrome.TEXT);
        MenuChrome.set(rFov, trackX - 6f, cy - 11f, trackW + 12f, 22f);
    }

    private static String fovLabel(int fov) {
        if (fov >= 110) return "Квейк Про · " + fov;
        if (fov == 70) return "Нормально · 70";
        if (fov <= 30) return "Мин. · " + fov;
        return String.valueOf(fov);
    }

    // ---------- Плитки категорий ----------
    private void drawTiles(Matrix3x2fStack ms, float x, float y, float w, int mx, int my) {
        float colW = (w - PAD_X * 2f - TILE_GAP) / 2f;
        float top = y + TILES_PAD;
        long now = System.currentTimeMillis();
        for (int i = 0; i < 10; i++) {
            int col = i % 2, row = i / 2;
            float tx = x + PAD_X + col * (colW + TILE_GAP);
            float tyRest = top + row * (TILE_H + TILE_GAP);
            boolean disabled = i == DATA_INDEX;
            boolean hov = !disabled && MenuChrome.hit(mx, my, tx, tyRest, colW, TILE_H);
            float t = MenuChrome.upd(hTiles[i], hov);
            // Стаггер-появление (fade+слайд) + hover-подъём. Хит/клик — по «покою».
            float appear = MathHelper.clamp((now - openMs - i * 22L) / 240f, 0f, 1f);
            appear = 1f - (1f - appear) * (1f - appear);
            float alpha = (disabled ? 0.45f : 1f) * appear;
            float ty = tyRest + (1f - appear) * 14f - t * 2f;

            Render2D.drawRoundedVerticalGradient(ms, tx, ty, colW, TILE_H, 10f,
                    fade(new Color(24, 30, 27, 179), alpha), fade(new Color(15, 19, 17, 179), alpha));
            Render2D.drawRoundedRect(ms, tx + 10f, ty + 1f, colW - 20f, 1f, 0.5f, MenuChrome.white(0.04f * alpha));
            Color bd = disabled ? MenuChrome.white(0.08f * alpha)
                    : MenuChrome.lerp(MenuChrome.white(0.08f), MenuChrome.white(0.22f), t);
            Render2D.drawBorder(ms, tx, ty, colW, TILE_H, 10f, 1.0f, 1.4f, bd);

            // Тайл иконки. Иконки векторные (в шрифтах мода нет глифов под все 10 категорий —
            // ни ноты, ни глобуса), поэтому вытягиваем читаемость размером и контрастом: на 16px
            // тонким штрихом поверх приглушённого цвета они сливались в пятно.
            float it = 36f, ix = tx + 14f, iy = ty + (TILE_H - it) / 2f;
            Render2D.drawRoundedRect(ms, ix, iy, it, it, 8f, MenuChrome.white(0.06f * alpha));
            Render2D.drawBorder(ms, ix, iy, it, it, 8f, 1.0f, 1.2f, MenuChrome.white(0.10f * alpha));
            Color iconColor = disabled ? MenuChrome.MUTED3
                    : MenuChrome.lerp(MenuChrome.TEXT3, MenuChrome.accent(), t);
            drawCatIcon(ms, i, ix + it / 2f, iy + it / 2f, 20f, fade(iconColor, alpha));

            Instance nf = Fonts.GOLOS.getFont(13.5f);
            Render2D.drawFont(ms, nf, CAT[i], ix + it + 12f, ty + TILE_H / 2f - nf.getHeight() / 2f, fade(MenuChrome.TEXT2, alpha));

            Instance cf = Fonts.GOLOS.getFont(15f);
            Render2D.drawFont(ms, cf, "›", tx + colW - 18f, ty + TILE_H / 2f - cf.getHeight() / 2f, fade(MenuChrome.MUTED3, alpha));

            MenuChrome.set(rTiles[i], tx, tyRest, colW, TILE_H);
        }
    }

    private void drawCatIcon(Matrix3x2fStack ms, int i, float cx, float cy, float s, Color c) {
        switch (i) {
            case 0 -> ClickGuiIcons.user(ms, cx, cy, s, c);
            case 1 -> ClickGuiIcons.musicNote(ms, cx, cy, s, c);
            case 2 -> ClickGuiIcons.monitor(ms, cx, cy, s, c);
            case 3 -> ClickGuiIcons.keyboard(ms, cx, cy, s, c);
            case 4 -> ClickGuiIcons.globe(ms, cx, cy, s, c);
            case 5 -> ClickGuiIcons.chat(ms, cx, cy, s, c);
            case 6 -> ClickGuiIcons.layers(ms, cx, cy, s, c);
            case 7 -> ClickGuiIcons.accessibility(ms, cx, cy, s, c);
            case 8 -> ClickGuiIcons.database(ms, cx, cy, s, c);
            default -> ClickGuiIcons.info(ms, cx, cy, s, c);
        }
    }

    // ---------- Футер: «Готово» ----------
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

    private static Color fade(Color c, float k) {
        if (k >= 0.999f) return c;
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.round(c.getAlpha() * MathHelper.clamp(k, 0f, 1f)));
    }

    // ---------- Ввод ----------
    @Override
    protected boolean contentClick(float mx, float my, int button) {
        if (button != 0) return false;
        if (MenuChrome.in(rFov, mx, my)) { draggingFov = true; return true; }
        if (MenuChrome.in(rDone, mx, my)) { mc.options.write(); back(); return true; }
        if (MenuChrome.in(rOnline, mx, my)) { openOnline(); return true; }
        for (int i = 0; i < 10; i++) {
            if (i == DATA_INDEX) continue;
            if (MenuChrome.in(rTiles[i], mx, my)) { openCategory(i); return true; }
        }
        return false;
    }

    @Override
    protected void contentRelease(float mx, float my, int button) {
        if (button == 0) draggingFov = false;
    }

    private void openCategory(int i) {
        // transitionTo вместо setScreen: экран доигрывает уход и только потом подменяется.
        switch (i) {
            case 0 -> transitionTo(new SubSettingsScreen(this, "Внешний вид", SettingsCatalog.appearance()));
            case 1 -> transitionTo(new SubSettingsScreen(this, "Музыка и звуки", SettingsCatalog.sound()));
            case 2 -> transitionTo(GraphicsSettings.create(this));
            case 3 -> transitionTo(new SubSettingsScreen(this, "Управление", SettingsCatalog.controls()));
            case 4 -> transitionTo(new LanguageListScreen(this));
            case 5 -> transitionTo(new SubSettingsScreen(this, "Чат", SettingsCatalog.chat()));
            case 6 -> transitionTo(new PacksListScreen(this));
            case 7 -> transitionTo(new SubSettingsScreen(this, "Спец. возможности", SettingsCatalog.accessibility()));
            case 9 -> transitionTo(new CreditsListScreen(this));
            default -> { }
        }
    }

    private void openOnline() {
        MenuChrome.keepMusicAcrossSwap();
        transitionTo(new SubSettingsScreen(this, "Онлайн", SettingsCatalog.online()));
    }
}
