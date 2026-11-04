package ru.method.linkenvisuals.modules.impl.render;

import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.impl.BooleanSetting;
import ru.method.linkenvisuals.modules.settings.impl.ColorSetting;
import ru.method.linkenvisuals.modules.settings.impl.NumberSetting;
import ru.method.linkenvisuals.utils.render.FogBlurRenderer;

import java.awt.Color;

public class FogBlur extends Module {

    private final NumberSetting fogStrength = new NumberSetting("settings.fogblur.strength", 6.0f, 1.0f, 20.0f, 1.0f);
    private final NumberSetting fogDistance = new NumberSetting("settings.fogblur.distance", 50.0f, 0.0f, 200.0f, 10.0f);
    private final BooleanSetting linearSampling = new BooleanSetting("settings.fogblur.linear_sampling", true);
    private final BooleanSetting rainbow = new BooleanSetting("settings.fogblur.rainbow", false);
    private final ColorSetting fogColor = new ColorSetting("settings.fogblur.color", new Color(120, 170, 255, 65), () -> !rainbow.getValue());
    private final NumberSetting rainbowOpacity = new NumberSetting("settings.fogblur.rainbow.opacity", 30.0f, 1.0f, 100.0f, 1.0f, rainbow::getValue);
    private final NumberSetting rainbowSaturation = new NumberSetting("settings.fogblur.rainbow.saturation", 70.0f, 0.0f, 100.0f, 1.0f, rainbow::getValue);
    private final NumberSetting rainbowBrightness = new NumberSetting("settings.fogblur.rainbow.brightness", 100.0f, 0.0f, 100.0f, 1.0f, rainbow::getValue);

    public FogBlur() {
        super("FogBlur", Category.Visuals);
    }

    @Override
    public void onDisable() {
        FogBlurRenderer.release();
        super.onDisable();
    }

    // FogBlur теперь вызывается напрямую из GameRendererMixin.onRenderWorld ДО отрисовки мод-геометрии
    // (Render3D: глифы/партиклы). Раньше он шёл через EventRenderWorld — ПОСЛЕ глифов/партиклов — и мылил их.
    public void renderCurrent() {
        FogBlurRenderer.render(
                fogStrength.getValue(),
                fogDistance.getValue(),
                linearSampling.getValue(),
                rainbow.getValue(),
                rainbowOpacity.getValue() / 100.0f,
                rainbowSaturation.getValue() / 100.0f,
                rainbowBrightness.getValue() / 100.0f,
                fogColor.getValue()
        );
    }
}
