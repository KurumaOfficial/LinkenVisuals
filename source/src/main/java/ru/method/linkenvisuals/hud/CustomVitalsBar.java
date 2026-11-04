package ru.method.linkenvisuals.hud;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import ru.method.linkenvisuals.modules.settings.api.Nameable;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import ru.method.linkenvisuals.utils.render.Render2D;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;

import java.awt.Color;

/**
 * Кастомные полосы здоровья/голода/опыта (+ воздух/броня/маунт) в стиле Link GUI — рисуются вместо
 * ванильных над хотбаром, ровно по его ширине (182px). Форма выбирается настройкой «Vitals Style».
 *
 * <p>Экспа — фирменным акцентом темы ({@code accent()→accentSecondary()}), как хотбар и чат;
 * HP/голод — семантические красный/янтарный, той же геометрией скругления, что и ячейки хотбара.</p>
 */
public final class CustomVitalsBar {

    private CustomVitalsBar() {
    }

    public enum VitalsStyle implements Nameable {
        Bars("Bars"),
        Stack("Stack"),
        Segments("Segments");

        private final String label;

        VitalsStyle(String label) {
            this.label = label;
        }

        @Override
        public String getName() {
            return label;
        }
    }

    /** Снимок состояния игрока для отрисовки (заполняется в InGameHudMixin). */
    public static final class State {
        public VitalsStyle style = VitalsStyle.Segments;
        public int segCount = 8;
        public boolean showArmorRow = true;
        public float healthFrac, foodFrac, expFrac;
        public int health, food, level, armor;
        public float airFrac = 1f;
        public boolean showAir;
        public boolean riding;
        public float mountHealthFrac;
        public float jumpFrac;
        public boolean poison, wither, hunger;
    }

    private static final float TOTAL_W = 182f;
    private static final float SLOT = 18f;

    private static final Color HEALTH = new Color(0xE0, 0x32, 0x4A);
    private static final Color HEALTH_POISON = new Color(0x7C, 0xB3, 0x42);
    private static final Color HEALTH_WITHER = new Color(0x6E, 0x4A, 0x57);
    private static final Color FOOD = new Color(0xD9, 0xA4, 0x41);
    private static final Color FOOD_HUNGER = new Color(0x8B, 0xC3, 0x4A);
    private static final Color AIR = new Color(0x4D, 0x9B, 0xFF);
    private static final Color MOUNT = new Color(0xC9, 0x6A, 0x85);
    private static final Color ARMOR = new Color(0xC8, 0xD2, 0xDC);

    // Анимация (один локальный игрок).
    private static float dispHealth = -1f;
    private static long lastMs = 0L;
    private static float damageFlash = 0f;

    public static void render(DrawContext context, State s) {
        int sw = context.getScaledWindowWidth();
        int sh = context.getScaledWindowHeight();
        Object ms = context.getMatrices();

        float x0 = sw / 2f - TOTAL_W / 2f;
        float bottom = sh - SLOT - 4f - 2f - 1f;

        switch (s.style == null ? VitalsStyle.Segments : s.style) {
            case Stack -> renderStack(ms, x0, bottom, s);
            case Bars -> renderBars(ms, x0, bottom, s);
            default -> renderSegments(ms, x0, bottom, s);
        }
    }

    // ================= Segments (основной) =================
    private static void renderSegments(Object ms, float x0, float bottom, State s) {
        updateAnim(s);

        final float expH = 6f;
        final float segH = 6f;
        final float gap = 3f;
        final float thinH = 3f;
        final float mid = 8f;
        final int count = Math.max(3, Math.min(10, s.segCount));

        float half = (TOTAL_W - mid) / 2f;
        float segW = (half - (count - 1) * gap) / count;

        // Пульс при низком HP + вспышка урона.
        float pulse = 1f;
        if (s.healthFrac > 0f && s.healthFrac < 0.3f) {
            float t = (System.currentTimeMillis() % 1000L) / 1000f;
            pulse = 0.72f + 0.28f * (0.5f + 0.5f * (float) Math.sin(t * Math.PI * 2f));
        }
        int hAlpha = Math.round(248f * pulse);

        Color healthColor = s.wither ? HEALTH_WITHER : (s.poison ? HEALTH_POISON : HEALTH);
        Color foodColor = s.hunger ? FOOD_HUNGER : FOOD;

        // Раскладка снизу вверх: экспа → главный ряд (HP|еда/маунт) → тонкий ряд (броня|воздух).
        float expY = bottom - expH;
        float mainY = expY - gap - segH;

        boolean showArmor = s.showArmorRow && s.armor > 0;
        boolean showAir = s.showAir;
        float thinY = mainY - 2f - thinH;

        // Тонкий верхний ряд: броня слева, воздух справа.
        if (showArmor) {
            segRow(ms, x0, thinY, half, segW, thinH, gap, count, s.armor / 20f, ARMOR, false, 210, 0f);
        }
        if (showAir) {
            segRow(ms, x0 + TOTAL_W - half, thinY, half, segW, thinH, gap, count, s.airFrac, AIR, true, 235, 0f);
        }

        // Главный ряд: HP слева.
        float ghost = Math.max(0f, dispHealth - s.healthFrac);
        segRow(ms, x0, mainY, half, segW, segH, gap, count, s.healthFrac, healthColor, false, hAlpha, ghost);
        if (damageFlash > 0.01f) {
            Render2D.drawRoundedRect(ms, x0 - 1f, mainY - 1f, half + 2f, segH + 2f, 3f,
                    ColorUtils.alpha(Color.WHITE, Math.round(70f * damageFlash)));
        }

        // Главный ряд: еда или (при езде) HP маунта справа.
        if (s.riding) {
            segRow(ms, x0 + TOTAL_W - half, mainY, half, segW, segH, gap, count, s.mountHealthFrac, MOUNT, true, 245, 0f);
        } else {
            segRow(ms, x0 + TOTAL_W - half, mainY, half, segW, segH, gap, count, s.foodFrac, foodColor, true, 245, 0f);
        }

        // Экспа (или заряд прыжка при езде на прыгуне) + чип уровня по центру.
        boolean jump = s.riding && s.jumpFrac > 0f;
        float barFrac = jump ? s.jumpFrac : s.expFrac;
        Color a1 = jump ? new Color(0xF2, 0xC5, 0x72) : LinkHudStyle.accent();
        Color a2 = jump ? new Color(0xD9, 0x9A, 0x2E) : LinkHudStyle.accentSecondary();

        Render2D.drawRoundedRect(ms, x0, expY, TOTAL_W, expH, expH / 2f, LinkHudStyle.inset(225));
        Render2D.drawBorder(ms, x0, expY, TOTAL_W, expH, expH / 2f, 1f, 1f,
                ColorUtils.alpha(LinkHudStyle.accent(), 45));
        if (barFrac > 0f) {
            float fw = Math.max(expH, TOTAL_W * clamp(barFrac));
            Render2D.drawRoundedHorizontalGradient(ms, x0, expY, fw, expH, expH / 2f,
                    ColorUtils.alpha(a1, 255), ColorUtils.alpha(a2, 255));
        }
        if (!jump && s.level > 0) {
            drawLevelChip(ms, x0 + TOTAL_W / 2f, expY + expH / 2f, expH, Integer.toString(s.level));
        }
    }

    /** Ряд из {@code count} сегментов с дробным заполнением последнего + врезанная тёмная подложка. */
    private static void segRow(Object ms, float x, float y, float rowW, float segW, float segH, float gap,
                               int count, float frac, Color color, boolean rtl, int alpha, float ghostFrac) {
        // Подложка-лента: зазоры читаются как «вырезы».
        Render2D.drawRoundedRect(ms, x - 1f, y - 1f, rowW + 2f, segH + 2f, 3f, LinkHudStyle.inset(120));

        float filled = clamp(frac) * count;
        float ghost = clamp(frac + ghostFrac) * count;
        Color emptyBorder = ColorUtils.alpha(LinkHudStyle.isLightTheme() ? Color.BLACK : Color.WHITE, 55);

        for (int k = 0; k < count; k++) {
            // k — порядок от «стартовой» стороны (лево для HP/брони, право для еды/воздуха).
            float segX = rtl ? x + (count - 1 - k) * (segW + gap) : x + k * (segW + gap);

            // Пустая база сегмента + рамка (виден максимум на любом фоне).
            Render2D.drawRoundedRect(ms, segX, y, segW, segH, 2f, LinkHudStyle.inset(165));
            Render2D.drawBorder(ms, segX, y, segW, segH, 2f, 1f, 1f, emptyBorder);

            float segFill = clamp(filled - k);
            if (segFill > 0f) {
                float w = Math.max(1.5f, segW * segFill);
                float fx = rtl ? segX + (segW - w) : segX;
                Render2D.drawRoundedRect(ms, fx, y, w, segH, 2f, ColorUtils.alpha(color, alpha));
            }

            // Призрак урона (белый, между текущим и «оседающим» уровнем).
            float ghostFill = clamp(ghost - k) - segFill;
            if (ghostFill > 0.02f) {
                float w = Math.max(1.5f, segW * ghostFill);
                float base = rtl ? segX + (segW - clamp(filled - k) * segW) : segX + clamp(filled - k) * segW;
                float gx = rtl ? base - w : base;
                Render2D.drawRoundedRect(ms, gx, y, w, segH, 2f, ColorUtils.alpha(Color.WHITE, 150));
            }
        }
    }

    private static void drawLevelChip(Object ms, float cx, float cy, float expH, String num) {
        float size = 6f;
        float tw = Fonts.ROUNDBOLD.getWidth(num, size);
        float chipH = expH + 3f;
        float chipW = tw + 8f;
        float cxL = cx - chipW / 2f;
        float cyT = cy - chipH / 2f;
        Render2D.drawRoundedRect(ms, cxL, cyT, chipW, chipH, chipH / 2f, LinkHudStyle.inset(235));
        Render2D.drawBorder(ms, cxL, cyT, chipW, chipH, chipH / 2f, 1f, 1f,
                ColorUtils.alpha(LinkHudStyle.accent(), 95));
        Render2D.drawFont(ms, Fonts.ROUNDBOLD.getFont(size), num,
                cx - tw / 2f, cy - size / 2f - 0.5f,
                ColorUtils.alpha(LinkHudStyle.accentSecondary(), 255));
    }

    private static void updateAnim(State s) {
        long now = System.currentTimeMillis();
        float dt = lastMs == 0L ? 1f / 60f : Math.min(0.1f, (now - lastMs) / 1000f);
        lastMs = now;

        if (dispHealth < 0f) dispHealth = s.healthFrac;
        if (s.healthFrac < dispHealth) {
            damageFlash = 1f;
            dispHealth += (s.healthFrac - dispHealth) * (1f - (float) Math.exp(-6f * dt));
        } else {
            dispHealth = s.healthFrac;
        }
        damageFlash *= (float) Math.exp(-4f * dt);
        if (damageFlash < 0.01f) damageFlash = 0f;
    }

    // ================= Bars / Stack (альтернативы) =================
    private static void renderBars(Object ms, float x0, float bottom, State s) {
        float expH = 3f, rowH = 4f, gap = 3f;
        float expY = bottom - expH;
        float rowY = expY - gap - rowH;
        float half = (TOTAL_W - 6f) / 2f;
        bar(ms, x0, rowY, half, rowH, 2f, s.healthFrac, HEALTH);
        bar(ms, x0 + TOTAL_W - half, rowY, half, rowH, 2f, s.foodFrac, FOOD);
        bar(ms, x0, expY, TOTAL_W, expH, 1.5f, s.expFrac, LinkHudStyle.accent());
        if (s.level > 0) {
            String str = Integer.toString(s.level);
            float size = 6.5f;
            float w = Fonts.ROUNDBOLD.getWidth(str, size);
            // Цифра уровня — по вертикали на уровне ряда HP/еды (по центру ряда), а не над ним.
            Render2D.drawFont(ms, Fonts.ROUNDBOLD.getFont(size), str,
                    x0 + TOTAL_W / 2f - w / 2f, rowY + rowH / 2f - size / 2f,
                    ColorUtils.alpha(LinkHudStyle.accentSecondary(), 255));
        }
    }

    private static void renderStack(Object ms, float x0, float bottom, State s) {
        float pad = 3f, barH = 4f, rowGap = 3f;
        float panelH = pad * 2f + barH * 3f + rowGap * 2f;
        float panelY = bottom - panelH;
        LinkHudStyle.panel(ms, x0, panelY, TOTAL_W, panelH, LinkHudStyle.radius(TOTAL_W, panelH), 1f);
        float numW = 15f;
        float barW = TOTAL_W - pad * 2f - numW;
        float bx = x0 + pad;
        float y = panelY + pad;
        stackRow(ms, bx, y, barW, barH, s.healthFrac, HEALTH, Integer.toString(s.health), x0 + TOTAL_W - pad);
        y += barH + rowGap;
        stackRow(ms, bx, y, barW, barH, s.foodFrac, FOOD, Integer.toString(s.food), x0 + TOTAL_W - pad);
        y += barH + rowGap;
        stackRow(ms, bx, y, barW, barH, s.expFrac, LinkHudStyle.accent(), Integer.toString(s.level), x0 + TOTAL_W - pad);
    }

    private static void stackRow(Object ms, float x, float y, float w, float h, float frac,
                                 Color color, String num, float rightEdge) {
        bar(ms, x, y, w, h, 2f, frac, color);
        float size = 6f;
        float tw = Fonts.ROUNDBOLD.getWidth(num, size);
        Render2D.drawFont(ms, Fonts.ROUNDBOLD.getFont(size), num,
                rightEdge - tw, y + (h - size) / 2f - 0.5f,
                ColorUtils.alpha(LinkHudStyle.textBase(), 235));
    }

    private static void bar(Object ms, float x, float y, float w, float h, float r, float frac, Color color) {
        Render2D.drawRoundedRect(ms, x, y, w, h, r, LinkHudStyle.inset(200));
        frac = clamp(frac);
        if (frac <= 0f) return;
        float fw = Math.max(h, w * frac);
        Render2D.drawRoundedRect(ms, x, y, fw, h, r, ColorUtils.alpha(color, 245));
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
