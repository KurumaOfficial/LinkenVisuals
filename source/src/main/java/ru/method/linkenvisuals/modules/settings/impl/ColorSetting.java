package ru.method.linkenvisuals.modules.settings.impl;


import ru.method.linkenvisuals.modules.settings.Setting;

import java.awt.Color;
import java.util.function.Supplier;


public class ColorSetting extends Setting<Color> {

    public ColorSetting(String name, Color defaultValue) {
        super(name, sanitize(defaultValue));
    }

    public ColorSetting(String name, Color defaultValue, Supplier<Boolean> visible) {
        super(name, sanitize(defaultValue), visible);
    }

    @Override
    public void setValue(Color value) {
        super.setValue(sanitize(value));
    }

    private static Color sanitize(Color value) {
        if (value == null) return Color.WHITE;
        return new Color(value.getRed(), value.getGreen(), value.getBlue(), value.getAlpha());
    }
}
