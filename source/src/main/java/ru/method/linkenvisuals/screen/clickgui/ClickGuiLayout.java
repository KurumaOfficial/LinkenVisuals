package ru.method.linkenvisuals.screen.clickgui;

import net.minecraft.util.math.MathHelper;


public final class ClickGuiLayout {
    public static final float PANEL_WIDTH = 120f;
    public static final float PANEL_GAP = 4f;
    public static final float PANELS_Y_SHIFT = 125f;
    public static final float PANEL_OPEN_HEIGHT = 250f;
    public static final float PANEL_HEADER_HEIGHT = 20f;

    public static final float SEARCH_WIDTH = 150f;
    public static final float SEARCH_HEIGHT = 18f;
    public static final float SEARCH_Y_SHIFT = 140f;

    public static final float DESCRIPTION_Y_SHIFT = 165f;

    public static final float EDGE_PADDING = 4f;
    private static final float MIN_FIT_SCALE = 0.72f;
    private static final float FIT_RELAXATION = 0.42f;

    private ClickGuiLayout() {
    }

    public static float designWidth(int panelCount) {
        if (panelCount <= 0) {
            return PANEL_WIDTH;
        }
        return panelCount * PANEL_WIDTH + (panelCount - 1) * PANEL_GAP;
    }

    public static float halfHeightExtent() {
        float aboveCenter = PANELS_Y_SHIFT + 6f;
        float belowCenter = SEARCH_Y_SHIFT + SEARCH_HEIGHT;
        return Math.max(aboveCenter, belowCenter);
    }

    public static float halfWidthExtent(int panelCount) {
        return designWidth(panelCount) / 2f;
    }


    /** Screen fit factor at 100% design scale — multiply by {@code uiScale} separately. */
    public static float computeFitScale(float screenWidth, float screenHeight, int panelCount) {
        if (screenWidth <= 0f || screenHeight <= 0f) {
            return 1f;
        }

        float availableHalfWidth = Math.max(1f, screenWidth / 2f - EDGE_PADDING);
        float availableHalfHeight = Math.max(1f, screenHeight / 2f - EDGE_PADDING);

        float designHalfWidth = halfWidthExtent(panelCount);
        float designHalfHeight = halfHeightExtent();

        float fitX = availableHalfWidth / designHalfWidth;
        float fitY = availableHalfHeight / designHalfHeight;
        float rawFit = Math.min(1f, Math.min(fitX, fitY));
        if (rawFit >= 1f) {
            return 1f;
        }
        float relaxedFit = MathHelper.lerp(rawFit, 1f, FIT_RELAXATION);
        return MathHelper.clamp(relaxedFit, MIN_FIT_SCALE, 1f);
    }
}
