package ru.method.linkenvisuals.screen.clickgui.components.impl;

import ru.method.linkenvisuals.modules.settings.impl.NumberSetting;
import ru.method.linkenvisuals.screen.clickgui.components.Component;
import ru.method.linkenvisuals.utils.animations.Animation;
import ru.method.linkenvisuals.utils.animations.Easing;
import ru.method.linkenvisuals.utils.animations.infinity.InfinityAnimation;
import ru.method.linkenvisuals.utils.math.MathUtils;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;
import ru.method.linkenvisuals.screen.linkgui.LinkGuiChrome;
import ru.method.linkenvisuals.screen.linkgui.LinkGuiSettingStyle;
import ru.method.linkenvisuals.screen.windowgui.WindowGuiRender;
import ru.method.linkenvisuals.screen.linkgui.theme.LinkGuiThemeScope;
import ru.method.linkenvisuals.utils.render.LinkGuiRenderContext;
import ru.method.linkenvisuals.utils.render.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.util.math.MathHelper;

import java.awt.Color;
import java.math.BigDecimal;

public class SliderComponent extends Component {

    private final NumberSetting setting;
    private final InfinityAnimation animation = new InfinityAnimation(Easing.EASE_OUT_EXPO);
    private final Animation dragScaleAnimation = new Animation(90, 1f, false, Easing.EASE_OUT_EXPO);
    private float dragVisualProgress = 0f;
    private float displayedValue;
    private long lastDisplayUpdateNs;
    private boolean displayInitialized;
    private boolean drag;

    public SliderComponent(NumberSetting setting) {
        super(setting.getName());
        this.setting = setting;
        this.addHeight = () -> LinkGuiRenderContext.isActive() ? 6f : 3f;
        this.visible = setting::isVisible;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        dragScaleAnimation.update(drag);

        float trackStart = x + 4f;
        float trackWidth = width - 8f;
        float trackY = y + 13f;
        float trackHeight = 4f;

        if (LinkGuiRenderContext.isActive()) {
            trackStart = x + 2f;
            trackWidth = width - 4f;
            trackY = y + LinkGuiSettingStyle.ROW_HEIGHT - 6f;
        }

        float knobCenterY = trackY + trackHeight / 2f;

        if (drag) {
            float safeTrackWidth = Math.max(1f, trackWidth);
            float t = MathHelper.clamp((mouseX - trackStart) / safeTrackWidth, 0f, 1f);
            dragVisualProgress = t;
            float value = MathHelper.clamp(
                    MathUtils.round(t * (setting.getMax() - setting.getMin()) + setting.getMin(), setting.getIncrement()),
                    setting.getMin(),
                    setting.getMax()
            );
            setting.setValue(value);
        }

        Color textColor = ColorUtils.getTextColor(255);
        Color trackColor = new Color(23, 23, 23, 100);

        float settingProgress = getSettingProgress();
        float targetProgress = drag ? trackWidth * dragVisualProgress : trackWidth * settingProgress;
        int progressDuration = drag ? 40 : 150;
        float progress = animation.animate(targetProgress, progressDuration);
        float clampedProgress = MathUtils.clamp(progress, 0f, trackWidth);

        float dragProgress = dragScaleAnimation.getValue();
        float knobScale = MathUtils.lerp(1f, 1.35f, dragProgress);
        float knobCenterX = MathUtils.clamp(trackStart + clampedProgress, trackStart, trackStart + trackWidth);

        String label = I18n.translate(setting.getName());
        float animatedValue = updateDisplayedValue();
        String valueText = formatDisplayValue(animatedValue, setting.getIncrement());
        boolean hovered = MathUtils.isHovered(x, y, width, height, mouseX, mouseY);

        if (LinkGuiRenderContext.isActive()) {
            float valueW = Fonts.MEDIUM.getWidth(valueText, LinkGuiSettingStyle.VALUE_SIZE);
            // Название занимает всё место до значения справа (раньше было жёстко 55% — длинные имена обрезались).
            float labelMaxWidth = Math.max(width * 0.45f, width - valueW - 12f);
            // Текст слайдера приподнят на 2px.
            LinkGuiSettingStyle.drawRowLabel(context.getMatrices(), label, x + 2f, y - 2f, labelMaxWidth);
            WindowGuiRender.drawText(context.getMatrices(), Fonts.MEDIUM, valueText,
                    x + width - valueW - 2f, LinkGuiSettingStyle.rowCenterY(y) - 2f,
                    ColorUtils.getTextColor(170), LinkGuiSettingStyle.VALUE_SIZE);
            Render2D.drawRoundedRect(context.getMatrices(), trackStart, trackY, trackWidth, trackHeight, 1f, new Color(255, 255, 255, 28));
            Render2D.drawRoundedRect(context.getMatrices(), trackStart, trackY, clampedProgress, trackHeight, 1f, LinkGuiThemeScope.accent());
            // Точка слайдера всегда видна + контрастный глоу (общий метод, −25%, во всех слайдерах).
            float knobWidth = 8f * knobScale;
            LinkGuiChrome.drawSliderKnob(context.getMatrices(), knobCenterX, knobCenterY, knobWidth);
            return;
        }

        float valueWidth = Fonts.REGULAR.getWidth(valueText, 6.5f) + 8f;
        float labelMaxWidth = Math.max(0f, width - 12f - valueWidth);
        float labelWidth = Fonts.REGULAR.getWidth(label, 7.5f);
        float offset = getMarqueeOffset(labelWidth, labelMaxWidth, hovered);
        Render2D.startScissor(context, x + 4f, y, labelMaxWidth, height);
        Render2D.drawFont(context.getMatrices(), Fonts.REGULAR.getFont(7.5f), label, x + 4f - offset, y + 3f, textColor);
        if (offset > 0f && labelWidth > labelMaxWidth) {
            Render2D.drawFont(context.getMatrices(), Fonts.REGULAR.getFont(7.5f), label, x + 4f - offset + labelWidth + MARQUEE_GAP, y + 3f, textColor);
        }
        Render2D.stopScissor(context);
        Render2D.drawRoundedRect(context.getMatrices(), trackStart, trackY, trackWidth, trackHeight, 0.5f, trackColor);
        Render2D.drawRoundedRect(context.getMatrices(), trackStart, trackY, clampedProgress, trackHeight, 0.5f, ColorUtils.getGlobalColor());

        if (drag) {
            float knobWidth = 10f * knobScale;
            float knobHeight = 6.5f * knobScale;
            float knobRadius = knobHeight / 2f;
            float knobX = knobCenterX - knobWidth / 2f;
            float knobY = knobCenterY - knobHeight / 2f;

            Color knobGlass = new Color(220, 220, 220, 210);
            Render2D.drawRoundedRect(
                    context.getMatrices(),
                    knobX,
                    knobY,
                    knobWidth,
                    knobHeight,
                    knobRadius,
                    knobGlass
            );
        } else {
            float knobRadius = 3f * knobScale;
            float ringRadius = (3f + 0.6f) * knobScale;
            float ringSize = ringRadius * 2f;
            float knobSize = knobRadius * 2f;
            Color knobRing = ColorUtils.alpha(ColorUtils.getGlassOutlineColor(), 170);

            Render2D.drawRoundedRect(
                    context.getMatrices(),
                    knobCenterX - ringRadius,
                    knobCenterY - ringRadius,
                    ringSize,
                    ringSize,
                    ringRadius,
                    knobRing
            );
            Render2D.drawRoundedRect(
                    context.getMatrices(),
                    knobCenterX - knobRadius,
                    knobCenterY - knobRadius,
                    knobSize,
                    knobSize,
                    knobRadius,
                    ColorUtils.getTextColor(255)
            );
        }

        Render2D.drawFont(context.getMatrices(), Fonts.REGULAR.getFont(6f), valueText,
                x + width - Fonts.REGULAR.getWidth(valueText, 6.5f) - 4.5f, y + 5f, textColor);
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return;
        // Зона захвата — вся строка слайдера. В Link-режиме трек рисуется внизу строки
        // (y + ROW_HEIGHT - 6), а раньше клик ловился только по полоске y+12..y+18 и промахивался.
        float hitH = Math.max(height, LinkGuiSettingStyle.ROW_HEIGHT);
        if (!MathUtils.isHovered(x, y, width, hitH, (float) mouseX, (float) mouseY)) return;
        dragVisualProgress = getMouseProgress((float) mouseX);
        drag = true;
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {
        if (button != 0) return;
        drag = false;
    }

    @Override
    public void keyPressed(int keyCode, int scanCode, int modifiers) {

    }

    @Override
    public void keyReleased(int keyCode, int scanCode, int modifiers) {

    }

    @Override
    public void charTyped(char chr, int modifiers) {

    }

    private String formatValue(float value, float increment) {
        float rounded = MathUtils.round(value, increment);
        if (Math.abs(rounded) < 0.0001f) {
            rounded = 0f;
        }
        BigDecimal decimal = new BigDecimal(Float.toString(rounded)).stripTrailingZeros();
        return decimal.toPlainString();
    }

    private String formatDisplayValue(float value, float increment) {
        float displayStep = increment >= 1.0f ? 0.1f : Math.min(increment, 0.01f);
        float rounded = MathUtils.round(value, displayStep);
        if (Math.abs(rounded) < 0.0001f) {
            rounded = 0f;
        }
        BigDecimal decimal = new BigDecimal(Float.toString(rounded)).stripTrailingZeros();
        return decimal.toPlainString();
    }

    private float updateDisplayedValue() {
        float target = setting.getValue();
        long now = System.nanoTime();
        if (!displayInitialized) {
            displayedValue = target;
            lastDisplayUpdateNs = now;
            displayInitialized = true;
            return displayedValue;
        }

        float deltaSeconds = Math.max(0f, Math.min(0.08f, (now - lastDisplayUpdateNs) / 1_000_000_000f));
        lastDisplayUpdateNs = now;
        float factor = 1.0f - (float) Math.exp(-9.0f * deltaSeconds);
        displayedValue = MathHelper.lerp(MathHelper.clamp(factor, 0f, 1f), displayedValue, target);

        if (Math.abs(displayedValue - target) <= Math.max(0.0001f, setting.getIncrement() * 0.003f)) {
            displayedValue = target;
        }
        return displayedValue;
    }

    private float getSettingProgress() {
        float range = setting.getMax() - setting.getMin();
        if (Math.abs(range) <= 1.0E-6f) {
            return 0f;
        }
        return MathHelper.clamp((setting.getValue() - setting.getMin()) / range, 0f, 1f);
    }

    /** Светлая ли тема — по яркости фона (для выбора цвета глоу под точкой слайдера). */
    private static boolean isLightTheme() {
        Color bg = LinkGuiThemeScope.background();
        float lum = (0.299f * bg.getRed() + 0.587f * bg.getGreen() + 0.114f * bg.getBlue()) / 255f;
        return lum > 0.6f;
    }

    private float getMouseProgress(float mouseX) {
        float trackStart = x + 4f;
        float trackWidth = Math.max(1f, width - 8f);
        return MathHelper.clamp((mouseX - trackStart) / trackWidth, 0f, 1f);
    }
}
