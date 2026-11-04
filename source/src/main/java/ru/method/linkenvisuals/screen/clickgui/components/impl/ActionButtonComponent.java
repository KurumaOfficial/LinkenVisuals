package ru.method.linkenvisuals.screen.clickgui.components.impl;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.resource.language.I18n;
import ru.method.linkenvisuals.modules.settings.impl.ActionSetting;
import ru.method.linkenvisuals.screen.clickgui.components.Component;
import ru.method.linkenvisuals.utils.math.MathUtils;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import ru.method.linkenvisuals.utils.render.Render2D;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;

import java.awt.Color;

public class ActionButtonComponent extends Component {
    private static final float BUTTON_HEIGHT = 15f;
    private final ActionSetting setting;

    public ActionButtonComponent(ActionSetting setting) {
        super(setting.getName());
        this.setting = setting;
        this.addHeight = () -> 4f;
        this.visible = setting::isVisible;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        float buttonX = x + 4f;
        float buttonY = y + 2f;
        float buttonW = width - 8f;
        boolean hovered = MathUtils.isHovered(buttonX, buttonY, buttonW, BUTTON_HEIGHT, mouseX, mouseY);
        Color background = hovered ? ColorUtils.alpha(ColorUtils.getPrimaryColor(), 105) : new Color(255, 255, 255, 26);
        Color outline = hovered ? ColorUtils.alpha(ColorUtils.getPrimaryColor(), 170) : ColorUtils.alpha(ColorUtils.getGlassOutlineColor(), 130);
        String label = I18n.translate(setting.getLabelKey());

        Render2D.drawRoundedRect(context.getMatrices(), buttonX, buttonY, buttonW, BUTTON_HEIGHT, 5f, background);
        Render2D.drawBorder(context.getMatrices(), buttonX, buttonY, buttonW, BUTTON_HEIGHT, 5f, 0.55f, new ru.method.linkenvisuals.utils.render.ColorRGBA(outline));

        float fontSize = 7.2f;
        float textW = Fonts.SEMIBOLD.getWidth(label, fontSize);
        float textH = Fonts.SEMIBOLD.getHeight(fontSize);
        Render2D.drawFont(context.getMatrices(), Fonts.SEMIBOLD.getFont(fontSize), label, buttonX + (buttonW - textW) / 2f, buttonY + (BUTTON_HEIGHT - textH) / 2f - 0.5f, ColorUtils.getTextColor(245));
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return;
        if (MathUtils.isHovered(x + 4f, y + 2f, width - 8f, BUTTON_HEIGHT, (float) mouseX, (float) mouseY)) {
            setting.run();
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

    @Override
    public float getLayoutHeight() {
        return BUTTON_HEIGHT + 4f;
    }
}
