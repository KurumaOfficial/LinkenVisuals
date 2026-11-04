package ru.method.linkenvisuals.hud.windows.components.impl;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.resource.language.I18n;
import ru.method.linkenvisuals.hud.windows.components.WindowComponent;
import ru.method.linkenvisuals.modules.settings.api.Nameable;
import ru.method.linkenvisuals.modules.settings.impl.EnumSetting;
import ru.method.linkenvisuals.utils.math.MathUtils;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import ru.method.linkenvisuals.utils.render.Render2D;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;

import java.awt.Color;

public class EnumComponent extends WindowComponent {

    private final EnumSetting<?> setting;

    public EnumComponent(String name, EnumSetting<?> setting) {
        super(name);
        this.setting = setting;
        this.visible = setting::isVisible;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        Color textColor = ColorUtils.getTextColor((int) (255 * animation.getValue()));
        String label = I18n.translate(getName());
        String value = resolveValueName();

        float valueWidth = Fonts.ROUNDBOLD.getWidth(value, 8f);
        float valueAreaWidth = valueWidth + 16f;
        drawMarqueeLabel(
                context,
                label,
                8f,
                x + 5f,
                y + 4f,
                Math.max(0f, width - valueAreaWidth - 5f),
                height,
                textColor,
                mouseX,
                mouseY,
                true
        );

        float valueX = x + width - valueWidth - 16f;
        Render2D.startScissor(context, valueX, y, valueWidth + 11f, height);
        Render2D.drawFont(
                context.getMatrices(),
                Fonts.ROUNDBOLD.getFont(8f),
                value,
                valueX,
                y + 4f,
                textColor
        );
        Render2D.stopScissor(context);

        Render2D.drawFont(
                context.getMatrices(),
                Fonts.ICONSNEW.getFont(10f),
                "J",
                x + width - 11f,
                y + 3f,
                textColor
        );
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (!MathUtils.isHovered(x, y, width, height, (float) mouseX, (float) mouseY)) return;
        if (button == 0 || button == 1) {
            setting.increaseEnum();
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

    private String resolveValueName() {
        Enum<?> value = setting.getValue();
        if (value instanceof Nameable nameable) {
            return I18n.translate(nameable.getName());
        }
        return value.name();
    }
}
