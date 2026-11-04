package ru.method.linkenvisuals.screen.windowgui;

import java.awt.Color;

/** Drainwalk dwmenu palette (Theme.BLACK / ITheme defaults). */
public final class WindowGuiTheme {
    public static final Color BACKGROUND_FIRST = hex("#222128");
    public static final Color BACKGROUND_SECOND = hex("#111111");
    public static final Color TEXT_FIRST = hex("#FFFFFF");
    public static final Color TEXT_SECOND = hex("#A39BD6");
    public static final Color ADDITIONAL_FIRST = hex("#555170");
    public static final Color ADDITIONAL_SECOND = hex("#A39BD6");
    public static final Color ADDITIONAL_THIRD = hex("#C7BEFF");
    public static final Color UI_BORDER = hex("#55526E");
    public static final Color MODULE_BACKGROUND = hex("#25242D");
    public static final Color SLIDER_ADDITIONAL_FIRST = hex("#787878");
    public static final Color SLIDER_ADDITIONAL_SECOND = hex("#8E8E8E");

    private WindowGuiTheme() {}

    public static Color hex(String hex) {
        return Color.decode(hex);
    }
}
