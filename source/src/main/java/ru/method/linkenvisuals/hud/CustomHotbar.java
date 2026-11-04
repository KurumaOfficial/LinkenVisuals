package ru.method.linkenvisuals.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import ru.method.linkenvisuals.utils.render.ColorRGBA;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import ru.method.linkenvisuals.utils.render.Render2D;

import java.awt.Color;

/**
 * Кастомный хотбар в стиле Link GUI: фон-панель в цветах темы, ячейки, предметы (с количеством/
 * прочностью/кулдауном через {@code drawStackOverlay}) и плавно «едущее» выделение выбранного слота.
 * Рисуется вместо ванильного, когда включён модуль Custom Hotbar — в ЛЮБОМ стиле GUI (Link и Panel),
 * поэтому перекрывает и хотбар от ресурс-пака (см. InGameHudMixin).
 */
public final class CustomHotbar {

    private CustomHotbar() {
    }

    // Размеры подобраны так, что общая ширина фон-панели = 182px — ровно как ванильный экспа-бар
    // и ванильный хотбар: 9*SLOT + 8*GAP + 2*PAD = 9*18 + 8*2 + 2*2 = 182. Высота панели 18+2*2 = 22px.
    private static final float SLOT = 18f;
    private static final float GAP = 2f;
    private static final float PAD = 2f;

    // Анимированная позиция выделения (дробный индекс слота) — плавный наезд без перелёта/дрожания.
    private static float animSelected = 0f;
    // «Поп» при смене слота: всплеск 0..1, плавно затухающий до нуля.
    private static float popTimer = 0f;
    private static int lastSelected = -1;
    private static long lastTime = 0L;

    public static void render(DrawContext context, RenderTickCounter tick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        PlayerEntity player = mc.player;
        if (player == null) return;

        int sw = context.getScaledWindowWidth();
        int sh = context.getScaledWindowHeight();
        float totalW = 9f * SLOT + 8f * GAP;
        float x0 = sw / 2f - totalW / 2f;
        float y0 = sh - SLOT - 4f;

        var ms = context.getMatrices();

        // Фон-панель.
        LinkHudStyle.panel(ms, x0 - PAD, y0 - PAD, totalW + PAD * 2f, SLOT + PAD * 2f,
                LinkHudStyle.radius(totalW + PAD * 2f, SLOT + PAD * 2f), 1f);

        // Ячейки. Цвет один на все — LinkHudStyle.inset() внутри делает lookup темы + 2 аллокации Color,
        // звать его 10 раз за кадр незачем.
        Color cellColor = LinkHudStyle.inset(150);
        for (int i = 0; i < 9; i++) {
            float cx = x0 + i * (SLOT + GAP);
            Render2D.drawRoundedRect(ms, cx, y0, SLOT, SLOT, 4f, cellColor);
        }

        // --- Анимация выделения: плавный наезд (независимый от FPS) + «поп» при смене слота ---
        long now = System.currentTimeMillis();
        float dt = lastTime == 0L ? 1f / 60f : Math.min(0.08f, (now - lastTime) / 1000f);
        lastTime = now;

        int selected = player.getInventory().getSelectedSlot();
        if (lastSelected == -1) {
            // Первый кадр — без наезда от нуля.
            lastSelected = selected;
            animSelected = selected;
        } else if (selected != lastSelected) {
            popTimer = 1f;
            lastSelected = selected;
        }
        // Экспоненциальный наезд к цели: плавно, без перелёта и дрожания, одинаково на любом FPS.
        float slide = 1f - (float) Math.exp(-19f * dt);
        animSelected += (selected - animSelected) * slide;
        if (Math.abs(selected - animSelected) < 0.001f) animSelected = selected;
        // Затухание «попа» по времени.
        popTimer *= (float) Math.exp(-7f * dt);
        if (popTimer < 0.01f) popTimer = 0f;
        // Сглаженный (ease-out) поп для масштабирования.
        float pop = popTimer * popTimer;

        float selX = x0 + animSelected * (SLOT + GAP);
        Color accent = ColorUtils.getPrimaryColor();
        // Подмешиваем к белому — чтобы выделение было контрастным и не сливалось с ячейками при любой теме.
        Color hi = ColorUtils.fade(accent, Color.WHITE, 0.28f);
        float grow = pop * 3f;
        float bx = selX - 1f - grow, by = y0 - 1f - grow;
        float bw = SLOT + 2f + grow * 2f, bh = SLOT + 2f + grow * 2f;
        // Заметная заливка выбранного слота + сплошная яркая рамка.
        Render2D.drawRoundedRect(ms, bx, by, bw, bh, 5f + grow, ColorUtils.alpha(hi, Math.round(140 + 60 * pop)));
        Render2D.drawBorder(ms, bx, by, bw, bh, 5f + grow, 2.0f + pop * 0.8f,
                new ColorRGBA(ColorUtils.alpha(hi, 255)));

        // Предметы хотбара (0..8). Выбранный слот слегка «подпрыгивает» (масштаб) при смене.
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            float cx = x0 + i * (SLOT + GAP);
            int ix = Math.round(cx + (SLOT - 16f) / 2f);
            int iy = Math.round(y0 + (SLOT - 16f) / 2f);
            boolean isSel = i == selected;
            if (isSel && pop > 0.001f) {
                float s = 1f + pop * 0.18f;
                float ccx = ix + 8f, ccy = iy + 8f;
                ms.pushMatrix();
                ms.translate(ccx, ccy);
                ms.scale(s, s);
                ms.translate(-ccx, -ccy);
                context.drawItem(player, stack, ix, iy, ix + iy * sw);
                ms.popMatrix();
            } else {
                context.drawItem(player, stack, ix, iy, ix + iy * sw);
            }
            context.drawStackOverlay(mc.textRenderer, stack, ix, iy);
        }

        // Оффхенд (слева от бара, если занят).
        ItemStack off = player.getOffHandStack();
        if (!off.isEmpty()) {
            float ox = x0 - PAD - GAP - SLOT;
            Render2D.drawRoundedRect(ms, ox, y0, SLOT, SLOT, 4f, cellColor);
            int ix = Math.round(ox + (SLOT - 16f) / 2f);
            int iy = Math.round(y0 + (SLOT - 16f) / 2f);
            context.drawItem(player, off, ix, iy, 0);
            context.drawStackOverlay(mc.textRenderer, off, ix, iy);
        }
    }
}
