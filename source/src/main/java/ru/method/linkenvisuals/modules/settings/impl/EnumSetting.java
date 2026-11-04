package ru.method.linkenvisuals.modules.settings.impl;


import ru.method.linkenvisuals.modules.settings.Setting;
import ru.method.linkenvisuals.modules.settings.api.EnumConverter;
import ru.method.linkenvisuals.modules.settings.api.Nameable;

import java.util.function.Supplier;


public class EnumSetting<Value extends Enum<Value> & Nameable> extends Setting<Value> {

    public EnumSetting(String name, Value defaultValue) {
        super(name, defaultValue);
    }

    public EnumSetting(String name, Value defaultValue, Supplier<Boolean> visible) {
        super(name, defaultValue, visible);
    }

    public void increaseEnum() {
        setValue(EnumConverter.increaseEnum(value));
    }

    public String currentEnumName() {
        return value.getName();
    }

    public void setEnumValue(String value) {
        Value[] constants = this.value.getDeclaringClass().getEnumConstants();
        for (Value e : constants) {
            if (e.getName().equalsIgnoreCase(value)) {
                setValue(e);
                break;
            }
        }
    }
}
