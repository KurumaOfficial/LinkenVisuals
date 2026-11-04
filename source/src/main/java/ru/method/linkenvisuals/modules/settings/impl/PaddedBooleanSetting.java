package ru.method.linkenvisuals.modules.settings.impl;


public class PaddedBooleanSetting extends BooleanSetting {

    private final float paddingX;

    public PaddedBooleanSetting(String name, Boolean defaultValue, float paddingX) {
        super(name, defaultValue);
        this.paddingX = paddingX;
    }

    public float getPaddingX() {
        return paddingX;
    }
}
