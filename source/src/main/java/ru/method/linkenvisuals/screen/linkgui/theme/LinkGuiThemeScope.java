package ru.method.linkenvisuals.screen.linkgui.theme;

import ru.method.linkenvisuals.utils.render.ColorUtils;

import java.awt.Color;

/**
 * Scoped Link GUI palette for render helpers. Theme colors are synced to {@link ColorUtils}
 * via {@link LinkGuiThemeManager} so HUD and sliders follow the selected preset.
 */
public final class LinkGuiThemeScope {
    private static final ThreadLocal<LinkGuiThemePreset> ACTIVE = new ThreadLocal<>();

    private LinkGuiThemeScope() {
    }

    public static void begin(LinkGuiThemePreset preset) {
        ACTIVE.set(preset == null ? LinkGuiThemePreset.MIDNIGHT : preset);
    }

    public static void end() {
        ACTIVE.remove();
    }

    public static Color accent() {
        LinkGuiThemePreset preset = ACTIVE.get();
        if (preset == null) return ColorUtils.getPrimaryColor();
        return transition(LinkGuiThemeManager.getPrevious().primary(), preset.primary());
    }

    public static Color text() {
        LinkGuiThemePreset preset = ACTIVE.get();
        if (preset == null) return ColorUtils.getTextColor();
        return transition(LinkGuiThemeManager.getPrevious().text(), preset.text());
    }

    public static Color text(int alpha) {
        Color base = text();
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), Math.max(0, Math.min(255, alpha)));
    }

    public static Color background() {
        LinkGuiThemePreset preset = ACTIVE.get();
        if (preset == null) return ColorUtils.getBackgroundColor();
        return transition(LinkGuiThemeManager.getPrevious().background(), preset.background());
    }

    /** Плавная интерполяция цвета во время смены темы. */
    private static Color transition(Color from, Color to) {
        float t = LinkGuiThemeManager.transitionProgress();
        if (from == null || t >= 1f) {
            return to;
        }
        int r = Math.round(from.getRed() + (to.getRed() - from.getRed()) * t);
        int g = Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * t);
        int b = Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * t);
        return new Color(r, g, b);
    }
}
