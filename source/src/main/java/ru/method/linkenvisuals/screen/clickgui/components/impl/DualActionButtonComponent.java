package ru.method.linkenvisuals.screen.clickgui.components.impl;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.resource.language.I18n;
import ru.method.linkenvisuals.screen.clickgui.components.Component;
import ru.method.linkenvisuals.utils.math.MathUtils;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import ru.method.linkenvisuals.utils.render.Render2D;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;

import java.awt.Color;

public class DualActionButtonComponent extends Component {
    private static final float BUTTON_HEIGHT = 14f;
    private static final float BUTTON_GAP = 4f;

    private final String leftLabelKey;
    private final String rightLabelKey;
    private final Runnable leftAction;
    private final Runnable rightAction;

    public DualActionButtonComponent(String leftLabelKey, Runnable leftAction, String rightLabelKey, Runnable rightAction) {
        super(leftLabelKey + "|" + rightLabelKey);
        this.leftLabelKey = leftLabelKey;
        this.rightLabelKey = rightLabelKey;
        this.leftAction = leftAction;
        this.rightAction = rightAction;
        this.addHeight = () -> 4f;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        float buttonWidth = Math.max(0f, (width - BUTTON_GAP - 8f) / 2f);
        float leftX = x + 4f;
        float rightX = leftX + buttonWidth + BUTTON_GAP;
        float buttonY = y + 2f;

        renderButton(context, leftX, buttonY, buttonWidth, BUTTON_HEIGHT, leftLabelKey, mouseX, mouseY);
        renderButton(context, rightX, buttonY, buttonWidth, BUTTON_HEIGHT, rightLabelKey, mouseX, mouseY);
    }

    private void renderButton(DrawContext context, float x, float y, float width, float height, String labelKey, int mouseX, int mouseY) {
        boolean hovered = MathUtils.isHovered(x, y, width, height, mouseX, mouseY);
        Color background = hovered
                ? ColorUtils.alpha(ColorUtils.getPrimaryColor(), 95)
                : new Color(255, 255, 255, 24);
        Color outline = hovered
                ? ColorUtils.alpha(ColorUtils.getPrimaryColor(), 155)
                : ColorUtils.alpha(ColorUtils.getGlassOutlineColor(), 130);
        Color textColor = ColorUtils.getTextColor(245);

        Render2D.drawRoundedRect(context.getMatrices(), x, y, width, height, 4f, background);
        Render2D.drawRoundedRect(context.getMatrices(), x, y, width, 1f, 1f, outline);

        String label = I18n.translate(labelKey);
        float fontSize = 7.0f;
        float textWidth = Fonts.SEMIBOLD.getWidth(label, fontSize);
        float textHeight = Fonts.SEMIBOLD.getHeight(fontSize);
        float textX = x + (width - textWidth) / 2f;
        float textY = y + (height - textHeight) / 2f - 0.5f;
        Render2D.drawFont(context.getMatrices(), Fonts.SEMIBOLD.getFont(fontSize), label, textX, textY, textColor);
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return;

        float buttonWidth = Math.max(0f, (width - BUTTON_GAP - 8f) / 2f);
        float leftX = x + 4f;
        float rightX = leftX + buttonWidth + BUTTON_GAP;
        float buttonY = y + 2f;

        if (MathUtils.isHovered(leftX, buttonY, buttonWidth, BUTTON_HEIGHT, (float) mouseX, (float) mouseY)) {
            leftAction.run();
            return;
        }
        if (MathUtils.isHovered(rightX, buttonY, buttonWidth, BUTTON_HEIGHT, (float) mouseX, (float) mouseY)) {
            rightAction.run();
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
