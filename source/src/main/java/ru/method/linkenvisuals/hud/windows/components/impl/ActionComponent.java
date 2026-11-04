package ru.method.linkenvisuals.hud.windows.components.impl;

import net.minecraft.client.gui.DrawContext;
import ru.method.linkenvisuals.hud.windows.components.WindowComponent;
import ru.method.linkenvisuals.utils.math.MathUtils;
import ru.method.linkenvisuals.utils.render.Render2D;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;

import java.awt.Color;

public class ActionComponent extends WindowComponent {

    private final String icon;
    private final Runnable action;
    private final Color baseColor;
    private static final float ICON_SIZE = 10.0f;
    private static final float TEXT_SIZE = 9.0f;
    private static final float PADDING_LEFT = 6f;
    private static final float PADDING_RIGHT = 6f;
    private static final float GAP = 6f;

    public ActionComponent(String text, String icon, Color baseColor, Runnable action) {
        super(text);
        this.icon = icon;
        this.action = action;
        this.baseColor = baseColor;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        float scaleValue = animation != null ? animation.getValue() : 1.0f;
        if (scaleValue <= 0.001f) return;

        int alpha = Math.max(0, Math.min(255, (int) (255 * scaleValue)));

        Color color = new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), alpha);

        float iconW = Fonts.ICONSNEW.getWidth(icon, ICON_SIZE);
        float textW = Fonts.MEDIUM.getWidth(getName(), TEXT_SIZE);
        float contentW = iconW + GAP + textW;

        float minX = x + PADDING_LEFT;
        float maxX = x + width - PADDING_RIGHT - contentW;
        float centeredX = x + (width - contentW) / 2f;
        float contentStartX = maxX < minX ? minX : MathUtils.clamp(centeredX, minX, maxX);

        float iconLineHeight = Fonts.ICONSNEW.getLineHeight(ICON_SIZE);
        float textLineHeight = Fonts.MEDIUM.getLineHeight(TEXT_SIZE);

        float iconY = y - 8 + (height - iconLineHeight) / 2f + Fonts.ICONSNEW.getAscender(ICON_SIZE);
        float textY = y - 7 + (height - textLineHeight) / 2f + Fonts.MEDIUM.getAscender(TEXT_SIZE);

        context.getMatrices().pushMatrix();
        float centerX = x + width / 2f;
        float centerY = y + height / 2f;
        context.getMatrices().translate(centerX, centerY);
        context.getMatrices().scale(scaleValue, scaleValue);
        context.getMatrices().translate(-centerX, -centerY);

        // Фон-кнопка в стилистике: скруглённая заливка цветом действия (красный для «Удалить»),
        // ярче при наведении — выглядит как кнопка, а не просто текст.
        boolean hovered = MathUtils.isHovered(x, y, width, height, (float) mouseX, (float) mouseY);
        float bgFactor = (hovered ? 0.34f : 0.16f) * scaleValue;
        int bgAlpha = Math.max(0, Math.min(255, Math.round(255 * bgFactor)));
        Render2D.drawRoundedRect(context.getMatrices(), x + 3f, y + 2f, width - 6f, height - 4f, 5f,
                new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), bgAlpha));

        Color contentColor = hovered
                ? new Color(Math.min(255, baseColor.getRed() + 30), Math.min(255, baseColor.getGreen() + 30), Math.min(255, baseColor.getBlue() + 30), alpha)
                : color;
        Render2D.drawFont(context.getMatrices(), Fonts.ICONSNEW.getFont(ICON_SIZE), icon, contentStartX, iconY, contentColor);
        Render2D.drawFont(context.getMatrices(), Fonts.MEDIUM.getFont(TEXT_SIZE), getName(), contentStartX + iconW + GAP, textY, contentColor);
        context.getMatrices().popMatrix();
    }

    public static float getPreferredWidth(String text, String icon) {
        if (text == null) text = "";
        if (icon == null) icon = "";
        float iconW = Fonts.ICONSNEW.getWidth(icon, ICON_SIZE);
        float textW = Fonts.MEDIUM.getWidth(text, TEXT_SIZE);
        return PADDING_LEFT + PADDING_RIGHT + iconW + GAP + textW;
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return;
        if (!MathUtils.isHovered(x, y, width, height, (float) mouseX, (float) mouseY)) return;
        try {
            action.run();
        } catch (Exception ignored) {}
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
