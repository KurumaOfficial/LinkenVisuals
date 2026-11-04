package ru.method.linkenvisuals.screen.linkgui;

import org.joml.Matrix3x2fStack;
import net.minecraft.util.math.MathHelper;
import ru.method.linkenvisuals.screen.linkgui.theme.LinkGuiThemeScope;
import ru.method.linkenvisuals.utils.render.ColorRGBA;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import ru.method.linkenvisuals.utils.render.Render2D;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public final class LinkGuiCursorTrail {
    private static final long TRAIL_LIFETIME_MS = 780L;
    private static final int MAX_POINTS = 64;
    private static final float MIN_STEP = 2.4f;

    private final List<Point> points = new ArrayList<>(MAX_POINTS);
    private float lastX = Float.NaN;
    private float lastY = Float.NaN;

    private record Point(float x, float y, long timeMs) {}

    public void clear() {
        points.clear();
        lastX = Float.NaN;
        lastY = Float.NaN;
    }

    public void update(float x, float y) {
        // Защита от мусорных координат: когда мышь уходит за экран, MC отдаёт ~Integer.MAX_VALUE.
        // Тогда dist огромный → segments в миллиард → цикл добавляет миллиарды точек до trim() → OutOfMemory.
        if (!Float.isFinite(x) || !Float.isFinite(y) || Math.abs(x) > 100000f || Math.abs(y) > 100000f) {
            lastX = Float.NaN;
            lastY = Float.NaN;
            return;
        }

        long now = System.currentTimeMillis();
        expireOldPoints(now);

        if (Float.isNaN(lastX)) {
            points.add(new Point(x, y, now));
            lastX = x;
            lastY = y;
            trim();
            return;
        }

        float dx = x - lastX;
        float dy = y - lastY;
        float dist = (float) Math.hypot(dx, dy);
        if (dist < 0.25f) {
            return;
        }

        // Ограничиваем число сегментов (даже при большом прыжке не больше MAX_POINTS) — чтобы цикл
        // не мог добавить астрономическое число точек за один кадр.
        int segments = Math.max(1, Math.min(MAX_POINTS, (int) Math.ceil(dist / MIN_STEP)));
        for (int i = 1; i <= segments; i++) {
            float t = i / (float) segments;
            points.add(new Point(lastX + dx * t, lastY + dy * t, now));
        }

        lastX = x;
        lastY = y;
        trim();
    }

    public void render(Matrix3x2fStack stack) {
        long now = System.currentTimeMillis();
        expireOldPoints(now);
        if (points.isEmpty()) {
            return;
        }

        Color accent = LinkGuiThemeScope.accent();
        Color secondary = ColorUtils.getSecondaryColor();
        int lastIndex = points.size() - 1;

        for (int i = 0; i < points.size(); i++) {
            Point point = points.get(i);
            float progress = lastIndex <= 0 ? 1f : i / (float) lastIndex;
            float ageT = MathHelper.clamp((now - point.timeMs) / (float) TRAIL_LIFETIME_MS, 0f, 1f);
            float fade = (1f - ageT) * (1f - ageT);
            if (fade <= 0.015f) {
                continue;
            }

            float size = MathHelper.lerp(progress, 2.2f, 7.5f);
            float alpha = fade * MathHelper.lerp(progress, 0.18f, 0.92f);
            drawSoftOrb(stack, point.x, point.y, size * 2.6f, accent, secondary, alpha * 0.35f);
            drawSoftOrb(stack, point.x, point.y, size * 1.45f, accent, accent, alpha * 0.55f);

            if (i % 5 == 0 && progress > 0.15f) {
                float sparklePhase = (point.timeMs % 1000L) / 1000f;
                float ox = (float) Math.sin(sparklePhase * Math.PI * 2f + i * 0.7f) * (2.5f + progress * 2f);
                float oy = (float) Math.cos(sparklePhase * Math.PI * 2f + i * 0.55f) * (2f + progress * 1.5f);
                int sparkAlpha = Math.round(255f * fade * MathHelper.lerp(progress, 0.2f, 0.85f));
                if (sparkAlpha > 8) {
                    Color spark = blend(accent, Color.WHITE, 0.65f, sparkAlpha);
                    Render2D.drawCircle(stack, point.x + ox, point.y + oy, 1.1f + progress * 0.8f, spark);
                }
            }
        }

        for (int i = 1; i < points.size(); i++) {
            Point prev = points.get(i - 1);
            Point current = points.get(i);
            float progress = i / (float) lastIndex;
            float ageT = MathHelper.clamp((now - current.timeMs) / (float) TRAIL_LIFETIME_MS, 0f, 1f);
            float fade = (1f - ageT) * (1f - ageT);
            if (fade <= 0.02f) {
                continue;
            }

            float thickness = MathHelper.lerp(progress, 1.2f, 3.6f);
            int alpha = Math.round(255f * fade * MathHelper.lerp(progress, 0.12f, 0.72f));
            if (alpha <= 6) {
                continue;
            }

            Color ribbon = blend(accent, secondary, progress * 0.45f, alpha);
            Render2D.drawLine(stack, prev.x, prev.y, current.x, current.y, thickness, new ColorRGBA(ribbon));
        }

        Point head = points.get(lastIndex);
        float headAge = MathHelper.clamp((now - head.timeMs) / (float) TRAIL_LIFETIME_MS, 0f, 1f);
        float headFade = 1f - headAge;
        if (headFade <= 0.02f) {
            return;
        }

        float pulse = 0.86f + 0.14f * (float) Math.sin(now * 0.0085);
        drawCursorHead(stack, head.x, head.y, accent, secondary, headFade, pulse, now);
    }

    private void drawCursorHead(Matrix3x2fStack stack, float x, float y, Color accent, Color secondary,
                                float fade, float pulse, long now) {
        float halo = 22f * pulse;
        drawSoftOrb(stack, x, y, halo, accent, secondary, fade * 0.22f);
        drawSoftOrb(stack, x, y, 14f * pulse, accent, accent, fade * 0.38f);

        int ringAlpha = Math.round(255f * fade * 0.55f);
        Color ring = blend(accent, Color.WHITE, 0.35f, ringAlpha);
        Render2D.drawCircle(stack, x, y, 5.8f * pulse, ring);

        int coreAlpha = Math.round(255f * fade);
        Color core = blend(accent, Color.WHITE, 0.55f, coreAlpha);
        Render2D.drawCircle(stack, x, y, 3.2f * pulse, core);

        int hotAlpha = Math.round(255f * fade * 0.95f);
        Render2D.drawCircle(stack, x, y, 1.35f, new Color(255, 255, 255, hotAlpha));

        float orbit = now * 0.0042f;
        for (int i = 0; i < 3; i++) {
            float angle = orbit + i * ((float) Math.PI * 2f / 3f);
            float dist = 9.5f * pulse;
            float px = x + (float) Math.cos(angle) * dist;
            float py = y + (float) Math.sin(angle) * dist;
            int dotAlpha = Math.round(255f * fade * 0.42f);
            Render2D.drawCircle(stack, px, py, 1.4f, blend(secondary, accent, 0.5f, dotAlpha));
        }
    }

    private static void drawSoftOrb(Matrix3x2fStack stack, float x, float y, float size,
                                    Color inner, Color outer, float alpha) {
        if (alpha <= 0.01f || size <= 0.5f) {
            return;
        }
        int a = Math.round(255f * MathHelper.clamp(alpha, 0f, 1f));
        if (a <= 4) {
            return;
        }
        Color mid = blend(inner, outer, 0.35f, a);
        float half = size / 2f;
        // ПЕРФ: здесь был drawBlurredRect — полноценный GPU-блюр-проход НА КАЖДЫЙ орб. А орбов рисуется
        // ДВА на каждую точку следа (+2 на голову), т.е. несколько десятков блюр-проходов за кадр —
        // именно это роняло FPS в разы при открытом GUI. Мягкое свечение собираем из затухающих кругов:
        // визуально то же самое, но без единого блюр-прохода.
        int layers = 4;
        for (int i = layers; i >= 1; i--) {
            float r = half * (i / (float) layers);
            int la = Math.round(a * 0.55f / i);
            if (la <= 0) continue;
            Render2D.drawCircle(stack, x, y, r, new Color(mid.getRed(), mid.getGreen(), mid.getBlue(), Math.min(255, la)));
        }
    }

    private static Color blend(Color a, Color b, float t, int alpha) {
        t = MathHelper.clamp(t, 0f, 1f);
        int r = Math.round(MathHelper.lerp(t, a.getRed(), b.getRed()));
        int g = Math.round(MathHelper.lerp(t, a.getGreen(), b.getGreen()));
        int bl = Math.round(MathHelper.lerp(t, a.getBlue(), b.getBlue()));
        return new Color(r, g, bl, Math.max(0, Math.min(255, alpha)));
    }

    private void expireOldPoints(long now) {
        long cutoff = now - TRAIL_LIFETIME_MS;
        points.removeIf(point -> point.timeMs < cutoff);
    }

    private void trim() {
        while (points.size() > MAX_POINTS) {
            points.remove(0);
        }
    }
}
