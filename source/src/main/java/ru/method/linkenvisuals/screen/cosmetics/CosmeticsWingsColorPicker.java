package ru.method.linkenvisuals.screen.cosmetics;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.util.math.MathHelper;
import ru.method.linkenvisuals.utils.math.MathUtils;
import ru.method.linkenvisuals.utils.render.ColorRGBA;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import ru.method.linkenvisuals.utils.render.Render2D;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;
import ru.method.linkenvisuals.cosmetics.model.CosmeticProfile;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;

public final class CosmeticsWingsColorPicker {
    private static final int HUE_SEGMENTS = 24;

    private int colorArgb = CosmeticProfile.DEFAULT_TINT;
    private boolean draggingHue;
    private boolean draggingValue;
    private boolean dirty;

    private float hue;
    private float saturation = 1f;
    private float value = 1f;
    private boolean hsvInitialized;

    public void setColor(int argb) {
        if (this.colorArgb != argb) {
            this.colorArgb = argb;
            hsvInitialized = false;
        }
    }

    public int colorArgb() {
        return colorArgb;
    }

    public boolean isAdjusting() {
        return draggingHue || draggingValue;
    }

    public boolean consumeDirty() {
        if (!dirty) {
            return false;
        }
        dirty = false;
        return true;
    }

    public void updateDrag(int mouseX, int mouseY, float x, float y, float width) {
        float hueY = y + 22f;
        float hueH = 10f;
        float valueY = hueY + hueH + 5f;
        float valueH = 7f;
        float hueX = x + 4f;
        float hueW = width - 8f;

        if (draggingHue) {
            updateHue(mouseX, hueX, hueW);
        }
        if (draggingValue) {
            updateValue(mouseX, hueX, hueW);
        }
    }

    public void render(DrawContext context, float x, float y, float width, int mouseX, int mouseY, int alpha, String labelKey) {
        syncHsvFromColorIfNeeded();

        // Лейбл цвета (по слоту) сдвинут на 3px вправо; пресеты цветов убраны — только пикер.
        Render2D.drawFont(
                context.getMatrices(),
                Fonts.MEDIUM.getFont(6.8f),
                I18n.translate(labelKey),
                x + 3f,
                y + 3f,
                ColorUtils.getTextColor(alpha)
        );

        float swatchSize = 14f;
        // Свотч текущего цвета — чуть левее от правого края (не вплотную).
        float swatchX = x + width - swatchSize - 6f;
        float swatchY = y;
        Render2D.drawRoundedRect(context.getMatrices(), swatchX, swatchY, swatchSize, swatchSize, 4f, new Color(colorArgb, true));
        Render2D.drawBorder(context.getMatrices(), swatchX, swatchY, swatchSize, swatchSize, 4f, 0.55f, new ColorRGBA(ColorUtils.alpha(Color.WHITE, 90)));

        float hueX = x + 4f;
        float hueY = y + 22f;
        float hueW = width - 8f;
        float hueH = 10f;
        drawHueBar(context, hueX, hueY, hueW, hueH, alpha);

        float valueY = hueY + hueH + 5f;
        float valueH = 7f;
        drawValueBar(context, hueX, valueY, hueW, valueH, alpha);

        if (draggingHue) {
            updateHue(mouseX, hueX, hueW);
        }
        if (draggingValue) {
            updateValue(mouseX, hueX, hueW);
        }
    }

    public float expandedHeight() {
        return 22f + 10f + 5f + 7f + 4f;
    }

    public boolean mouseClicked(float x, float y, float width, double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }

        float hueX = x + 4f;
        float hueY = y + 22f;
        float hueW = width - 8f;
        float hueH = 10f;
        if (MathUtils.isHovered(hueX, hueY, hueW, hueH, (float) mouseX, (float) mouseY)) {
            draggingHue = true;
            updateHue((int) mouseX, hueX, hueW);
            return true;
        }

        float valueY = hueY + hueH + 5f;
        float valueH = 7f;
        if (MathUtils.isHovered(hueX, valueY, hueW, valueH, (float) mouseX, (float) mouseY)) {
            draggingValue = true;
            updateValue((int) mouseX, hueX, hueW);
            return true;
        }

        return false;
    }

    public boolean mouseReleased(int button) {
        if (button != 0) {
            return false;
        }
        boolean wasDragging = draggingHue || draggingValue;
        draggingHue = false;
        draggingValue = false;
        return wasDragging;
    }

    public boolean keyPressed(int keyCode) {
        return false;
    }

    private void drawHueBar(DrawContext context, float x, float y, float width, float height, int alpha) {
        Render2D.drawRoundedRect(context.getMatrices(), x, y, width, height, 3f, new Color(255, 255, 255, Math.round(30f * (alpha / 255f))));
        float segmentW = width / HUE_SEGMENTS;
        for (int i = 0; i < HUE_SEGMENTS; i++) {
            float left = x + i * segmentW;
            float right = i == HUE_SEGMENTS - 1 ? x + width : left + segmentW;
            Color leftColor = Color.getHSBColor(i / (float) HUE_SEGMENTS, 1f, 1f);
            Color rightColor = Color.getHSBColor((i + 1) / (float) HUE_SEGMENTS, 1f, 1f);
            Render2D.drawHorizontalGradient(context.getMatrices(), left, y, right - left, height, leftColor, rightColor);
        }
        float markerX = x + hue * width;
        Render2D.drawRoundedRect(context.getMatrices(), markerX - 1.5f, y - 1f, 3f, height + 2f, 1.5f, Color.WHITE);
    }

    private void drawValueBar(DrawContext context, float x, float y, float width, float height, int alpha) {
        Color hueColor = Color.getHSBColor(hue, 1f, 1f);
        Color dark = new Color(18, 20, 28, Math.round(220f * (alpha / 255f)));
        Render2D.drawRoundedRect(context.getMatrices(), x, y, width, height, 3f, dark);
        // Левая половина: БЕЛЫЙ → насыщенный цвет (saturation 0→1). Правая: цвет → ЧЁРНЫЙ (value 1→0).
        // Так в одном слайдере доступны и белый (слева), и любой оттенок, и затемнение.
        float half = width * 0.5f;
        Render2D.drawHorizontalGradient(context.getMatrices(), x, y, half, height, Color.WHITE, hueColor);
        Render2D.drawHorizontalGradient(context.getMatrices(), x + half, y, width - half, height, hueColor, Color.BLACK);
        float p = value >= 0.999f ? saturation * 0.5f : 0.5f + (1f - value) * 0.5f;
        float markerX = x + p * width;
        Render2D.drawRoundedRect(context.getMatrices(), markerX - 1.5f, y - 1f, 3f, height + 2f, 1.5f, Color.WHITE);
    }

    private void updateHue(int mouseX, float hueX, float hueW) {
        hue = MathHelper.clamp((mouseX - hueX) / hueW, 0f, 1f);
        // Перетаскивание ОТТЕНКА подразумевает «хочу цвет» — поднимаем насыщенность, иначе из белого
        // (saturation=0) любой оттенок остаётся белым. Белый при этом доступен через нижний слайдер (слева).
        if (saturation < 0.15f) {
            saturation = 1f;
        }
        if (value < 0.15f) {
            value = 1f;
        }
        applyHsv();
    }

    private void updateValue(int mouseX, float barX, float barW) {
        float p = MathHelper.clamp((mouseX - barX) / barW, 0f, 1f);
        if (p <= 0.5f) {
            saturation = p * 2f;   // 0 (белый) → 1 (насыщенный)
            value = 1f;
        } else {
            saturation = 1f;
            value = 1f - (p - 0.5f) * 2f;  // 1 (цвет) → 0 (чёрный)
        }
        applyHsv();
    }

    private void applyHsv() {
        int rgb = Color.HSBtoRGB(hue, saturation, value) & 0x00FFFFFF;
        colorArgb = 0xFF000000 | rgb;
        dirty = true;
    }

    private void syncHsvFromColorIfNeeded() {
        if (hsvInitialized) {
            return;
        }
        int rgb = colorArgb & 0x00FFFFFF;
        float[] hsb = Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
        hue = hsb[0];
        saturation = hsb[1];
        value = hsb[2];
        hsvInitialized = true;
    }
}
