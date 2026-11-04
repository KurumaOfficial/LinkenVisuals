package ru.method.linkenvisuals.modules.impl.render;

import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.api.Nameable;
import ru.method.linkenvisuals.modules.settings.impl.BooleanSetting;
import ru.method.linkenvisuals.modules.settings.impl.ColorSetting;
import ru.method.linkenvisuals.modules.settings.impl.EnumSetting;
import ru.method.linkenvisuals.modules.settings.impl.NumberSetting;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import ru.method.linkenvisuals.utils.render.HandsRenderer;

import java.awt.Color;

public class Hands extends Module {

    public final EnumSetting<Mode> mode = new EnumSetting<>("modules.settings.hands.mode", Mode.OwnColor);
    public final BooleanSetting syncWithTheme = new BooleanSetting("settings.hands.sync_with_theme", true, this::usesShaderColor);
    public final ColorSetting shaderColor = new ColorSetting("settings.hands.shader_color", new Color(151, 71, 255, 255), () -> usesShaderColor() && !syncWithTheme.getValue());
    public final ColorSetting solidColor = new ColorSetting("modules.settings.hands.solid_color", new Color(0, 255, 0, 255), () -> mode.getValue() == Mode.OwnColor);
    public final ColorSetting solidColor2 = new ColorSetting("modules.settings.hands.solid_color2", new Color(255, 0, 0, 255), () -> mode.getValue() == Mode.OwnColor);
    public final NumberSetting gradientSpeed = new NumberSetting("modules.settings.hands.gradient_speed", 0.5f, 0.1f, 1.0f, 0.1f, this::usesAnimatedGradient);
    public final NumberSetting blurStrength = new NumberSetting("modules.settings.hands.blur_strength", 10.0f, 2.0f, 20.0f, 1.0f);

    public Hands() {
        super("Hands", Category.Visuals);
        getSettings().add(mode);
        getSettings().add(syncWithTheme);
        getSettings().add(shaderColor);
        getSettings().add(solidColor);
        getSettings().add(solidColor2);
        getSettings().add(gradientSpeed);
        getSettings().add(blurStrength);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        HandsRenderer.release();
    }

    public Color getResolvedShaderColor() {
        return syncWithTheme.getValue() ? ColorUtils.getPrimaryColor() : shaderColor.getValue();
    }

    public boolean usesAnimatedGradient() {
        return mode.getValue() != Mode.Transparent;
    }

    public boolean usesShaderColor() {
        return mode.getValue() == Mode.Plasma || mode.getValue() == Mode.Nebula || mode.getValue() == Mode.Cobweb;
    }

    public enum Mode implements Nameable {
        OwnColor("settings.hands.mode.own_color"),
        Transparent("settings.hands.mode.transparent"),
        Plasma("settings.hands.mode.plasma"),
        Nebula("settings.hands.mode.nebula"),
        Cobweb("settings.hands.mode.cobweb");

        private final String name;

        Mode(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }
}
