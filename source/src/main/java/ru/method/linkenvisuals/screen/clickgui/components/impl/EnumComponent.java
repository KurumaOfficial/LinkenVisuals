package ru.method.linkenvisuals.screen.clickgui.components.impl;

import ru.method.linkenvisuals.modules.impl.combat.AutoSwap;
import ru.method.linkenvisuals.modules.settings.impl.EnumSetting;
import ru.method.linkenvisuals.screen.clickgui.components.Component;
import ru.method.linkenvisuals.modules.settings.api.Nameable;
import ru.method.linkenvisuals.screen.linkgui.LinkGuiChrome;
import ru.method.linkenvisuals.screen.linkgui.LinkGuiSettingStyle;
import ru.method.linkenvisuals.utils.animations.AnimatedString;
import ru.method.linkenvisuals.utils.animations.Animation;
import ru.method.linkenvisuals.utils.animations.Easing;
import ru.method.linkenvisuals.utils.math.MathUtils;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import ru.method.linkenvisuals.utils.render.LinkGuiRenderContext;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;
import ru.method.linkenvisuals.utils.render.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

public class EnumComponent extends Component {

    private final EnumSetting<?> setting;
    private final Animation openAnimation = new Animation(180, 1f, false, Easing.EASE_OUT_EXPO);
    private final Map<Enum<?>, Animation> pickAnimations = new HashMap<>();
    private boolean open;
    // Link-режим: кнопка-чип значения + анимация «переписывания» текста (как в поиске).
    private final Animation hoverAnimation = new Animation(160, 1f, false, Easing.EASE_OUT_EXPO);
    private final AnimatedString valueAnim;

    public EnumComponent(EnumSetting<?> setting) {
        super(setting.getName());
        this.setting = setting;
        this.valueAnim = new AnimatedString(220, Easing.EASE_OUT_CUBIC,
                resolveDisplay(setting.getValue(), Color.WHITE).text());
        for (Enum<?> enums : setting.getValue().getClass().getEnumConstants()) {
            Animation selectAnimation = new Animation(180, 1f, false, Easing.EASE_OUT_EXPO);
            selectAnimation.snapTo(enums == setting.getValue());
            pickAnimations.put(enums, selectAnimation);
        }
        openAnimation.snapTo(false);
        this.addHeight = () -> openAnimation.getValue() > 0 ? ((setting.getValue().getClass().getEnumConstants().length * 14f)) * (float)openAnimation.getValue() : 0;
        this.visible = setting::isVisible;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        openAnimation.update(open);

        if (LinkGuiRenderContext.isActive()) {
            renderLink(context, mouseX, mouseY);
            return;
        }

        Color textColor = ColorUtils.getTextColor(255);

        String label = I18n.translate(setting.getName()) + ": ";
        DisplayText currentDisplay = resolveDisplay(setting.getValue(), textColor);
        float labelWidth = Fonts.REGULAR.getWidth(label, 7.5f);
        float valueWidth = Fonts.REGULAR.getWidth(currentDisplay.text(), 7.5f);
        float labelMaxWidth = Math.max(0f, width - 10f - valueWidth);
        boolean hovered = MathUtils.isHovered(x, y, width, height, mouseX, mouseY);
        float offset = getMarqueeOffset(labelWidth, labelMaxWidth, hovered);
        float labelX = x + 4f;
        Render2D.startScissor(context, labelX, y, labelMaxWidth, height);
        Render2D.drawFont(context.getMatrices(),
                Fonts.REGULAR.getFont(7.5f),
                label,
                labelX - offset,
                y + 3f,
                textColor
        );
        if (offset > 0f && labelWidth > labelMaxWidth) {
            Render2D.drawFont(context.getMatrices(),
                    Fonts.REGULAR.getFont(7.5f),
                    label,
                    labelX - offset + labelWidth + MARQUEE_GAP,
                    y + 3f,
                    textColor
            );
        }
        Render2D.stopScissor(context);
        float valueX = labelX + Math.min(labelWidth, labelMaxWidth) + 1.5f;
        Render2D.drawFont(context.getMatrices(),
                Fonts.REGULAR.getFont(7.5f),
                currentDisplay.text(),
                valueX,
                y + 3f,
                currentDisplay.color()
        );

        if (openAnimation.getValue() > 0) {
            float animatedHeight = (setting.getValue().getClass().getEnumConstants().length * 14f) * (float)openAnimation.getValue();

            Render2D.startScissor(context, x, y + height, width, animatedHeight);

            float yOffset = height;
            for (Enum<?> enums : setting.getValue().getClass().getEnumConstants()) {
                Animation anim = pickAnimations.get(enums);
                anim.update(enums == setting.getValue());

                Color iconColor = ColorUtils.getTextColor((int) (255 * anim.getValue()));
                Render2D.drawFont(context.getMatrices(), Fonts.ICONSNEW.getFont(10f), "K", x + width - 14f, y + yOffset + 2f, iconColor);

                DisplayText display = resolveDisplay(enums, textColor);
                Render2D.drawFont(context.getMatrices(),
                        Fonts.REGULAR.getFont(7.5f),
                        display.text(),
                        x + 6f,
                        y + yOffset + 2f,
                        display.color()
                );
                yOffset += 14f;
            }

            Render2D.stopScissor(context);
        }
    }

    /** Link-режим: слева подпись, справа кнопка-чип с текущим значением и анимацией переписывания. */
    private void renderLink(DrawContext context, int mouseX, int mouseY) {
        Color themeText = ColorUtils.getTextColor(255);
        DisplayText disp = resolveDisplay(setting.getValue(), themeText);
        valueAnim.update(disp.text());

        float size = 7.6f;
        float chipH = LinkGuiSettingStyle.ROW_HEIGHT - 6f;
        float vw = Math.max(Fonts.MEDIUM.getWidth(valueAnim.getCurrent(), size),
                Fonts.MEDIUM.getWidth(valueAnim.getPrevious(), size));
        float chipW = MathUtils.clamp(vw + 14f, 38f, width * 0.62f);
        float chipX = x + width - chipW - 2f;
        float chipY = y + 3f;
        boolean hovered = MathUtils.isHovered(chipX, chipY, chipW, chipH, mouseX, mouseY);
        hoverAnimation.update(hovered);

        LinkGuiSettingStyle.drawRowLabel(context.getMatrices(), I18n.translate(setting.getName()),
                x + 2f, y, Math.max(10f, width - chipW - 8f));
        LinkGuiChrome.drawPillButton(context.getMatrices(), chipX, chipY, chipW, chipH, 5f, hoverAnimation.getValue());

        Render2D.startScissor(context, chipX + 1f, chipY, chipW - 2f, chipH);
        drawChipValue(context, chipX + chipW / 2f, chipY + chipH / 2f, size, disp.color());
        Render2D.stopScissor(context);
    }

    /** Текст значения: при смене старое уезжает вверх и гаснет, новое приезжает снизу (как в поиске). */
    private void drawChipValue(DrawContext context, float centerX, float centerY, float size, Color color) {
        float th = Fonts.MEDIUM.getHeight(size);
        float baseY = centerY - th / 2f;
        if (!valueAnim.isAnimating()) {
            String t = valueAnim.getCurrent();
            float tw = Fonts.MEDIUM.getWidth(t, size);
            Render2D.drawFont(context.getMatrices(), Fonts.MEDIUM.getFont(size), t, centerX - tw / 2f, baseY, color);
            return;
        }
        float p = Math.max(0f, Math.min(1f, valueAnim.progress()));
        float shift = th * 1.1f;
        String prev = valueAnim.getPrevious();
        String curr = valueAnim.getCurrent();
        float pw = Fonts.MEDIUM.getWidth(prev, size);
        float cw = Fonts.MEDIUM.getWidth(curr, size);
        Color cPrev = withAlpha(color, Math.round(color.getAlpha() * (1f - p)));
        Color cCurr = withAlpha(color, Math.round(color.getAlpha() * p));
        Render2D.drawFont(context.getMatrices(), Fonts.MEDIUM.getFont(size), prev, centerX - pw / 2f, baseY - shift * p, cPrev);
        Render2D.drawFont(context.getMatrices(), Fonts.MEDIUM.getFont(size), curr, centerX - cw / 2f, baseY + shift * (1f - p), cCurr);
    }

    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, alpha)));
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (LinkGuiRenderContext.isActive()) {
            // Весь row кликабелен; ЛКМ — следующее значение.
            if (button == 0 && MathUtils.isHovered(x, y, width, height, (float) mouseX, (float) mouseY)) {
                setting.increaseEnum();
            }
            return;
        }

        if (MathUtils.isHovered(x, y, width, height, (float) mouseX, (float) mouseY)) {
            if (button == 0) setting.increaseEnum();
            else if (button == 1) open = !open;
        }

        if (open && button == 0) {
            float yOffset = height;
            if (MathUtils.isHovered(x, y + height, width, addHeight.get(), (float)mouseX, (float)mouseY)) {
                for (Enum<?> enums : setting.getValue().getClass().getEnumConstants()) {
                    if (MathUtils.isHovered(x, y + yOffset, width, 14f, (float) mouseX, (float) mouseY)) {
                        setting.setEnumValue(((Nameable) enums).getName());
                        break;
                    }
                    yOffset += 14f;
                }
            }
        }
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {

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

    private DisplayText resolveDisplay(Enum<?> value, Color fallback) {
        if (value instanceof AutoSwap.ItemType itemType) {
            Text text = itemType.getDisplayName();
            Color color = fallback;
            TextColor styleColor = text.getStyle().getColor();
            if (styleColor != null) {
                color = new Color(styleColor.getRgb());
            }
            return new DisplayText(text.getString(), color);
        }
        return new DisplayText(I18n.translate(((Nameable) value).getName()), fallback);
    }

    private record DisplayText(String text, Color color) {}
}
