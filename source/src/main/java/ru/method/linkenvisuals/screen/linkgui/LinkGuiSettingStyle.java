package ru.method.linkenvisuals.screen.linkgui;

import lombok.experimental.UtilityClass;
import org.joml.Matrix3x2fStack;
import ru.method.linkenvisuals.screen.windowgui.WindowGuiColors;
import ru.method.linkenvisuals.screen.windowgui.WindowGuiRender;
import ru.method.linkenvisuals.screen.linkgui.theme.LinkGuiThemeScope;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;

import java.awt.Color;

@UtilityClass
public class LinkGuiSettingStyle {
    public static final float ROW_HEIGHT = 24f;
    public static final float LABEL_SIZE = 8.6f;
    public static final float VALUE_SIZE = 7.8f;

    public float rowCenterY(float rowY) {
        return rowY + (ROW_HEIGHT - textHeight(LABEL_SIZE)) / 2f;
    }

    public float textHeight(float size) {
        return WindowGuiRender.textHeight(Fonts.MEDIUM, size);
    }

    public void drawRowLabel(Matrix3x2fStack stack, String label, float x, float y, float maxWidth) {
        Color color = LinkGuiThemeScope.text(220);
        float textY = rowCenterY(y);
        String clipped = clipText(label, maxWidth, LABEL_SIZE);
        WindowGuiRender.drawText(stack, Fonts.MEDIUM, clipped, x, textY, color, LABEL_SIZE);
    }

    public void drawValueChip(Matrix3x2fStack stack, String text, float x, float y, float width, float height, float strength) {
        LinkGuiChrome.drawPillButton(stack, x, y, width, height, 5f, strength);
        float textW = Fonts.MEDIUM.getWidth(text, VALUE_SIZE);
        float textH = textHeight(VALUE_SIZE);
        WindowGuiRender.drawText(stack, Fonts.MEDIUM, text,
                x + (width - textW) / 2f, y + (height - textH) / 2f,
                LinkGuiThemeScope.text((int) (200 + 55 * strength)), VALUE_SIZE);
    }

    public void drawMiniToggle(Matrix3x2fStack stack, float x, float y, float progress) {
        LinkGuiChrome.drawToggle(stack, x, y, 30f, 15f, progress);
    }

    public String clipText(String text, float maxWidth, float size) {
        if (Fonts.MEDIUM.getWidth(text, size) <= maxWidth) return text;
        while (text.length() > 3 && Fonts.MEDIUM.getWidth(text + "...", size) > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        return text + "...";
    }

    public Color accent(float alpha) {
        Color accent = LinkGuiThemeScope.accent();
        return WindowGuiColors.withAlpha(accent, alpha);
    }
}
