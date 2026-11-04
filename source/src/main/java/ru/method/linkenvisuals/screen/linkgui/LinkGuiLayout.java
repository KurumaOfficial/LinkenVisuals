package ru.method.linkenvisuals.screen.linkgui;

import net.minecraft.util.math.MathHelper;

/** Fixed 1280×720 design canvas — side panels aligned to main window. */
public final class LinkGuiLayout {
    public static final float CANVAS_WIDTH = 1280f;
    public static final float CANVAS_HEIGHT = 720f;

    public static final float RADIUS = 12f;
    public static final float MAIN_WIDTH = 720f;
    public static final float MAIN_HEIGHT = 480f;
    public static final float DOCK_WIDTH = 360f;
    public static final float DOCK_HEIGHT = 48f;
    public static final float DOCK_GAP = 12f;
    public static final float SIDE_WIDTH = 220f;
    public static final float THEME_WIDTH = 240f;
    public static final float MARGIN = 32f;
    public static final float MAIN_TOP = 100f;
    public static final float HEADER = 52f;
    public static final float MUSIC = 84f;

    public static final float INTERFACE_HEIGHT = 188f;
    public static final float PROFILE_HEIGHT = 240f;
    // Темы сеткой свотчей 2×N (компактнее списка) + селектор «GUI Style» и ряд «Custom Scoreboard»
    // внизу секции Interface.
    public static final float THEME_HEIGHT = 570f;
    public static final float CARD_HEIGHT = 86f;

    private static final float MIN_FIT_SCALE = 0.55f;
    private static final float SCREEN_PAD = 12f;

    public record Frame(
            float mainX, float mainY,
            float dockX, float dockY,
            float interfaceX, float interfaceY,
            float profileX, float profileY,
            float themeX, float themeY
    ) {}

    private LinkGuiLayout() {}

    public static Frame compute() {
        float mainX = (CANVAS_WIDTH - MAIN_WIDTH) / 2f;
        float mainY = MAIN_TOP;
        float dockX = (CANVAS_WIDTH - DOCK_WIDTH) / 2f;
        float dockY = mainY + MAIN_HEIGHT + DOCK_GAP;

        float interfaceX = MARGIN;
        float interfaceY = mainY;
        float profileX = MARGIN + 24f;
        float profileY = mainY; // лево-ВЕРХ (раньше был лево-низ)
        float themeX = CANVAS_WIDTH - MARGIN - THEME_WIDTH;
        float themeY = mainY + (MAIN_HEIGHT - THEME_HEIGHT) / 2f;

        return new Frame(mainX, mainY, dockX, dockY, interfaceX, interfaceY, profileX, profileY, themeX, themeY);
    }

    /** Screen fit factor at 100% design scale — multiply by {@code uiScale} separately. */
    public static float computeFitScale(float screenWidth, float screenHeight) {
        if (screenWidth <= 0f || screenHeight <= 0f) {
            return 1f;
        }
        float fitX = (screenWidth - SCREEN_PAD * 2f) / CANVAS_WIDTH;
        float fitY = (screenHeight - SCREEN_PAD * 2f) / CANVAS_HEIGHT;
        float rawFit = Math.min(fitX, fitY);
        if (rawFit >= 1f) {
            return 1f;
        }
        return MathHelper.clamp(rawFit, MIN_FIT_SCALE, 1f);
    }
}
