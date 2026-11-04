package ru.method.linkenvisuals.utils.render;

import ru.method.linkenvisuals.screen.clickgui.ClickGuiLayout;

public final class LiquidPanelPresets {

    public static final float CLICKGUI_PANEL_WIDTH = ClickGuiLayout.PANEL_WIDTH;
    public static final float CLICKGUI_PANEL_HEADER = ClickGuiLayout.PANEL_HEADER_HEIGHT;
    public static final float CLICKGUI_PANEL_OPEN = ClickGuiLayout.PANEL_OPEN_HEIGHT;
    public static final float CLICKGUI_SEARCH_WIDTH = ClickGuiLayout.SEARCH_WIDTH;
    public static final float CLICKGUI_SEARCH_HEIGHT = ClickGuiLayout.SEARCH_HEIGHT;
    public static final float CLICKGUI_SETTINGS_WIDTH = 120f;
    public static final float CLICKGUI_SETTINGS_HEIGHT = 170f;
    /** Inner glass area of bind / enum chips (after 1px inset). */
    public static final float CLICKGUI_CHIP_WIDTH = 42f;
    public static final float CLICKGUI_CHIP_HEIGHT = 12f;
    /** Fixed color picker overlay (see {@code ColorComponent} layout constants). */
    public static final float CLICKGUI_COLOR_PICKER_WIDTH = 152f;
    public static final float CLICKGUI_COLOR_PICKER_HEIGHT = 195f;

    public static final float HUD_TILE = 18f;
    public static final float TARGET_HUD_WIDTH = 116f;
    public static final float TARGET_HUD_HEIGHT = 31f;

    private static final float[] WIDTH_PRESETS = {
            10f, 18f, 20f, 42f, 90f, 98f, 100f, 110f, 116f, 120f, 130f, 140f, 150f, 152f, 160f, 170f, 180f, 200f, 220f, 250f
    };

    private static final float[] HEIGHT_PRESETS = {
            6f, 8f, 10f, 12f, 14f, 18f, 20f, 22f, 24f, 27f, 31f, 32f, 36f, 40f, 50f, 60f, 70f, 80f,
            100f, 120f, 150f, 170f, 195f, 200f, 250f
    };

    private LiquidPanelPresets() {
    }

    public static float snapWidth(float value) {
        return snapPreset(value, WIDTH_PRESETS);
    }

    public static float snapHeight(float value) {
        return snapPreset(value, HEIGHT_PRESETS);
    }

    private static float snapPreset(float value, float[] presets) {
        if (value <= 0f) {
            return value;
        }
        float best = presets[0];
        float bestDistance = Math.abs(value - best);
        for (float preset : presets) {
            float distance = Math.abs(value - preset);
            if (distance < bestDistance) {
                best = preset;
                bestDistance = distance;
            }
        }
        return best;
    }
}
