package ru.method.linkenvisuals.screen.clickgui.components.impl;

import ru.method.linkenvisuals.modules.settings.impl.BooleanSetting;
import ru.method.linkenvisuals.modules.settings.impl.PaddedBooleanSetting;
import ru.method.linkenvisuals.screen.clickgui.components.Component;
import ru.method.linkenvisuals.utils.animations.Animation;
import ru.method.linkenvisuals.utils.animations.Easing;
import ru.method.linkenvisuals.utils.math.MathUtils;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;
import ru.method.linkenvisuals.screen.linkgui.LinkGuiSettingStyle;
import ru.method.linkenvisuals.utils.render.LinkGuiRenderContext;
import ru.method.linkenvisuals.utils.render.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.resource.language.I18n;

import java.awt.Color;

public class BooleanComponent extends Component {

    private final BooleanSetting setting;
    private final Animation toggleAnimation = new Animation(180, 1f, true, Easing.EASE_OUT_EXPO);

    public BooleanComponent(BooleanSetting setting) {
        super(setting.getName());
        this.setting = setting;
        this.visible = setting::isVisible;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        toggleAnimation.update(setting.getValue());

        if (LinkGuiRenderContext.isActive()) {
            String label = I18n.translate(setting.getName());
            LinkGuiSettingStyle.drawRowLabel(context.getMatrices(), label, x + 2f, y, width - 40f);
            LinkGuiSettingStyle.drawMiniToggle(context.getMatrices(), x + width - 34f, y + 4.5f, toggleAnimation.getValue());
            return;
        }

        Color textColor = ColorUtils.getTextColor(255);
        Color switchBgColor = new Color(23, 23, 23, 100);
        Color knobColor = ColorUtils.getTextColor(255);
        boolean hovered = MathUtils.isHovered(x, y, width, height, mouseX, mouseY);

        float paddingX = 0f;
        if (setting instanceof PaddedBooleanSetting padded) {
            paddingX = padded.getPaddingX();
        }
        String label = I18n.translate(setting.getName());
        float labelX = x + 4f + paddingX;
        float labelMaxWidth = Math.max(0f, width - 26f - paddingX);
        float labelWidth = Fonts.REGULAR.getWidth(label, 7.5f);
        float offset = getMarqueeOffset(labelWidth, labelMaxWidth, hovered);
        Render2D.startScissor(context, labelX, y, labelMaxWidth, height);
        Render2D.drawFont(context.getMatrices(), Fonts.REGULAR.getFont(7.5f), label, labelX - offset, y + 3f, textColor);
        if (offset > 0f && labelWidth > labelMaxWidth) {
            Render2D.drawFont(context.getMatrices(), Fonts.REGULAR.getFont(7.5f), label, labelX - offset + labelWidth + MARQUEE_GAP, y + 3f, textColor);
        }
        Render2D.stopScissor(context);
        Render2D.drawRoundedRect(context.getMatrices(), x + width - 20f, y + 3.5f, 16f, 8f, 2.5f, switchBgColor);
        Render2D.drawRoundedRect(context.getMatrices(), x + width - 20f, y + 3.5f, 16f * (float)toggleAnimation.getValue(), 8f, 2.5f, ColorUtils.getGlobalColor((int) (255 * toggleAnimation.getLinear())));
        Render2D.drawRoundedRect(context.getMatrices(), x + width - 19.5f + (8f * (float)toggleAnimation.getValue()), y + 4f, 7f, 7f, 2.5f, knobColor);
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (LinkGuiRenderContext.isActive()) {
            if (MathUtils.isHovered(x + width - 34f, y + 4.5f, 30f, 15f, (float) mouseX, (float) mouseY) && button == 0) {
                setting.setValue(!setting.getValue());
            }
            return;
        }
        if (MathUtils.isHovered(x + width - 20f, y + 3.5f, 16f, 8f, (float) mouseX, (float) mouseY) && button == 0) setting.setValue(!setting.getValue());
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
}
