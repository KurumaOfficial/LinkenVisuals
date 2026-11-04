package ru.method.linkenvisuals.hud.impl.island;

import ru.method.linkenvisuals.api.events.impl.EventRender2D;
import ru.method.linkenvisuals.utils.math.MathUtils;
import ru.method.linkenvisuals.utils.render.Render2D;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;

import java.awt.Color;

public final class DynamicIslandWaves {

    private final float[] wavePhases = new float[]{0.1f, 1.05f, 2.1f, 2.95f};
    private final float[] waveValues = new float[]{0.25f, 0.25f, 0.25f, 0.25f};
    private long lastWaveUpdate;

    public float getWaveBlockWidth(float widthScale) {
        float barWidth = 3.0f * widthScale * 0.95f;
        float gap = 1.0f * widthScale * 0.95f;
        return 4 * barWidth + (4 - 1) * gap;
    }

    public void render(EventRender2D e, Color baseColor, float textX, float textY, float padding, float height, float width, float islandX, float alpha,
                       float visibleTextWidth, float widthScale, float heightScale, float waveOffset, float coverCenterY, boolean expanded) {
        if (alpha <= 0.02f || visibleTextWidth <= 0.01f) return;
        float maxX = islandX + width - padding;
        float barWidth = 3.0f * widthScale * 0.95f;
        float gap = 1.0f * widthScale * 0.95f;
        float totalWaveWidth = getWaveBlockWidth(widthScale);
        float wavesX = textX + visibleTextWidth + 2.8f;
        float maxWaveX = maxX - waveOffset - totalWaveWidth;
        if (expanded) {
            wavesX = maxWaveX;
        } else if (wavesX > maxWaveX) {
            wavesX = maxWaveX;
        }

        float baseHeight = (height - padding * 2f) * 0.55f * heightScale * 0.95f;
        float maxBarHeight = Math.max(2f, (height - padding * 1.0f) * 0.65f * heightScale * 0.95f);
        float baseCenterLine = textY + Fonts.ROUNDBOLD.getHeight(8f) * 0.55f;
        float centerLine = expanded ? MathUtils.lerp(baseCenterLine, coverCenterY, 0.35f) : baseCenterLine;

        for (int i = 0; i < 4; i++) {
            float wave = waveValues[i];
            float centerFalloff = 1f - Math.abs(i - (4 - 1) / 2f) * 0.18f;
            float barHeight = Math.max(2.0f, Math.min(maxBarHeight, baseHeight * (0.38f + wave * 1.05f) * centerFalloff));
            float bx = wavesX + i * (barWidth + gap);
            float by = centerLine - barHeight / 2f;
            Render2D.drawRoundedRect(
                    e.getContext().getMatrices(),
                    bx,
                    by,
                    barWidth,
                    barHeight,
                    barWidth * 0.4f,
                    new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), (int) (200 * alpha))
            );
        }
    }

    public void update(boolean playing) {
        long now = System.currentTimeMillis();
        if (lastWaveUpdate == 0L) {
            lastWaveUpdate = now;
            return;
        }
        float delta = Math.max(1f, now - lastWaveUpdate);
        lastWaveUpdate = now;

        if (!playing) {
            for (int i = 0; i < waveValues.length; i++) {
                waveValues[i] = MathUtils.lerp(waveValues[i], 0.22f, 0.16f);
            }
            return;
        }

        float speedBase = 0.012f;
        float lerpSpeed = 0.16f;
        for (int i = 0; i < wavePhases.length; i++) {
            wavePhases[i] += speedBase * delta * (1f + i * 0.15f);
            double combined = Math.sin(wavePhases[i])
                    + Math.sin(wavePhases[i] * 1.35 + 0.9f) * 0.6
                    + Math.sin(wavePhases[i] * 0.72 + 1.4f) * 0.4;
            float target = MathUtils.clamp((float) ((combined + 2.0f) / 4.0f), 0.12f, 1.0f);
            float easedTarget = (float) Math.pow(target, 1.35f);
            waveValues[i] = MathUtils.lerp(waveValues[i], easedTarget, lerpSpeed);
        }
    }
}
