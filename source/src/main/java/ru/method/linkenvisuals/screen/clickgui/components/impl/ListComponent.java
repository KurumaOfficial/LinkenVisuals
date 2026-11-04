package ru.method.linkenvisuals.screen.clickgui.components.impl;

import ru.method.linkenvisuals.modules.settings.impl.BooleanSetting;
import ru.method.linkenvisuals.modules.settings.impl.ListSetting;
import ru.method.linkenvisuals.screen.clickgui.components.Component;
import ru.method.linkenvisuals.utils.animations.Animation;
import ru.method.linkenvisuals.utils.animations.Easing;
import ru.method.linkenvisuals.utils.math.MathUtils;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import ru.method.linkenvisuals.utils.render.Render2D;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.resource.language.I18n;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

public class ListComponent extends Component {

    private final ListSetting setting;
    private final Animation openAnimation = new Animation(180, 1f, false, Easing.EASE_OUT_EXPO);
    private final Map<BooleanSetting, Animation> pickAnimations = new HashMap<>();
    private boolean open;

    public ListComponent(ListSetting setting) {
        super(setting.getName());
        this.setting = setting;
        for (BooleanSetting s : setting.getValue()) {
            Animation toggleAnimation = new Animation(180, 1f, false, Easing.EASE_OUT_EXPO);
            toggleAnimation.snapTo(s.getValue());
            pickAnimations.put(s, toggleAnimation);
        }
        openAnimation.snapTo(false);
        this.addHeight = () -> openAnimation.getValue() > 0 ? ((setting.getValue().size() * 14f)) * (float)openAnimation.getValue() : 0;
        this.visible = setting::isVisible;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        openAnimation.update(open);
        Color textColor = ColorUtils.getTextColor(255);

        String label = I18n.translate(setting.getName());
        String count = "(" + setting.getToggled().size() + "/" + setting.getValue().size() + ")";
        float countWidth = Fonts.REGULAR.getWidth(count, 7.5f);
        float labelMaxWidth = Math.max(0f, width - 12f - countWidth);
        float labelWidth = Fonts.REGULAR.getWidth(label, 7.5f);
        boolean hovered = MathUtils.isHovered(x, y, width, height, mouseX, mouseY);
        float offset = getMarqueeOffset(labelWidth, labelMaxWidth, hovered);
        float labelX = x + 5f;
        Render2D.startScissor(context, labelX, y, labelMaxWidth, height);
        Render2D.drawFont(context.getMatrices(),
                Fonts.REGULAR.getFont(7.5f),
                label,
                labelX - offset,
                y + 3.5f,
                textColor
        );
        if (offset > 0f && labelWidth > labelMaxWidth) {
            Render2D.drawFont(context.getMatrices(),
                    Fonts.REGULAR.getFont(7.5f),
                    label,
                    labelX - offset + labelWidth + MARQUEE_GAP,
                    y + 3.5f,
                    textColor
            );
        }
        Render2D.stopScissor(context);

        Render2D.drawFont(context.getMatrices(),
                Fonts.REGULAR.getFont(7.5f),
                count,
                x + width - countWidth - 5f,
                y + 3.5f,
                textColor
        );

        if (openAnimation.getValue() > 0) {
            float yOffset = height;
            for (BooleanSetting setting : setting.getValue()) {
                Animation anim = pickAnimations.get(setting);
                anim.update(setting.getValue());

                Color iconColor = ColorUtils.getTextColor((int) (255 * anim.getValue()));
                Render2D.drawFont(context.getMatrices(), Fonts.ICONSNEW.getFont(10f), "D", x + width - 14f, y + yOffset + 3.5f, iconColor);
                Render2D.drawFont(context.getMatrices(),
                        Fonts.REGULAR.getFont(7.5f),
                        I18n.translate(setting.getName()),
                        x + 6f,
                        y + yOffset + 3.5f,
                        textColor
                );
                yOffset += 14f;
            }
        }
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (MathUtils.isHovered(x, y, width, height, (float) mouseX, (float) mouseY) && button == 1) open = !open;

        if (openAnimation.getValue() > 0 && button == 0) {
            float yOffset = height;
            for (BooleanSetting setting : setting.getValue()) {
                if (MathUtils.isHovered(x, y + yOffset, width, 14f, (float) mouseX, (float) mouseY)) {
                    setting.setValue(!setting.getValue());
                    break;
                }

                yOffset += 14f;
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
}
