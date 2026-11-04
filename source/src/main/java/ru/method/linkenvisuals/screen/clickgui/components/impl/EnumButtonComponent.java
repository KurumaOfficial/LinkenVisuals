package ru.method.linkenvisuals.screen.clickgui.components.impl;

import ru.method.linkenvisuals.modules.settings.api.Nameable;
import ru.method.linkenvisuals.modules.settings.impl.EnumSetting;
import ru.method.linkenvisuals.screen.clickgui.components.Component;
import ru.method.linkenvisuals.utils.animations.Animation;
import ru.method.linkenvisuals.utils.animations.Easing;
import ru.method.linkenvisuals.utils.math.MathUtils;
import ru.method.linkenvisuals.utils.render.BorderRadius;
import ru.method.linkenvisuals.utils.render.ColorRGBA;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import ru.method.linkenvisuals.utils.render.Render2D;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.resource.language.I18n;

import java.awt.Color;

public class EnumButtonComponent extends Component {

    private final EnumSetting<?> setting;
    private final Animation hoverAnimation = new Animation(140, 1f, false, Easing.EASE_OUT_EXPO);

    public EnumButtonComponent(EnumSetting<?> setting) {
        super(setting.getName());
        this.setting = setting;
        this.visible = setting::isVisible;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        float buttonWidth = 44f;
        float buttonX = x + width - 48f;
        float buttonY = y + 2f;
        float buttonHeight = height - 4f;

        boolean hovered = MathUtils.isHovered(buttonX, buttonY, buttonWidth, buttonHeight, mouseX, mouseY);
        hoverAnimation.update(hovered);

        Color textColor = ColorUtils.getTextColor(255);
        String label = I18n.translate(setting.getName());
        float labelWidth = Fonts.REGULAR.getWidth(label, 7.5f);
        float labelMaxWidth = Math.max(0f, width - 54f);
        boolean rowHovered = MathUtils.isHovered(x, y, width, height, mouseX, mouseY);
        float offset = getMarqueeOffset(labelWidth, labelMaxWidth, rowHovered);
        float nameY = y + (height - Fonts.REGULAR.getHeight(7.5f)) / 2f + 1f;
        Render2D.startScissor(context, x + 4f, y, labelMaxWidth, height);
        Render2D.drawFont(context.getMatrices(), Fonts.REGULAR.getFont(7.5f), label, x + 4f - offset, nameY, textColor);
        if (offset > 0f && labelWidth > labelMaxWidth) {
            Render2D.drawFont(context.getMatrices(), Fonts.REGULAR.getFont(7.5f), label, x + 4f - offset + labelWidth + MARQUEE_GAP, nameY, textColor);
        }
        Render2D.stopScissor(context);

        float radius = 5f;
        float inset = 1f;
        float innerX = buttonX + inset;
        float innerY = buttonY + inset;
        float innerW = buttonWidth - (inset * 2f);
        float innerH = buttonHeight - (inset * 2f);
        float innerRadius = Math.max(0f, radius - inset);

        Color glass = ColorUtils.getGlassColor(255);
        Render2D.drawLiquidRectClickGuiChip(
                context.getMatrices(),
                innerX,
                innerY,
                innerW,
                innerH,
                new BorderRadius(innerRadius),
                new ColorRGBA(glass),
                2.5f,
                18.0f,
                11f,
                220.0f,
                true,
                0f,
                0.19f
        );

        int accentAlpha = (int) MathUtils.lerp(90f, 165f, (float) hoverAnimation.getValue());
        Render2D.drawRoundedRect(
                context.getMatrices(),
                innerX,
                innerY,
                innerW,
                innerH,
                innerRadius,
                ColorUtils.getGlobalColor(accentAlpha)
        );

        String display = I18n.translate(((Nameable) setting.getValue()).getName());
        display = fitText(display, buttonWidth - 8f);
        float textWidth = Fonts.MEDIUM.getWidth(display, 7f);
        float textHeight = Fonts.MEDIUM.getHeight(7f);
        float textX = buttonX + (buttonWidth - textWidth) / 2f;
        float textY = buttonY + (buttonHeight - textHeight) / 2f + 1f;
        Render2D.drawFont(context.getMatrices(), Fonts.MEDIUM.getFont(7f), display, textX, textY, textColor);
    }

    private String fitText(String text, float maxWidth) {
        if (Fonts.MEDIUM.getWidth(text, 7f) <= maxWidth) return text;

        String ellipsis = "...";
        float ellipsisWidth = Fonts.MEDIUM.getWidth(ellipsis, 7f);
        String trimmed = text;
        while (trimmed.length() > 1 && Fonts.MEDIUM.getWidth(trimmed, 7f) + ellipsisWidth > maxWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + ellipsis;
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && MathUtils.isHovered(x + width - 48f, y + 2f, 44f, height - 4f, (float) mouseX, (float) mouseY)) {
            setting.increaseEnum();
        }
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {}

    @Override
    public void keyPressed(int keyCode, int scanCode, int modifiers) {}

    @Override
    public void keyReleased(int keyCode, int scanCode, int modifiers) {}

    @Override
    public void charTyped(char chr, int modifiers) {}
}
